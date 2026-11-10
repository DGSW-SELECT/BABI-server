# 인수인계 메모 등록 (TS-76)

기존 메모 브랜치에서 등록 기능만 분리하고 현재 아기 멤버·JWT 구조에 맞춘 PR이다.
경로 및 응답 필드 변경은 프론트엔드 협의 대상이다. 기존 문서에서 확정된 명세라고 간주하지 않는다.

## API

- POST /babi/api/babies/{babyId}/handover-memos
- Authorization: Bearer <accessToken>
- Content-Type: application/json
- 기존 쿠키 인증도 공통 보안 필터를 따른다. 쿠키 요청은 기존 CSRF 절차와 X-CSRF-Token이 필요하다.

요청:

```json
{"content":"낮잠 후 장난감을 정리했습니다."}
```

content는 필수이며 공백만 입력할 수 없고 최대 1000자이다.
작성자는 서버가 인증 정보에서 결정한다. groupId, authorId, authorMemberId를 요청에 보내지 않는다.
추가 필드에 작성자 ID를 넣어도 서버의 작성자 결정에 사용되지 않는다.

성공 응답 (HTTP 200):

```json
{
  "success": true,
  "message": "인수인계 메모가 등록되었습니다.",
  "data": {
    "memoId": 15,
    "authorMemberId": 7,
    "content": "낮잠 후 장난감을 정리했습니다.",
    "createdAt": "2026-11-10T17:00:00"
  }
}
```

authorMemberId는 users.id가 아닌 baby_members.id이다.
createdAt은 Asia/Seoul 기준 작성 시각이며 응답에는 시간대 오프셋이 붙지 않는다.
메모는 보호자가 직접 작성한 원문이고 AI 생성 결과가 아니다.

| 상황 | HTTP | error.code |
| --- | --- | --- |
| 입력 누락·공백·1000자 초과 | 400 | VALIDATION_ERROR |
| 잘못된 JSON | 400 | INVALID_REQUEST_BODY |
| 인증 없음 | 401 | UNAUTHORIZED |
| 잘못된·폐기된 토큰 또는 비활성 계정 | 401 | INVALID_TOKEN |
| 타 가족·제거된 멤버·쓰기 권한 없음·존재하지 않는 아기 | 403 | FORBIDDEN |
| 인증 저장소 장애 | 503 | AUTH_SERVICE_UNAVAILABLE |
| 예상하지 못한 저장 오류 | 500 | INTERNAL_SERVER_ERROR |

오류는 기존 ApiResponse 형식으로 전달한다:

```json
{
  "success": false,
  "message": "접근 권한이 없습니다.",
  "data": null,
  "error": {"code": "FORBIDDEN", "details": []}
}
```

같은 내용의 메모를 여러 번 쓸 수 있다. 요청 중복 제거 기능은 없으므로 응답을 받지 못했다고 자동 재전송하지 않는다.
AI 실패·육아 기록 없음은 이 등록 API의 오류 상태가 아니다.

## 권한 및 트랜잭션

최신 develop의 BabyAccessRepository.findMemberId를 재사용한다.
활성 계정 및 활성 멤버이며 OWNER 또는 CARE_ALL/CARE_WRITE 권한을 가진 경우 등록 가능하다.
VIEWER는 권한 행이 남아 있어도 등록할 수 없다.
쓰기 권한을 먼저 검사하고, 새 트랜잭션에서 사용자와 아기를 잠근 후 권한을 다시 확인하고 저장한다.
이는 기존 탈퇴·멤버 변경의 잠금 순서와 맞추며 저장 직전 권한 변경을 확인하기 위한 것이다.

## DB 및 기존 코드 변경

handover_memos 테이블에 baby_id, author_member_id, content, created_at을 저장한다.
(baby_id, author_member_id)의 복합 FK로 타 아기 멤버 지정은 DB에서도 차단한다.
기존 groupId는 제거하고 authorId 응답은 authorMemberId로 변경했다.
메모 테이블은 첨부 원본 DB에 없어서 신규 DDL이 필요하다.

신규 초기화 스키마는 src/main/resources/schema.sql, H2 테스트 스키마는 src/test/resources/schema-test.sql에 반영했다.
기존 DB 추가용 파일은 docs/schema/handover-memos.mysql.sql이다. DROP이나 기존 데이터 수정은 포함하지 않는다.
팀 승인 후 DB 담당자가 대상 스키마, 부모 테이블 및 타입, InnoDB/utf8mb4 기본값을 확인하고 DDL을 적용한 다음 서버를 배포한다.
이 PR은 실제 DB 적용이나 배포를 수행하지 않는다. MySQL 실DB 검증과 프론트엔드 연동 확인은 남아 있다.
서버 롤백 시에도 작성된 메모 테이블과 데이터는 보존한다.

## 범위

이번 PR은 등록 API와 저장 검증만 포함한다.
목록 조회 API, 수정·삭제, 메모의 AI 브리핑 포함은 이번 변경에 포함하지 않는다.
AI 브리핑 연동 요구는 별도 후속 작업으로 남겨 두며 기존 브리핑 코드를 함께 가져오지 않았다.

## 검증

`./gradlew test --tests 'com.select.domain.handover.HandoverMemoIntegrationTest'`

실제 임의 포트 서버·JWT 필터·JPA/H2·공통 권한 SQL·복합 FK를 검증한다.
Redis 토큰 폐기 저장소만 mock으로 대체하며 권한 저장소와 메모 저장소는 mock이 아니다.
