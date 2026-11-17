# 보호자 초대 격리 MySQL 자동 테스트

프로젝트 루트에서 실행한다.

```bash
python3 scripts/run_isolated_invitation_mysql.py
```

Docker, 로컬 `mysql:8.4` 이미지, Java 21과 Gradle wrapper가 필요하다. 스크립트는 `scripts/check_final_schema_isolated.py`를 호출하여 저장소의 `src/main/resources/schema.sql`에서 CREATE TABLE 및 CREATE VIEW 문만 추출한다. 이 CREATE 문은 팀의 최종 SQL과 일치한다. 원본의 DROP 문은 실행하지 않는다. 최종 SQL 원본을 직접 검증하려면 다음과 같이 경로를 지정할 수 있다.

```bash
python3 scripts/check_final_schema_isolated.py /path/to/babi_DB_Real.sql --app-test
```

매 실행마다 임의 이름의 MySQL 컨테이너, 테스트 전용 DB·계정·임시 비밀번호, 임시 메모리 저장소와 로컬 임의 포트를 사용한다. 개발용 `babi` DB, 기존 컨테이너·볼륨 및 `.env`는 사용하지 않는다. 생성한 마커를 연결 전에 확인하며 성공·실패 시 해당 컨테이너만 종료한다. `BabyInvitationMySqlIntegrationTest`를 실행하고 성공 시 결과를 `build/invitation-mysql-results/`에 보존한다.

이 테스트의 SMTP 발송기와 초대 제한기는 Mock이며, 실제 Gmail 또는 Redis를 사용하지 않는다. Hibernate는 스키마를 `validate`한다. H2 전체 테스트는 별도로 실행한다. 이 결과는 운영 DB의 스키마·데이터나 실제 SMTP 전달, 모든 부하 상황을 보증하지 않는다.

이전 수동 `invitation-mysql` 프로필과 `scripts/invitation_mysql.py`는 비활성화되어 있다. `docs/schema/baby-invitations.mysql.sql`은 폐기된 이전 제안으로 실행하지 않는다.
