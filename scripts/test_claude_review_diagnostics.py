import json
from pathlib import Path
import tempfile
import unittest

from claude_review_diagnostics import failure_message


class ClaudeReviewDiagnosticsTest(unittest.TestCase):
    def result(self, text):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            path.write_text(json.dumps([{"type": "result", "is_error": True, "result": text}]))
            return failure_message(path)

    def test_reports_authentication_failure_without_echoing_credentials_or_annotations(self):
        message = self.result("API Error: 401 OAuth access token is invalid. private-secret\n::notice::injected")
        self.assertIn("authentication failed", message)
        self.assertNotIn("private-secret", message)
        self.assertNotIn("::notice::", message)

    def test_distinguishes_usage_access_model_and_connection_failures(self):
        for error, expected in [("API Error 429 rate_limit", "usage limit"), ("API Error 403", "access denied"),
                                ("404 model not found", "model unavailable"), ("connection timed out", "timed out")]:
            with self.subTest(error=error):
                self.assertIn(expected, self.result(error))

    def test_unknown_error_does_not_dump_provider_output(self):
        self.assertNotIn("private-secret", self.result("unexpected private-secret"))

    def test_session_metadata_cannot_be_misclassified_as_an_http_error(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "execution.json"
            path.write_text(json.dumps([{"type": "result", "is_error": True, "result": "unexpected error",
                                        "session_id": "401", "duration_ms": 429, "total_cost_usd": 0.403}]))
            self.assertIn("unclassified", failure_message(path))

    def test_missing_and_invalid_files_produce_safe_diagnostics(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "missing.json"
            self.assertIn("unavailable", failure_message(path))
            path.write_text("private-secret malformed JSON")
            self.assertIn("unavailable", failure_message(path))
            path.write_text('{"secret":"private-secret"}')
            self.assertIn("unexpected format", failure_message(path))


if __name__ == "__main__":
    unittest.main()
