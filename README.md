# BABI Server

BABI 서비스의 Spring Boot 백엔드 프로젝트입니다.

## 실행 환경

- Java 21
- Spring Boot 4.0.8
- Gradle 9.7.1

## Discord 시연 환경과 로컬 실행

기본 `local`/`aws` 프로필은 기존 Gmail SMTP 발송을 사용합니다. Discord는 `local,discord-demo` 또는 `aws,discord-demo` 프로필을 명시한 경우에만 활성화됩니다. Discord 인증은 이메일 소유권을 증명하지 **않는 시연 전용**이며, `@example.invalid` 주소만 허용합니다. 실제 사용자 계정이나 데이터로 운영하지 말고 접근이 제한된 비공개 Discord 채널을 사용하세요. 채널의 인증번호와 초대 코드를 볼 수 있는 사람은 이를 사용할 수 있습니다. Discord에는 주소와 초대 링크를 보내지 않습니다.

기존 Compose의 `babi` DB와 Redis에는 연결하지 마세요. 별도의 MySQL 8.4 인스턴스에 `babi_discord_demo` DB와 `babi_user` 계정을 준비하고, 최종 스키마를 그 전용 DB에만 별도로 적용하세요. 서버는 `schema.sql`을 자동 실행하지 않으며(`spring.sql.init.mode: never`) Hibernate `validate`만 수행합니다. 공유 DB를 초기화하지 않습니다. 별도의 인증된 Redis 인스턴스도 필요합니다.

예를 들어 본인이 만든 임시 Docker 컨테이너를 사용할 수 있습니다. 아래 값은 셸에만 입력하고, 컨테이너 이름이 이미 존재하면 실행하지 마세요. 공개 포트 대신 호스트 루프백의 임의 포트를 사용합니다. 다음 명령에서 `MYSQL_PASSWORD`와 `BABI_DEMO_REDIS_PASSWORD`는 전용 무작위 값입니다.

```bash
# 비밀번호는 화면에 표시되지 않도록 각각 입력합니다.
read -r -s MYSQL_PASSWORD; export MYSQL_PASSWORD
read -r -s BABI_DEMO_REDIS_PASSWORD; export BABI_DEMO_REDIS_PASSWORD
export BABI_DEMO_REDIS_MARKER="$(openssl rand -hex 32)"
docker run -d --rm --name babi-discord-demo-mysql --tmpfs /var/lib/mysql \
  -p 127.0.0.1::3306 -e MYSQL_DATABASE=babi_discord_demo -e MYSQL_USER=babi_user \
  -e MYSQL_ROOT_PASSWORD="$MYSQL_PASSWORD" -e MYSQL_PASSWORD mysql:8.4
docker run -d --rm --name babi-discord-demo-redis --tmpfs /data \
  -p 127.0.0.1::6379 redis:7-alpine redis-server --save '' --appendonly no \
  --requirepass "$BABI_DEMO_REDIS_PASSWORD"
# MySQL이 준비된 뒤, 전용 컨테이너인지 확인하고 스키마를 적용합니다.
docker exec -i babi-discord-demo-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot babi_discord_demo' < src/main/resources/schema.sql
docker exec -e REDISCLI_AUTH="$BABI_DEMO_REDIS_PASSWORD" babi-discord-demo-redis \
  redis-cli SET babi:discord-demo:isolation-marker "$BABI_DEMO_REDIS_MARKER"
# 표시된 포트를 전용 접속 설정에 사용합니다.
docker port babi-discord-demo-mysql 3306
docker port babi-discord-demo-redis 6379
```

서버를 중단한 뒤 **본인이 생성한 전용 컨테이너만** `docker stop babi-discord-demo-mysql babi-discord-demo-redis`로 종료합니다. 임시 DB와 Redis 데이터는 사라집니다. 기존 Compose 컨테이너나 볼륨은 건드리지 마세요.

`.env.example`을 `.env`로 복사하고 전용 연결 정보와 무작위 비밀값을 입력하세요. `.env`는 Git에 포함하지 마세요. `local`은 작업 디렉터리의 `.env`를 읽으며, 환경변수가 같은 이름의 값보다 우선합니다. 기존 `.env`가 있다면 덮어쓰지 마세요. 음성 STT를 사용하려면 `OPENAI_API_KEY`도 실제 키로 변경합니다.

`OPENAI_API_KEY`는 음성 입력 STT를 호출할 때 필요합니다. 값이 없어도 서버는 시작되지만 음성 변환 API는 `STT_FAILED`로 실패합니다. 실제 키는 Git에 커밋하지 않습니다.

| 변수 | 설명 |
| --- | --- |
| `BABI_DISCORD_DEMO_ENABLED` | 시연 환경에서만 `true` |
| `DISCORD_WEBHOOK_URL` | 비공개 채널에서 만든 Webhook URL. 비밀값입니다. |
| `BABI_DEMO_DB_URL` | 전용 DB `jdbc:mysql://호스트:포트/babi_discord_demo` |
| `MYSQL_PASSWORD` | 전용 `babi_user` 비밀번호 |
| `BABI_DEMO_REDIS_HOST`, `BABI_DEMO_REDIS_PORT` | 전용 Redis 주소와 6379가 아닌 포트 |
| `BABI_DEMO_REDIS_PASSWORD` | 전용 Redis 비밀번호, 24자 이상 |
| `BABI_DEMO_REDIS_MARKER` | 전용 Redis에 미리 저장한 48자 이상의 소문자 16진수 확인값 |
| `JWT_SECRET`, `EMAIL_VERIFICATION_SECRET` | 각각 별도의 무작위 비밀값 |
| `BABY_INVITATION_LINK_BASE_URL` | 기존 초대 API에 필요한 HTTPS 주소. Discord로 전송하지 않습니다. |

일반 `local`/`aws` 프로필에서 Gmail 발송을 사용하려면 `.env`의 `MAIL_USERNAME`과 `MAIL_PASSWORD`를 실제 Gmail 주소와 앱 비밀번호로 설정합니다. `discord-demo` 프로필에서는 Discord가 발송을 담당하므로 해당 값이 필요하지 않습니다.

### 웹후크 주소 변경

팀원이 비공개 Discord 시연 채널에서 새 웹후크를 만든 뒤, 로컬에서는 Git에 포함되지 않는 `.env`의 `DISCORD_WEBHOOK_URL` 값만 교체합니다. 환경변수나 IntelliJ 실행 구성에 같은 키가 있으면 해당 값이 `.env`보다 우선하므로 함께 확인하세요. AWS 시연 환경에서는 담당자가 비밀 설정 파일 `/etc/babi/app.env`의 같은 값을 교체합니다. 값은 `DISCORD_WEBHOOK_URL=새_웹후크_URL` 형식으로 한 줄에 넣고 Git·채팅·로그에 공유하지 않습니다.

웹후크 주소는 서버가 **시작할 때** 읽으므로 변경 후 해당 서버를 재시작해야 합니다. 운영 환경에서는 담당자의 배포 절차와 승인에 따라 재시작하세요. 새 주소의 수신을 확인한 후 기존 웹후크를 Discord에서 삭제합니다. 주소가 외부에 노출됐다면 즉시 폐기하고 새로 발급하세요. 주소가 없거나 형식이 잘못되면 서버가 오류를 알리고 시작하지 않습니다.

전용 Redis에 인증 후 키 `babi:discord-demo:isolation-marker`에 `BABI_DEMO_REDIS_MARKER`와 같은 값을 준비하세요. 예를 들어 `openssl rand -hex 32`로 비밀값을 만들 수 있습니다. 확인값은 전용 Redis를 잘못 연결하는 사고를 줄이지만, DB와 Redis에 실제 사용자 데이터가 없는지 직접 확인해야 합니다. 비밀번호나 Webhook URL을 로그에 출력하거나 공유하지 마세요.

프로젝트 루트에서 실행합니다. IntelliJ에서는 같은 작업 디렉터리와 `local` 프로필을 지정하세요.

```bash
./gradlew bootRun --args='--spring.profiles.active=local,discord-demo'
```

실행 후 Swagger UI(`/swagger-ui.html`)에서 다음 순서로 테스트합니다. 실제 호출은 테스트 전용 서버에서만 하세요.

1. `POST /auth/email/send`에 `{"email":"owner@example.invalid"}`를 전달하고 비공개 Discord 채널의 인증번호를 확인합니다.
2. `POST /auth/email/verify`에 같은 이메일과 `code`를 전달하고 응답의 `verificationToken`을 받습니다.
3. `POST /auth/signup`에 이메일, `password`, `passwordConfirm`, `nickname`, `verificationToken`을 전달한 뒤 `POST /auth/login`으로 로그인합니다. 정확한 요청·응답은 Swagger를 참조하세요.
4. Bearer 액세스 토큰으로 `POST /babies`에서 아기를 등록하고 `GET /babies/{babyId}/invitation-code`로 코드를 발급합니다.
5. `POST /babies/{babyId}/invitations`에 `{"email":"guardian@example.invalid"}`를 전달하면 Discord 채널에 초대 코드가 표시됩니다. 로그인한 다른 테스트 계정은 `/babies/invitations/check`, `/babies/invitations/accept`에 `{"code":"ABC123"}` 형식으로 입력할 수 있습니다.

Discord 연결은 최대 3초, 요청은 최대 5초로 제한합니다. 응답 본문은 사용하지 않고 즉시 닫습니다. 초대 데이터는 발송 전에 커밋되지만, 유효한 코드와 권한을 유지하기 위해 발송 중 아기 행 잠금이 유지됩니다. 5초는 HTTP 요청 제한이며 전체 트랜잭션 시간의 보장은 아닙니다. 타임아웃 후 초대 기록은 남고 발송 API는 실패하므로 재발송할 수 있습니다. 커밋 직후 서버가 종료되면 자동 재시도되지 않습니다.

## 보호자 초대 발송 제한

보호자 초대 발송은 인증번호의 `EmailSendRateLimiter`가 아닌 기존 `InvitationRateLimiter`로 별도 제한합니다. 기본값은 발송자별 시간당 10회, 아기별 시간당 30회, 아기와 대상 이메일별 시간당 3회입니다. 세 제한 중 하나라도 초과하면 HTTP 429 `INVITATION_RATE_LIMITED`를 반환하고 발송하지 않습니다. Redis 장애 시에는 503으로 차단합니다. 이메일 주소는 Redis 키에 해시로 저장합니다.

## 인증번호 발송 제한

`POST /auth/email/send`는 이메일별 기존 60초 재발송 제한 외에 Redis 원자적 제한을 적용합니다. 기본값은 **전체 서버 합계 1분당 30회**, 요청 출처별 **1분당 10회**입니다. `BABI_EMAIL_SEND_GLOBAL_PER_MINUTE`와 `BABI_EMAIL_SEND_SOURCE_PER_MINUTE`로 양의 정수만 지정할 수 있습니다. 누락 시 기본값을 쓰며, 0·음수·숫자가 아닌 값은 서버 기동을 실패시킵니다. 제한은 첫 요청부터 시작하는 60초 고정 구간으로 계산되며, 카운터는 구간이 끝나면 만료됩니다. 발송 실패나 동일 이메일 재발송도 요청으로 집계합니다.

초과 시 기존 응답 형식의 HTTP 429, `EMAIL_SEND_RATE_LIMITED`를 반환합니다. Discord 자체의 429는 발송 실패로 처리되므로 이 오류 코드와 구분됩니다. Redis 오류 시 발송을 허용하지 않고 HTTP 503 `EMAIL_VERIFICATION_UNAVAILABLE`을 반환합니다.

`X-Forwarded-For` 등 요청 헤더는 출처 판별에 사용하지 않습니다. `server.forward-headers-strategy=none`인 로컬에서는 서버에 직접 연결된 주소별로 제한합니다. AWS처럼 전달 헤더를 처리하는 환경에서는 주소 위조를 피하려고 모든 요청을 같은 출처로 취급하며 전역 제한도 함께 적용합니다. 따라서 프록시 뒤의 여러 사용자가 출처 한도를 공유할 수 있습니다. AWS에서 개별 사용자 제한이 필요하면 신뢰할 프록시 범위, 헤더 덮어쓰기, 직접 접근 차단을 팀에서 먼저 검증해야 합니다. 이 제한은 이메일 소유권 인증이나 시연 서버 접근 통제의 대체 수단이 아닙니다.

## AWS 시연 배포 준비

현재 AWS 연결과 배포는 검증되지 않았습니다. 기존 `deploy/babi-server.service`는 `aws`만 활성화하므로 Discord가 자동으로 켜지지 않습니다. 시연 배포는 별도의 승인된 서비스 설정으로 `aws,discord-demo`를 명시해야 합니다. 팀원이 기존 AWS 설정을 확인하고 **전용 시연 MySQL과 Redis**를 별도 준비해야 합니다. `aws,discord-demo` 프로필은 `SPRING_DATASOURCE_URL`을 `babi_discord_demo`로 명시하고 전용 DB 계정, 위의 Discord·Redis·비밀 환경변수를 제공해야만 시작합니다. 기존 `babi` 기본값으로는 안전 검증에 실패합니다. HTTPS 아웃바운드와 비공개 Discord 채널, 환경변수 전달, Redis 확인값, DB 스키마를 배포 전에 확인하세요. 세부 사항은 [AWS 운영 문서](docs/aws-runtime.md)를 참조하세요. 실제 사용자 인증에는 사용할 수 없습니다.

## 일일 리포트 API

`POST /auth/login`에서 받은 `accessToken`을 `Authorization: Bearer <token>`으로 전달합니다.
모든 리포트 API는 활성 계정이며 `active_baby_members`에 남아 있는 소유자 또는 `baby_permissions`가 부여된 멤버만 사용할 수 있습니다. 아기 생성자라도 멤버에서 제거되면 접근할 수 없습니다.

| Method | Endpoint | 동작 |
|---|---|---|
| POST | `/babi/api/babies/{babyId}/daily-reports` | 생성 (`reportDate`, `summary`) |
| GET | `/babi/api/babies/{babyId}/daily-reports/{date}` | 조회 |
| PATCH | `/babi/api/babies/{babyId}/daily-reports/{date}` | 요약 수정 (`summary`) |
| DELETE | `/babi/api/babies/{babyId}/daily-reports/{date}` | 삭제 |

7일 통계는 리포트 날짜 6일 전 00:00부터 다음 날 00:00 직전까지 계산합니다. 경계를 넘는 수면은 구간과 겹치는 시간만 포함합니다.

## 음성 기록 API

음성 입력은 STT 변환과 최종 저장을 분리합니다. STT 단계에서는 DB에 저장하지 않습니다.

| Method | Content-Type | Path | 설명 |
|---|---|---|---|
| `POST` | `multipart/form-data` | `/api/v1/babies/{babyId}/voice-records/stt` | `audio` 파일을 STT 변환해 transcript만 반환 |
| `POST` | `application/json` | `/api/v1/babies/{babyId}/voice-records` | 사용자가 확인·수정한 transcript를 `voice_inputs`에 저장 |
| `GET` | - | `/api/v1/babies/{babyId}/voice-records/{voiceInputId}` | 저장된 음성 기록 단건 조회 |
| `PATCH` | `application/json` | `/api/v1/babies/{babyId}/voice-records/{voiceInputId}` | 저장된 음성 기록의 transcript 수정 |

- STT 변환 결과는 DB에 저장하지 않으며, 사용자가 내용을 확인·수정한 뒤 저장 API를 호출합니다.
- 저장된 기록도 `PATCH` API로 다시 수정할 수 있습니다.
- 모든 음성 기록 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.
- 요청자는 해당 아기의 활성 구성원이어야 하며, 아니면 `403 FORBIDDEN`을 반환합니다. 아기가 없으면 `404 NOT_FOUND`를 반환합니다.
- 음성 파일은 25MB 이하이며 `mp3`, `mp4`, `m4a`, `mpeg`, `mpga`, `wav`, `webm`, `ogg`, `flac`, `aac`를 지원합니다.
- 저장 요청 예시:

```json
{
  "transcript": "2시에 분유 100ml 먹였어"
}
```

- 수정 요청 예시:

```json
{
  "transcript": "2시에 분유 120ml 먹였어"
}
```

> `familyMemberId`는 클라이언트가 보내지 않고, 인증된 사용자의 `baby_members.id`를 서버에서 결정합니다.

## 검증

```bash
./gradlew test
./gradlew build
```

## Swagger API 문서

서버 실행 후 다음 주소에서 현재 브랜치의 Controller와 DTO를 기반으로 생성한 API 문서를 확인할 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

문서와 UI 리소스는 JWT 없이 접근할 수 있습니다. 로그인·회원가입·이메일 인증 API도 기존대로 인증 없이 호출합니다. 보호된 API를 호출하려면 로그인 응답의 `accessToken`을 Swagger UI의 **Authorize** 버튼에 입력합니다. `Bearer ` 접두어 없이 JWT 값만 입력하면 됩니다. 이메일 인증의 `verificationToken`은 회원가입 요청 본문에 넣는 별도 토큰입니다.

Swagger의 **Try it out**은 실제 API 요청을 실행합니다. 이메일 발송·회원가입 등을 시도할 때는 사용 중인 서버와 연결 환경을 확인합니다.

## 패키지 구조

```text
com.select
├── global
│   ├── config
│   ├── exception
│   ├── response
│   └── security
└── domain
    ├── auth
    ├── baby
    ├── report
    ├── user
    └── voice
        ├── client
        ├── controller
        ├── dto
        │   ├── request
        │   └── response
        ├── entity
        ├── exception
        ├── repository
        ├── service
        └── support
```

새 핵심 기능은 `domain` 아래에 같은 계층으로 추가합니다.
