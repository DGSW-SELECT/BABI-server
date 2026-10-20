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

2. MySQL을 실행합니다.

```bash
docker compose up -d
docker compose ps
```

MySQL은 `127.0.0.1:3307`으로만 노출되며, 데이터는 `babi_mysql_data` Docker Volume에 보존됩니다.

3. IntelliJ에서 `local` 프로필로 애플리케이션을 실행합니다. 실행 구성에 환경 변수 `MYSQL_PASSWORD`를 `.env`의 값과 동일하게 설정해야 합니다. Gradle로 실행할 경우:

```bash
MYSQL_PASSWORD='your-local-password' ./gradlew bootRun --args='--spring.profiles.active=local'
```

종료는 `docker compose down`을 사용합니다. 이 명령은 컨테이너만 중지·제거하며 Volume은 삭제하지 않습니다.

테스트는 H2 메모리 DB를 사용합니다. 테스트 종료 후 데이터는 삭제되며 H2는 배포용 JAR에 포함되지 않습니다.

## 배포 DB

배포 환경은 MySQL DB를 준비하고 다음 환경 변수를 설정해야 합니다. 비밀번호를 Git에 저장하지 않습니다.

- `SPRING_DATASOURCE_URL`: `jdbc:mysql://localhost:3306/babi` 형식의 연결 주소
- `SPRING_DATASOURCE_USERNAME`: DB 계정
- `SPRING_DATASOURCE_PASSWORD`: DB 비밀번호

## 검증

```bash
./gradlew test
./gradlew build
```

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
