# 보호자 초대 API

모든 API는 `Authorization: Bearer <accessToken>`이 필요하다. 경로에 별도 전역 접두사는 없다. 예시의 코드와 이름은 가상 값이다.

| Method | 경로 | 권한 | 동작 |
| --- | --- | --- | --- |
| GET | `/babies/{babyId}/invitation-code` | OWNER | 활성 코드 조회. 코드가 없으면 최초 발급 |
| POST | `/babies/{babyId}/invitation-code/reissue` | OWNER | 기존 코드 무효화 및 새 코드 발급 |
| POST | `/babies/{babyId}/invitations` | OWNER | 이메일에 현재 코드와 HTTPS 링크 발송 |
| POST | `/babies/invitations/check` | 로그인 사용자 | 코드 유효성 및 아기 정보 확인 |
| POST | `/babies/invitations/accept` | 로그인 사용자 | 코드로 보호자 참여 |

## 코드 발급과 재발급

코드는 SecureRandom으로 만든 영문 대문자·숫자 6자리이며 입력은 대소문자를 구분하지 않는다. 신규 코드는 발급부터 14일 동안 유효하며 최대 100회 수락할 수 있다. 조회나 이메일 재발송으로 코드는 변경되지 않는다. 기존 코드에 저장된 최대 횟수는 변경하지 않는다.

만료 또는 사용 횟수 소진 시 자동 재발급하지 않는다. OWNER가 재발급하면 기존 코드는 `REVOKED`가 되고 새 코드의 사용 횟수는 0이다. 과거 코드는 발급 원장에 남는다.

코드 조회 또는 재발급 성공 응답은 HTTP 200이다.

```json
{"success":true,"message":"초대 코드 조회에 성공했습니다.","data":{"code":"ABC123"}}
```

재발급 메시지는 `초대 코드를 재발급했습니다.`이다.

## 이메일 발송

```json
{"email":"guardian@example.invalid"}
```

이메일은 정규화되어 저장되며 코드와 `https://...?...` 형태의 초대 링크가 발송된다. 같은 아기와 이메일에 아직 유효한 PENDING 초대가 있다면 재사용하며 만료 시각을 연장하지 않는다. 만료된 이메일 초대는 EXPIRED 처리하고 새 초대 기록을 만든다. 코드가 만료되었거나 소진된 경우 OWNER가 먼저 코드를 재발급해야 한다. 재발급 시 PENDING 이메일 초대는 CANCELED 처리한다.

성공 응답의 `expiresAt`은 **이메일 초대 기록**의 만료 시각이다.

```json
{"success":true,"message":"보호자 초대 이메일을 발송했습니다.","data":{"babyId":1,"name":"아기","expiresAt":"2026-11-20T00:00:00Z"}}
```

## 코드 확인과 참여

두 API의 요청 본문은 같다.

```json
{"code":"ABC123"}
```

확인 및 수락의 `expiresAt`은 **초대 코드**의 만료 시각이다. 확인은 사용 횟수를 증가시키지 않는다.

```json
{"success":true,"message":"보호자 초대를 확인했습니다.","data":{"babyId":1,"name":"아기","expiresAt":"2026-11-20T00:00:00Z"}}
```

수락 성공 메시지는 `보호자 초대를 수락했습니다.`이다. 로그인 사용자는 유효한 코드만으로 참여할 수 있다. 이메일 초대 기록, 이메일 일치 및 이메일 토큰 검증은 요구하지 않는다. 따라서 코드를 아는 다른 로그인 사용자도 참여할 수 있다.

수락하면 `baby_members`에 GUARDIAN으로 등록하고 `baby_permissions`에 권한을 부여한다. 이미 활성 보호자라면 중복 등록하지 않는다. 탈퇴 시 `removed_at`을 기록하고 권한을 삭제하며, 같은 사용자가 다시 참여하면 기존 멤버 행을 재활성화한다. OWNER는 먼저 다른 활성 보호자에게 권한을 이전한 뒤 탈퇴할 수 있다. 아기 수정 및 초대 관리는 OWNER만 할 수 있다.

사용 횟수 증가와 멤버 등록은 같은 트랜잭션이며 중복 수락이나 롤백은 횟수를 소비하지 않는다. 아기 행 잠금은 수락, 재발급 및 권한 변경의 동시 요청을 직렬화한다.

## 오류

오류 응답 형식의 예시는 다음과 같다. 입력 검증 오류의 `details`에는 필드별 정보가 포함될 수 있다.

```json
{"success":false,"message":"초대 코드의 사용 횟수를 모두 사용했습니다. 소유자가 재발급해야 합니다.","data":null,"error":{"code":"INVITATION_CODE_EXHAUSTED","details":[]}}
```

| HTTP | 코드 | 상황 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` / `INVALID_REQUEST_BODY` | 형식에 맞지 않는 코드·이메일 또는 본문 |
| 400 | `INVITATION_INVALID` | 존재하지 않거나 만료·폐기된 코드 |
| 400 | `INVITATION_SELF` | OWNER 자신의 이메일로 초대 발송 |
| 401 | `UNAUTHORIZED` / `INVALID_TOKEN` | 인증 누락 또는 JWT 오류 |
| 403 | `FORBIDDEN` | OWNER가 아닌 사용자의 관리 요청 |
| 404 | `NOT_FOUND` | 아기를 찾을 수 없음 |
| 409 | `INVITATION_ALREADY_MEMBER` | 이미 활성 보호자 |
| 409 | `INVITATION_CODE_EXPIRED` | OWNER가 조회·발송하려는 코드 만료 |
| 409 | `INVITATION_CODE_EXHAUSTED` | 코드 사용 횟수 소진 |
| 429 | `INVITATION_RATE_LIMITED` | 초대 요청 제한 초과 |
| 503 | `INVITATION_UNAVAILABLE` | Redis·DB·링크 설정 등 의존성 장애 |
| 503 | `INVITATION_SEND_FAILED` | 이메일 발송 실패 |

## 앱 링크와 운영

`BABY_INVITATION_LINK_BASE_URL`에 HTTPS 초대 웹 경로를 설정해야 이메일 발송이 가능하다. Android App Links, iOS Universal Links 및 미설치 사용자용 안내 페이지는 앱·웹 담당자가 별도로 구성한다. 코드가 URL에 들어가므로 접근 로그와 분석 이벤트에 노출되지 않도록 운영 환경을 설정해야 한다.

기본 요청 제한은 확인 30회/분, 수락 10회/분, 코드 재발급 10회/시간(사용자별), 이메일 발송 10회/시간(사용자별)·30회/시간(아기별)·3회/시간(아기와 이메일별)이다. Redis 장애 시 제한을 우회하지 않는다. SMTP와 DB는 분산 트랜잭션이 아니므로 발송 성공 후 DB 커밋이 실패할 수 있다.

기존 API 소비자는 보호자 역할 문자열이 `MEMBER`에서 `GUARDIAN`으로 변경된 점을 반영해야 한다. 보호자 목록은 `GET /babies/{babyId}/members`, 소유권 이전은 `PATCH /babies/{babyId}/members/owner`, 본인 탈퇴는 `DELETE /babies/{babyId}/members/me`이다.
