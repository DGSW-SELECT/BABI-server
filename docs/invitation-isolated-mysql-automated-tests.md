# 보호자 초대 격리 MySQL 자동 테스트

## 실행

프로젝트 루트에서 `python3 scripts/run_isolated_invitation_mysql.py`를 실행한다. 로컬에 Docker와 `mysql:8.4` 이미지가 있어야 하며 이미지를 자동으로 내려받지 않는다. Java 21과 프로젝트 Gradle wrapper를 사용한다.

스크립트는 난수 이름의 새 MySQL 컨테이너를 생성하고 테스트 전용 DB·계정·임시 비밀번호를 부여한다. 데이터 디렉터리는 전용 tmpfs이며 호스트 포트는 `127.0.0.1`의 임의 포트다. 개발용 `3307`, `babi` DB, 기존 컨테이너·볼륨·`.env`는 사용하지 않는다. 컨테이너가 준비되면 `src/test/resources/mysql/invitation-base.sql`과 `docs/schema/baby-invitations.mysql.sql`을 이 새 DB에만 적용한다. 성공·실패와 무관하게 이 스크립트가 생성한 컨테이너만 종료한다.

이 스키마는 테스트용 신규 DB의 제안 구조다. 운영·개발 DB의 덤프나 실제 마이그레이션이 아니다. 기존 MySQL 수동 테스트 프로필과 `scripts/invitation_mysql.py`는 공유 서비스 접근을 방지하기 위해 비활성화되었다. 이 문서의 독립 컨테이너 자동 테스트를 사용한다.

MySQL 테스트는 `BabyInvitationMySqlIntegrationTest`를 명시적으로 실행한다. 기존 초대 H2 테스트를 상속하여 동일한 HTTP/JWT·서비스·동시성 시나리오를 MySQL에서 수행하며, 메일 발송과 초대 제한기는 Mock이다. 테스트 프로필의 테스트 전용 JWT·이메일 secret을 사용한다. JPA는 `validate`이며 스키마 자동 생성·수정은 꺼져 있다. 테스트 DB에 생성한 무작위 마커를 연결 전에 검사하고, DB 이름·사용자와 로컬 포트를 제한한다. 결과 XML은 성공 시 `build/invitation-mysql-results/`에 보존한다. 테스트 계정은 임시 DB에서만 사용한다.

## 스키마 검토

테스트는 6개 테이블의 PK, 5개 자동 증가 ID, 주요 UNIQUE, 코드 바이너리 타입·길이, 이메일·generation 문자열 길이, 날짜 타입, NOT NULL, InnoDB, 초대 테이블의 아기 FK를 확인한다. FK, NOT NULL, 회원과 이메일 초대 UNIQUE 위반이 실제 MySQL에서 거부되고 해당 테스트 트랜잭션이 롤백되는지도 검사한다. Hibernate의 `validate`도 실행한다.

별도 테스트용 기본 DDL과 제공된 초대 제안 DDL을 조합한 결과다. 기존 `baby_members`에는 baby/user FK가 정의되어 있지 않으며 `babies.user_id`도 이 fixture에서는 FK가 없다. 발급 원장과 활성 코드 사이의 FK, 이메일 초대의 `(baby_id,generation)`과 현재 코드 세대 사이의 FK도 없다. 서비스의 잠금·검증으로 처리하는 부분이므로, 실제 배포 스키마의 FK·인덱스·이관 계획은 팀이 별도로 확정해야 한다. 특히 기존 DB에 저장된 과거 코드가 원장에 누락되면 과거 코드 재사용 방지를 소급 보장할 수 없다.

## 한계

Mockito 메일 대역은 Gmail 송수신과 SMTP·DB 간 원자성을 검증하지 않는다. 초대 요청 제한기는 Mock이므로 실제 Redis 장애·Lua·분산 배치 동작은 별도로 검증해야 한다. 테스트는 단일 MySQL 8.4 인스턴스와 특정 격리 설정에서 수행하며 실제 운영 스키마, 부하, 재시작 및 모든 실행 순서를 보증하지 않는다. 인증 도입 전 계정의 이메일 소유권을 입증하는 저장 상태도 현재 없다.
