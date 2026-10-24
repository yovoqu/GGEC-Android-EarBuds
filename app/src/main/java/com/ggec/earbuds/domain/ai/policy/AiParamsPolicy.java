package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;

/**
 * 生成参数的生效规则（GEN-01…06），逐个参数解析：
 * 此模型有值 → 用它；否则全局开启、范围含该模型且全局该项有值 → 用全局；否则平台默认（不发送）。
 * 发送前按协议裁剪到合法范围。
 */
public final class AiParamsPolicy {

    public enum Source {
        MODEL,
        GLOBAL,
        DEFAULT
    }

    public enum Param {
        TEMPERATURE,
        TOP_P,
        MAX_OUTPUT
    }

    /** 未知最大输出时允许的上限。 */
    public static final int MAX_OUTPUT_FALLBACK = 1_000_000;

    /** 某个参数的解析结果：设定值、来源、实际发送值、是否被协议裁剪。 */
    public record Value(@Nullable Number value, @NonNull Source source, @Nullable Number sent, boolean clamped) {
        public boolean sendsNothing() {
            return sent == null;
        }
    }

    public record Resolved(@NonNull Value temperature, @NonNull Value topP, @NonNull Value maxOutput) {

        public Value get(Param param) {
            return switch (param) {
                case TEMPERATURE -> temperature;
                case TOP_P -> topP;
                case MAX_OUTPUT -> maxOutput;
            };
        }

        /** 实际发送的参数（平台默认的项为空，请求里不带）。 */
        public AiModelParams sent() {
            return new AiModelParams(
                    temperature.sent() == null ? null : temperature.sent().doubleValue(),
                    topP.sent() == null ? null : topP.sent().doubleValue(),
                    maxOutput.sent() == null ? null : maxOutput.sent().intValue());
        }

        public boolean anyFromModel() {
            return temperature.source() == Source.MODEL || topP.source() == Source.MODEL
                    || maxOutput.source() == Source.MODEL;
        }
    }

    public record Range(double min, double max) {
        public boolean contains(double value) {
            return value >= min && value <= max;
        }
    }

    private AiParamsPolicy() {
    }

    public static Range range(Param param, AiProtocol protocol, @Nullable AiModel model) {
        return switch (param) {
            case TEMPERATURE -> new Range(0, protocol == AiProtocol.MESSAGES ? 1 : 2);
            case TOP_P -> new Range(0, 1);
            case MAX_OUTPUT -> new Range(1, model != null && model.maxOutputTokens() != null
                    && model.maxOutputTokens() > 0 ? model.maxOutputTokens() : MAX_OUTPUT_FALLBACK);
        };
    }

    @NonNull
    public static Resolved resolve(@NonNull AiPlatformConfig platform, @NonNull AiModel model,
                                   @NonNull AiChatPreferences preferences) {
        AiModelParams own = platform.modelParams().get(model.id());
        boolean globalApplies = preferences.globalParamsEnabled()
                && preferences.globalCovers(platform.id(), model.id());
        AiProtocol protocol = platform.protocol();
        return new Resolved(
                value(own == null ? null : own.temperature(), globalApplies ? preferences.temperature() : null,
                        range(Param.TEMPERATURE, protocol, model), false),
                value(own == null ? null : own.topP(), globalApplies ? preferences.topP() : null,
                        range(Param.TOP_P, protocol, model), false),
                value(own == null ? null : own.maxOutputTokens(),
                        globalApplies ? preferences.maxOutputTokens() : null,
                        range(Param.MAX_OUTPUT, protocol, model), true));
    }

    /** 请求用的参数快照。没有模型时只看全局（范围按"全部模型"处理）。 */
    @NonNull
    public static AiModelParams snapshot(@Nullable AiModel model, @Nullable AiPlatformConfig platform,
                                         @NonNull AiChatPreferences preferences) {
        if (model != null && platform != null) {
            return resolve(platform, model, preferences).sent();
        }
        if (!preferences.globalParamsEnabled()) {
            return AiModelParams.EMPTY;
        }
        return new AiModelParams(preferences.temperature(), preferences.topP(), preferences.maxOutputTokens());
    }

    private static Value value(@Nullable Number own, @Nullable Number global, Range range, boolean integer) {
        Number chosen;
        Source source;
        if (own != null) {
            chosen = own;
            source = Source.MODEL;
        } else if (global != null) {
            chosen = global;
            source = Source.GLOBAL;
        } else {
            return new Value(null, Source.DEFAULT, null, false);
        }
        double raw = chosen.doubleValue();
        double clamped = Math.max(range.min(), Math.min(range.max(), raw));
        boolean changed = clamped != raw;
        Number sent = integer ? (Number) (int) Math.round(clamped) : (Number) clamped;
        return new Value(chosen, source, sent, changed);
    }
}
