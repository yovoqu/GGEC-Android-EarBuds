package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

/**
 * 某个模型自己的生成参数。字段为 null 表示"这一项没设过"，继续往下一层找。
 *
 * <p>与全局参数（{@link AiChatPreferences}）分开放：全局是"我给所有模型定个调"，
 * 模型级是"这个模型我要单独来"。两者混成一个字段，就没法回答"这个值是谁给的"。</p>
 */
public record AiModelParams(
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable Integer maxOutputTokens) {

    public static final AiModelParams EMPTY = new AiModelParams(null, null, null);

    public boolean isEmpty() {
        return temperature == null && topP == null && maxOutputTokens == null;
    }
}