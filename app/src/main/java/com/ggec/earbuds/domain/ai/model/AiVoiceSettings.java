package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

/**
 * 语音相关的配置：选哪种模式、两种模式各自用哪个模型。
 *
 * <p>为什么把两种模式的模型分开存：OMNI 需要一个支持实时语音的模型，
 * CASCADED 需要"能对话的模型 + 能转写的模型 + 能合成的声音"。
 * 用同一个字段会让用户在切换模式后发现"模型不支持"，然后再回来改一次。</p>
 *
 * <p>所有字段都允许为空，空表示"跟对话页当前选择的平台/模型走"：
 * 绝大多数用户只有一个平台，不该被迫为语音再配一遍。</p>
 *
 * @param mode              当前模式
 * @param transcriptionModel 级联模式的转写模型（如 whisper-1）；null 表示用所选平台的默认转写模型
 * @param synthesisVoice    合成音色名；null 表示平台默认音色
 * @param realtimeModel     全双工模式使用的模型；null 表示让用户从具备 REALTIME_AUDIO 能力的模型里挑
 * @param silenceTimeoutMillis 判定"说完了"的静音时长；说话停顿时长因人和场景而异，可调
 */
public record AiVoiceSettings(
        AiMode mode,
        @Nullable String transcriptionModel,
        @Nullable String synthesisVoice,
        @Nullable String realtimeModel,
        int silenceTimeoutMillis) {

    /** 默认 900ms：短于它会把换气当说完，长于它会让人觉得"它怎么还不说话"。 */
    public static final int DEFAULT_SILENCE_TIMEOUT_MILLIS = 900;
    private static final int MIN_SILENCE_TIMEOUT_MILLIS = 300;
    private static final int MAX_SILENCE_TIMEOUT_MILLIS = 4000;

    public AiVoiceSettings {
        mode = mode == null ? AiMode.defaultMode() : mode;
        silenceTimeoutMillis = Math.max(MIN_SILENCE_TIMEOUT_MILLIS,
                Math.min(MAX_SILENCE_TIMEOUT_MILLIS, silenceTimeoutMillis));
        transcriptionModel = blankToNull(transcriptionModel);
        synthesisVoice = blankToNull(synthesisVoice);
        realtimeModel = blankToNull(realtimeModel);
    }

    public static AiVoiceSettings defaults() {
        return new AiVoiceSettings(AiMode.defaultMode(), null, null, null, DEFAULT_SILENCE_TIMEOUT_MILLIS);
    }

    public AiVoiceSettings withMode(AiMode next) {
        return new AiVoiceSettings(next, transcriptionModel, synthesisVoice, realtimeModel, silenceTimeoutMillis);
    }

    public AiVoiceSettings withTranscriptionModel(@Nullable String value) {
        return new AiVoiceSettings(mode, value, synthesisVoice, realtimeModel, silenceTimeoutMillis);
    }

    public AiVoiceSettings withSynthesisVoice(@Nullable String value) {
        return new AiVoiceSettings(mode, transcriptionModel, value, realtimeModel, silenceTimeoutMillis);
    }

    public AiVoiceSettings withRealtimeModel(@Nullable String value) {
        return new AiVoiceSettings(mode, transcriptionModel, synthesisVoice, value, silenceTimeoutMillis);
    }

    public AiVoiceSettings withSilenceTimeoutMillis(int value) {
        return new AiVoiceSettings(mode, transcriptionModel, synthesisVoice, realtimeModel, value);
    }

    /**
     * 模式能不能真的跑起来。返回的错误由界面用中文呈现，所以这里给的是"缺什么"，不是一句话。
     */
    @Nullable
    public AiValidationError validate() {
        if (mode == AiMode.OMNI && (realtimeModel == null || realtimeModel.isBlank())) {
            return AiValidationError.VOICE_NEEDS_REALTIME_MODEL;
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
