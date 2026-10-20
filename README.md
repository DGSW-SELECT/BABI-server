# BABI Server

BABI 서비스의 Spring Boot 백엔드 프로젝트입니다.

## 실행 환경

- Java 21
- Spring Boot 4.0.8
- Gradle 9.7.1

## 로컬 실행

Spring Boot는 IntelliJ 또는 Gradle로 호스트에서 실행하고, MySQL만 Docker로 실행합니다.

1. 환경 변수 파일을 준비합니다. 실제 비밀번호는 Git에 커밋하지 않습니다.

```bash
cp .env.example .env
```

`.env`에서 `MYSQL_ROOT_PASSWORD`와 `MYSQL_PASSWORD`를 각자의 로컬 비밀번호로 변경합니다.

`JWT_SECRET`은 JWT 서명 및 검증에 사용하는 필수 환경 변수입니다. `.env`의 `JWT_SECRET`을 각자의 무작위 비밀키로 변경합니다. HS256 서명에 사용하는 비밀키는 최소 32바이트 이상이어야 하며, 실제 비밀키는 Git에 커밋하지 않습니다.

`.env` 파일은 Docker Compose에서 설정값을 읽는 데 사용되며, 현재 프로젝트에는 호스트에서 실행하는 Spring Boot에 `.env`를 자동으로 로드하는 설정이 없습니다. 애플리케이션 실행 시 아래와 같이 환경 변수를 별도로 전달해야 합니다.

2. MySQL을 실행합니다.

```bash
docker compose up -d
docker compose ps
```

MySQL은 `127.0.0.1:3307`으로만 노출되며, MySQL 데이터 디렉터리(`/var/lib/mysql`)는 `babi_mysql_data` Docker Volume에 저장됩니다.

3. IntelliJ에서 `local` 프로필로 애플리케이션을 실행합니다. 실행 구성에 환경 변수 `MYSQL_PASSWORD`와 `JWT_SECRET`을 `.env`의 값과 동일하게 설정해야 합니다. Gradle로 실행할 경우:

```bash
MYSQL_PASSWORD='your-local-password' JWT_SECRET='your-random-jwt-secret-at-least-32-bytes' ./gradlew bootRun --args='--spring.profiles.active=local'
```

위 명령의 비밀번호와 비밀키 예시는 실제 값이 아닌 자리표시자이며, 실행 전에 `.env`에 설정한 값으로 변경해야 합니다.

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
