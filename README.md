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

실제 이메일 계정·앱 비밀번호·비밀키는 `.env`에만 보관하고 Git에 커밋하지 않습니다. 아래 실행 명령은 `.env`를 셸에서 읽으므로 `KEY=value` 형식을 유지하고, 공백이나 셸 특수문자가 들어가는 값은 작은따옴표로 감쌉니다. 직접 작성한 신뢰할 수 있는 `.env`만 읽습니다.

`.env` 파일은 Docker Compose에서 설정값을 읽는 데 사용되며, 현재 프로젝트에는 호스트에서 실행하는 Spring Boot에 `.env`를 자동으로 로드하는 설정이 없습니다. 애플리케이션 실행 시 아래와 같이 환경 변수를 별도로 전달해야 합니다.

2. MySQL과 Redis를 실행합니다.

```bash
docker compose up -d
docker compose ps
```

MySQL은 `127.0.0.1:3307`으로만 노출되며, MySQL 데이터 디렉터리(`/var/lib/mysql`)는 `babi_mysql_data` Docker Volume에 저장됩니다.

3. IntelliJ에서 `local` 프로필로 애플리케이션을 실행합니다. 실행 구성의 환경 변수에 `MYSQL_PASSWORD`, `JWT_SECRET`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `EMAIL_VERIFICATION_SECRET`을 `.env`의 값과 동일하게 설정해야 합니다. `.env` 파일을 만드는 것만으로 IntelliJ 실행 구성에 적용되지는 않습니다. `JWT_ACCESS_TOKEN_EXPIRATION_MINUTES`를 변경했다면 해당 값도 전달합니다.

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
