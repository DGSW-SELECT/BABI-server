"""Classify failed SDK results without publishing model output or credentials."""
import json
import os
from pathlib import Path
import re


def failure_message(path):
    try:
        messages = json.loads(path.read_text())
    except (OSError, ValueError):
        return "Claude failed; execution diagnostics are unavailable."
    if not isinstance(messages, list):
        return "Claude failed; execution diagnostics have an unexpected format."
    results = [message for message in messages if isinstance(message, dict) and message.get("type") == "result"]
    text = json.dumps([{key: message.get(key) for key in ("result", "errors", "error", "error_status")}
                       for message in results]).lower()
    if re.search(r"\b401\b", text) or any(value in text for value in ("authentication_failed", "authentication_error", "invalid api key", "oauth token")):
        return "Claude authentication failed. Refresh CLAUDE_CODE_OAUTH_TOKEN using claude setup-token and update the repository secret."
    if re.search(r"\b429\b", text) or any(value in text for value in ("rate_limit", "usage limit", "out of extra usage")):
        return "Claude usage limit reached. Check the account limits before retrying."
    if re.search(r"\b403\b", text):
        return "Claude account access denied. Check subscription and model access."
    if any(value in text for value in ("model not found", "model_not_found", "unknown model")):
        return "Claude model unavailable. Check the configured model and account access."
    if any(value in text for value in ("timeout", "timed out", "connection error")):
        return "Claude provider connection failed or timed out. Check service availability."
    return "Claude failed with an unclassified SDK error. Execution output remains private."


if __name__ == "__main__":
    path = os.getenv("CLAUDE_EXECUTION_FILE") or str(Path(os.environ["RUNNER_TEMP"]) / "claude-execution-output.json")
    print("::error::" + failure_message(Path(path)))
