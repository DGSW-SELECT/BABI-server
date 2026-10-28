# 이메일 인증과 회원가입 연동 결과

> 이 문서는 verificationToken 도입 전 구현과 테스트의 기록입니다. 이메일 전역 값 `1`에 기반한 아래 가입 계약은 폐기됐습니다. 현재 서비스는 일회성 토큰을 필수로 요구하며, 최신 요청·응답 및 Redis 처리 방식은 [이메일 인증 토큰 API 계약](api/email-verification.md)을 따릅니다. 과거의 165개 테스트 결과를 현재 토큰 보안 검증 결과로 해석하지 마세요.

2026-10-25 KST. 대상: `/Users/dgsw49/Desktop/Development/BABI-server`, 브랜치 `feature/email-verification`.

## 확인 및 변경 범위

현재 체크아웃과 상위 경로에는 AGENTS.md/CLAUDE.md가 없었다. 동일 저장소의 다른 로컬 체크아웃에 있는 SELECT/BABI AGENTS.md와 CLAUDE.md를 참고해 기존 레이어, record DTO, 생성자 주입, ApiResponse/ErrorCode 및 Gradle Wrapper 관례를 유지했다. 브랜치와 origin/status를 먼저 확인했고 기존 이메일 인증/DB 작업을 덮어쓰지 않았다.

수정 파일:

- `src/main/java/com/select/domain/auth/service/EmailVerificationService.java`: 이메일 정규화, 유형별 예외, 가입용 인증 예약 및 트랜잭션 완료 처리
- `src/main/java/com/select/domain/user/service/UserService.java`: 기본 가입 검증 후 인증 예약, 로그인과 공통 이메일 정규화
- `src/main/java/com/select/domain/auth/dto/request/EmailSendRequest.java`
- `src/main/java/com/select/domain/auth/dto/request/EmailVerifyRequest.java`
- `src/main/java/com/select/domain/user/dto/request/SignupRequest.java`
- `src/main/java/com/select/domain/user/dto/request/LoginRequest.java`: 위 네 record DTO 모두 같은 정규화 사용. 인증 DTO 이메일 길이도 320자로 제한
- `src/main/java/com/select/global/exception/ErrorCode.java`
- `src/main/java/com/select/global/exception/GlobalExceptionHandler.java`: 인증 실패의 구조화된 상태/오류 응답
- 기존 테스트 4개: UserServiceTest, UserEmailConstraintTest, UserControllerTest, JwtAuthenticationIntegrationTest. 생성자 의존성을 반영하고 기존 테스트 목적을 유지

추가 파일:

- `src/main/java/com/select/global/util/EmailNormalizer.java`
- `src/main/java/com/select/domain/auth/exception/EmailVerificationException.java`
- `src/test/java/com/select/domain/auth/service/EmailVerificationServiceTest.java`
- `src/test/java/com/select/domain/auth/service/EmailSignupIntegrationTest.java`
- `src/test/java/com/select/domain/auth/controller/EmailVerificationControllerTest.java`
- 이 문서

## 이메일과 API 계약

발송·검증·회원가입·로그인 모두 `trim().toLowerCase(Locale.ROOT)`을 사용한다. DTO 생성 시 검증 전에 적용하며 서비스 직접 호출도 같은 공통 함수를 통과한다. 이메일 별칭/점 제거 등의 추가 규칙은 넣지 않았다. 인증번호는 그대로 검증하며 기존 HMAC-SHA256 저장 방식과 인증/시도/재발송 TTL은 유지했다.

회원가입 요청/응답 record와 성공 201, 기존 로그인/JWT/중복 이메일 409/비밀번호 불일치 처리는 유지한다. 가입에 유효한 이메일 인증이 추가로 필요하다. 비밀번호 불일치 및 이미 존재하는 이메일은 예약 전에 거부하므로 인증을 소비하지 않는다.

| 오류 코드 | HTTP | 의미 |
|---|---|---|
| EMAIL_CODE_MISMATCH | 400 | 인증번호 불일치 |
| EMAIL_CODE_EXPIRED | 400 | 인증번호 없음/만료/이미 사용 |
| EMAIL_ATTEMPTS_EXCEEDED | 429 | 최대 입력 횟수 초과 |
| EMAIL_RESEND_TOO_SOON | 429 | 재발송 제한 |
| EMAIL_NOT_VERIFIED | 400 | 인증 완료 정보 없음/만료 |
| EMAIL_VERIFICATION_IN_PROGRESS | 409 | 같은 인증을 다른 가입 요청이 예약 중 |
| EMAIL_VERIFICATION_UNAVAILABLE | 503 | Redis 장애/예상하지 못한 인증 처리 결과 |
| EMAIL_SEND_FAILED | 503 | 인증 메일 전송 실패 |

응답은 기존 ApiResponse의 success/message/data/error.code/error.details 형식을 따른다. 내부 Redis 오류나 인증번호/secret을 응답에 담지 않는다.

## Redis 일회성 사용과 실제 DB 커밋

기존 인증 완료 키 `email:verified:{정규화 이메일}`, 값 `1`, TTL 10분을 유지한다. 조회 후 삭제하는 구현 대신 Lua 두 개를 추가했다. 기존 발송/번호검증 Lua 본문은 변경하지 않았다.

1. `UserService.signup()`의 실제 Spring 트랜잭션 안에서 기본 가입 검증 후 예약한다.
2. 예약 Lua는 값이 `1`이며 PTTL이 양수일 때에만 `signup:{임의 UUID}`로 원자적으로 변경한다. Redis KEEPTTL로 원래 만료 시각을 유지한다. 다른 요청은 예약 중 오류를 받는다. 만료/TTL 없는 키/임의 값은 인증으로 인정하지 않는다.
3. 비밀번호 해시 및 saveAndFlush를 실행한다. flush는 커밋이 아니므로 메서드 내부에서 인증을 삭제하지 않는다.
4. Spring TransactionSynchronization.afterCompletion의 실제 결과가 COMMITTED일 때만 내 예약 토큰을 삭제한다.
5. 확정된 ROLLED_BACK이면 내 토큰이 아직 유효한 경우 `1`로 복원한다. KEEPTTL이므로 남은 TTL만 유지한다. 이미 만료됐거나 새 인증/다른 요청으로 바뀐 키는 복원하거나 지우지 않는다.
6. UNKNOWN은 복원하지 않고 내 예약을 소비한다. 성공 여부가 불명확한 DB 작업에 대해 인증 재사용을 허용하지 않는다.

한 인증을 동시에 두 가입 요청이 예약할 수 없다. MySQL 이메일 UNIQUE도 중복 계정 생성을 최종 차단한다. 새 인증이 진행되어 키 값이 바뀌어도 이전 요청의 완료 처리가 새 인증을 제거하지 않도록 토큰을 비교한다.

## 실패 처리와 남은 위험

MySQL과 Redis 사이에 분산 트랜잭션/자동 공동 롤백은 없다. 이 구현은 원자적 예약과 실제 트랜잭션 결과에 따른 조건부 보상 방식이다.

- DB 저장/flush/커밋 전 단계 실패가 명확히 롤백되면 인증은 원래 남은 시간 안에서 재사용 가능하다.
- DB 커밋 후 Redis 삭제 실패가 발생해도 이미 완료된 가입을 실패 응답으로 바꾸지 않는다. 경고를 남기며 예약 값은 `1`이 아니므로 가입 인증으로 재사용할 수 없다. 원래 TTL 또는 새 인증으로 정리된다.
- 롤백 후 Redis 복구 실패나 프로세스 강제 종료가 발생하면 예약이 TTL까지 남을 수 있어 재인증이 필요할 수 있다. 보안을 위해 자동으로 `1`로 간주하지 않는다.
- Redis 장애로 예약 성공 여부를 확인할 수 없으면 회원 저장 전에 가입을 거부한다. Redis가 예약을 반영한 뒤 응답이 유실되면 역시 TTL까지 대기/재인증이 필요할 수 있다.
- Redis 재시작·비동기 복제 failover까지 포함하는 엄밀한 전역 exactly-once를 보장하는 설계는 아니다. 영속 outbox/예약 테이블/재처리 작업은 DB 스키마 변경 금지와 현재 범위를 고려해 도입하지 않았다.
- 가입 요청 처리 중 인증 유효기간이 끝나도, 만료 전에 예약한 요청은 완료될 수 있다. 이후 요청에는 만료된 키를 새로 사용할 수 없다.
- 이전 버전이 만든 대문자/공백 이메일 Redis 키는 새 정규화 키와 연결하지 않는다. 기존 TTL로 만료되며 필요한 사용자는 재인증한다.

## 검증

실행 명령(Java 21 경로만 환경에 맞게 변경):

```bash
JAVA_HOME=/Users/dgsw49/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home BABI_REDIS_TEST_PORT=6379 ./gradlew compileJava test --offline --console=plain
```

결과: 컴파일 성공, 총 **165건 통과**, 실패/오류/건너뜀 0. 기존 130건과 신규 35건이다. 실제 Redis 통합 테스트 13건이 포함됐다.

- 미인증/만료 가입 거부, 인증 후 가입·로그인·JWT 발급, 중복 이메일, 인증 재사용 거부
- 이메일 발송/검증 API의 대소문자·공백 일치, 재발송 제한, 불일치/시도 초과/만료, 전송 실패 정리
- 실제 HTTP 동시 가입 두 요청 중 1건만 성공, 첫 트랜잭션이 열린 동안 다른 요청 예약 차단
- saveAndFlush 후 외부 트랜잭션 롤백 및 beforeCommit 실패 보상, 실제 저장 실패 후 인증 복구
- 만료 인증 복구 방지, 오래된 완료 작업이 새 인증을 덮어쓰거나 지우지 않음
- UNKNOWN 결과 소비, Redis 장애 거부, 커밋 후 Redis 정리 실패가 성공 응답을 뒤집지 않음
- 기존 가입/로그인/탈퇴 상태/JWT 보안 테스트 유지. 기존 JWT 테스트는 인증 서비스 mock을 사용하고 새 통합 테스트는 실제 인증 서비스/Lua를 사용

통합 테스트는 H2와 실제 127.0.0.1 Redis를 사용하며 이메일은 UUID 기반 `babi-test-…@example.invalid`이다. 해당 테스트가 만든 네 종류의 정확한 Redis 키만 정리하고 FLUSHDB/FLUSHALL은 사용하지 않는다. SMTP EmailSender는 MockitoBean으로 교체해 외부 Gmail 발송을 차단한다. BABI_REDIS_TEST_PORT가 없으면 실제 Redis 통합 테스트만 건너뛰며 나머지 단위/회귀 테스트는 실행 가능하다. 환경 변수를 바꿔 다시 검증할 때에는 Gradle 캐시 영향을 피하려고 `--rerun-tasks`를 추가할 수 있다.

Spring Boot는 테스트의 RANDOM_PORT에서 실제 HTTP를 처리했다. 실제 개발 MySQL에는 연결/변경하지 않았다. 따라서 이번 연동의 MySQL 직접 통합 검증과 실 Gmail 전송은 수행하지 않았으며, 기존 MySQL UNIQUE 및 트랜잭션 동작을 전제로 한다.

GmailEmailSender, Entity, build.gradle, compose.yml, application 설정 3개의 총 7개 보호 파일은 최초와 해시가 일치한다. `.env`는 읽거나 출력하지 않았다. 실제 회원 데이터, DB 스키마, Docker 볼륨을 변경하지 않았고 commit/push/merge/stage도 수행하지 않았다.

`git diff --check` 및 신규 파일 별도 공백 검사를 통과했다. 기존 Git 변경은 그대로 남아 있으므로 전체 git diff에는 이전 이메일/DB 작업도 함께 보인다. 이 문서의 파일 목록이 이번 연동의 변경 범위다.
