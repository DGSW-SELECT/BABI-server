#!/usr/bin/env python3
"""Inspect final CREATE statements in a disposable MySQL 8.4 instance, never shared databases."""
import os
import re
import secrets
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


def run(*args, **kw):
    return subprocess.run(args, text=True, check=True, **kw)


def main():
    if len(sys.argv) not in (2, 3) or (len(sys.argv) == 3 and sys.argv[2] != '--app-test'):
        raise SystemExit('Usage: python3 scripts/check_final_schema_isolated.py /path/to/babi_DB_Real.sql [--app-test]')
    source = Path(sys.argv[1]).read_text(encoding='utf-8')
    # Strip SQL line comments before splitting; only CREATE TABLE / VIEW statements are accepted.
    stripped = re.sub(r'(?m)^\s*--.*$', '', source)
    statements = [s.strip() for s in stripped.split(';') if s.strip()]
    creates = [s for s in statements if re.match(r'(?is)^CREATE\s+(?:TABLE|OR\s+REPLACE\s+VIEW)\b', s)]
    if len(creates) < 10 or any(re.search(r'(?im)^\s*(?:DROP|DELETE|TRUNCATE|ALTER|UPDATE|INSERT|USE)\b', s) for s in creates):
        raise RuntimeError('Unexpected SQL; refusing to initialize test database')
    name = 'babi-final-schema-check-' + uuid.uuid4().hex[:12]
    password = secrets.token_hex(24)
    user_password = secrets.token_hex(24)
    marker = secrets.token_hex(24)
    container = None
    try:
        container = run('docker', 'run', '-d', '--rm', '--pull=never', '--name', name,
                        '--tmpfs', '/var/lib/mysql:rw', '-p', '127.0.0.1::3306', '-e', 'MYSQL_ROOT_PASSWORD=' + password,
                        '-e', 'MYSQL_DATABASE=babi_invitation_isolated', '-e', 'MYSQL_USER=babi_isolated_tester',
                        '-e', 'MYSQL_PASSWORD=' + user_password, 'mysql:8.4', capture_output=True).stdout.strip()
        if not re.fullmatch('[0-9a-f]{64}', container):
            raise RuntimeError('Unexpected isolated container id')
        mysql = ['docker', 'exec', '-i', container, 'sh', '-c',
                 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot babi_invitation_isolated --batch --skip-column-names']
        for _ in range(120):
            p = subprocess.run(mysql, input='SELECT 1;', text=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if p.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Isolated MySQL did not become ready')
        run(*mysql, input=';\n'.join(creates) + ';\n')
        check = """SELECT CONCAT(table_name, '.', column_name, ':', column_type, ':', is_nullable)
          FROM information_schema.columns WHERE table_schema=DATABASE() AND
          ((table_name='allocated_baby_invitation_codes' AND column_name IN ('code','allocated_at')) OR
           (table_name='baby_invitation_codes' AND column_name IN ('id','baby_id','code','max_uses','used_count')) OR
           (table_name='baby_email_invitations' AND column_name IN ('id','invited_email','token_hash')))
          ORDER BY table_name, ordinal_position;"""
        output = run(*mysql, input=check, capture_output=True).stdout
        print('Isolated MySQL 8.4; original CREATE statements:', len(creates))
        print(output, end='')
        if 'baby_invitation_codes.max_uses:int:NO' not in output or 'baby_email_invitations.token_hash:char(64):NO' not in output:
            raise RuntimeError('Expected final SQL constraints missing')
        if len(sys.argv) == 3:
            run(*mysql, input="CREATE TABLE isolation_guard (run_token CHAR(48) NOT NULL); INSERT INTO isolation_guard VALUES ('" + marker + "');")
            port_text = run('docker', 'port', container, '3306/tcp', capture_output=True).stdout.strip()
            if not re.fullmatch(r'127\.0\.0\.1:[0-9]+', port_text):
                raise RuntimeError('Unexpected isolated port')
            port = port_text.rsplit(':', 1)[1]
            if port in ('3306', '3307'):
                raise RuntimeError('Refusing development database port')
            env = {k: v for k, v in os.environ.items() if not (k.startswith('SPRING_') or k.startswith('BABI_ISOLATED_MYSQL_') or k in {'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'GRADLE_OPTS', 'BABI_REDIS_TEST_PORT', 'MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'JWT_SECRET', 'EMAIL_VERIFICATION_SECRET', 'MAIL_USERNAME', 'MAIL_PASSWORD'})}
            env.update(BABI_ISOLATED_MYSQL_TEST='enabled', BABI_ISOLATED_MYSQL_PORT=port,
                       BABI_ISOLATED_MYSQL_PASSWORD=user_password, BABI_ISOLATED_MYSQL_MARKER=marker)
            run('./gradlew', '--no-daemon', 'test', '--rerun-tasks', '--tests',
                'com.select.domain.baby.service.BabyInvitationMySqlIntegrationTest',
                '--tests', 'com.select.domain.care.CareRecordMySqlIntegrationTest',
                '--tests', 'com.select.domain.report.DailyReportGenerationMySqlIntegrationTest',
                cwd=Path(__file__).resolve().parents[1], env=env)
            results = Path(__file__).resolve().parents[1] / 'build/test-results/test'
            for test_class in ['com.select.domain.baby.service.BabyInvitationMySqlIntegrationTest',
                               'com.select.domain.care.CareRecordMySqlIntegrationTest',
                               'com.select.domain.report.DailyReportGenerationMySqlIntegrationTest']:
                suite = ET.parse(results / ('TEST-' + test_class + '.xml')).getroot()
                if int(suite.get('tests', '0')) == 0 or any(int(suite.get(key, '0')) for key in ['skipped', 'errors', 'failures']):
                    raise RuntimeError('Isolated MySQL suite did not run cleanly: ' + test_class)
                print('Verified MySQL suite:', test_class, suite.get('tests'), 'tests, no skips')
    finally:
        if container:
            subprocess.run(['docker', 'stop', container], stdout=subprocess.DEVNULL, check=False)
            print('Stopped isolated test container and discarded tmpfs.')


if __name__ == '__main__':
    main()
