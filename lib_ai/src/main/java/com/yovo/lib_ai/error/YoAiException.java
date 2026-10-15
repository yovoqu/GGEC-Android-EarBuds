package com.yovo.lib_ai.error;

import androidx.annotation.Nullable;

/**
 * lib_ai 调用失败的公共基类。网络、HTTP 状态和协议解析错误都从这里分支。
 */
public class YoAiException extends RuntimeException {

    public YoAiException(@Nullable String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
