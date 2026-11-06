package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

/**
 * 流式转写回调（OpenAI {@code stream=true} 的 SSE 事件）。
 */
public interface YoTranscriptionStreamListener {

    /** 一小段新增文本。 */
    void onDelta(@NonNull String delta);

    /** 结束，携带完整转写结果。 */
    void onComplete(@NonNull YoTranscription transcription);

    /** 失败。 */
    void onFailure(@NonNull YoAiException error);
}
