package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;

/**
 * 语音链路里可以预期的失败。用受检异常表达，是为了让"忘了处理失败"变成编译错误——
 * 语音这条路上失败太常见（没权限、设备被占用、平台不支持），静默吞掉只会表现成"它不说话"。
 */
public final class AiVoiceException extends Exception {

    private final AiFailure failure;

    public AiVoiceException(@NonNull AiFailure failure) {
        super(failure.kind().name() + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
        this.failure = failure;
    }

    @NonNull
    public AiFailure failure() {
        return failure;
    }
}
