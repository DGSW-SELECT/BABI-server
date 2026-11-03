#!/usr/bin/env python3
"""Deploy tested public main releases without AWS or GitHub credentials."""

import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import zipfile

ROOT = Path("/opt/babi")
REPOSITORY = "DGSW-SELECT/BABI-server"
API = "https://api.github.com/repos/" + REPOSITORY
MAX_JAR_BYTES = 150 * 1024 * 1024


def api_json(path):
    request = Request(API + path, headers={"Accept": "application/vnd.github+json", "User-Agent": "babi-deploy"})
    with urlopen(request, timeout=20) as response:
        return json.load(response)


def verify_jar(path, revision):
    with zipfile.ZipFile(path) as jar:
        info = jar.read("META-INF/build-info.properties").decode()
        if f"build.commit={revision}" not in info.splitlines():
            raise ValueError("JAR commit does not match the requested revision")
        jar.getinfo("BOOT-INF/classes/application-aws.yml")
        jar.getinfo("BOOT-INF/classes/com/select/BabiServerApplication.class")
        if any(name.startswith("BOOT-INF/lib/h2-") for name in jar.namelist()):
            raise ValueError("Deployment JAR must not include H2")


def ready():
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        try:
            with urlopen("http://127.0.0.1:8080/v3/api-docs", timeout=2) as response:
                paths = json.load(response)["paths"]
            if all(path in paths for path in ("/auth/login", "/auth/signup", "/auth/email/send", "/auth/email/verify")):
                try:
                    urlopen("http://127.0.0.1:8080/babi/api/babies/1/daily-reports/2026-11-03", timeout=2).close()
                except HTTPError as error:
                    if error.code == 401:
                        return True
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(2)
    return False


def install_jar(candidate, revision):
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Invalid commit SHA")
    verify_jar(candidate, revision)
    current = ROOT / "babi-server.jar"
    previous = ROOT / "babi-server.previous.jar"
    shutil.copy2(current, previous)
    candidate.chmod(0o644)
    os.replace(candidate, current)
    try:
        subprocess.run(["systemctl", "restart", "babi-server"], check=True, timeout=60)
        if not ready():
            raise RuntimeError("New API failed readiness checks")
    except Exception:
        rollback = ROOT / "babi-server.rollback.jar"
        shutil.copy2(previous, rollback)
        os.replace(rollback, current)
        (ROOT / "failed-revision").write_text(revision + "\n")
        subprocess.run(["systemctl", "restart", "babi-server"], check=True, timeout=60)
        raise
    marker = ROOT / "deployed-revision.next"
    marker.write_text(revision + "\n")
    os.replace(marker, ROOT / "deployed-revision")
    (ROOT / "failed-revision").unlink(missing_ok=True)
    print("Deployed", revision, flush=True)


def deploy_latest():
    revision = api_json("/commits/main")["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Invalid main commit SHA")
    for marker in ("deployed-revision", "failed-revision"):
        if (ROOT / marker).exists() and (ROOT / marker).read_text().strip() == revision:
            return
    tag = "aws-" + revision
    try:
        release = api_json("/releases/tags/" + tag)
    except HTTPError as error:
        if error.code == 404:
            error.close()
            print("Waiting for a tested release of", revision, flush=True)
            return
        raise
    if release["draft"] or release["tag_name"] != tag or release["target_commitish"] != revision:
        raise ValueError("Release does not target current main")
    asset = next(asset for asset in release["assets"] if asset["name"] == "babi-server.jar")
    url = f"https://github.com/{REPOSITORY}/releases/download/{tag}/babi-server.jar"
    if asset["browser_download_url"] != url or not 0 < asset["size"] <= MAX_JAR_BYTES:
        raise ValueError("Invalid release asset")
    digest = asset.get("digest", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("Release asset has no valid SHA-256 digest")
    with tempfile.TemporaryDirectory(prefix="deploy-", dir=ROOT) as directory:
        candidate = Path(directory) / "babi-server.jar"
        hasher = hashlib.sha256()
        size = 0
        with urlopen(url, timeout=60) as response, candidate.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                if size > asset["size"]:
                    raise ValueError("Release download exceeds expected size")
                hasher.update(chunk)
                output.write(chunk)
        if size != asset["size"] or "sha256:" + hasher.hexdigest() != digest:
            raise ValueError("Release download failed checksum verification")
        if api_json("/commits/main")["sha"] != revision:
            print("Main changed during download; skipping stale release", flush=True)
            return
        install_jar(candidate, revision)


if __name__ == "__main__":
    with (ROOT / "deploy.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        deploy_latest()
