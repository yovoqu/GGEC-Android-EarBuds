package com.yovo.lib_ai.params;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 生成参数的解析规则：先定"取哪个值"，再按协议和模型裁到合法区间。
 *
 * <p>取值优先级逐项判断，不是整组判断：模型只改了温度，Top P 仍然跟随全局。
 * 整组判断的话，改一项会把没改过的项也一起冻住，用户下次改全局发现不生效。
 *
 * <p>全局值要不要对该模型生效（开关、作用范围）由调用方先筛过再传进来。
 * SDK 不知道"作用范围"这种产品概念，只负责"给了全局值就用"。
 */
public final class YoParamsPolicy {

    /** 模型没声明最大输出时的兜底上限。 */
    public static final int MAX_OUTPUT_FALLBACK = 1_000_000;

    /** OpenAI 系协议的温度上界。 */
    public static final double TEMPERATURE_MAX = 2.0;

    /** Anthropic Messages 协议的温度上界只有 1。 */
    public static final double TEMPERATURE_MAX_MESSAGES = 1.0;

    private YoParamsPolicy() {
    }

    /** 某个参数在该协议、该模型下的合法区间。 */
    @NonNull
    public static YoParamsRange range(
            @NonNull YoParam param,
            @Nullable YoProtocol protocol,
            @Nullable Integer modelMaxOutputTokens) {
        return switch (param) {
            case TEMPERATURE -> new YoParamsRange(0, protocol == YoProtocol.MESSAGES
                    ? TEMPERATURE_MAX_MESSAGES : TEMPERATURE_MAX);
            case TOP_P -> new YoParamsRange(0, 1);
            case MAX_OUTPUT -> new YoParamsRange(1,
                    modelMaxOutputTokens != null && modelMaxOutputTokens > 0
                            ? modelMaxOutputTokens : MAX_OUTPUT_FALLBACK);
        };
    }

    /**
     * 解析一组参数。
     *
     * @param modelParams      该模型自己的设定，没有就传 {@code null} 或 {@link YoGenerationParams#EMPTY}
     * @param globalParams     全局设定；调用方负责先判断它对该模型是否生效
     * @param protocol         决定温度上界；{@code null} 按通用上界处理
     * @param modelMaxOutputTokens 模型声明的最大输出，用来收紧最大输出上限
     */
    @NonNull
    public static YoParamsResolution resolve(
            @Nullable YoGenerationParams modelParams,
            @Nullable YoGenerationParams globalParams,
            @Nullable YoProtocol protocol,
            @Nullable Integer modelMaxOutputTokens) {
        Map<YoParam, YoParamsSource> sources = new EnumMap<>(YoParam.class);
        Set<YoParam> clamped = EnumSet.noneOf(YoParam.class);

        Double requestedTemperature = choose(
                modelParams == null ? null : modelParams.temperature(),
                globalParams == null ? null : globalParams.temperature(),
                YoParam.TEMPERATURE, sources);
        Double requestedTopP = choose(
                modelParams == null ? null : modelParams.topP(),
                globalParams == null ? null : globalParams.topP(),
                YoParam.TOP_P, sources);
        Integer requestedMaxOutput = chooseInteger(
                modelParams == null ? null : modelParams.maxOutputTokens(),
                globalParams == null ? null : globalParams.maxOutputTokens(),
                YoParam.MAX_OUTPUT, sources);

        YoGenerationParams requested = YoGenerationParams.of(
                requestedTemperature, requestedTopP, requestedMaxOutput);

        YoGenerationParams sent = YoGenerationParams.of(
                clamp(requestedTemperature,
                        range(YoParam.TEMPERATURE, protocol, modelMaxOutputTokens),
                        YoParam.TEMPERATURE, clamped),
                clamp(requestedTopP,
                        range(YoParam.TOP_P, protocol, modelMaxOutputTokens),
                        YoParam.TOP_P, clamped),
                clamp(requestedMaxOutput,
                        range(YoParam.MAX_OUTPUT, protocol, modelMaxOutputTokens),
                        YoParam.MAX_OUTPUT, clamped));

        return new YoParamsResolution(requested, sent, sources, clamped);
    }

    /** 不知道模型最大输出时的解析。 */
    @NonNull
    public static YoParamsResolution resolve(
            @Nullable YoGenerationParams modelParams,
            @Nullable YoGenerationParams globalParams,
            @Nullable YoProtocol protocol) {
        return resolve(modelParams, globalParams, protocol, null);
    }

    /** 直接用 {@link YoGenerationDefaults} 解析一遍，结果就是"默认值按协议裁剪后的样子"。 */
    @NonNull
    public static YoParamsResolution defaults(@Nullable YoProtocol protocol) {
        return resolve(YoGenerationDefaults.params(), null, protocol, null);
    }

    @Nullable
    private static Double choose(
            @Nullable Double own,
            @Nullable Double global,
            @NonNull YoParam param,
            @NonNull Map<YoParam, YoParamsSource> sources) {
        if (own != null) {
            sources.put(param, YoParamsSource.MODEL);
            return own;
        }
        if (global != null) {
            sources.put(param, YoParamsSource.GLOBAL);
            return global;
        }
        sources.put(param, YoParamsSource.PLATFORM_DEFAULT);
        return null;
    }

    @Nullable
    private static Integer chooseInteger(
            @Nullable Integer own,
            @Nullable Integer global,
            @NonNull YoParam param,
            @NonNull Map<YoParam, YoParamsSource> sources) {
        if (own != null) {
            sources.put(param, YoParamsSource.MODEL);
            return own;
        }
        if (global != null) {
            sources.put(param, YoParamsSource.GLOBAL);
            return global;
        }
        sources.put(param, YoParamsSource.PLATFORM_DEFAULT);
        return null;
    }

    @Nullable
    private static Double clamp(
            @Nullable Double value,
            @NonNull YoParamsRange range,
            @NonNull YoParam param,
            @NonNull Set<YoParam> clamped) {
        if (value == null) {
            return null;
        }
        double result = range.clamp(value);
        if (result != value) {
            clamped.add(param);
        }
        return result;
    }

    @Nullable
    private static Integer clamp(
            @Nullable Integer value,
            @NonNull YoParamsRange range,
            @NonNull YoParam param,
            @NonNull Set<YoParam> clamped) {
        if (value == null) {
            return null;
        }
        int result = (int) Math.round(range.clamp(value));
        if (result != value) {
            clamped.add(param);
        }
        return result;
    }
}
