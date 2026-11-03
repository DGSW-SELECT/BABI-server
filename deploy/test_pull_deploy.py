import hashlib
import io
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
import zipfile

import pull_deploy as deploy


class PullDeployTest(unittest.TestCase):
    revision = "a" * 40

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.patcher = patch.object(deploy, "ROOT", self.root)
        self.patcher.start()
        self.addCleanup(self.patcher.stop)
        (self.root / "babi-server.jar").write_bytes(b"previous working JAR")
        (self.root / "deployed-revision").write_text("b" * 40)
        self.candidate = self.root / "candidate.jar"
        with zipfile.ZipFile(self.candidate, "w") as jar:
            jar.writestr("META-INF/build-info.properties", "build.commit=" + self.revision)
            jar.writestr("BOOT-INF/classes/application-aws.yml", "")
            jar.writestr("BOOT-INF/classes/com/select/BabiServerApplication.class", b"class")

    def release(self):
        content = self.candidate.read_bytes()
        return {
            "draft": False, "tag_name": "aws-" + self.revision, "target_commitish": self.revision,
            "assets": [{"name": "babi-server.jar", "size": len(content),
                        "browser_download_url": f"https://github.com/{deploy.REPOSITORY}/releases/download/aws-{self.revision}/babi-server.jar",
                        "digest": "sha256:" + hashlib.sha256(content).hexdigest()}],
        }

    def test_success_records_exact_revision_and_preserves_previous_jar(self):
        with patch.object(deploy.subprocess, "run"), patch.object(deploy, "ready", return_value=True):
            deploy.install_jar(self.candidate, self.revision)
        self.assertEqual(self.revision, (self.root / "deployed-revision").read_text().strip())
        self.assertEqual(b"previous working JAR", (self.root / "babi-server.previous.jar").read_bytes())

    def test_failed_readiness_restores_previous_jar_and_does_not_advance_revision(self):
        with patch.object(deploy.subprocess, "run") as restart, patch.object(deploy, "ready", return_value=False):
            with self.assertRaisesRegex(RuntimeError, "readiness"):
                deploy.install_jar(self.candidate, self.revision)
        self.assertEqual(2, restart.call_count)
        self.assertEqual(b"previous working JAR", (self.root / "babi-server.jar").read_bytes())
        self.assertEqual("b" * 40, (self.root / "deployed-revision").read_text())
        self.assertEqual(self.revision, (self.root / "failed-revision").read_text().strip())

    def test_wrong_embedded_revision_never_restarts_running_api(self):
        with patch.object(deploy.subprocess, "run") as restart:
            with self.assertRaisesRegex(ValueError, "commit"):
                deploy.install_jar(self.candidate, "c" * 40)
        restart.assert_not_called()
        self.assertEqual(b"previous working JAR", (self.root / "babi-server.jar").read_bytes())

    def test_restart_command_failure_also_restores_previous_jar(self):
        with patch.object(deploy.subprocess, "run", side_effect=[subprocess.CalledProcessError(1, "systemctl"), None]):
            with self.assertRaises(subprocess.CalledProcessError):
                deploy.install_jar(self.candidate, self.revision)
        self.assertEqual(b"previous working JAR", (self.root / "babi-server.jar").read_bytes())
        self.assertEqual(self.revision, (self.root / "failed-revision").read_text().strip())

    def test_corrupt_download_never_replaces_running_jar(self):
        release = self.release()
        release["assets"][0]["digest"] = "sha256:" + "0" * 64
        with patch.object(deploy, "api_json", side_effect=[{"sha": self.revision}, release]), \
                patch.object(deploy, "urlopen", return_value=io.BytesIO(self.candidate.read_bytes())), \
                patch.object(deploy, "install_jar") as install:
            with self.assertRaisesRegex(ValueError, "checksum"):
                deploy.deploy_latest()
        install.assert_not_called()

    def test_newer_main_during_download_is_not_replaced_by_old_release(self):
        with patch.object(deploy, "api_json", side_effect=[{"sha": self.revision}, self.release(), {"sha": "c" * 40}]), \
                patch.object(deploy, "urlopen", return_value=io.BytesIO(self.candidate.read_bytes())), \
                patch.object(deploy, "install_jar") as install:
            deploy.deploy_latest()
        install.assert_not_called()

    def test_missing_release_waits_without_restarting_api(self):
        with patch.object(deploy, "api_json", side_effect=[{"sha": self.revision}, HTTPError("url", 404, "pending", {}, None)]), \
                patch.object(deploy, "install_jar") as install:
            deploy.deploy_latest()
        install.assert_not_called()

    def test_failed_revision_is_not_restarted_every_five_minutes(self):
        (self.root / "failed-revision").write_text(self.revision)
        with patch.object(deploy, "api_json", return_value={"sha": self.revision}) as api:
            deploy.deploy_latest()
        self.assertEqual(1, api.call_count)


if __name__ == "__main__":
    unittest.main()
