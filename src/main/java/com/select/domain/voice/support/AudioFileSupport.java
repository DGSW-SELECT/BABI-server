package com.select.domain.voice.support;

import com.select.domain.voice.exception.AudioTooLargeException;
import com.select.domain.voice.exception.InvalidAudioException;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

public final class AudioFileSupport {

    public static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/mpga", "audio/mpeg3", "audio/x-mpeg-3",
            "audio/wav", "audio/x-wav", "audio/wave",
            "audio/webm", "audio/ogg", "audio/mp4", "audio/m4a", "audio/x-m4a",
            "audio/flac", "audio/x-flac", "audio/aac"
    );

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "mpga", "mpeg", "mp4", "m4a", "wav", "webm", "ogg", "flac", "aac"
    );

    private AudioFileSupport() {
    }

    public static void validate(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new InvalidAudioException("음성 파일이 비어 있습니다.");
        }
        if (audio.getSize() > MAX_SIZE_BYTES) {
            throw new AudioTooLargeException();
        }
        if (!isSupported(audio)) {
            throw new InvalidAudioException("지원하지 않는 음성 파일 형식입니다.");
        }
    }

    public static boolean isSupported(MultipartFile audio) {
        return SUPPORTED_CONTENT_TYPES.contains(contentType(audio))
                || SUPPORTED_EXTENSIONS.contains(extension(audio.getOriginalFilename()));
    }

    public static String filenameFor(MultipartFile audio) {
        String original = audio.getOriginalFilename();
        if (SUPPORTED_EXTENSIONS.contains(extension(original))) {
            return original;
        }

        String derivedExtension = extensionForContentType(contentType(audio));
        if (derivedExtension == null) {
            throw new InvalidAudioException("음성 파일 형식을 확인할 수 없습니다.");
        }

        String base = StringUtils.hasText(original) ? original : "audio";
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base + "." + derivedExtension;
    }

    private static String contentType(MultipartFile audio) {
        String contentType = audio.getContentType();
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon);
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "audio/mpeg", "audio/mp3", "audio/mpga", "audio/mpeg3", "audio/x-mpeg-3" -> "mp3";
            case "audio/wav", "audio/x-wav", "audio/wave" -> "wav";
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/flac", "audio/x-flac" -> "flac";
            case "audio/aac" -> "aac";
            default -> null;
        };
    }
}
