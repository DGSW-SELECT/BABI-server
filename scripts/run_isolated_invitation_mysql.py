#!/usr/bin/env python3
"""Run invitation tests using the final CREATE schema in disposable MySQL 8.4."""
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    if len(sys.argv) != 1:
        raise SystemExit("Usage: python3 scripts/run_isolated_invitation_mysql.py")
    subprocess.run([sys.executable, str(ROOT / "scripts/check_final_schema_isolated.py"),
                    str(ROOT / "src/main/resources/schema.sql"), "--app-test"],
                   cwd=ROOT, check=True)
    result = ROOT / "build/test-results/test/TEST-com.select.domain.baby.service.BabyInvitationMySqlIntegrationTest.xml"
    destination = ROOT / "build/invitation-mysql-results"
    destination.mkdir(parents=True, exist_ok=True)
    shutil.copy2(result, destination / result.name)


if __name__ == "__main__":
    main()
