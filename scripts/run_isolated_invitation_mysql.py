#!/usr/bin/env python3
"""Run invitation tests on a new disposable MySQL container, never the local BABI DB."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
DB = "babi_invitation_isolated"
USER = "babi_isolated_tester"


def call(*args, **kwargs):
    return subprocess.run(args, check=True, text=True, **kwargs)


def main():
    name = "babi-isolated-mysql-test-" + uuid.uuid4().hex[:12]
    root_password = secrets.token_hex(24)
    password = secrets.token_hex(24)
    marker = secrets.token_hex(24)
    container = None
    try:
        result = call("docker", "run", "-d", "--rm", "--pull=never", "--name", name,
                      "--tmpfs", "/var/lib/mysql:rw", "-p", "127.0.0.1::3306",
                      "-e", "MYSQL_ROOT_PASSWORD=" + root_password,
                      "-e", "MYSQL_DATABASE=" + DB, "-e", "MYSQL_USER=" + USER,
                      "-e", "MYSQL_PASSWORD=" + password, "mysql:8.4", capture_output=True)
        container = result.stdout.strip()
        if not container or len(container) != 64:
            raise RuntimeError("Unexpected container ID; refusing to continue.")
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            ready = subprocess.run(["docker", "exec", container, "sh", "-c",
                'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin -uroot ping --silent'],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Isolated MySQL did not become ready.")
        port_text = call("docker", "port", container, "3306/tcp", capture_output=True).stdout.strip()
        if not port_text.startswith("127.0.0.1:"):
            raise RuntimeError("Unexpected published port; refusing to continue.")
        port = int(port_text.rsplit(":", 1)[1])
        if port == 3307:
            raise RuntimeError("Refusing to use the development MySQL port.")
        schema = (ROOT / "src/test/resources/mysql/invitation-base.sql").read_text()
        schema += "\n" + (ROOT / "docs/schema/baby-invitations.mysql.sql").read_text()
        schema += "\nCREATE TABLE isolation_guard (run_token CHAR(48) NOT NULL); INSERT INTO isolation_guard VALUES ('" + marker + "');\n"
        call("docker", "exec", "-i", container, "sh", "-c",
             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$MYSQL_DATABASE"', input=schema)
        print(f"Isolated MySQL 8.4 ready: 127.0.0.1:{port}/{DB}; tmpfs data; no shared volume.", flush=True)
        env = {k: v for k, v in os.environ.items() if not (
            k.startswith("SPRING_") or k.startswith("BABI_ISOLATED_MYSQL_") or
            k in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "BABI_REDIS_TEST_PORT", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "JWT_SECRET", "EMAIL_VERIFICATION_SECRET", "MAIL_USERNAME", "MAIL_PASSWORD"})}
        env.update(BABI_ISOLATED_MYSQL_TEST="enabled", BABI_ISOLATED_MYSQL_PORT=str(port),
                   BABI_ISOLATED_MYSQL_PASSWORD=password, BABI_ISOLATED_MYSQL_MARKER=marker)
        call("./gradlew", "--no-daemon", "test", "--rerun-tasks", "--tests",
             "com.select.domain.baby.service.BabyInvitationMySqlIntegrationTest", cwd=ROOT, env=env)
        result_file = ROOT / "build/test-results/test/TEST-com.select.domain.baby.service.BabyInvitationMySqlIntegrationTest.xml"
        saved_results = ROOT / "build/invitation-mysql-results"
        saved_results.mkdir(parents=True, exist_ok=True)
        shutil.copy2(result_file, saved_results / result_file.name)
    finally:
        if container:
            subprocess.run(["docker", "stop", container], check=False, stdout=subprocess.DEVNULL)
            print("Stopped only the isolated test container; its tmpfs was discarded.", flush=True)


if __name__ == "__main__":
    main()
