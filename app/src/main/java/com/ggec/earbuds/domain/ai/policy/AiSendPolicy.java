package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

/**
 * 对话页能否发送（CHAT-04）。返回第一个不满足的条件；全部满足返回空。
 */
public final class AiSendPolicy {

    public enum Blocker {
        /** 输入为空：只禁用按钮，不显示原因行。 */
        EMPTY,
        GENERATING,
        NO_MODEL,
        NO_API_KEY,
        OFFLINE
    }

    private AiSendPolicy() {
    }

    @Nullable
    public static Blocker evaluate(@Nullable String input, boolean hasModel, boolean platformConfigured,
                                   boolean online, boolean generating) {
        if (generating) {
            return Blocker.GENERATING;
        }
        if (!hasModel) {
            return Blocker.NO_MODEL;
        }
        if (!platformConfigured) {
            return Blocker.NO_API_KEY;
        }
        if (!online) {
            return Blocker.OFFLINE;
        }
        if (input == null || input.isBlank()) {
            return Blocker.EMPTY;
        }
        return null;
    }

    /** 这个原因是否要在输入条上方写出来。 */
    public static boolean explains(@Nullable Blocker blocker) {
        return blocker == Blocker.NO_MODEL || blocker == Blocker.NO_API_KEY || blocker == Blocker.OFFLINE;
    }
}
