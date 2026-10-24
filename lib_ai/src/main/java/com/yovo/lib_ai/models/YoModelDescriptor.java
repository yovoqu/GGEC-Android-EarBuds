package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * 一个模型"叫什么、能做什么、这些说法是谁给的"。
 *
 * <p>{@code source} 不是装饰：{@link YoCapabilitySource#INFERRED} 的模型到了设置页要显成待确认状态，
 * 挡住"看起来支持、实际 400"。
 *
 * @param id            对话请求里的 {@code model} 字段原文
 * @param displayName   界面展示名，空则等于 id
 * @param capabilities  能力集合，不可变
 * @param source        能力的出处
 * @param contextWindow 上下文窗口；平台没给就是 {@code null}，不猜数值
 * @param family        家族（{@code gpt-4o} / {@code claude-3-5} / {@code deepseek} / {@code whisper}），未知为 {@code null}
 */
public record YoModelDescriptor(
        String id,
        String displayName,
        Set<YoModelCapability> capabilities,
        YoCapabilitySource source,
        @Nullable Integer contextWindow,
        @Nullable String family) {

    public YoModelDescriptor {
        id = requireId(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        source = source == null ? YoCapabilitySource.INFERRED : source;
        contextWindow = contextWindow != null && contextWindow > 0 ? contextWindow : null;
        family = family == null || family.isBlank() ? null : family.trim();
    }

    /**
     * 最常用的构造：没有上下文窗口、没有家族。
     */
    @NonNull
    public static YoModelDescriptor of(@NonNull String id,
                                       @NonNull Set<YoModelCapability> capabilities,
                                       @NonNull YoCapabilitySource source) {
        return new YoModelDescriptor(id, id, capabilities, source, null, null);
    }

    /**
     * 这个模型是否具备某个能力。能力为 {@code null} 时一律 {@code false}。
     */
    public boolean supports(@Nullable YoModelCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    /**
     * 只要有一个能力命中就算。
     */
    public boolean supportsAny(@Nullable Collection<YoModelCapability> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return false;
        }
        for (YoModelCapability capability : wanted) {
            if (supports(capability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 换一套能力（用户覆盖、或平台元数据补充）。
     */
    @NonNull
    public YoModelDescriptor withCapabilities(@Nullable Collection<YoModelCapability> newCapabilities,
                                              @NonNull YoCapabilitySource newSource) {
        Objects.requireNonNull(newSource, "source");
        return new YoModelDescriptor(id, displayName,
                newCapabilities == null ? Set.of() : Set.copyOf(newCapabilities), newSource,
                contextWindow, family);
    }

    /**
     * 内置表按家族命中时，把 id 换回平台真正给出的那个（例如带日期后缀的版本号）。
     * 展示名原本就是 id 的跟着换，否则保留人写的名字。
     */
    @NonNull
    public YoModelDescriptor withId(@Nullable String newId) {
        String resolved = requireId(newId);
        String name = displayName.equals(id) ? resolved : displayName;
        return new YoModelDescriptor(resolved, name, capabilities, source, contextWindow, family);
    }

    /**
     * 带上下文窗口的副本，用于把平台列表里拿到的数值补进来。
     */
    @NonNull
    public YoModelDescriptor withContextWindow(@Nullable Integer newContextWindow) {
        return new YoModelDescriptor(id, displayName, capabilities, source, newContextWindow, family);
    }

    @Override
    public String toString() {
        return "YoModelDescriptor{id=" + id
                + ", capabilities=" + capabilities
                + ", source=" + source
                + '}';
    }

    @NonNull
    private static String requireId(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("model id is required");
        }
        return value.trim();
    }
}
