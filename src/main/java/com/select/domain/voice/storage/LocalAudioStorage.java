package com.select.domain.voice.storage;

import com.select.domain.voice.exception.AudioStorageException;
import com.select.domain.voice.support.AudioFileSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 로컬 파일 시스템에 음성 파일을 저장한다.
 *
 * <p>원본 파일명은 경로 조작 위험이 있으므로 저장에 사용하지 않고,
 * 날짜 디렉터리 아래 UUID 파일명으로 저장한다.</p>
 */
public class LocalAudioStorage implements AudioStorage {

    private static final DateTimeFormatter DATE_DIRECTORY_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path root;

    public LocalAudioStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(MultipartFile audio) {
        String relativePath = LocalDate.now().format(DATE_DIRECTORY_FORMAT)
                + "/" + UUID.randomUUID() + "." + AudioFileSupport.storageExtension(audio);
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream inputStream = audio.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return relativePath;
        } catch (IOException e) {
            throw new AudioStorageException("음성 파일을 저장하지 못했습니다.", e);
        }
    }

    @Override
    public MultipartFile load(String path) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new AudioStorageException("저장된 음성 파일을 찾을 수 없습니다.");
        }
        return new PathMultipartFile(file);
    }

    @Override
    public void delete(String path) {
        Path file = resolve(path);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new AudioStorageException("음성 파일을 삭제하지 못했습니다.", e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    private Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new AudioStorageException("음성 파일 경로가 비어 있습니다.");
        }

        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new AudioStorageException("허용되지 않는 음성 파일 경로입니다.");
        }
        return resolved;
    }
}
