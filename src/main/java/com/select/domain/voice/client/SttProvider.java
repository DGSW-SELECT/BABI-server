package com.select.domain.voice.client;

/**
 * STT 제공자 선택 값.
 *
 * <p>{@link #AUTO}는 API 키가 있으면 실제 제공자를, 없으면 가짜 클라이언트를 사용한다.
 * 모델이 확정되기 전에도 서버가 정상적으로 기동되도록 하기 위한 개발용 기본값이다.</p>
 */
public enum SttProvider {

    AUTO,
    OPENAI,
    FAKE
}
