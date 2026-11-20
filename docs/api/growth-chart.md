# 영유아 성장 그래프

GET `/babies/{babyId}/growth-chart` (JWT, 활성 보호자)

`ApiResponse.success`의 data에 birthDate, measurements, standardStatus, source,
sourceUrl, reference를 반환한다. 기존 목록/비교 API는 유지한다.

- measurements: 출생일부터 정확히 24개월 되는 날짜까지 포함, 측정일/ID 오름차순.
  growthRecordId, measuredDate, ageDays, heightCm, weightKg. 누락된 측정값은 null이며
  0으로 그리면 안 된다. 이 구간 전체를 반환하므로 기존 ID 커서를 순회할 필요가 없다.
- reference: 0~24개월 월별 25점. ageMonths, date, heightCm/weightKg 각각 p3,p50,p97.
  아기의 성별에 맞는 기존 2017 소아청소년 성장도표 LMS로 계산한다.
- p50은 산술평균이 아닌 중앙값. 프론트에서 '표준 중앙값'으로 표기한다.
- 성별 미등록이면 SEX_REQUIRED, reference는 빈 배열이며 실제 측정값은 표시 가능.
- 프론트는 같은 날짜 축에 측정선을 그리고 표준 중앙값은 점선으로 표시한다.
  키(cm)/몸무게(kg) 탭 전환 시 각 필드만 선택한다. 측정선은 감소해도 그대로 표시한다.
- 월별 기준점 사이는 시각화 보간이며 일별 의료 기준값으로 취급하지 않는다.
- 2017 도표의 24개월 측정 방식 전환(누운키/선키)을 자동 보정하지 않는다.
  원자료 기준을 사용하며 의료 진단용이 아니다. 원자료 CSV와 SQL 변경 없음.

401: 인증 없음. 404 BABY_MEMBER_NOT_FOUND: 비구성원/탈퇴한 보호자.
