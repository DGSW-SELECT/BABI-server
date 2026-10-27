# Project instructions

## Structure and stack
- Java 21, Spring Boot 4.0.8, Gradle 9.7.1. Use `./gradlew`.
- `src/main/java/com/select/domain`: domain controller, DTO, entity, repository, service layers.
- `src/main/java/com/select/global`: shared exception, response, and security code.
- `src/main/resources`: Spring application configuration and profiles.
- `src/test/java/com/select`: JUnit 5 and Mockito tests.

## Commands
- Run locally: `MYSQL_PASSWORD=... JWT_SECRET=... ./gradlew bootRun --args='--spring.profiles.active=local'` (`JWT_SECRET` must be at least 32 bytes).
- Build: `./gradlew build`.
- Test: `./gradlew test`.
- Targeted test: `./gradlew test --tests 'fully.qualified.TestClass'`.

## Development rules
- Read relevant code before edits. Reuse existing layers and conventions.
- Keep changes scoped; no unrelated refactors or changes to others' work.
- Preserve existing Java formatting, constructor injection, validation, and API response conventions.
- Reuse the existing JWT authentication (`/auth/login`). Report CRUD must verify the authenticated user's baby access before reading or writing reports.
- `src/main/resources/schema.sql` initializes required tables before JPA. It creates missing tables only; existing table changes require a separate migration.
- Reproduce bugs and run relevant tests and actual startup/API checks.
- Keep secrets out of tracked files; use environment variables for external database credentials.
- H2 is development/test-only and in-memory; local data disappears when the server stops. Exclude H2 from deployment JARs.
- Deployment requires MySQL and `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Update this file when project-wide rules change. CLAUDE.md only points here.
- Responses: Korean, concise; follow `caveman` full mode until explicitly disabled.

## Git and deployment
- Check `git status` and `git remote -v` at the start of work.
- Use the existing GitHub origin and branch conventions (`feat/`, `fix/`, `chore/`).
- Do not create remotes, commit, push, or deploy without authorization.
- Ask before destructive operations, history rewrites, or production deployment.
- No deployment workflow is documented; verify target configuration before deployment.
- All commit messages must use `prefix: description`, short and specific.
- Prefixes: `feat` (feature), `fix` (bug), `refactor` (refactoring), `docs` (documentation), `style` (style), `test` (tests), `chore` (maintenance), `perf` (performance), `ci` (CI/CD), `build` (build system).
