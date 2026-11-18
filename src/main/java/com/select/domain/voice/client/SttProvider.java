package com.select.domain.voice.client;

/**
 * STT 제공자 선택 값.
 *
 * <p>가짜 제공자는 설정에서 명시적으로 선택한 경우에만 사용한다.</p>
 */
public enum SttProvider {

    OPENAI,
    FAKE
}
