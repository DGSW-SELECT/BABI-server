package com.select.domain.voice.support;

import com.select.domain.voice.exception.InvalidAudioException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioFileSupportTest {

    @Test
    void derivesFilenameExtensionFromContentTypeWhenFilenameHasNoExtension() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice", "audio/mpeg", "audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(AudioFileSupport.filenameFor(audio)).isEqualTo("voice.mp3");
    }

    @Test
    void keepsSupportedOriginalFilename() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.webm", "audio/webm;codecs=opus", "audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(AudioFileSupport.isSupported(audio)).isTrue();
        assertThat(AudioFileSupport.filenameFor(audio)).isEqualTo("voice.webm");
    }

    @Test
    void rejectsUnsupportedFile() {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "voice.txt", "text/plain", "not-audio".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> AudioFileSupport.validate(audio))
                .isInstanceOf(InvalidAudioException.class);
    }
}
