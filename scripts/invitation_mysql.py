#!/usr/bin/env python3
"""Isolated local MySQL/Redis setup. Never executes DDL/DML against babi."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / "work"
ENV = WORK / "invitation-mysql.env"
STATE = WORK / "invitation-mysql-verification.json"
MYSQL = "babi-server-mysql-1"
REDIS = "babi-server-redis-1"
DB = "babi_invitation_test"
USER = "babi_invitation_tester"
BASE_TABLES = ["users", "babies", "baby_members"]
NEW_TABLES = ["baby_invitation_codes", "allocated_baby_invitation_codes", "baby_email_invitations"]
PREFIX = "babi_invitation_test:"


def private_write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "w") as stream:
        stream.write(text)


def settings():
    return dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))


def sql(statement, test=False, allow_failure=False):
    if test:
        command = 'read -r MYSQL_PWD; export MYSQL_PWD; exec mysql --batch --raw --skip-column-names -u babi_invitation_tester babi_invitation_test'
        data = settings()["INVITATION_TEST_DB_PASSWORD"] + "\n" + statement
    else:
        command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'
        data = statement
    result = subprocess.run(["docker", "exec", "-i", MYSQL, "sh", "-c", command], input=data,
                            text=True, capture_output=True)
    if result.returncode and not allow_failure:
        # Never echo submitted SQL or credentials on failure.
        raise RuntimeError("MySQL command failed; details suppressed to protect credentials. No cleanup/reset was attempted.")
    return result


def redis(*args, database=15):
    result = subprocess.run(["docker", "exec", REDIS, "redis-cli", "--raw", "-n", str(database), *args],
                            capture_output=True, check=True)
    return result.stdout


def redis_protected_snapshot():
    result = {}
    for database in (0, 15):
        digest = hashlib.sha256()
        keys = sorted(redis("--scan", database=database).splitlines())
        protected = [key for key in keys if not (database == 15 and key.startswith(PREFIX.encode()))]
        for key in protected:
            digest.update(key + b"\0")
            digest.update(redis("DUMP", key.decode(), database=database))
        result[str(database)] = {"keys": len(protected), "sha256": digest.hexdigest()}
    return result


def source_snapshot():
    tables = sql('SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA="babi" ORDER BY TABLE_NAME;').stdout.splitlines()
    if sorted(tables) != sorted(BASE_TABLES):
        raise RuntimeError("Unexpected source tables; stop for review.")
    definitions = {}
    counts = {}
    for table in BASE_TABLES:
        definitions[table] = sql(f"SHOW CREATE TABLE babi.`{table}`;").stdout.split("\t", 1)[1].strip()
        counts[table] = int(sql(f"SELECT COUNT(*) FROM babi.`{table}`;").stdout.strip())
    command = ('MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --no-create-info --skip-triggers '
               '--single-transaction --skip-lock-tables --skip-comments --skip-add-locks --skip-disable-keys '
               '--skip-extended-insert --order-by-primary --hex-blob --no-tablespaces '
               '--set-gtid-purged=OFF --column-statistics=0 babi')
    dump = subprocess.run(["docker", "exec", MYSQL, "sh", "-c", command], capture_output=True)
    if dump.returncode:
        raise RuntimeError("Read-only source fingerprint failed; no database changes performed by this check.")
    # Source records are hashed only in memory, never printed or copied into test storage/files.
    return {"tables": tables, "rows": counts,
            "schema_sha256": hashlib.sha256(json.dumps(definitions, sort_keys=True).encode()).hexdigest(),
            "data_sha256": hashlib.sha256(dump.stdout).hexdigest()}


def normalize_ddl(ddl):
    return re.sub(r" AUTO_INCREMENT=\d+", "", ddl).strip()


def setup():
    if ENV.exists() or STATE.exists():
        raise RuntimeError("Local test setup files already exist. Use verify/run; existing credentials are never overwritten.")
    if sql(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{DB}';").stdout.strip():
        raise RuntimeError("Test database already exists. It will not be reset or overwritten.")
    if sql(f"SELECT User FROM mysql.user WHERE User='{USER}';").stdout.strip():
        raise RuntimeError("Test user already exists. Existing grants/passwords will not be changed.")
    if sql('SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA="babi" AND REFERENCED_TABLE_NAME IS NOT NULL;').stdout.strip() != "0":
        raise RuntimeError("Source contains foreign keys; CREATE TABLE LIKE requires manual FK review first.")
    if redis("--scan", "--pattern", PREFIX + "*").strip():
        raise RuntimeError("Test Redis namespace already exists; will not overwrite its data.")
    ddl = (ROOT / "docs/schema/baby-invitations.mysql.sql").read_text()
    ddl = "\n".join(line for line in ddl.splitlines() if not line.lstrip().startswith("--"))
    statements = [value.strip() for value in ddl.split(";") if value.strip()]
    names = [re.match(r"CREATE TABLE (\w+)\s*\(", value) for value in statements]
    if len(names) != 3 or any(match is None for match in names) or {m[1] for m in names} != set(NEW_TABLES):
        raise RuntimeError("Unexpected invitation DDL; stop for review.")
    if re.search(r"\b(DROP|ALTER|INSERT|UPDATE|DELETE|USE)\b", ddl, re.I) or "babi." in ddl:
        raise RuntimeError("Unsafe or unexpected SQL in invitation schema.")
    baseline = {"source": source_snapshot(), "redis_protected": redis_protected_snapshot()}
    values = {"INVITATION_TEST_DB_PASSWORD": secrets.token_hex(32),
              "INVITATION_TEST_JWT_SECRET": secrets.token_hex(32),
              "INVITATION_TEST_EMAIL_SECRET": secrets.token_hex(32),
              "INVITATION_TEST_ACCOUNT_PASSWORD": "Te" + secrets.token_hex(7) + "1!",
              "INVITATION_TEST_PORT": "18081"}
    private_write(ENV, "".join(f"{key}={value}\n" for key, value in values.items()))
    private_write(STATE, json.dumps(baseline, indent=2) + "\n")
    # All schema mutations explicitly name the new test schema or use its selected database.
    sql(f"CREATE DATABASE `{DB}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    for table in BASE_TABLES:
        sql(f"CREATE TABLE `{DB}`.`{table}` LIKE `babi`.`{table}`;")
        original = sql(f"SHOW CREATE TABLE babi.`{table}`;").stdout.split("\t", 1)[1]
        cloned = sql(f"SHOW CREATE TABLE `{DB}`.`{table}`;").stdout.split("\t", 1)[1]
        if normalize_ddl(original) != normalize_ddl(cloned):
            raise RuntimeError("Cloned structure differs; stop without resetting any database.")
    sql(f"USE `{DB}`;\n" + ";\n".join(statements) + ";")
    sql(f"CREATE USER '{USER}'@'%' IDENTIFIED BY '{values['INVITATION_TEST_DB_PASSWORD']}';\n"
        f"GRANT SELECT, INSERT, UPDATE, DELETE ON `{DB}`.* TO '{USER}'@'%';")
    if any(int(sql(f"SELECT COUNT(*) FROM `{DB}`.`{table}`;").stdout) for table in BASE_TABLES + NEW_TABLES):
        raise RuntimeError("Unexpected records in new test database.")
    print("Created isolated database and six empty tables; source records were not copied.")
    verify()


def verify():
    baseline = json.loads(STATE.read_text())
    if baseline["source"] != source_snapshot():
        raise RuntimeError("Source database fingerprint changed. Stop and investigate; no reset/repair will be attempted.")
    if baseline["redis_protected"] != redis_protected_snapshot():
        raise RuntimeError("Protected Redis keys changed or expired. Stop and investigate; no cleanup will be attempted.")
    assert sql("SELECT DATABASE();", test=True).stdout.strip() == DB
    tables = sql("SHOW TABLES;", test=True).stdout.splitlines()
    assert sorted(tables) == sorted(BASE_TABLES + NEW_TABLES)
    for table in BASE_TABLES:
        original = sql(f"SHOW CREATE TABLE babi.`{table}`;").stdout.split("\t", 1)[1]
        cloned = sql(f"SHOW CREATE TABLE `{DB}`.`{table}`;").stdout.split("\t", 1)[1]
        assert normalize_ddl(original) == normalize_ddl(cloned), table
    foreign_keys = sql(f"SELECT TABLE_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME "
                       f"FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='{DB}' "
                       "AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME;").stdout.splitlines()
    assert len(foreign_keys) == 2 and all(f"\t{DB}\tbabies" in line for line in foreign_keys)
    unique = sql(f"SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='{DB}' AND NON_UNIQUE=0;").stdout.splitlines()
    assert {"uk_baby_invitation_code", "uk_baby_invitation_code_allocation", "uk_baby_invitation_email",
            "uk_baby_members_baby_user", "uk_users_email"}.issubset(unique)
    checks = sql(f"SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS "
                 f"WHERE TABLE_SCHEMA='{DB}' AND CONSTRAINT_TYPE='CHECK';").stdout.splitlines()
    assert "ck_baby_email_invitation_status" in checks
    grants = sql(f"SELECT PRIVILEGE_TYPE FROM information_schema.SCHEMA_PRIVILEGES "
                 f"WHERE TABLE_SCHEMA='{DB}' AND GRANTEE=CONCAT(CHAR(39),'{USER}',CHAR(39),'@',CHAR(39),'%',CHAR(39));").stdout.splitlines()
    assert set(grants) == {"SELECT", "INSERT", "UPDATE", "DELETE"}
    grantee = f"CONCAT(CHAR(39),'{USER}',CHAR(39),'@',CHAR(39),'%',CHAR(39))"
    assert sql(f"SELECT PRIVILEGE_TYPE FROM information_schema.USER_PRIVILEGES "
               f"WHERE GRANTEE={grantee} AND PRIVILEGE_TYPE<>'USAGE';").stdout.strip() == ""
    assert sql(f"SELECT TABLE_SCHEMA FROM information_schema.SCHEMA_PRIVILEGES "
               f"WHERE GRANTEE={grantee} AND TABLE_SCHEMA<>'{DB}';").stdout.strip() == ""
    assert sql("SELECT id FROM babi.users LIMIT 0;", test=True, allow_failure=True).returncode != 0
    assert redis("PING").strip() == b"PONG"
    probe = PREFIX + "connection-check:" + secrets.token_hex(8)
    assert redis("SET", probe, "ok", "EX", "30", "NX").strip() == b"OK"
    assert redis("GET", probe).strip() == b"ok"
    assert redis("DEL", probe).strip() == b"1"  # Only the exact probe created above.
    print("PASS: test MySQL connection, six tables, two test-only FKs, UNIQUE indexes and DML-only grants.")
    print("PASS: source MySQL schema/data fingerprints and protected Redis fingerprints unchanged; Redis PONG.")


def constraints():
    before = {table: int(sql(f"SELECT COUNT(*) FROM `{table}`;", test=True).stdout)
              for table in BASE_TABLES + NEW_TABLES}
    parent = ("INSERT INTO babies(name,birth_date,created_at,user_id) "
              "VALUES('constraint-probe','2025-01-01',NOW(6),-1); SET @probe_baby=LAST_INSERT_ID(); ")
    email_row = ("INSERT INTO baby_email_invitations(baby_id,email,generation,sent_at,expires_at,status) "
                 "VALUES(@probe_baby,'constraint-probe@example.invalid','probe',NOW(6),DATE_ADD(NOW(6),INTERVAL 14 DAY),'PENDING'); ")
    probes = [
        ("active-code FK", 1452, "INSERT INTO baby_invitation_codes(baby_id,code,generation) VALUES(-1,'Tst001','probe');"),
        ("email-invitation FK", 1452, "SET @probe_baby=-1; " + email_row),
        ("active-code UNIQUE", 1062, parent
         + "INSERT INTO baby_invitation_codes(baby_id,code,generation) VALUES(@probe_baby,'Tst001','probe'); "
         + parent + "INSERT INTO baby_invitation_codes(baby_id,code,generation) VALUES(@probe_baby,'Tst001','probe');"),
        ("permanent allocation UNIQUE", 1062,
         "INSERT INTO allocated_baby_invitation_codes(code) VALUES('Tst002'); "
         "INSERT INTO allocated_baby_invitation_codes(code) VALUES('Tst002');"),
        ("baby/email UNIQUE", 1062, parent + email_row + email_row),
        ("baby/member UNIQUE", 1062, parent
         + "INSERT INTO baby_members(baby_id,user_id,role) VALUES(@probe_baby,-1,'MEMBER'); "
         + "INSERT INTO baby_members(baby_id,user_id,role) VALUES(@probe_baby,-1,'MEMBER');"),
        ("invitation status CHECK", 3819, parent + email_row.replace("'PENDING'", "'INVALID'")),
    ]
    for label, expected, statement in probes:
        # mysql batch exits on the expected error; disconnect rolls the open transaction back.
        result = sql("START TRANSACTION; " + statement + " ROLLBACK;", test=True, allow_failure=True)
        if result.returncode == 0 or f"ERROR {expected}" not in result.stderr:
            raise RuntimeError("Unexpected constraint result: " + label + "; details suppressed.")
    after = {table: int(sql(f"SELECT COUNT(*) FROM `{table}`;", test=True).stdout)
             for table in BASE_TABLES + NEW_TABLES}
    assert before == after, "Constraint probes must not persist test rows"
    verify()
    print("PASS: seven real MySQL FK/UNIQUE/CHECK rejection probes; all probe transactions rolled back.")


def run():
    verify()
    env = os.environ.copy()
    for key in list(env):
        if key.startswith("SPRING_") or key in {"JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "GRADLE_OPTS",
                "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "JWT_SECRET", "EMAIL_VERIFICATION_SECRET", "MAIL_USERNAME", "MAIL_PASSWORD"}:
            env.pop(key)
    env.update(settings())
    java_home = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
    env["JAVA_HOME"] = java_home
    subprocess.run([str(ROOT / "gradlew"), "bootJar", "--quiet"], cwd=ROOT, env=env, check=True)
    jar = ROOT / "build/libs/babi-server-0.0.1-SNAPSHOT.jar"
    os.chdir(ROOT)
    os.execve(str(Path(java_home) / "bin/java"), [str(Path(java_home) / "bin/java"), "-jar", str(jar),
              "--spring.profiles.active=invitation-mysql"], env)


def smoke():
    config = settings()
    base = "http://127.0.0.1:" + config["INVITATION_TEST_PORT"]

    def request(path, body=None, token=None, method=None, expected=200):
        headers = {"Content-Type": "application/json"}
        if token: headers["Authorization"] = "Bearer " + token
        data = None if body is None else json.dumps(body).encode()
        message = urllib.request.Request(base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(message, timeout=15) as response:
                status, payload = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, payload = error.code, error.read()
        if status != expected:
            raise RuntimeError(f"Smoke request failed: {path} HTTP {status}; response omitted to protect tokens/mail data.")
        return json.loads(payload)

    request("/babies", expected=401)
    request("/v3/api-docs")
    emails = ["owner@babi-invitation-test.example.invalid", "guest@babi-invitation-test.example.invalid"]
    tokens = []
    password = config["INVITATION_TEST_ACCOUNT_PASSWORD"]
    for index, email in enumerate(emails):
        if not int(sql(f"SELECT COUNT(*) FROM users WHERE email='{email}';", test=True).stdout):
            mailbox = WORK / "invitation-mysql-mail"
            before = set(mailbox.glob("*.txt")) if mailbox.exists() else set()
            request("/auth/email/send", {"email": email})
            created = set(mailbox.glob("*.txt")) - before
            assert len(created) == 1, "Expected exactly one local-only test mail"
            code = re.search(r"인증번호는 ([0-9]{6})", created.pop().read_text())[1]
            proof = request("/auth/email/verify", {"email": email, "code": code})["data"]["verificationToken"]
            request("/auth/signup", {"email": email, "password": password, "passwordConfirm": password,
                    "nickname": "invitation-test-" + str(index), "verificationToken": proof}, expected=201)
        tokens.append(request("/auth/login", {"email": email, "password": password})["data"]["accessToken"])
    owned = request("/babies", token=tokens[0])["data"]
    if owned:
        baby_id = owned[0]["babyId"]
    else:
        baby_id = request("/babies", {"name": "초대 통합 테스트 아기", "birthDate": "2025-01-01"}, tokens[0])["data"]["babyId"]
    code = request(f"/babies/{baby_id}/invitation-code", token=tokens[0])["data"]["code"]
    guest_babies = request("/babies", token=tokens[1])["data"]
    if not any(baby["babyId"] == baby_id for baby in guest_babies):
        request(f"/babies/{baby_id}/invitations", {"email": emails[1]}, tokens[0])
        request("/babies/invitations/check", {"code": code}, tokens[1])
        request("/babies/invitations/accept", {"code": code}, tokens[1])
    assert len(request(f"/babies/{baby_id}/members", token=tokens[0])["data"]) == 2
    keys = redis("--scan", "--pattern", PREFIX + "*").splitlines()
    assert keys and all(key.startswith(PREFIX.encode()) for key in keys)
    private_result = WORK / "invitation-mysql-smoke.json"
    if not private_result.exists():
        private_write(private_result, json.dumps({"baseUrl": base, "ownerEmail": emails[0],
                      "guestEmail": emails[1], "babyId": baby_id, "externalSmtpUsed": False}, indent=2) + "\n")
    verify()
    print("PASS: real MySQL/Redis + JWT signup/login, baby registration, invitation code/send/check/accept and members.")
    print("Mail was saved privately to work/invitation-mysql-mail; no external SMTP was used.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["setup", "verify", "run", "smoke", "constraints"])
    args = parser.parse_args()
    try:
        {"setup": setup, "verify": verify, "run": run, "smoke": smoke, "constraints": constraints}[args.action]()
    except (RuntimeError, AssertionError, subprocess.CalledProcessError, OSError) as error:
        print("STOP:", str(error) or "An isolation check failed.", file=sys.stderr)
        sys.exit(1)
