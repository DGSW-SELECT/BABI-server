# 성장 기록 API

JWT를 `Authorization: Bearer <accessToken>`으로 전달한다. 아기의 활성 보호자만 접근할 수 있다. 기존 프로젝트 응답과 같이 성공 응답은 아래 JSON 객체를 직접 반환하고 오류는 공통 오류 형식을 사용한다.

## 기록 생성

`POST /babies/{babyId}/growth-records` (기존 `/babies/{babyId}/growth_records`도 사용 가능)

```json
{"measuredDate":"2026-09-23","heightCm":70.5,"weightKg":9.2,"memo":"정기 측정"}
```

측정일은 오늘 또는 과거여야 하며 키(cm)와 몸무게(kg) 중 하나 이상 필요하다. 입력값은 양수이며 소수점 둘째 자리까지, 메모는 255자까지 허용한다. 동일 아기의 동일 날짜 기록은 한 건만 허용한다. 성공은 201이고 `growthRecordId`, `babyId`, `authorMemberId`, `heightCm`, `weightKg`, `measuredDate`, `memo`, `createdAt`을 반환한다. 기존 `height`, `weight` 필드도 유지한다. 미측정 항목은 null이다.

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

표준 성장곡선이나 백분위 값은 제공하지 않는다. 신뢰할 수 있는 기준 데이터와 적용 연령·성별 정책이 정해져 있지 않기 때문이다.
