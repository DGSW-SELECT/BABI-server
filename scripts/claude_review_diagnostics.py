"""Report sanitized failed SDK results without publishing tool output or credentials."""
import json
import os
from pathlib import Path
import re


def normalize_token(raw):
    token = "".join(raw.split())
    if not re.fullmatch(r"sk-ant-oat01-[A-Za-z0-9_-]+", token):
        raise ValueError("CLAUDE_CODE_OAUTH_TOKEN must contain only the OAuth token from claude setup-token.")
    return token


def failure_message(path, secrets=()):
    try:
        messages = json.loads(path.read_text())
    except (OSError, ValueError):
        return "Claude failed; execution diagnostics are unavailable."
    if not isinstance(messages, list):
        return "Claude failed; execution diagnostics have an unexpected format."
    results = [message for message in messages if isinstance(message, dict) and message.get("type") == "result"]
    text = json.dumps([{key: message.get(key) for key in ("result", "errors", "error", "error_status")}
                       for message in results]).lower()
    if re.search(r"\b401\b", text) or any(value in text for value in ("authentication_failed", "authentication_error", "invalid api key", "oauth token", "not logged in", "invalid auth token")):
        return "Claude authentication failed. Refresh CLAUDE_CODE_OAUTH_TOKEN using claude setup-token and update the repository secret."
    if re.search(r"\b429\b", text) or any(value in text for value in ("rate_limit", "usage limit", "out of extra usage", "hit your limit")):
        return "Claude usage limit reached. Check the account limits before retrying."
    if re.search(r"\b403\b", text):
        return "Claude account access denied. Check subscription and model access."
    if any(value in text for value in ("model not found", "model_not_found", "unknown model")):
        return "Claude model unavailable. Check the configured model and account access."
    if any(value in text for value in ("timeout", "timed out", "connection error")):
        return "Claude provider connection failed or timed out. Check service availability."
    failed = [message for message in results if message.get("is_error") is True or message.get("subtype") != "success"]
    excerpts = [message.get("result") for message in failed if isinstance(message.get("result"), str)]
    if not excerpts:
        return "Claude failed with an unclassified SDK error. No failed-result text is available."
    excerpt = " ".join(excerpts[-1].split())
    for secret in secrets:
        if secret:
            excerpt = excerpt.replace(secret, "[REDACTED]")
    excerpt = re.sub(r"(?:sk-[A-Za-z0-9_-]+|gh[pousr]_[A-Za-z0-9]+|github_pat_[A-Za-z0-9_]+|Bearer\s+\S+)", "[REDACTED]", excerpt, flags=re.IGNORECASE)
    excerpt = re.sub(r"https?://\S+|[\w.+-]+@[\w.-]+", "[REDACTED]", excerpt)
    return "Claude failed: " + excerpt[:300].replace("%", "%25")


if __name__ == "__main__":
    path = os.getenv("CLAUDE_EXECUTION_FILE") or str(Path(os.environ["RUNNER_TEMP"]) / "claude-execution-output.json")
    print("::error::" + failure_message(Path(path), (os.getenv("CLAUDE_CODE_OAUTH_TOKEN"),)))
