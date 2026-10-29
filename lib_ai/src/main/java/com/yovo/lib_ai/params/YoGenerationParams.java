package com.yovo.lib_ai.params;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一组生成参数。每一项都可以为空，为空表示"不发送这个字段"，交给平台默认。
 *
 * <p>为什么用空表示平台默认、而不是填一个具体数字：各平台的默认值不一样，
 * 填死一个数等于强行改变某个平台的既有行为，出了问题也无从判断是谁的锅。
 *
 * <p>这里只做基本合法性检查（非负、最大输出为正），不按协议裁剪——裁剪要看协议和模型，
 * 属于 {@link YoParamsPolicy} 的职责。
 *
 * @param temperature    采样温度，{@code null} 表示不发送
 * @param topP           核采样累积概率，{@code null} 表示不发送
 * @param maxOutputTokens 最大输出 token，{@code null} 表示不发送
 */
public record YoGenerationParams(
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable Integer maxOutputTokens) {

    /** 三项都不发送。 */
    public static final YoGenerationParams EMPTY = new YoGenerationParams(null, null, null);

    public YoGenerationParams {
        if (temperature != null && temperature < 0) {
            throw new IllegalArgumentException("temperature must not be negative");
        }
        if (topP != null && topP < 0) {
            throw new IllegalArgumentException("topP must not be negative");
        }
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }

    @NonNull
    public static YoGenerationParams of(
            @Nullable Double temperature,
            @Nullable Double topP,
            @Nullable Integer maxOutputTokens) {
        return new YoGenerationParams(temperature, topP, maxOutputTokens);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** 三项都没有值。 */
    public boolean isEmpty() {
        return temperature == null && topP == null && maxOutputTokens == null;
    }

    /** 有没有任何一项要发送。 */
    public boolean hasAny() {
        return !isEmpty();
    }

    public static final class Builder {
        private Double temperature;
        private Double topP;
        private Integer maxOutputTokens;

        private Builder() {
        }

        @NonNull
        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        @NonNull
        public Builder topP(@Nullable Double topP) {
            this.topP = topP;
            return this;
        }

        @NonNull
        public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        @NonNull
        public YoGenerationParams build() {
            return new YoGenerationParams(temperature, topP, maxOutputTokens);
        }
    }
}
