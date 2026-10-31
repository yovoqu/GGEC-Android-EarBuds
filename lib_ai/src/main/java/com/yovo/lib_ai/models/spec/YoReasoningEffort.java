package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 思考力度档位，按从弱到强声明。{@link #wire()} 就是请求里 {@code reasoning_effort} 的取值，
 * 与 Chat 请求构造器接受的那一组一致，所以规格里列出来的档位可以原样塞进请求。
 */
public enum YoReasoningEffort {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    private final String wire;

    YoReasoningEffort(String wire) {
        this.wire = wire;
    }

    @NonNull
    public String wire() {
        return wire;
    }

    /**
     * 按报文写法解析（忽略大小写和首尾空白）。认不出的返回 {@code null}：
     * 请求构造器也发不出去的档位，规格里记了也没用。
     */
    @Nullable
    public static YoReasoningEffort fromWire(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (YoReasoningEffort effort : values()) {
            if (effort.wire.equalsIgnoreCase(trimmed)) {
                return effort;
            }
        }
        return null;
    }
}
