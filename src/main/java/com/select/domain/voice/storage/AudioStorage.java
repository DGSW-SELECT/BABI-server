package com.select.domain.voice.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 비동기 STT 작업에서 사용할 음성 파일 저장소.
 *
 * <p>로컬 파일 시스템 구현을 기본으로 사용하며, 운영 환경에서 S3 등으로 교체할 수 있도록
 * 인터페이스로 분리했다.</p>
 */
public interface AudioStorage {

    /**
     * 음성 파일을 저장하고 이후 조회에 사용할 상대 경로를 반환한다.
     */
    String store(MultipartFile audio);

    /**
     * 저장된 상대 경로의 음성 파일을 {@link MultipartFile}로 읽는다.
     */
    MultipartFile load(String path);

    /**
     * 저장된 음성 파일을 삭제한다.
     */
    void delete(String path);

    /**
     * 저장된 음성 파일이 존재하는지 확인한다.
     */
    boolean exists(String path);
}
