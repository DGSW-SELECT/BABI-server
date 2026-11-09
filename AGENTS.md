# Project instructions

## Structure and stack
- Java 21, Spring Boot 4.0.8, Gradle 9.7.1. Use `./gradlew`.
- `src/main/java/com/select/domain`: domain controller, DTO, entity, repository, service layers.
- `src/main/java/com/select/global`: shared exception, response, and security code.
- `src/main/resources`: Spring application configuration and profiles.
- `src/test/java/com/select`: JUnit 5 and Mockito tests.

## Commands
- Run locally: `MYSQL_PASSWORD=... JWT_SECRET=... ./gradlew bootRun --args='--spring.profiles.active=local'` (`JWT_SECRET` must be at least 32 bytes).
- AWS runtime: `java -jar /opt/babi/babi-server.jar --spring.profiles.active=aws`; use `deploy/app.env.example` and `deploy/babi-server.service` after infrastructure approval.
- Deployment script tests: `python3 -m unittest discover -s deploy -p 'test_*.py' -v`.
- Build: `./gradlew build`.
- Test: `./gradlew test`.
- Targeted test: `./gradlew test --tests 'fully.qualified.TestClass'`.

## Development rules
- Read relevant code before edits. Reuse existing layers and conventions.
- Keep changes scoped; no unrelated refactors or changes to others' work.
- Preserve existing Java formatting, constructor injection, validation, and API response conventions.
- Shared care records live in `domain/care` and reuse the five existing record tables. Keep JWT-derived author membership, action-specific permissions, VIEWER read-only access, and active account/membership checks. Reuse `domain/baby/repository/BabyAccessRepository` across records and reports.
- AI daily reports use OpenAI Responses with anonymous numeric facts only; never send names, IDs, notes, or transcripts. Keep `OPENAI_API_KEY` outside Git, bounded timeouts/output, no automatic retries, and explicit failure when unconfigured. Tests must mock OpenAI, not make billable calls.
- Reproduce bugs and run relevant tests and actual startup/API checks.
- Keep secrets out of tracked files; use environment variables for external database credentials.
- AWS resources must use account `380536267838`, region `ap-northeast-3`. Never create or hardcode access keys; any future AWS SDK integration must use the default credentials chain and an execution IAM role.
- AWS resource changes, deployment, and billable requests require explicit approval of targets, configuration, and costs. Add only services used by existing code.
- KRW 60,000 remains the AWS budget reference. On 2026-11-03 the user approved disabling the final stop schedule; both start and stop schedules are DISABLED. No automatic budget cutoff exists. Further operating schedule changes require approval; see `docs/aws-runtime.md`.
- School AWS ownership tags are applied asynchronously. After creating a security group, verify its automatic `username=select-1` and `group=select` tags before authorizing rules. Do not forge ownership tags or change school IAM policies.
- H2 is development/test-only and in-memory; local data disappears when the server stops. Exclude H2 from deployment JARs.
- Deployment requires MySQL credentials and `JWT_SECRET`; external DB connections use `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- The `aws` profile also requires an independent `EMAIL_VERIFICATION_SECRET` and loopback Redis. SMTP credentials are optional for demo operation; unset credentials must return `503` without bypassing email verification. Never commit demo passwords.
- Keep deployment artifact directories `0755` and non-secret files `0644` so container users can read mounted schema/config files. Keep `/etc/babi` `0700` and secret environment files `0600`.
- The `aws` profile defaults to local MySQL on `127.0.0.1:3307` using `MYSQL_PASSWORD`; datasource environment variables can override it. Docker initializes a new MySQL volume from `schema.sql`; the application only validates the schema and never resets it on restart.
- `babi_DB_Real.sql` and the approved invitation extension (2026-11-03) are the schema sources. Keep the `baby_members` / `active_baby_members` model and account status checks. Never run their DROP statements against existing data; the checked-in `schema.sql` excludes active DROP statements.
- The local DataGrip tunnel is managed by user LaunchAgent `com.select.babi-db-tunnel` on `127.0.0.1:13307`. Do not start a second forwarder on that port. Use `aws login --profile babi-select-1 --region ap-northeast-3` when its existing temporary credentials expire; never create access keys to keep the tunnel alive.
- `deploy/compose.proxy.yml` and `deploy/Caddyfile` expose only HTTPS/HTTP to `api.babi.prot.kr` on Linux; the application stays on `127.0.0.1:8080`. The `aws` profile uses native forwarded-header handling for HTTPS behind Caddy. Certificate volumes persist across restarts.
- Update this file when project-wide rules change. CLAUDE.md only points here.
- Responses: Korean, concise; follow `caveman` full mode until explicitly disabled.

## Git and deployment
- Check `git status` and `git remote -v` at the start of work.
- Use the existing GitHub origin and branch conventions (`feat/`, `fix/`, `chore/`).
- Do not create remotes, commit, push, or deploy without authorization.
- Ask before destructive operations, history rewrites, or production deployment.
- AWS runtime preparation is documented in `docs/aws-runtime.md`; infrastructure creation and deployment still require approval.
- `.github/workflows/aws-deploy.yml` tests PRs and publishes tested `main` JARs as public `aws-<commit SHA>` prereleases. The existing EC2 checks every five minutes through `babi-deploy.timer`, validates SHA-256 and embedded commit, and restores the previous JAR on failed startup. No AWS access keys, GitHub token on EC2, or self-hosted Actions runner is used. Automatic deployment never executes schema migrations or updates its root-owned installer; schema and installer changes need separate deployment review.
- `main` requires two PR approvals and last-push approval under the existing GitHub ruleset. Do not bypass or weaken this protection.
- All commit messages must use `prefix: description`, short and specific.
- Prefixes: `feat` (feature), `fix` (bug), `refactor` (refactoring), `docs` (documentation), `style` (style), `test` (tests), `chore` (maintenance), `perf` (performance), `ci` (CI/CD), `build` (build system).
