package com.select.domain.voice.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 로컬에 저장된 음성 파일을 STT 클라이언트가 읽을 수 있도록 감싼다.
 */
class PathMultipartFile implements MultipartFile {

    private static final String FIELD_NAME = "audio";

    private final Path path;

    PathMultipartFile(Path path) {
        this.path = path;
    }

    @Override
    public String getName() {
        return FIELD_NAME;
    }

    @Override
    public String getOriginalFilename() {
        return path.getFileName().toString();
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public boolean isEmpty() {
        try {
            return Files.size(path) == 0;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("음성 파일 크기를 확인할 수 없습니다.", e);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(Path dest) throws IOException, IllegalStateException {
        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
        Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
