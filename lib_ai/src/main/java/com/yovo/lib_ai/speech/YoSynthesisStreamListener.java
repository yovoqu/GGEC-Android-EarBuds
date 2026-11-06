package com.yovo.lib_ai.speech;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

/**
 * 流式合成回调。OpenAI {@code stream_format=sse} 下 {@link #onAudio} 会被调用多次；
 * 非流式方言只在拿到完整结果后回调一次。
 */
public interface YoSynthesisStreamListener {

    /** 一小段音频字节（可能是完整音频，也可能是增量片段）。 */
    void onAudio(@NonNull byte[] chunk);

    /** 合成结束，携带可用的完整结果（可能同时含远端 URL）。 */
    void onComplete(@NonNull YoSynthesisResult result);

    /** 失败。分类用 {@code YoAiErrors.kindOf(error)} 取。 */
    void onFailure(@NonNull YoAiException error);
}
