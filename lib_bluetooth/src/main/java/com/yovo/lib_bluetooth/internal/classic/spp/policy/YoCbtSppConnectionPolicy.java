package com.yovo.lib_bluetooth.internal.classic.spp.policy;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 组合连接策略与回退标志的 SPP 连接策略配置。
 */
public final class YoCbtSppConnectionPolicy {

    private final YoCbtSppConnectionStrategy strategy;
    private final boolean allowFallback;

    public YoCbtSppConnectionPolicy(@NonNull YoCbtSppConnectionStrategy strategy, boolean allowFallback) {
        this.strategy = strategy;
        this.allowFallback = allowFallback;
    }

    @NonNull
    public YoCbtSppConnectionStrategy getStrategy() {
        return strategy;
    }

    public boolean isAllowFallback() {
        return allowFallback;
    }

    @NonNull
    public static YoCbtSppConnectionPolicy defaultUuidOnly() {
        return new YoCbtSppConnectionPolicy(YoCbtSppConnectionStrategy.UUID_ONLY, false);
    }

    @NonNull
    public static YoCbtSppConnectionPolicy defaultUserSpecified() {
        return new YoCbtSppConnectionPolicy(YoCbtSppConnectionStrategy.USER_SPECIFIED_ONLY, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtSppConnectionPolicy)) return false;
        YoCbtSppConnectionPolicy that = (YoCbtSppConnectionPolicy) o;
        return allowFallback == that.allowFallback && strategy == that.strategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy, allowFallback);
    }

    @Override
    public String toString() {
        return "YoCbtSppConnectionPolicy{strategy=" + strategy
                + ", allowFallback=" + allowFallback + '}';
    }
}
