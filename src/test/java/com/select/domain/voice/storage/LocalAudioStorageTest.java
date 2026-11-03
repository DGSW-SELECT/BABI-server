package com.select.domain.voice.storage;

import com.select.domain.voice.exception.AudioStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAudioStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndLoadsAudioWithGeneratedName() throws IOException {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice.mp3", "audio/mpeg", "audio-bytes".getBytes(StandardCharsets.UTF_8)
        );

        String path = storage.store(audio);

        assertThat(path).endsWith(".mp3");
        assertThat(path).doesNotContain("voice.mp3");
        assertThat(storage.exists(path)).isTrue();

        MultipartFile loaded = storage.load(path);
        assertThat(loaded.getBytes()).isEqualTo(audio.getBytes());
        assertThat(loaded.getOriginalFilename()).endsWith(".mp3");
        assertThat(loaded.getContentType()).isEqualTo("audio/mpeg");
        assertThat(loaded.getSize()).isEqualTo(audio.getSize());
    }

    @Test
    void derivesExtensionFromContentTypeWhenFilenameHasNoExtension() {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);
        MultipartFile audio = new MockMultipartFile(
                "audio", "voice", "audio/wav", "audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(storage.store(audio)).endsWith(".wav");
    }

    @Test
    void deletesStoredAudio() {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);
        String path = storage.store(new MockMultipartFile(
                "audio", "voice.m4a", "audio/m4a", "audio".getBytes(StandardCharsets.UTF_8)
        ));

        storage.delete(path);

        assertThat(storage.exists(path)).isFalse();
    }

    @Test
    void loadingMissingFileThrowsStorageException() {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);

        assertThatThrownBy(() -> storage.load("2026/11/03/missing.mp3"))
                .isInstanceOf(AudioStorageException.class);
    }

    @Test
    void rejectsPathOutsideStorageRoot() {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);

        assertThatThrownBy(() -> storage.load("../outside.mp3"))
                .isInstanceOf(AudioStorageException.class)
                .hasMessageContaining("허용되지 않는");
    }

    @Test
    void rejectsBlankPath() {
        LocalAudioStorage storage = new LocalAudioStorage(tempDir);

        assertThatThrownBy(() -> storage.exists(" "))
                .isInstanceOf(AudioStorageException.class);
    }
}
