package com.yovo.lib_ai.params;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 一次参数解析的结果：想发什么、实际发什么、每一项取自哪里、哪几项被裁剪过。
 *
 * <p>{@code requested} 与 {@code sent} 分开，是为了让界面能显示"你设的是 2.5，实际按 2.0 发送"。
 * 只给一个 {@code sent} 的话，用户会以为自己设的值生效了。
 *
 * @param requested 解析出来、裁剪之前的值
 * @param sent      实际要发送的值，{@code null} 项不发送
 * @param sources   每一项的来源
 * @param clamped   被裁剪过的项
 */
public record YoParamsResolution(
        @NonNull YoGenerationParams requested,
        @NonNull YoGenerationParams sent,
        @NonNull Map<YoParam, YoParamsSource> sources,
        @NonNull Set<YoParam> clamped) {

    public YoParamsResolution {
        if (requested == null || sent == null) {
            throw new IllegalArgumentException("requested and sent are required");
        }
        sources = immutableSources(sources);
        clamped = immutableParams(clamped);
    }

    /** 某一项的来源。 */
    @NonNull
    public YoParamsSource source(@NonNull YoParam param) {
        YoParamsSource source = sources.get(param);
        return source == null ? YoParamsSource.PLATFORM_DEFAULT : source;
    }

    /** 这一项有没有被裁剪过。 */
    public boolean isClamped(@NonNull YoParam param) {
        return clamped.contains(param);
    }

    /** 三项都不发送，即全部交给平台默认。 */
    public boolean sendsNothing() {
        return sent.isEmpty();
    }

    /** 有没有任何一项来自模型自己的设定。 */
    public boolean anyFromModel() {
        return sources.containsValue(YoParamsSource.MODEL);
    }

    @NonNull
    private static Map<YoParam, YoParamsSource> immutableSources(
            @NonNull Map<YoParam, YoParamsSource> source) {
        if (source == null) {
            throw new IllegalArgumentException("sources is required");
        }
        EnumMap<YoParam, YoParamsSource> copy = new EnumMap<>(YoParam.class);
        copy.putAll(source);
        return Collections.unmodifiableMap(copy);
    }

    @NonNull
    private static Set<YoParam> immutableParams(@NonNull Set<YoParam> source) {
        if (source == null) {
            throw new IllegalArgumentException("clamped is required");
        }
        if (source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(source));
    }
}
