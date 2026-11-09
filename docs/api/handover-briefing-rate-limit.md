# 인수인계 브리핑 생성 제한

POST /babi/api/babies/{babyId}/handover-briefings 에 적용한다.
계정 활성 상태와 아기 권한을 확인한 후, 기록 조회 전에 Redis에서 생성 시도를 예약한다.

- 기본 정책: 아기별 60초에 한 번. 같은 아기의 모든 보호자와 서버 인스턴스가 공유한다.
- 환경변수: BABI_HANDOVER_COOLDOWN_SECONDS (양의 정수, 기본 60).
- Redis의 만료 시간 포함 SET NX로 예약한다. 거부된 요청은 만료 시간을 연장하지 않는다.
- 기록 없음, AI 실패, DB 저장 실패도 예약을 유지한다. 대기 후 재시도한다.
- 제한 초과: HTTP 429, error.code = HANDOVER_BRIEFING_RATE_LIMITED.
- Retry-After 헤더는 설정된 전체 대기 시간(초)을 반환한다. 남은 시간보다 길 수 있다.
- Redis 장애 또는 결과 확인 불가: HTTP 503, error.code = HANDOVER_BRIEFING_UNAVAILABLE.
- GET /babi/api/babies/{babyId}/handover-briefings/latest 는 이 제한을 적용하지 않는다.

이 정책은 MVP 기본안이다. 실제 AI 연결 시 처리 시간 제한 및 전체 서비스 사용량 한도를 함께 검토한다.
쿨다운보다 생성 시간이 길면 다음 요청과 실행이 겹칠 수 있으므로, 장시간 실행 전체를 잠그는 기능은 아니다.
