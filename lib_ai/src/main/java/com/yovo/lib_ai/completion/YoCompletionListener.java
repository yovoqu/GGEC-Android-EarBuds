package com.yovo.lib_ai.completion;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.error.YoAiException;

/**
 * 回调按顺序送到客户端的回调执行器上。取消之后不会再有任何回调。
 */
public interface YoCompletionListener {

    void onEvent(@NonNull YoCompletionEvent event);

    void onComplete(@NonNull YoCompletionResult result);

    void onFailure(@NonNull YoAiException error);
}
