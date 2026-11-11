# BABI Server

BABI 서비스의 Spring Boot 백엔드 프로젝트입니다.

## 실행 환경

- Java 21
- Spring Boot 4.0.8
- Gradle 9.7.1

## 로컬 실행

Spring Boot는 IntelliJ 또는 Gradle로 호스트에서 실행하고, MySQL과 Redis는 Docker로 실행합니다.

1. 환경 변수 파일을 준비합니다. 실제 비밀번호는 Git에 커밋하지 않습니다.

```bash
cp .env.example .env
```

`.env`에서 `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`를 각자의 로컬 값으로 변경합니다.

`JWT_SECRET`은 JWT 서명 및 검증에 사용하는 필수 환경 변수입니다. `.env`의 `JWT_SECRET`을 각자의 무작위 비밀키로 변경합니다. HS256 서명에 사용하는 비밀키는 최소 32바이트 이상이어야 하며, 실제 비밀키는 Git에 커밋하지 않습니다.

`OPENAI_API_KEY`는 실제 STT 제공자를 사용할 때 필요한 환경 변수입니다. `STT_PROVIDER=auto`(기본값)에서 키가 없으면 개발용 `FakeSttClient`가 동작하므로 서버는 정상 기동됩니다. 실제 음성 변환을 사용하려면 키를 설정하고 `STT_PROVIDER=openai`를 지정합니다. 실제 키는 Git에 커밋하지 않습니다.

`local` 프로필에서는 이메일 인증을 호출하지 않더라도 `.env`의 다음 필수 환경 변수를 설정해야 합니다. 예시 파일의 값은 자리표시자이므로 실제 로컬 설정으로 바꿔야 합니다.

| 환경 변수 | 용도 및 설정 방법 |
| --- | --- |
| `MAIL_USERNAME` | Gmail SMTP 발신 계정의 이메일 주소를 입력합니다. |
| `MAIL_PASSWORD` | 해당 Gmail 계정에서 발급한 앱 비밀번호를 입력합니다. 일반 로그인 비밀번호를 사용하지 않습니다. |
| `EMAIL_VERIFICATION_SECRET` | 인증번호를 HMAC-SHA256으로 해시하는 독립적인 무작위 비밀키입니다. `JWT_SECRET`과 다른 값을 사용합니다. 예를 들어 `openssl rand -hex 32`로 생성한 값을 설정할 수 있습니다. |

실제 이메일 계정·앱 비밀번호·비밀키는 `.env`에만 보관하고 Git에 커밋하지 않습니다. 아래 실행 명령은 `.env`를 셸에서 읽으므로 `KEY=value` 형식을 유지하고, 공백이나 셸 특수문자가 들어가는 값은 작은따옴표로 감쌉니다. 직접 작성한 신뢰할 수 있는 `.env`만 읽습니다.

`.env` 파일은 Docker Compose에서 설정값을 읽는 데 사용되며, 현재 프로젝트에는 호스트에서 실행하는 Spring Boot에 `.env`를 자동으로 로드하는 설정이 없습니다. 애플리케이션 실행 시 아래와 같이 환경 변수를 별도로 전달해야 합니다.

2. MySQL과 Redis를 실행합니다.

```bash
docker compose up -d
docker compose ps
```

MySQL은 `127.0.0.1:3307`으로만 노출되며, MySQL 데이터 디렉터리(`/var/lib/mysql`)는 `babi_mysql_data` Docker Volume에 저장됩니다.

3. IntelliJ에서 `local` 프로필로 애플리케이션을 실행합니다. 실행 구성의 환경 변수에 `MYSQL_PASSWORD`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `EMAIL_VERIFICATION_SECRET`을 `.env`의 값과 동일하게 설정해야 합니다. 실제 STT를 사용할 때만 `OPENAI_API_KEY`와 `STT_PROVIDER=openai`를 추가합니다. `.env` 파일을 만드는 것만으로 IntelliJ 실행 구성에 적용되지는 않습니다. `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`를 변경했다면 해당 값도 전달합니다.

Gradle로 실행할 경우 프로젝트 루트에서 다음 명령을 실행합니다. Bash 또는 Zsh 기준이며, 괄호 안의 별도 셸에서 `.env`의 변수를 export한 뒤 Spring Boot를 실행합니다.

```bash
(
  set -a
  . ./.env || exit
  set +a
  ./gradlew bootRun --args='--spring.profiles.active=local'
)
```

`set -a`는 `.env`에서 읽은 변수를 자식 Gradle·Spring Boot 프로세스에 전달합니다. 별도 셸이 종료되면 이 설정은 현재 터미널에 남지 않습니다.

종료는 `docker compose down`을 사용합니다. 이 명령은 컨테이너와 네트워크를 제거하며, 일반적으로 `babi_mysql_data` Volume의 DB 데이터는 유지됩니다.

**주의:** `docker compose down -v`는 `babi_mysql_data` 데이터 Volume도 삭제하므로 저장된 DB 데이터가 손실될 수 있습니다. 기존 데이터를 유지해야 한다면 `-v` 옵션을 사용하지 않습니다.

## 음성 기록 API

음성 입력은 동기 STT, 비동기 STT 작업, 최종 저장으로 나뉩니다.

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/v1/babies/{babyId}/voice-records/stt` | `multipart/form-data`의 `audio` 파일을 즉시 STT 변환해 transcript만 반환 |
| `POST` | `/api/v1/babies/{babyId}/voice-records/stt-jobs` | 음성 파일을 저장하고 비동기 STT 작업 등록 (`202 Accepted`) |
| `GET` | `/api/v1/babies/{babyId}/voice-records/stt-jobs/{jobId}` | 비동기 STT 작업 상태·결과 조회 |
| `POST` | `/api/v1/babies/{babyId}/voice-records/stt-jobs/{jobId}/retry` | 최종 실패(`FAILED`)한 작업 재시도 |
| `POST` | `/api/v1/babies/{babyId}/voice-records` | 사용자가 확인한 transcript를 `voice_inputs`에 저장하고 육아기록을 자동 분석·저장 |
| `GET` | `/api/v1/babies/{babyId}/voice-records/{voiceInputId}` | 저장된 음성 기록과 자동 저장된 육아기록 단건 조회 |

- 모든 음성 기록 API는 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.
- 음성 파일은 25MB 이하이며 `mp3`, `mp4`, `m4a`, `mpeg`, `mpga`, `wav`, `webm`, `ogg`, `flac`, `aac`를 지원합니다.

### 비동기 STT 작업

비동기 작업은 `stt_jobs` 테이블과 백그라운드 워커로 처리합니다.

```text
PENDING -> PROCESSING -> COMPLETED
                     \-> PENDING (재시도 대기) -> PROCESSING ...
                     \-> FAILED (최대 시도 횟수 초과)
```

1. `POST .../stt-jobs?familyMemberId=7`에 `audio` 파일을 보내면 파일을 저장하고 `PENDING` 작업을 만듭니다.
2. 워커가 작업을 원자적으로 선점해 `PROCESSING`으로 바꾸고 STT를 호출합니다.
3. 성공하면 `voice_inputs`와 `parenting_records`를 저장하고 작업을 `COMPLETED`로 바꿉니다. 응답의 `voiceInputId`로 저장된 기록을 조회할 수 있습니다.
4. 실패하면 `stt.jobs.retry-delay` 후 재시도하고, `stt.jobs.max-attempts`를 넘으면 `FAILED`로 남깁니다. `FAILED` 작업은 재시도 API로 다시 등록할 수 있습니다.
5. 서버 중단으로 `PROCESSING`에 오래 머문 작업은 `stt.jobs.stale-timeout` 이후 자동 복구됩니다.

작업 등록 응답 예시:

```json
{
  "success": true,
  "message": "음성 변환 작업이 등록되었습니다.",
  "data": {
    "jobId": 20,
    "babyId": 1,
    "familyMemberId": 7,
    "status": "PENDING",
    "transcript": null,
    "voiceInputId": null,
    "attemptCount": 0,
    "maxAttempts": 3,
    "errorMessage": null,
    "createdAt": "2026-11-03T16:44:00",
    "completedAt": null,
    "parentingRecords": []
  }
}
```

완료 조회 응답의 `data`에는 `status: "COMPLETED"`, `transcript`, `voiceInputId`, `parentingRecords`가 채워집니다.

비동기 STT 주요 설정:

| 설정 | 기본값 | 설명 |
|---|---|---|
| `stt.provider` | `auto` | `auto`: 키가 있으면 실제 API, 없으면 fake / `openai`: 실제 API 강제 / `fake`: 모델 없이 흐름 검증 |
| `stt.fake-transcript` | `분유 120ml 먹였어요` | fake 제공자가 반환할 문장 |
| `stt.jobs.storage-dir` | `./storage/stt` | 음성 파일 저장 디렉터리 (`storage/`는 Git 제외) |
| `stt.jobs.max-attempts` | `3` | 최대 처리 시도 횟수 |
| `stt.jobs.retry-delay` | `30s` | 실패 후 재시도 대기 시간 |
| `stt.jobs.stale-timeout` | `10m` | 멈춘 `PROCESSING` 작업 복구 기준 |
| `stt.jobs.worker.enabled` | `true` | 백그라운드 워커 실행 여부 |
| `stt.jobs.worker.poll-interval` | `5s` | 작업 폴링 주기 |
| `stt.jobs.worker.initial-delay` | `10s` | 서버 기동 후 첫 폴링 지연 |
| `stt.jobs.worker.batch-size` | `5` | 한 번에 가져올 작업 수 |

- 실제 모델이 확정되면 `RestSttClient` 또는 새 `SttClient` 구현을 추가하고 `SttClientConfig`에서 선택하면 됩니다. 작업 상태·재시도·저장 흐름은 모델과 무관하게 그대로 사용합니다.
- 저장 요청 예시:

```json
{
  "transcript": "2시에 분유 100ml 먹였어",
  "familyMemberId": 1
}
```

- 저장 시 transcript에서 수유, 수면, 기저귀 기록을 규칙 기반으로 추출해 `parenting_records`에 함께 저장합니다.
- 한 문장에서 여러 기록이 인식되면 여러 건이 저장됩니다. 예: `분유 120ml 먹이고 기저귀 갈았어` → 수유 1건, 기저귀 1건
- 기록 시각은 문장의 시간 표현(`오후 2시`, `14:30`, `어제 저녁 7시`)을 사용하고, 시간 표현이 없으면 음성 기록 저장 시각을 사용합니다.
- 기록 종류를 인식하지 못한 문장은 `voice_inputs`에만 저장되고 `parentingRecords`는 빈 배열로 반환됩니다.
- 현재 규칙 기반 분석기가 인식하는 표현:
  - 수유: `분유`, `모유`, `수유`, `이유식`, `젖병`, `직수`, `퓨레`, `죽`, `밥`, 금액 단위 `ml`/`cc`
  - 수면: `잠을`, `잠이`, `잠들`, `낮잠`, `밤잠`, `잤`, `수면`, `재웠` + `N시간 M분`/`N분` duration
  - 기저귀: `기저귀`, `응가`, `대변`, `오줌`, `소변`, `쉬야`, `똥`
- 응답 예시:

```json
{
  "success": true,
  "message": "음성 기록이 저장되었습니다.",
  "data": {
    "voiceInputId": 10,
    "babyId": 1,
    "familyMemberId": 7,
    "transcript": "오후 2시에 분유 100ml 먹였어",
    "sttStatus": "COMPLETED",
    "createdAt": "2026-11-03T16:44:00",
    "parentingRecords": [
      {
        "parentingRecordId": 100,
        "babyId": 1,
        "familyMemberId": 7,
        "voiceInputId": 10,
        "recordType": "FEEDING",
        "occurredAt": "2026-11-03T14:00:00",
        "amountMl": 100,
        "durationMinutes": null,
        "feedingType": "FORMULA",
        "diaperType": null,
        "memo": "오후 2시에 분유 100ml 먹였어",
        "createdAt": "2026-11-03T16:44:00"
      }
    ]
  }
}
```

> 현재 분석기는 MVP용 규칙 기반 구현입니다. `ParentingRecordAnalyzer` 인터페이스를 유지하고 있으므로 이후 LLM 기반 분석기로 교체할 수 있습니다.

> family 도메인은 추가되어 있지만, 현재 음성 기록 API는 `familyMemberId`를 요청으로 받습니다. JWT 사용자와 가족 구성원을 연결해 서버에서 ID를 결정하는 작업은 아직 반영되지 않았습니다.

## Swagger API 문서

서버 실행 후 다음 주소에서 현재 브랜치의 Controller와 DTO를 기반으로 생성한 API 문서를 확인할 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

문서와 UI 리소스는 JWT 없이 접근할 수 있습니다. 로그인·회원가입·이메일 인증 API도 기존대로 인증 없이 호출합니다. 보호된 API를 호출하려면 로그인 응답의 `accessToken`을 Swagger UI의 **Authorize** 버튼에 입력합니다. `Bearer ` 접두어 없이 JWT 값만 입력하면 됩니다. 이메일 인증의 `verificationToken`은 회원가입 요청 본문에 넣는 별도 토큰입니다.

Swagger의 **Try it out**은 실제 API 요청을 실행합니다. 이메일 발송·회원가입 등을 시도할 때는 사용 중인 서버와 연결 환경을 확인합니다.

## 코드 구조

```text
src
├── main
│   ├── java/com/select
│   │   ├── BabiServerApplication.java
│   │   ├── global
│   │   │   ├── config
│   │   │   ├── exception
│   │   │   ├── response
│   │   │   ├── security/jwt
│   │   │   └── util
│   │   └── domain
│   │       ├── auth (controller, dto/request, dto/response, exception, mail, service)
│   │       ├── baby (entity, exception, repository, service)
│   │       ├── family (entity, repository)
│   │       ├── parenting (analyzer, dto/response, entity, repository, service)
│   │       ├── user (controller, dto/request, dto/response, entity, exception, repository, service)
│   │       └── voice
│   │           ├── client/dto/response
│   │           ├── controller
│   │           ├── dto/request, dto/response
│   │           ├── entity
│   │           ├── exception
│   │           ├── repository
│   │           ├── service
│   │           ├── storage
│   │           └── support
│   └── resources (application.yml, application-local.yml)
└── test
    ├── java/com/select
    │   ├── BabiServerApplicationTests.java
    │   ├── domain (auth, parenting, user, voice)
    │   └── global (config, exception, security/jwt)
    └── resources (application-test.yml, test schemas)
```

새 기능은 `domain/<기능>` 아래에 필요한 계층을 추가합니다. 공통 설정·예외·응답·인증 코드는 `global`에 둡니다.
