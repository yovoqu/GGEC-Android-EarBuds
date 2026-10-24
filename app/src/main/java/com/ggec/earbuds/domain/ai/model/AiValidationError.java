package com.ggec.earbuds.domain.ai.model;

public enum AiValidationError {
    API_KEY_EMPTY,
    API_KEY_INVALID,
    BASE_URL_REQUIRED,
    BASE_URL_INVALID,
    BASE_URL_NOT_HTTPS,
    NAME_EMPTY,
    PROTOCOL_UNSUPPORTED,
    MODEL_ID_EMPTY,
    MODEL_ID_INVALID,
    PLATFORM_UNKNOWN,
    PLATFORM_NOT_CONFIGURED,
    MODEL_UNKNOWN,
    PROMPT_TOO_LONG,
    /** 选了全双工模式，但没有指定或没有可用的实时语音模型。 */
    VOICE_NEEDS_REALTIME_MODEL,
    /** 级联模式的转写模型为空，且当前平台没有具备转写能力的模型。 */
    VOICE_NEEDS_TRANSCRIPTION_MODEL
}
