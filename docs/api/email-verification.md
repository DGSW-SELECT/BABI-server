# 이메일 인증 토큰과 회원가입 API 계약

2026-10-25. `feature/email-verification`의 클라이언트 결합 인증 토큰 변경 사항이다. 공통 `ApiResponse`와 기존 오류 코드는 그대로 사용한다. 원본 API 명세 전체는 접근 가능한 저장소/문서에서 확인되지 않았으므로, 사용자가 지정한 성공 응답과 현재 `ErrorCode`를 이 변경의 기준으로 삼았다.

## 프론트엔드 흐름

1. 이메일·비밀번호·비밀번호 확인·닉네임을 입력 상태에 보관한다.
2. `POST /auth/email/send`로 인증번호를 요청한다.
3. `POST /auth/email/verify`에 이메일과 인증번호를 보낸다.
4. 성공 응답의 `data.verificationToken`을 받자마자 보관 중인 가입 정보와 함께 `POST /auth/signup`을 호출한다.
5. 가입 201 응답을 받은 경우에만 완료 화면을 표시한다. 인증 성공만으로 가입 완료로 표시하지 않는다.

별도의 두 번째 가입 버튼은 필요하지 않다. 프론트엔드 코드는 이번 백엔드 변경 범위에 포함하지 않았다. 이메일 인증 토큰은 가입 권한만 증명하며, 로그인 JWT로 사용할 수 없다. 비밀번호·토큰은 콘솔/분석 이벤트/서버 로그에 기록하지 말고 브라우저 입력 상태에서만 짧게 보관한다.

## 인증번호 검증

`POST /auth/email/verify`, 인증 불필요.

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

200 성공 응답:

```json
{
  "success": true,
  "message": "회원가입 이메일 인증번호 확인 요청이 처리되었습니다.",
  "data": {
    "email": "user@example.com",
    "verified": true,
    "expiresIn": 600,
    "verificationToken": "발급된-43자리-Base64URL-토큰"
  }
}
```

위 토큰 문자열은 설명용 자리표시자다. 실제 값은 `SecureRandom` 32바이트(256비트)를 패딩 없는 Base64URL로 인코딩한 ASCII 43자리다. 이 값은 검증에 성공한 요청의 응답에만 반환한다. `expiresIn`은 초 단위이고 Redis 설정에 사용하는 동일한 600초 Duration에서 계산한다. 실제 만료 기준은 발급 시점의 서버 Redis TTL이다. 네트워크 지연 동안에도 시간이 흐르며 가입 요청은 만료 전에 토큰을 예약해야 한다.

기존 `email`, `verified`, `expiresIn` 응답 필드를 포함하고 `verificationToken`을 추가했다. DTO는 `EmailVerificationResponse` record다. 현재 작업 전 구현은 data=null이었으며, 이번 변경에서 요청된 명세 필드를 구현했다.

## 회원가입

`POST /auth/signup`, 인증 불필요. 요청에 `verificationToken`이 새 필수 필드로 추가된다.

```json
{
  "email": "user@example.com",
  "password": "Test1234!",
  "passwordConfirm": "Test1234!",
  "nickname": "테스트",
  "verificationToken": "이메일-검증-응답에서-받은-43자리-값"
}
```

토큰 자리표시자는 실제 검증 응답 값으로 바꿔야 한다. 토큰은 trim·소문자 변환하지 않으며 정확히 전달한다. 이메일만 기존 규칙대로 trim + Locale.ROOT 소문자 변환한다.

201 성공 응답은 기존 계약을 유지한다.

```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "data": {
    "userId": 123,
    "email": "user@example.com",
    "nickname": "테스트"
  }
}
```

인증되지 않은 이메일, 다른 이메일의 토큰, 잘못된 토큰, 만료·소비된 토큰은 가입할 수 없다. 새 인증번호 발송은 기존 인증을 폐기하고, 새 번호 검증 완료 시 새 토큰 해시가 저장되어 이전 토큰으로 새 예약을 만들 수 없다. 이미 만료 전에 올바른 토큰으로 예약한 DB 트랜잭션은 기존 동작대로 완료될 수 있다. 재인증이 이미 진행 중인 DB 트랜잭션을 취소하는 것은 아니다.

## 실패 응답과 상태 코드

모든 실패는 기존 `ApiResponse`의 `success=false`, `message`, `data=null`, `error.code`, `error.details`를 사용한다. 새 `TOO_MANY_ATTEMPTS` 코드를 만들지 않았다.

| 상황 | HTTP | 기존 ErrorCode |
|---|---|---|
| 인증번호 불일치 | 400 | EMAIL_CODE_MISMATCH |
| 인증번호 없음/만료/이미 소비됨 | 400 | EMAIL_CODE_EXPIRED |
| 인증번호 입력 횟수 초과 | 429 | EMAIL_ATTEMPTS_EXCEEDED |
| 60초 재발송 제한 | 429 | EMAIL_RESEND_TOO_SOON |
| 토큰 누락·빈 값·43자리 Base64URL 규칙 불일치 | 400 | VALIDATION_ERROR |
| 형태는 맞으나 토큰/이메일 불일치·만료·소비, 과거 값 1 | 400 | EMAIL_NOT_VERIFIED |
| 같은 올바른 토큰의 다른 가입 요청이 예약 중 | 409 | EMAIL_VERIFICATION_IN_PROGRESS |
| 이미 가입한 이메일 | 409 | DUPLICATE_EMAIL |
| 비밀번호 확인 불일치 | 400 | PASSWORD_MISMATCH |
| Redis 장애/예상하지 못한 결과 | 503 | EMAIL_VERIFICATION_UNAVAILABLE |
| 메일 발송 실패 | 503 | EMAIL_SEND_FAILED |

가입의 기존 비밀번호 확인/중복 이메일 선행 검사 순서는 유지한다. 잘못된 토큰을 제출한 요청에는 예약 중 상태를 노출하지 않는다. DTO를 거치지 않는 서비스 호출도 토큰 형식과 해시를 검증한다.

입력 횟수 초과의 실제 응답:

```json
{
  "success": false,
  "message": "인증번호 입력 횟수를 초과했습니다.",
  "data": null,
  "error": {
    "code": "EMAIL_ATTEMPTS_EXCEEDED",
    "details": []
  }
}
```

`VALIDATION_ERROR`의 details에는 해당 필드의 검증 이유가 들어가며 토큰 원문은 포함하지 않는다. 실패 응답에서 새 인증 토큰을 발급하지 않는다.

## Redis 일회성과 DB 정합성

- `email:verified:{정규화 이메일}`에는 원문 토큰 대신 `proof:<이메일과 토큰에 결합된 SHA-256 해시>`를 저장한다. 도메인 구분 문자열과 구분자를 포함한다. 랜덤 토큰 원문은 Redis 키·값·Lua 인자에 넣지 않는다.
- 번호 검증 성공·번호 소비·기존 토큰 교체·600초 TTL 설정은 하나의 Lua 스크립트로 수행한다.
- 가입 Lua는 해시가 일치하고 PTTL이 양수일 때만 값을 `signup:<같은 해시>:<요청별 UUID>`로 바꾸며 KEEPTTL을 사용한다. 같은 올바른 토큰의 두 요청은 동시에 예약할 수 없다.
- `saveAndFlush()`는 커밋이 아니다. `afterCompletion(COMMITTED)`에서 내 예약 값이 일치할 때만 삭제한다.
- 확정 롤백이면 내 예약이 아직 살아 있을 때 같은 proof 해시를 KEEPTTL로 복구한다. 만료된 토큰이나 다른 새 토큰을 복구/덮어쓰기하지 않는다. 결과 불명(UNKNOWN)은 토큰을 복구하지 않는다.
- 커밋 후 Redis 정리 오류를 이유로 이미 완료된 가입을 실패로 응답하지 않는다. 예약 값은 인증 가능 proof가 아니므로 원래 TTL까지 재사용되지 않는다.
- 기존 Redis 값 `1`을 허용하는 호환 경로나 토큰 없는 가입 오버로드는 없다. 이전 인증 사용자는 재인증해야 한다. 실제 Redis 데이터를 마이그레이션하거나 전체 삭제하지 않았다.

토큰은 bearer 증명이므로 탈취한 사람도 사용할 수 있다. 실제 배포는 HTTPS를 사용해야 하며 클라이언트/프록시 로그의 body 수집을 피해야 한다. MySQL과 Redis는 분산 트랜잭션이 아니므로 프로세스 종료·Redis 장애·failover 시 재인증이 필요할 수 있다. 한 토큰으로 동시에 만든 계정의 중복은 기존 DB UNIQUE가 마지막으로 차단한다.

## 테스트

실제 Redis 통합 테스트는 `BABI_REDIS_TEST_PORT`를 지정해 실행한다. H2 + 실제 로컬 Redis + mock EmailSender를 사용하며 개발 MySQL/Gmail은 사용하지 않는다.

```bash
JAVA_HOME=/path/to/java21 BABI_REDIS_TEST_PORT=6379 ./gradlew clean test --offline --console=plain
```

검증 항목: 정상 검증→자동 가입→로그인, 누락/잘못된/다른 이메일/만료/소비 토큰 거부, 동일 토큰 동시 요청, 실제 flush 및 커밋 전 실패 보상, 원래 TTL 유지, 재인증 시 이전 토큰 무효화, 과거 전역 값 1 거부, 다른 HTTP 클라이언트의 계정 선점 차단, Redis 오류 처리 및 기존 JWT 회귀 테스트.
