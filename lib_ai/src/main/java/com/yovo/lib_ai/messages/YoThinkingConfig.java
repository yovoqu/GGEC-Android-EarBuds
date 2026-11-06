package com.yovo.lib_ai.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Anthropic 扩展思考配置。
 *
 * <p>{@code type} 支持 {@code adaptive/enabled/disabled/between_tools}；
 * {@code budgetTokens} 只对 enabled 有意义；{@code display} 控制思维摘要是否回传。</p>
 */
public final class YoThinkingConfig {

    public enum Type {
        ADAPTIVE("adaptive"),
        ENABLED("enabled"),
        DISABLED("disabled"),
        BETWEEN_TOOLS("between_tools");

        private final String wireValue;

        Type(String wireValue) {
            this.wireValue = wireValue;
        }

        @NonNull
        public String wireValue() {
            return wireValue;
        }
    }

    public enum Display {
        SUMMARIZED("summarized"),
        OMITTED("omitted");

        private final String wireValue;

        Display(String wireValue) {
            this.wireValue = wireValue;
        }

        @NonNull
        public String wireValue() {
            return wireValue;
        }
    }

    private final Type type;
    @Nullable
    private final Integer budgetTokens;
    @Nullable
    private final Display display;

    private YoThinkingConfig(Type type, @Nullable Integer budgetTokens, @Nullable Display display) {
        this.type = type;
        this.budgetTokens = budgetTokens;
        this.display = display;
    }

    @NonNull
    public static YoThinkingConfig adaptive() {
        return new YoThinkingConfig(Type.ADAPTIVE, null, null);
    }

    @NonNull
    public static YoThinkingConfig adaptive(@Nullable Display display) {
        return new YoThinkingConfig(Type.ADAPTIVE, null, display);
    }

    @NonNull
    public static YoThinkingConfig enabled(int budgetTokens) {
        if (budgetTokens < 1) {
            throw new IllegalArgumentException("budgetTokens must be positive");
        }
        return new YoThinkingConfig(Type.ENABLED, budgetTokens, null);
    }

    @NonNull
    public static YoThinkingConfig disabled() {
        return new YoThinkingConfig(Type.DISABLED, null, null);
    }

    @NonNull
    public static YoThinkingConfig betweenTools() {
        return new YoThinkingConfig(Type.BETWEEN_TOOLS, null, null);
    }

    @NonNull
    public static YoThinkingConfig of(
            @NonNull Type type,
            @Nullable Integer budgetTokens,
            @Nullable Display display) {
        if (budgetTokens != null && budgetTokens < 1) {
            throw new IllegalArgumentException("budgetTokens must be positive");
        }
        return new YoThinkingConfig(type, budgetTokens, display);
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public Integer budgetTokens() {
        return budgetTokens;
    }

    @Nullable
    public Display display() {
        return display;
    }
}
