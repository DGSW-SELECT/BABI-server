# AGENTS.md

## Project
BABI 서비스 Spring Boot 백엔드.
- Java 21, Spring Boot 4.0.8, Gradle

## Structure
```text
com.select
├── global   (config, exception, response, security)
└── domain
    └── <feature>  (controller, dto/request, dto/response, entity, repository, service)
```
새 기능은 `domain/<feature>` 아래 같은 계층으로 추가.

## Commands
- Run: `./gradlew bootRun --args='--spring.profiles.active=local'`
- Build: `./gradlew build`
- Test: `./gradlew test`

## Rules
- Controller는 요청/응답만 처리, 비즈니스 로직은 Service에 둔다.
- Entity를 API 응답으로 직접 반환하지 않고 DTO를 사용한다.
- 공통 예외/응답은 `global/exception`, `global/response`를 재사용한다.
- Secret(DB 비밀번호, API key 등)은 코드·yml에 커밋하지 않는다.

## Git
- Branch: `<prefix>/<name>` (예: `feat/login`, `chore/docker-setup`)
- `main` 직접 push 금지, PR로 merge.
- Commit: `prefix: description`
  - `feat` 새 기능 / `fix` 버그 수정 / `refactor` 리팩터링 / `docs` 문서
  - `style` 코드 스타일 / `test` 테스트 / `chore` 설정·유지보수
  - `perf` 성능 / `ci` CI/CD / `build` 빌드 시스템
  - 예: `feat: add login feature`, `fix: resolve timetable loading error`

## CI
- PR 생성/업데이트 시 `.github/workflows/claude-pr-review.yml`이 Claude 자동 리뷰 실행.
- Repo secret `ANTHROPIC_API_KEY` 필요.
