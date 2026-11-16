# 본인 프로필 API

인증된 사용자만 사용합니다. 쿠키 인증으로 변경 요청을 보낼 때 기존 CSRF 규칙을 따릅니다.

- `GET /auth/profile`: 본인의 `userId`, `email`, `nickname`을 조회합니다.
- `PATCH /auth/profile`: JSON `{"nickname":"새 이름"}`으로 닉네임을 변경합니다. 공백만으로 된 값과 50자 초과는 거부합니다.
- `POST /auth/profile/photo`: multipart/form-data의 `file`에 JPEG 또는 PNG 파일을 담습니다. 파일 및 변환된 이미지 크기는 최대 5 MiB, 이미지 픽셀은 최대 1,600만입니다. 성공 응답은 기존 `ApiResponse` 형식입니다.
- `GET /auth/profile/photo`: 본인의 이미지를 PNG로 반환합니다. 사진이 없으면 `PROFILE_PHOTO_NOT_FOUND`(404)를 반환합니다. 이미지는 개인 정보이므로 응답 캐시를 금지합니다.

음성 업로드와 공유하는 멀티파트 파서의 파일 한도는 25MB입니다. 프로필 사진의 5 MiB 한도는 멀티파트 파싱 후 사진 서비스에서 검사하고, 이를 초과한 파일은 `PROFILE_PHOTO_INVALID`(400)로 거부합니다. 공통 파서의 한도를 넘으면 파싱 단계에서 먼저 거부됩니다.

사진은 메타데이터를 제거한 PNG로 변환해 DB에 저장하며, 회원 탈퇴 시 사용자 익명화와 같은 트랜잭션에서 삭제합니다. 배포 전에 `docs/schema/profile-photo-migration.sql`을 검토하고 별도로 적용해야 합니다. 자동 초기화는 하지 않습니다. 로컬·AWS의 Hibernate validate 설정에서 테이블이 누락되면 애플리케이션 시작이 실패하여 기존 버전으로 복구할 수 있습니다. DB 용량, 백업 및 MySQL의 `max_allowed_packet`이 사진 크기를 수용하는지 확인해야 합니다.
