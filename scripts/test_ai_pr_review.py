import base64
import copy
import json
import tempfile
import subprocess
import sys
import unittest
import urllib.error
from pathlib import Path
from unittest.mock import MagicMock, patch

from scripts import ai_pr_review as review

HEAD = "a" * 40
BASE = "b" * 40


def result(decision="APPROVE", findings=None, head=HEAD):
    return {"decision": decision, "reviewed_commit": head, "summary": "리뷰 완료", "findings": findings or []}


class ReviewPolicyTest(unittest.TestCase):
    def setUp(self):
        self.env = {"CLAUDE_AVAILABLE": "true", "CLAUDE_OUTCOME": "success", "CLAUDE_RESULT": json.dumps(result()),
                    "REPOSITORY": "owner/repo", "EXPECTED_HEAD": HEAD, "PR_NUMBER": "7", "ACTOR": "maintainer",
                    "GITHUB_TOKEN": "github-test-secret", "DEEPSEEK_API_KEY": ""}
        self.pull = {"draft": False, "head": {"sha": HEAD, "repo": {"full_name": "owner/repo"}}, "base": {"sha": BASE}}
        self.posts = []
        self.permission = "write"
        self.forbidden = False
        self.github = patch.object(review, "github", side_effect=self.api).start()
        self.checks = patch.object(review, "ci_passed", return_value=True).start()
        self.provider = patch.object(review, "deepseek_review", return_value=result()).start()
        patch.object(review, "request_text", return_value="diff --git a/test b/test").start()
        patch("builtins.print").start()
        self.addCleanup(patch.stopall)

    def api(self, path, token, payload=None):
        self.assertEqual("github-test-secret", token)
        if "/collaborators/" in path:
            return {"permission": self.permission}
        if "/contents/AGENTS.md" in path:
            return {"content": base64.b64encode(b"Approved project instructions").decode()}
        if path.endswith("/reviews"):
            if self.forbidden and payload["event"] == "APPROVE":
                raise urllib.error.HTTPError("https://api.github.com/", 403, "Forbidden", {}, None)
            self.posts.append(payload)
            return {"id": 1}
        self.assertEqual("repos/owner/repo/pulls/7", path)
        return copy.deepcopy(self.pull)

    def test_missing_all_credentials_passes_without_network_or_approval(self):
        self.env.update(CLAUDE_AVAILABLE="false", CLAUDE_OUTCOME="skipped", CLAUDE_RESULT="")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "summary.md"
            self.env["GITHUB_STEP_SUMMARY"] = str(path)
            self.assertEqual("SKIPPED", review.main(self.env))
            self.assertIn("PASS; no approval", path.read_text())
        self.github.assert_not_called()
        self.provider.assert_not_called()
        self.assertEqual([], self.posts)

    def test_skipped_claude_cannot_use_a_stale_success_result_to_approve(self):
        self.env.update(CLAUDE_AVAILABLE="false", CLAUDE_OUTCOME="skipped")
        self.assertEqual("SKIPPED", review.main(self.env))
        self.github.assert_not_called()

    def test_clean_authenticated_claude_review_and_green_ci_create_approval(self):
        self.assertEqual("REVIEWED", review.main(self.env))
        self.assertEqual("APPROVE", self.posts[0]["event"])
        self.assertEqual(HEAD, self.posts[0]["commit_id"])
        self.checks.assert_called_once()
        self.provider.assert_not_called()

    def test_findings_or_incomplete_review_never_approve(self):
        for value in (result("COMMENT"), result(findings=["file.java:1 authorization missing"])):
            with self.subTest(value=value):
                self.env["CLAUDE_RESULT"] = json.dumps(value)
                review.main(self.env)
                self.assertEqual("COMMENT", self.posts[-1]["event"])
        self.checks.assert_not_called()

    def test_failed_or_pending_ci_withholds_approval(self):
        self.checks.return_value = False
        review.main(self.env)
        self.assertEqual("COMMENT", self.posts[0]["event"])

    def test_ci_api_error_withholds_approval(self):
        self.checks.side_effect = urllib.error.URLError("offline")
        review.main(self.env)
        self.assertEqual("COMMENT", self.posts[0]["event"])

    def test_changed_head_during_review_cannot_be_approved(self):
        def changed(*args):
            self.pull["head"]["sha"] = "c" * 40
            return True
        self.checks.side_effect = changed
        self.assertEqual("SKIPPED", review.main(self.env))
        self.assertEqual([], self.posts)

    def test_wrong_commit_or_malformed_claude_output_cannot_approve(self):
        for raw in ("not JSON", json.dumps(result(head=BASE)), "{}"):
            with self.subTest(raw=raw):
                self.env["CLAUDE_RESULT"] = raw
                self.assertEqual("SKIPPED", review.main(self.env))
        self.github.assert_not_called()

    def test_no_claude_token_uses_deepseek_for_comment_only(self):
        self.env.update(CLAUDE_AVAILABLE="false", CLAUDE_OUTCOME="skipped", DEEPSEEK_API_KEY="deepseek-test-secret")
        review.main(self.env)
        self.provider.assert_called_once()
        self.assertEqual("COMMENT", self.posts[0]["event"])
        self.assertIn("DeepSeek fallback", self.posts[0]["body"])
        self.checks.assert_not_called()

    def test_failed_claude_uses_deepseek_without_approval(self):
        self.env.update(CLAUDE_OUTCOME="failure", DEEPSEEK_API_KEY="deepseek-test-secret")
        review.main(self.env)
        self.assertEqual("COMMENT", self.posts[0]["event"])

    def test_failed_fallback_passes_without_a_fake_review(self):
        self.env.update(CLAUDE_OUTCOME="failure", DEEPSEEK_API_KEY="deepseek-test-secret")
        self.provider.side_effect = urllib.error.URLError("provider offline")
        self.assertEqual("SKIPPED", review.main(self.env))
        self.assertEqual([], self.posts)

    def test_read_only_actor_cannot_trigger_fallback_or_approval(self):
        self.permission = "read"
        self.env.update(CLAUDE_OUTCOME="failure", DEEPSEEK_API_KEY="deepseek-test-secret")
        self.assertEqual("SKIPPED", review.main(self.env))
        self.provider.assert_not_called()
        self.assertEqual([], self.posts)

    def test_draft_fork_and_stale_event_are_skipped(self):
        for change in ("draft", "fork", "head"):
            with self.subTest(change=change):
                original = copy.deepcopy(self.pull)
                if change == "draft": self.pull["draft"] = True
                if change == "fork": self.pull["head"]["repo"]["full_name"] = "outside/repo"
                if change == "head": self.pull["head"]["sha"] = BASE
                self.assertEqual("SKIPPED", review.main(self.env))
                self.pull = original
        self.assertEqual([], self.posts)

    def test_bot_actor_cannot_trigger_approval(self):
        self.env["ACTOR"] = "untrusted[bot]"
        self.assertEqual("SKIPPED", review.main(self.env))
        self.github.assert_not_called()

    def test_repository_approval_denial_falls_back_to_comment(self):
        self.forbidden = True
        review.main(self.env)
        self.assertEqual(["COMMENT"], [post["event"] for post in self.posts])

    def test_review_body_redacts_credentials_before_publication(self):
        value = result("COMMENT")
        value["summary"] = "github-test-secret sk-ant-oat01-private-token Bearer private-token"
        self.env["CLAUDE_RESULT"] = json.dumps(value)
        review.main(self.env)
        body = self.posts[0]["body"]
        for secret in ("github-test-secret", "sk-ant-oat01-private-token", "Bearer private-token"):
            self.assertNotIn(secret, body)
        self.assertIn("[REDACTED]", body)


class ProviderContractTest(unittest.TestCase):
    def test_real_entrypoint_passes_with_no_credentials(self):
        process = subprocess.run([sys.executable, "-m", "scripts.ai_pr_review"], env={},
                                 cwd=Path(__file__).resolve().parent.parent, capture_output=True, text=True)
        self.assertEqual(0, process.returncode, process.stderr)
        self.assertIn("SKIPPED", process.stdout)
        self.assertIn("PASS; no approval", process.stdout)

    def test_optional_credentials_and_wrapped_tokens(self):
        self.assertEqual(("", ""), review.credentials(" \n", ""))
        self.assertEqual(("", "deepseek-key"), review.credentials("invalid", " deepseek-\nkey "))
        self.assertEqual(("sk-ant-oat01-test", ""), review.credentials("sk-ant-oat01-\ntest", "bad\x00key"))

    def test_deepseek_request_is_bounded_json_mode_and_does_not_embed_credentials(self):
        response = {"choices": [{"finish_reason": "stop", "message": {"content": json.dumps(result())}}]}
        with patch.object(review, "request_text", return_value=json.dumps(response)) as request:
            self.assertEqual(result(), review.deepseek_review("diff", "rules", HEAD, "private-key", "deepseek-flash"))
        url, token, payload = request.call_args.args
        self.assertEqual("https://api.deepseek.com/chat/completions", url)
        self.assertEqual("private-key", token)
        self.assertEqual({"type": "json_object"}, payload["response_format"])
        self.assertEqual(4096, payload["max_tokens"])
        self.assertNotIn("private-key", json.dumps(payload))

    def test_blank_truncated_wrong_commit_or_invalid_provider_results_are_rejected(self):
        responses = [{}, {"choices": []}, {"choices": [{"finish_reason": "length", "message": {"content": "{}"}}]},
                     {"choices": [{"finish_reason": "stop", "message": {"content": "   "}}]},
                     {"choices": [{"finish_reason": "stop", "message": {"content": json.dumps(result(head=BASE))}}]}]
        for response in responses:
            with self.subTest(response=response), patch.object(review, "request_text", return_value=json.dumps(response)):
                with self.assertRaises(ValueError): review.deepseek_review("diff", "rules", HEAD, "key", "deepseek-flash")

    def test_provider_error_is_not_retried(self):
        with patch.object(review, "request_text", side_effect=urllib.error.URLError("offline")) as request:
            with self.assertRaises(OSError): review.deepseek_review("diff", "rules", HEAD, "key", "deepseek-flash")
            request.assert_called_once()

    def test_oversize_diff_is_not_sent_to_provider(self):
        with patch.object(review, "request_text") as request:
            with self.assertRaises(ValueError): review.deepseek_review("x" * (review.MAX_DIFF + 1), "rules", HEAD, "key", "deepseek-flash")
            request.assert_not_called()

    def test_review_shape_and_types_are_validated(self):
        for update in ({"decision": "PASS"}, {"summary": ""}, {"summary": "x" * 3001}, {"findings": "none"},
                       {"findings": [4]}, {"findings": [""]}, {"findings": ["x" * 1001]}, {"findings": ["x"] * 16}):
            with self.subTest(update=update), self.assertRaises(ValueError):
                review.parse_review(json.dumps(result() | update), HEAD)

    def test_http_timeout_headers_response_bound_and_redirect_rejection(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.read.return_value = b'{}'
        with patch.object(review.urllib.request, "build_opener") as build:
            build.return_value.open.return_value = response
            review.request_text("https://api.deepseek.com/chat/completions", "private-key", {"model": "deepseek-flash"})
            request = build.return_value.open.call_args.args[0]
            self.assertEqual("Bearer private-key", request.get_header("Authorization"))
            self.assertEqual(45, build.return_value.open.call_args.kwargs["timeout"])
            response.read.assert_called_once_with(review.MAX_RESPONSE + 1)
            response.read.return_value = b'x' * (review.MAX_RESPONSE + 1)
            with self.assertRaises(ValueError): review.request_text("https://api.deepseek.com/", "private-key")
        self.assertIsNone(review.NoRedirect().redirect_request(None, None, 302, "redirect", {}, "https://outside.invalid"))


class CiGateTest(unittest.TestCase):
    def test_green_build_ignores_its_own_review_job(self):
        checks = {"total_count": 2, "check_runs": [{"name": "review", "status": "in_progress"},
                                                 {"name": "build", "status": "completed", "conclusion": "success"}]}
        with patch.object(review, "github", return_value=checks): self.assertTrue(review.ci_passed("owner/repo", HEAD, "token"))

    def test_failed_ci_incomplete_list_or_timeout_withhold_approval(self):
        for checks in ({"total_count": 1, "check_runs": [{"name": "test", "status": "completed", "conclusion": "failure"}]},
                       {"total_count": 101, "check_runs": []},
                       {"total_count": 1, "check_runs": [{"name": "test", "status": "in_progress"}]}):
            with self.subTest(checks=checks), patch.object(review, "github", return_value=checks):
                self.assertFalse(review.ci_passed("owner/repo", HEAD, "token", timeout=0))


if __name__ == "__main__":
    unittest.main()
