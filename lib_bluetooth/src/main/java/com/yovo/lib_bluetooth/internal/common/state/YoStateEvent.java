package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 状态机事件基类。
 */
public abstract class YoStateEvent {
    private final long timestampMillis;
    private final String source;

    protected YoStateEvent(@NonNull String source) {
        this.timestampMillis = System.currentTimeMillis();
        this.source = source;
    }

    protected YoStateEvent(long timestampMillis, @NonNull String source) {
        this.timestampMillis = timestampMillis;
        this.source = source;
    }

    /**
     * 返回事件创建时的时间戳（Unix 纪元以来的毫秒数）。
     */
    public long timestampMillis() {
        return timestampMillis;
    }

    /**
     * 返回事件来源的人类可读描述（如创建该事件的类或组件）。
     */
    @NonNull
    public String source() {
        return source;
    }

    @NonNull
    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{source='" + source + "', timestamp=" + timestampMillis + "}";
    }
}
