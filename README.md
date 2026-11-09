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

`.env`에서 `MYSQL_ROOT_PASSWORD`와 `MYSQL_PASSWORD`를 각자의 로컬 비밀번호로 변경합니다.

`JWT_SECRET`은 JWT 서명 및 검증에 사용하는 필수 환경 변수입니다. `.env`의 `JWT_SECRET`을 각자의 무작위 비밀키로 변경합니다. HS256 서명에 사용하는 비밀키는 최소 32바이트 이상이어야 하며, 실제 비밀키는 Git에 커밋하지 않습니다.

`local` 프로필에서는 이메일 인증을 호출하지 않더라도 `.env`의 다음 필수 환경 변수를 설정해야 합니다. 예시 파일의 값은 자리표시자이므로 실제 로컬 설정으로 바꿔야 합니다.

| 환경 변수 | 용도 및 설정 방법 |
| --- | --- |
| `MAIL_USERNAME` | Gmail SMTP 발신 계정의 이메일 주소를 입력합니다. |
| `MAIL_PASSWORD` | 해당 Gmail 계정에서 발급한 앱 비밀번호를 입력합니다. 일반 로그인 비밀번호를 사용하지 않습니다. |
| `EMAIL_VERIFICATION_SECRET` | 인증번호를 HMAC-SHA256으로 해시하는 독립적인 무작위 비밀키입니다. `JWT_SECRET`과 다른 값을 사용합니다. 예를 들어 `openssl rand -hex 32`로 생성한 값을 설정할 수 있습니다. |

실제 이메일 계정·앱 비밀번호·비밀키는 Git에 커밋하지 않습니다. `.env`는 `.gitignore`에 포함되어 있습니다. Docker Compose와 Spring Boot가 같은 파일을 읽으므로 따옴표 없이 `KEY=value` 형식으로 작성하고, 값에는 공백·백슬래시·달러 기호를 사용하지 않습니다. 각 프로그램의 해석 방식이 달라질 수 있습니다. 비밀키에는 `openssl rand -hex 32`로 생성한 값을 사용할 수 있습니다. Gmail 앱 비밀번호는 표시되는 공백을 제거하여 입력합니다. 외부에서 받은 `.env`를 셸 명령으로 실행하지 않습니다.

`local` 프로필은 실행 프로세스의 **작업 디렉터리**에 있는 `.env`를 Spring의 properties 파일로 읽습니다. 파일 자체가 없어도 시작은 진행하지만 필요한 값이 다른 곳에도 없으면 시작에 실패합니다. OS 환경변수 또는 IntelliJ 실행 구성에서 명시한 환경변수가 `.env` 값보다 우선합니다. `local` 외의 프로필은 이 파일을 자동으로 읽지 않으며 운영 환경은 기존처럼 필요한 비밀값을 별도로 제공해야 합니다.

2. MySQL과 Redis를 실행합니다.

```bash
docker compose up -d
docker compose ps
```

MySQL은 `127.0.0.1:3307`으로만 노출되며, MySQL 데이터 디렉터리(`/var/lib/mysql`)는 `babi_mysql_data` Docker Volume에 저장됩니다.

3. IntelliJ의 Spring Boot 실행 구성에서 Main class를 `com.select.BabiServerApplication`, Active profiles를 `local`, Working directory를 **BABI-server 프로젝트 루트**로 지정하고 실행합니다. 이 설정에서는 `.env`의 값이 Spring 설정으로 전달되므로 환경변수를 하나씩 복사할 필요가 없습니다. 실행 구성에 동일한 환경변수가 이미 들어 있다면 그 값이 우선하므로 오래된 값이 없는지 확인합니다. IntelliJ의 별도 `.env` 플러그인은 필요하지 않습니다.

Gradle로 실행할 경우 프로젝트 루트에서 다음 명령을 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

`Could not resolve placeholder 'EMAIL_VERIFICATION_SECRET'`가 계속 발생하면 `local` 프로필, 실행 구성의 Working directory, `.env` 파일과 해당 키의 존재 여부를 확인합니다. 설정값 자체는 로그나 스크린샷에 노출하지 않습니다.

애플리케이션은 시작 시 `schema.sql`을 자동 실행하지 않습니다 (`spring.sql.init.mode: never`). 실행 전에 팀에서 승인한 스키마를 별도로 준비해야 하며, 로컬 프로필은 Hibernate `validate`로 엔티티와 스키마를 검증합니다. 서버 시작은 기존 데이터베이스를 초기화하거나 테이블을 변경하지 않습니다. 스키마 변경은 별도 검토와 마이그레이션이 필요합니다.

종료는 `docker compose down`을 사용합니다. 이 명령은 컨테이너와 네트워크를 제거하며, 일반적으로 `babi_mysql_data` Volume의 DB 데이터는 유지됩니다.

**주의:** `docker compose down -v`는 `babi_mysql_data` 데이터 Volume도 삭제하므로 저장된 DB 데이터가 손실될 수 있습니다. 기존 데이터를 유지해야 한다면 `-v` 옵션을 사용하지 않습니다.

테스트는 H2 메모리 DB를 사용합니다. 테스트 종료 후 데이터는 삭제되며 H2는 배포용 JAR에 포함되지 않습니다.

## 배포 DB

배포 환경은 MySQL DB를 준비하고 다음 환경 변수를 설정해야 합니다. 비밀번호를 Git에 저장하지 않습니다.

- `SPRING_DATASOURCE_URL`: `jdbc:mysql://localhost:3306/babi` 형식의 연결 주소
- `SPRING_DATASOURCE_USERNAME`: DB 계정
- `SPRING_DATASOURCE_PASSWORD`: DB 비밀번호
- `JWT_SECRET`: JWT 서명 키 (32바이트 이상)

AWS 프로필·Redis·SMTP 미설정 데모 운영과 `main` 자동 배포는 [AWS 운영 문서](docs/aws-runtime.md)를 참고합니다. 운영 DB는 자동 초기화하거나 migration하지 않습니다.

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
    └── user
        ├── controller
        ├── dto
        │   ├── request
        │   └── response
        ├── entity
        ├── repository
        └── service
```

새 핵심 기능은 `domain` 아래에 같은 계층으로 추가합니다.

Discord에 Webhook으로 연결되어 확인메시지가 전송됩니다

### 로그인 쿠키와 토큰 갱신

로그인에 성공하면 Access Token과 일회용 Refresh Token이 각각 `babi_access`, `babi_refresh` HttpOnly 쿠키로 발급됩니다. 기존 Bearer 헤더 및 로그인 응답의 `accessToken`도 호환을 위해 유지합니다. Refresh Token은 Redis에 해시로 저장되며, `POST /auth/refresh` 성공 시 교체됩니다. Redis에 장애가 발생하면 발급과 갱신은 실패합니다.

쿠키를 사용하는 쓰기 요청에는 `X-CSRF-Token` 헤더가 필요합니다. 로그인 후 `GET /auth/csrf` 응답의 `data.token`을 읽어 헤더에 넣고, 갱신 후에는 새 토큰을 다시 조회하세요. 브라우저에서는 쿠키 전송을 위해 `credentials: 'include'`를 사용하세요. 로그아웃은 `POST /auth/logout`이며 해당 세션의 Refresh Token을 폐기합니다. Access Token이 만료된 뒤에는 먼저 갱신한 다음 로그아웃하세요.

쿠키는 기본적으로 `Secure`, `SameSite=Lax`입니다. 로컬 HTTP 개발에 한해 `AUTH_COOKIES_SECURE=false`와 `local` 프로필을 함께 사용할 수 있습니다. 서로 다른 출처를 사용한다면 `AUTH_CORS_ALLOWED_ORIGINS=https://프론트엔드주소`에 신뢰할 수 있는 정확한 출처만 쉼표로 구분해 설정하세요. 미설정 시 `local`·`test` 프로필은 기존 `http://localhost:5173`을 허용하고, 다른 환경은 교차 출처를 허용하지 않습니다. 서로 다른 최상위 사이트 간 쿠키 사용은 현재 지원하지 않으므로 배포 주소가 확정되면 별도 검토가 필요합니다.
