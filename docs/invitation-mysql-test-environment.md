> **중단된 이전 수동 환경 — 아래 명령을 실행하지 마세요.** 이 문서의 나머지는 과거 기록입니다. 기존 수동 경로는 공유 MySQL·Redis를 사용해 현재 비활성화되었습니다. `scripts/invitation_mysql.py`와 `invitation-mysql` 프로필은 접속 전에 중단됩니다. 재현 가능한 테스트는 [격리 MySQL 자동 테스트](invitation-isolated-mysql-automated-tests.md)의 `python3 scripts/run_isolated_invitation_mysql.py`를 사용하세요. 기존 `work/` 메일함과 자격 파일은 자동 삭제하지 않으며, 이전 기록의 결과는 현재 검증 결과가 아닙니다.

# 보호자 초대 MySQL·Redis 격리 테스트 환경

2026-10-29 KST. 기존 컨테이너·볼륨과 `babi` DB는 유지한다. 기존 API/비즈니스 코드, application-local.yml, H2 테스트 설정은 변경하지 않았다.

## 구성

| 항목 | 설정 |
|---|---|
| MySQL | 기존 `babi-server-mysql-1`, 127.0.0.1:3307 |
| 새 DB | `babi_invitation_test`, utf8mb4 / utf8mb4_0900_ai_ci |
| 실행 계정 | `babi_invitation_tester` — 테스트 DB에만 SELECT/INSERT/UPDATE/DELETE |
| Java | macOS java_home으로 Java 21을 명시적으로 선택 |
| Spring 프로필 | `invitation-mysql` 단독 사용, JPA ddl-auto=validate, SQL 자동 초기화 금지 |
| 서버 | 127.0.0.1:18081 |
| Redis | 기존 `babi-server-redis-1`, 127.0.0.1:6379, 논리 DB 15 |
| Redis 키 | 모든 StringRedisTemplate 키 앞에 `babi_invitation_test:` 추가 |
| 메일 | 외부 SMTP 대신 비공개 로컬 메일함에 저장하는 테스트 EmailSender |

`babi_user`의 기존 개발 DB 권한은 변경하지 않았다. 테스트 계정은 개발 DB SELECT조차 거부되며 CREATE/ALTER/DROP 권한도 없다. 계정 Host의 `%`는 Docker bridge 연결을 위해 사용한다. 기존 MySQL 호스트 포트는 계속 127.0.0.1에만 공개된다.

## 생성한 테이블과 데이터

- `users`, `babies`, `baby_members`: 원본의 CREATE 구조·인덱스를 복제했으며 행 데이터는 복사하지 않았다. 원본에 FK가 없음을 먼저 확인했다. AUTO_INCREMENT 현재 값은 구조 비교에서 제외한다.
- `baby_invitation_codes`, `allocated_baby_invitation_codes`, `baby_email_invitations`: 기존 `docs/schema/baby-invitations.mysql.sql`을 검토한 뒤 새 DB에서만 실행했다.
- 신규 FK 2개는 모두 같은 테스트 DB의 `babies`를 참조한다. UNIQUE 인덱스 및 초대 status CHECK도 검증한다.
- 스모크 테스트는 테스트 DB에만 합성 OWNER/초대 대상 계정 2개, 아기 1개, 보호자 관계 2개와 초대 데이터를 생성한다. 실제 개발 DB의 이메일·회원 데이터를 사용하지 않는다.
- 제약 테스트는 별도 트랜잭션의 임시 레코드로 오류를 검증하고 모두 롤백한다. 테스트 DB의 AUTO_INCREMENT 번호에 간격이 생길 수 있다.

## 실행

프로젝트 루트에서 실행한다. setup은 최초 1회만 사용하며 이미 존재하는 DB·계정·자격 파일을 초기화하거나 덮어쓰지 않는다. 이 작업에서는 setup이 이미 완료되었다.

```bash
cd /Users/dgsw49/Desktop/Development/BABI-server

# 상태 확인: 원본 DB/보호 Redis 해시, 테스트 계정·테이블·제약을 확인
python3 scripts/invitation_mysql.py verify

# Java 21로 bootJar를 빌드한 뒤 격리 프로필로 실행
python3 scripts/invitation_mysql.py run
```

서버가 실행 중인 별도 터미널에서:

```bash
# 실제 MySQL + Redis + JWT/API 검증. 외부 메일은 보내지 않음
python3 scripts/invitation_mysql.py smoke

# 실제 FK/UNIQUE/CHECK 위반 7건을 기대하고 모든 임시 레코드를 롤백
python3 scripts/invitation_mysql.py constraints
```

다른 새 개발 환경에서 최초 구성할 때만 `python3 scripts/invitation_mysql.py setup`을 사용한다. DB/계정/로컬 설정이 이미 있으면 재생성하지 않고 중단한다. 일부 준비만 완료된 상태에서도 자동 DROP·복구를 하지 않는다.

실행 스크립트는 기존 SPRING_* 설정과 개발 DB/메일 비밀번호 환경변수를 자식 프로세스에서 제거한 뒤 테스트 전용 값만 적용한다. 테스트 프로필 자체도 다른 프로필의 병용, 다른 DB URL/사용자, ddl-auto 변경, Redis URL/DB 변경을 검사해 잘못된 연결 설정이면 기동을 거부한다. 직접 local 프로필과 함께 실행하지 않는다.

## 비밀값과 메일함

다음 파일은 기존 `work/` ignore 규칙으로 Git에서 제외된다.

- `work/invitation-mysql.env`: 새 테스트 DB 비밀번호, 별도 JWT/이메일 검증 secret, 합성 계정 비밀번호와 포트. 파일 권한 600.
- `work/invitation-mysql-verification.json`: 원본 DB의 테이블/행 수, 스키마·데이터 해시 및 기존 Redis 해시. 사용자 레코드 원문은 저장하지 않는다.
- `work/invitation-mysql-mail/*.txt`: 테스트 발송의 수신 주소·제목·본문. 디렉터리 700, 파일 600. 콘솔에 인증번호·초대 코드·이메일 본문을 출력하지 않는다.
- `work/invitation-mysql-smoke.json`: 합성 계정 이메일과 테스트 아기 ID, baseUrl. JWT나 비밀번호는 저장하지 않는다.

`@Primary` 테스트 EmailSender가 실제 GmailEmailSender 대신 사용된다. SMTP 설정도 127.0.0.1:1로 고정하므로 정상 SMTP 서버로 발송하지 않는다. 이 환경의 발송 성공은 로컬 파일 저장 성공이며 실제 SMTP 전달 테스트가 아니다. 원본 `.env`나 Gmail 계정 설정은 읽거나 수정하지 않는다.

Redis 키 직렬화기는 일반 명령뿐 아니라 Lua의 KEYS에도 접두사를 적용한다. 인증 이메일의 email:*와 초대의 baby:invite:* 로직은 수정하지 않았다. 값 직렬화는 기존 문자열 그대로 유지한다. 논리 DB 15에서도 접두사 없는 기존 키는 건드리지 않는다. FLUSHDB/FLUSHALL은 사용하지 않는다.

## 보호·검증 근거

최초 구성 전 `babi`의 테이블 DDL과 정렬된 데이터 덤프를 읽기 전용으로 해시했다. 덤프 내용은 메모리에서 해시만 계산하며 테스트 DB·파일·콘솔에 저장/복사하지 않는다. 구성 후와 API 실행 후 같은 해시와 행 수를 비교한다. 스키마/데이터가 다르면 원본을 수정·복원하지 않고 중단한다. 다른 개발 프로세스의 정상 변경이 발생한 경우에도 해시가 달라질 수 있으므로 원인을 먼저 확인한다.

보호 대상 Redis는 DB 0의 기존 키와 DB 15의 테스트 접두사 외 기존 키를 값 덤프 해시로 비교한다. TTL은 해시에서 제외하지만 키가 자연 만료되거나 다른 프로세스가 변경하면 검증은 중단할 수 있다. 연결 테스트는 자신이 만든 정확한 접두사/난수 키만 삭제한다.

실제 확인 항목:

- MySQL 테스트 계정 연결 및 6개 테이블, 기존 3개 구조 비교.
- FK 2개, UNIQUE 인덱스, 초대 상태 CHECK 메타데이터.
- 개발 DB 접근 거부와 테스트 DB DML 4개 권한만 부여된 상태.
- FK 2건·UNIQUE 4건·CHECK 1건의 실제 위반 거부 및 임시 레코드 롤백.
- Redis PONG과 제한된 접두사 키 쓰기/읽기/삭제.
- Java 21·Spring Boot 4.0.8 기동, JPA validate 통과, 실제 테스트 JDBC URL.
- 실제 MySQL·Redis를 사용하는 인증 메일 대역→검증→회원가입→로그인, 아기 등록, 초대 코드 조회·발송 대역·확인·수락·보호자 조회.
- 기존 H2 기반 자동화 테스트는 별도로 유지한다. 실제 Redis가 필요한 기존 EmailSignupIntegrationTest는 기존 조건에 따라 별도 실행 대상이다.

## Postman 준비

1. `baseUrl=http://127.0.0.1:18081`로 설정하고 서버를 위 run 명령으로 실행한다. `/babi/api/v1/` 접두사는 현재 앱에 없다.
2. 합성 계정은 `owner@babi-invitation-test.example.invalid`, `guest@babi-invitation-test.example.invalid`이다. 비밀번호는 로컬 env 파일의 `INVITATION_TEST_ACCOUNT_PASSWORD` 값만 사용한다. DB 비밀번호·JWT 서명 secret·전체 env 파일을 Postman에 가져오거나 공유하지 않는다.
3. 각각 `POST {{baseUrl}}/auth/login`에 `{"email":"...","password":"..."}`를 보내 `data.accessToken`을 ownerToken/guestToken으로 보관한다. 토큰은 로컬 비밀 변수로 관리하고 공유 export에서 제외한다.
4. 보호된 요청에는 `Authorization: Bearer {{ownerToken}}` 또는 guestToken과 JSON 요청에는 `Content-Type: application/json`을 지정한다.
5. 스모크가 만든 아기는 이미 guest가 MEMBER다. 이 아기에게 다시 초대하면 정상적으로 409가 나온다. 정상 초대 흐름을 새로 시험하려면 OWNER로 `POST /babies`에 `{"name":"수동 테스트 아기","birthDate":"2025-01-01"}`를 보내 새 babyId를 만든다.
6. OWNER로 `GET /babies/{babyId}/invitation-code`, `POST /babies/{babyId}/invitations`의 `{"email":"guest@babi-invitation-test.example.invalid"}`를 호출한다. 메일은 로컬 메일함에서 확인한다.
7. guestToken으로 `POST /babies/invitations/check`, 이어 `POST /babies/invitations/accept`에 `{"code":"받은 6자리 코드"}`를 보낸다. OWNER로 보호자 목록을 조회하면 두 명이 표시된다.
8. 새 계정 가입을 시험할 경우 `/auth/email/send`의 인증번호도 로컬 메일함에서 읽는다. 실제 이메일 수신함으로는 발송하지 않는다. 초대 링크의 example.invalid 주소는 로컬 안내용 자리표시자이므로 앱/웹 이동 검증에 사용할 수 없다.

개발 DB에 연결된 기존 서버를 baseUrl로 사용하지 않는다. 테스트 환경을 종료할 때는 위 run 명령의 터미널에서 Ctrl-C를 사용한다. 기존 Docker 컨테이너/볼륨과 두 DB를 제거하거나 초기화할 필요가 없다.

## 이번 구성의 최종 결과

- 기존 `babi`: users 3행, babies 1행, baby_members 2행 유지. 구성 전·후와 API 검증 후의 스키마·데이터 해시 동일.
- 새 테스트 DB: 합성 사용자 2명·아기 1명·보호자 2명, 아기 코드 1개·영구 코드 예약 1개·수락된 이메일 초대 1개 유지.
- Java 21·Spring Boot 4.0.8 기동 및 MySQL ddl-auto=validate 성공. 실제 JDBC URL이 babi_invitation_test임을 확인.
- MySQL/Redis/JWT/API 스모크와 실제 제약 위반 7건 검증 성공. 기존 H2 테스트 320건 통과, 실패/오류 0.
- SMTP는 외부 발송하지 않았다. 테스트 메일만 비공개 로컬 메일함에 저장했다.
- 검증에 사용한 invitation-mysql JVM만 종료했다. 컨테이너·볼륨·DB·테스트 계정은 유지했다. 위 run 명령으로 다시 시작한다.
- 이번 작업에서 추가한 소스/문서는 application-invitation-mysql.yml, InvitationMysqlTestConfig.java, scripts/invitation_mysql.py, 이 문서의 4개다. 기존 기능 작업 파일과 미추적 파일은 수정·삭제하지 않았다.
