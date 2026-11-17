# 성장 기록 API

JWT를 `Authorization: Bearer <accessToken>`으로 전달한다. 아기의 활성 보호자만 접근할 수 있다. 기존 프로젝트 응답과 같이 성공 응답은 아래 JSON 객체를 직접 반환하고 오류는 공통 오류 형식을 사용한다.

아기의 존재 여부를 비구성원에게 노출하지 않도록 인증된 사용자가 활성 보호자가 아니거나 아기가 존재하지 않으면 성장 기록 생성·조회·비교는 `404 BABY_MEMBER_NOT_FOUND`를 반환한다. JWT가 없거나 유효하지 않으면 401이다.

## 기록 생성

`POST /babies/{babyId}/growth-records` (기존 `/babies/{babyId}/growth_records`도 사용 가능)

```json
{"measuredDate":"2026-09-23","heightCm":70.5,"weightKg":9.2,"memo":"정기 측정"}
```

측정일은 오늘 또는 과거여야 하며 키(cm)와 몸무게(kg) 중 하나 이상 필요하다. 입력값은 양수이며 소수점 둘째 자리까지, 메모는 255자까지 허용한다. 동일 아기의 동일 날짜 기록은 한 건만 허용한다. 성공은 201이고 `growthRecordId`, `babyId`, `authorMemberId`, `heightCm`, `weightKg`, `measuredDate`, `memo`, `createdAt`을 반환한다. 기존 `height`, `weight` 필드도 유지한다. 미측정 항목은 null이다.

중복 방지는 생성 트랜잭션에서 아기 행을 잠근 후 기존 기록을 확인하는 방식이다. 현재 확정 스키마에는 `(baby_id, measured_date)` UNIQUE 제약이 없으므로 이 잠금 절차를 거치지 않는 직접 DB 쓰기나 다른 저장 경로의 중복까지 DB가 차단하지는 못한다. DB 차원의 보장이 필요하면 기존 중복 데이터를 먼저 확인하고 팀과 제약 추가를 협의해야 한다.

## 기록 조회

`GET /babies/{babyId}/growth-records?from=2026-01-01&to=2026-09-30&limit=20&cursor=123`

모든 쿼리는 선택 사항이다. 날짜 경계는 포함된다. `limit` 기본값은 20, 범위는 1~100이며 양의 정수 ID 커서를 사용한다. 기록 ID 내림차순으로 반환하므로 화면의 그래프는 측정일 기준으로 정렬할 수 있다.

```json
{"item":[{"growthRecordId":123,"measuredDate":"2026-09-23","heightCm":70.5,"weightKg":9.2,"memo":null}],"nextCursor":null}
```

다음 페이지가 없으면 `nextCursor`는 null이다. 날짜 범위가 역순이거나 커서·limit이 올바르지 않으면 400이다.

## 직전 측정과 비교

`GET /babies/{babyId}/growth-comparison?growthRecordId=123`

`/api/v1/babies/{babyId}/growth-comparison`도 지원한다. 지정 기록보다 측정일이 앞선 가장 최근 기록과 비교한다. 기록이 없거나 다른 아기의 기록이면 404이다. `current`와 `previous`는 조회 항목과 같은 구조다. `change`는 날짜 차이 `days`, 키 차이 `heightCm`, 몸무게 차이 `weightKg`를 포함한다. 비교 대상이 없으면 `previous`와 `change`는 null이고, 한쪽에 측정값이 없으면 해당 변화량은 null이다.

응답의 `standard`는 측정일 당시 만나이(완료된 개월 수)와 저장된 성별을 사용한 2017 소아청소년 성장도표의 연령별 신장·체중 백분위를 제공한다. 다음 예시는 남아 12개월, 키 75cm, 체중 9kg이다.

```json
{"status":"AVAILABLE","source":"2017 소아청소년 성장도표","sourceUrl":"https://knhanes.kdca.go.kr/knhanes/grtcht/dwnld/dtDtl.do?pg=1&st=&sb=1&menuId=20011002&dataNo=7","sex":"MALE","ageMonths":12,"heightPercentile":37.6,"weightPercentile":26.3}
```

성별이 저장되지 않은 아기는 `SEX_REQUIRED`, 측정일이 출생일 전이면 `MEASURED_BEFORE_BIRTH`, 228개월 이상이면 `AGE_OUT_OF_RANGE`를 반환하며 백분위는 null이다. 해당 항목에 측정값이 없을 때도 그 백분위는 null이다. 0~227개월에 해당하는 공식 L, M, S 값으로 LMS Z 점수와 정규분포 누적확률을 계산하고 백분위는 소수 첫째 자리로 반올림한다. 비교 결과는 성장 상태를 참고하기 위한 값이며 진단이나 의료적 판정을 제공하지 않는다. 개별 건강 판단은 전문가 상담이 필요하다.

아기 등록 `POST /babies` 또는 OWNER의 수정 `PATCH /babies/{babyId}`에 `"sex":"MALE"` 또는 `"sex":"FEMALE"`을 전달할 수 있다. 기존 클라이언트 호환을 위해 생략 가능하며, 기존 아기에게는 임의 값을 지정하지 않는다. 성장도표 비교를 사용하려면 OWNER가 해당 값을 입력해야 한다.

**자료 출처:** 질병관리청 국민건강영양조사 [2017 소아청소년 성장도표 기본자료](https://knhanes.kdca.go.kr/knhanes/grtcht/dwnld/dtDtl.do?pg=1&st=&sb=1&menuId=20011002&dataNo=7)의 `성장도표 데이터 테이블.xls`, `연령별 신장` 및 `연령별 체중` 시트. `src/main/resources/growth/kdca-2017-lms.csv`는 해당 시트의 성별·개월·L·M·S 열을 추출한 자료다. 원본 파일 SHA-256: `2f14e281f3414e5ef5354c8ef52f9143c91d470a2993d2b9484ef2c664b37d6a`. 공식 계산기의 가상 입력(남녀 각각 생후 12개월, 75cm, 9kg)과 결과를 대조했다.

배포 전 별도 승인 절차로 `docs/migrations/20261110_add_baby_sex.sql`을 적용해야 한다. 자동 초기화·마이그레이션은 실행하지 않으며 기존 NULL 데이터는 보호자가 별도 수정해야 한다.
