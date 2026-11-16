package com.select.domain.voice.dto.response;

/**
 * 프론트 음성 기록 화면의 미리보기 항목.
 *
 * <p>{@code type}은 {@code sleep}, {@code feeding}, {@code diaper}, {@code other} 중 하나다.</p>
 */
public record ParsedVoiceItemResponse(String type, String label) {
}
