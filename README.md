# BABI Server

BABI 서비스의 Spring Boot 백엔드 프로젝트입니다.

## 실행 환경

- Java 21
- Spring Boot 4.0.8
- Gradle 9.7.1

## 로컬 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
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
