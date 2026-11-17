# AI 인수인계 브리핑

## API

- `POST /babi/api/babies/{babyId}/handover-briefings`: 서울 기준 당일 기록으로 새 브리핑 생성
- `GET /babi/api/babies/{babyId}/handover-briefings/latest`: 저장된 최신 브리핑 조회
- 인증: `Authorization: Bearer <accessToken>`

생성은 OWNER 또는 CARE_ALL/CARE_WRITE 권한이 있는 활성 멤버만 가능하다.
최신 조회는 OWNER 또는 CARE_ALL/CARE_READ 권한이 있는 활성 멤버에게 허용하며 VIEWER도 읽기 권한이 있으면 조회할 수 있다.
생성 시 수유·수면·체온·기저귀·울음의 익명 숫자 통계만 OpenAI Responses API에 전달한다.
이름, 사용자·아기 ID, 보호자 메모, 육아 기록 메모, 음성 원문은 전달하지 않는다.
`store=false`, 연결 5초·응답 30초 제한, 출력 최대 1200 토큰을 적용하며 자동 재시도하지 않는다.

```text
OPENAI_API_KEY=서버_비밀_환경변수
OPENAI_MODEL=gpt-5-mini
```

키가 없거나 OpenAI가 실패·거절·불완전·빈 응답을 반환하면 HTTP 503과
`HANDOVER_BRIEFING_GENERATION_FAILED`를 반환하고 브리핑을 저장하지 않는다.
당일 기록이 없으면 OpenAI를 호출하지 않고 HTTP 404와 `NO_DAILY_CARE_RECORD`를 반환한다.
OpenAI 호출 중에는 DB 트랜잭션을 열어 두지 않으며, 저장 직전에 아기를 잠그고 쓰기 권한을 다시 확인한다.
학습용 가짜 기록과 응답은 `handover-fake` 프로필을 명시했을 때만 사용한다.

## 생성 제한

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

이 정책은 MVP 기본안이다. 프로젝트별 OpenAI 사용량·비용 제한은 배포 환경에서 별도로 설정한다.
쿨다운보다 생성 시간이 길면 다음 요청과 실행이 겹칠 수 있으므로, 장시간 실행 전체를 잠그는 기능은 아니다.
