#!/usr/bin/env python3
"""Retired manual test entry point; never contacts shared MySQL or Redis."""
import sys

if __name__ == "__main__":
    print("The legacy manual MySQL/Redis path is disabled because it used shared services. "
          "Run python3 scripts/run_isolated_invitation_mysql.py instead.", file=sys.stderr)
    sys.exit(2)
