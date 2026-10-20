package com.yovo.lib_ai.completion;

import androidx.annotation.NonNull;

/**
 * 流式过程中的一段增量。其余协议事件（心跳、块边界、用量）不在这里出现，最终结果里再汇总。
 */
public final class YoCompletionEvent {

    public enum Type {
        /** 回答正文。 */
        TEXT,
        /** 思考过程。不是所有模型都会给出。 */
        REASONING
    }

    private final Type type;
    private final String delta;

    private YoCompletionEvent(Type type, String delta) {
        this.type = type;
        this.delta = delta;
    }

    @NonNull
    public static YoCompletionEvent text(@NonNull String delta) {
        return new YoCompletionEvent(Type.TEXT, delta);
    }

    @NonNull
    public static YoCompletionEvent reasoning(@NonNull String delta) {
        return new YoCompletionEvent(Type.REASONING, delta);
    }

    @NonNull
    public Type type() {
        return type;
    }

    @NonNull
    public String delta() {
        return delta;
    }
}
