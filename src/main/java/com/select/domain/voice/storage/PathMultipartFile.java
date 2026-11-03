package com.select.domain.voice.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

/**
 * 로컬에 저장된 음성 파일을 STT 클라이언트가 읽을 수 있도록 감싼다.
 */
class PathMultipartFile implements MultipartFile {

    private static final String FIELD_NAME = "audio";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "mp3", "audio/mpeg",
            "mpga", "audio/mpeg",
            "mpeg", "audio/mpeg",
            "mp4", "audio/mp4",
            "m4a", "audio/m4a",
            "wav", "audio/wav",
            "webm", "audio/webm",
            "ogg", "audio/ogg",
            "flac", "audio/flac",
            "aac", "audio/aac"
    );

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
        return CONTENT_TYPES.getOrDefault(extension(), DEFAULT_CONTENT_TYPE);
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

    private String extension() {
        String filename = getOriginalFilename();
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
