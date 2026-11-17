# 공동육아 기록과 데일리 리포트

기준: [요구사항 명세서 FR-002·FR-005·FR-008](https://orbit1054-1787015122816.atlassian.net/wiki/spaces/SELECT/pages/5210113), [프로젝트 계획서](https://orbit1054-1787015122816.atlassian.net/wiki/spaces/SELECT/pages/2949121), [최종 API 명세서](https://orbit1054-1787015122816.atlassian.net/wiki/spaces/SELECT/pages/19464194/API), Jira TS-58·59·60·61. Backend 범위이며 기존 Web View는 변경하지 않는다.

## 리포트 점검 결과

기존 `develop`에는 수동 요약 CRUD와 7일 수유·수면·체온 통계가 있으며 기존 통합 테스트 16개가 통과했다. 문서에서 요구한 AI 자동 요약은 없었다. 읽기 전용 권한 및 무관한 권한으로 리포트를 변경·조회할 수 있는 문제도 4개 실패 테스트로 재현해 수정했다.

이 변경은 OpenAI 요약 생성과 공동 기록 API를 추가한다. `OPENAI_API_KEY`가 없으면 AI 생성은 `503`으로 실패한다. 실제 모델 응답의 품질·계정 권한·과금은 key 설정 후 별도 검증해야 하며 mock 결과를 실제 AI 호출 완료로 간주하지 않는다.

## 공통 인증과 권한

기존 JWT와 `ApiResponse`를 사용한다. `baby_members` / `active_baby_members`, `users.status='ACTIVE'`가 기준이며 제거되거나 탈퇴한 계정의 기존 JWT도 거부한다. OWNER는 모든 작업을 수행하고, VIEWER는 쓰기 권한 행이 있더라도 등록·변경할 수 없다.

| 기능 | 일반 멤버에게 필요한 권한 |
|---|---|
| 공동 기록 조회 | `CARE_READ` 또는 `CARE_ALL` |
| 공동 기록 등록 | `CARE_WRITE` 또는 `CARE_ALL` |
| 리포트 조회 | `REPORT_READ` 또는 `CARE_ALL` |
| 리포트 생성·변경·삭제 | `REPORT_WRITE` 또는 `CARE_ALL` |

기존 초대 수락은 `CARE_ALL`을 부여한다. 보호자별로 데이터를 복사하지 않고 같은 아기의 기록을 함께 조회한다. 작성자는 JWT의 실제 멤버 ID로 저장하며 요청 본문의 작성자 ID를 신뢰하지 않는다. 부모 그룹에서 나가도 기존 기록은 다른 허가된 보호자에게 유지된다. 공통 권한 SQL은 `domain/baby/repository/BabyAccessRepository`에 있다.

쓰기 요청은 트랜잭션 밖에서 권한을 먼저 확인해 비멤버가 다른 아기의 행 잠금이나 잠금 대기 연결을 점유하지 못하게 한다. 통과한 요청만 새 트랜잭션에서 아기 행을 잠그고 권한을 재확인한다. 첫 확인을 트랜잭션 밖에 두므로 MySQL의 repeatable-read에서도 대기 중 발생한 멤버 제거를 이전 snapshot으로 놓치지 않는다.

## 공동 기록 API

| Method | URL | 기능 |
|---|---|---|
| POST | `/api/v1/babies/{babyId}/records` | 검토한 육아 기록 한 건 등록 (`201`) |
| POST | `/api/v1/babies/{babyId}/feedings` | 수유 기록 한 건 등록 (`201`) |
| GET | `/api/v1/babies/{babyId}/records` | 날짜별 기록 통합 조회 (`200`) |

기록 등록 예시:

```json
{
  "recordType": "FEEDING",
  "occurredAt": "2026-11-08T08:00:00",
  "feedingType": "FORMULA",
  "amountMl": 120,
  "note": "수유 완료"
}
```

`recordType`, `occurredAt`은 필수다. 날짜·시간은 프로젝트 공통 규칙의 offset 없는 ISO 형식이다. `note`는 최대 5,000자이며 유형에 맞지 않는 필드를 섞으면 `400`이다.

| recordType | 추가 필드 |
|---|---|
| FEEDING | `feedingType` 필수: FORMULA / BREAST_MILK / BABY_FOOD / OTHER. `amountMl`은 미측정 시 생략, 입력 시 0 이상 |
| SLEEP | `occurredAt`은 시작 시각, `sleepEndAt`은 선택이며 시작보다 빠를 수 없음 |
| TEMPERATURE | `temperatureCelsius` 필수, 30.0~45.0, 소수점 한 자리까지 |
| DIAPER | `diaperType` 필수: URINE / STOOL / MIXED / OTHER |
| CRY | `cryType` 최대 50자, `confidence` 0~100·소수점 두 자리까지, 둘 다 선택 |

분석한 음성 기록과 연결할 때 `voiceInputId`를 추가할 수 있다. 같은 아기와 현재 작성자의 `voice_inputs` 행이어야 하며 다른 보호자나 다른 아기의 음성 ID는 `400 INVALID_CARE_RECORD`다. 울음 테이블은 음성 파싱 ID를 사용하지 않으므로 CRY의 `voiceInputId`는 허용하지 않는다. 이 API는 검토된 값을 저장하며 STT나 울음 분류 모델을 호출하지 않는다.

`/feedings` 본문은 `occurredAt`, `feedingType`, 선택 `amountMl`·`note`다. 동일한 권한 검사와 `feeding_records` 저장 경로를 사용한다. 실제 저장 테이블은 기존 `feeding_records`, `sleep_records`, `temperature_records`, `diaper_records`, `cry_records`이며 새 운영 테이블이나 migration은 없다.

조회 예시:

```text
GET /api/v1/babies/1/records?date=2026-11-08&recordType=FEEDING&authorMemberId=2&page=0&size=50
```

`date`는 필수다. `recordType`·`authorMemberId`는 선택 필터다. `page`는 0~10,000, `size`는 1~100이며 기본값은 0·50이다. 응답 `data`에는 `records`, `page`, `size`, `hasNext`가 있다. 목록은 기록 시각·유형·ID 순으로 정렬하며 날짜 구간은 해당 날짜 00:00 이상, 다음 날 00:00 미만이다. 수면은 선택 날짜와 겹치는 이전 날의 수면과 진행 중인 수면도 포함한다.

각 항목은 `recordId`, `recordType`, `babyId`, `authorMemberId`, `authorNickname`, `voiceInputId`, `occurredAt`, 유형별 값, `note`, `createdAt`을 포함한다. 서로 다른 유형의 테이블에서 같은 ID가 나올 수 있으므로 클라이언트 키는 `(recordType, recordId)`를 사용한다. 해당하지 않는 유형별 필드는 null이다. 타 아기의 작성자 필터를 넣어도 다른 아기의 기록은 노출되지 않는다.

## OpenAI 리포트 생성

```text
POST /babies/{babyId}/daily-reports/{date}/generate
```

본문은 없다. 성공은 기존 `DailyReportResponse`를 담은 `201`이며 기존 수동 CRUD 경로와 응답은 유지한다. 수유·수면·체온·기저귀·울음의 해당 날짜 통계를 [OpenAI Responses API](https://developers.openai.com/api/docs/guides/text)에 보내 한국어 요약을 저장한다. 이름·이메일·아기/사용자 ID·자유 텍스트 메모·음성 원문은 보내지 않는다. `store=false`를 사용하며 이는 OpenAI의 모든 데이터 보존 정책을 대체하는 설정은 아니다.

```dotenv
OPENAI_API_KEY=실제_키는_서버의_비밀_환경_파일에만_설정
OPENAI_MODEL=gpt-5-mini
```

키는 선택적인 시작 설정이며 실제 AI 호출 때 필요하다. API 요청은 최대 출력 2,048 토큰, 연결 5초·응답 30초 제한이며 자동 재시도하지 않는다. 기존 리포트나 진행 중인 생성 예약이 있으면 `409`, 요약할 기록이 없으면 `400 DAILY_REPORT_NO_RECORDS`로 모델을 호출하지 않는다.

짧은 트랜잭션에서 아기 쓰기 권한·기록을 확인하고 기존 `daily_reports`에 `PENDING` 예약을 저장한다. OpenAI 호출 중에는 DB 연결이나 행 잠금을 유지하지 않으며 `spring.jpa.open-in-view=false`로 HTTP 요청의 연결 유지도 차단한다. 다른 아기의 조회와 같은 아기의 기록 등록은 AI 응답을 기다리지 않는다. 완료 시 새 트랜잭션에서 권한과 예약 ID를 다시 확인하고 `COMPLETED`로 저장한다. 예약은 수동 CRUD의 조회·수정·삭제에서 `404`이며 같은 날짜의 수동 생성은 `409`다.

실패 시 해당 예약만 제거해 재시도할 수 있다. 프로세스 종료 등으로 남은 예약은 5분 후 생성 요청에서 교체하며, 새 예약 ID를 사용해 이전 호출의 늦은 응답·실패 정리가 새 작업을 덮어쓰거나 삭제하지 못하게 한다. 이는 기존 컬럼을 사용하므로 migration은 없다. API 타임아웃 뒤 제공자 측 처리가 계속될 가능성은 있으므로 실패 응답이 과금 없음을 보장하지 않는다.

OpenAI 오류·거절·미완료·잘못된 응답·키 미설정은 `503 DAILY_REPORT_GENERATION_UNAVAILABLE`이며 가짜 COMPLETED 리포트를 남기지 않는다. 요약은 8,000자를 넘으면 잘라 저장하지 않고 실패한다. 일일 수면은 날짜와 겹치는 완료 수면만 계산하며 진행 중인 수면은 횟수만 전송한다. 빈 측정값은 추측하지 않도록 측정 횟수와 null 값을 구분한다. 기존 7일 평균은 그대로 계산한다.

## 검증

```bash
./gradlew build
python3 scripts/check_final_schema_isolated.py src/main/resources/schema.sql --app-test
```

두 번째 명령은 Docker의 일회용 MySQL 8.4와 임의 포트·isolation marker를 사용한다. 개발·AWS DB에 연결하지 않으며 원본 SQL의 CREATE만 적용한다. 초대·공동 기록·리포트 생성의 MySQL 통합 테스트를 실행하고 각 suite의 건수와 skip 0을 확인한다. OpenAI 테스트는 로컬 mock HTTP 서버 또는 mock 생성기를 사용하며 실제 키나 과금 호출이 없다. `develop`·`main` PR에서 CI를 실행하고 자동 배포는 기존대로 `main`만 대상으로 한다.
