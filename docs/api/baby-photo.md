# 아기 사진

JWT 인증 필수. `/babies/{babyId}/photo`:

- POST multipart/form-data, `file` 파일: OWNER 등록/교체. 200 ApiResponse.success.
- GET: 활성 보호자 조회. 200 image/png, Cache-Control: no-store. 없으면 404 BABY_PHOTO_NOT_FOUND.
- DELETE: OWNER 삭제. 200 ApiResponse.success. 반복 삭제 가능.

기존 아기 정보 수정과 동일한 OWNER 정책. 탈퇴한 보호자/비구성원 조회는 404,
사진 변경은 403. JPEG/PNG만 허용, 원본 및 변환 결과 5MiB 이하, 1600만 픽셀 이하.
기존 프로필 사진과 공통 변환기를 사용하며 동시에 최대 2건만 변환한다.
검증/변환 후 아기 행 잠금과 OWNER 재확인을 거쳐 저장한다.
사용자 탈퇴 시 공유 아기 사진은 다른 보호자를 위해 유지한다.

사진 검증 오류는 기존 사용자 사진 API와 호환되도록 `PROFILE_PHOTO_INVALID`(400),
변환 처리량 초과는 `PROFILE_PHOTO_BUSY`(503)를 공유한다. 메시지는 아기·사용자
사진에 공통으로 적용되는 "사진" 문구를 사용한다.
클라이언트는 HTTP 상태와 `error.code`를 기준으로 분기하며, 안내용 `message` 문자열은
비교하지 않는다. `PROFILE_PHOTO_INVALID`는 파일 크기·형식·픽셀 수 등 이미지 검증 실패,
`PROFILE_PHOTO_BUSY`는 변환 처리량 초과를 뜻한다.

배포 전 `docs/schema/baby-photos-migration.mysql.sql` 별도 검토/적용 필요.
원본 schema.sql은 변경하지 않으며 자동 DDL/마이그레이션 실행 없음.
추가 테이블이 없으면 Hibernate validate가 실패하므로 먼저 마이그레이션 필요.
현재 프로필 사진과 동일하게 DB LONGBLOB 저장. 이미지 다운로드 응답을 캐시하지 않는다.
프론트는 업로드/교체 완료 후 GET으로 다시 조회한다. 기존 아기 응답 구조는 유지한다.
