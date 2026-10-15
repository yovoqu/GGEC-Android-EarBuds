package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP UUID 选择策略，决定发起 SPP 连接时使用哪个服务 UUID。
 */
public final class YoCbtSppUuidPolicy {

    /**
     * UUID 选择策略。
     */
    public enum Strategy {
        /** 仅使用标准 SPP UUID（{@code 00001101-...}）。 */
        STANDARD_SPP_ONLY,
        /** 优先使用调用方指定的 UUID，回退到标准 SPP。 */
        PREFERRED_THEN_SPP,
        /** 仅使用实际通过 SDP 解析到的 UUID。 */
        SDP_RESOLVED_ONLY,
        /** 使用任意可用 UUID（找到的第一个优先）。 */
        ANY_AVAILABLE
    }

    private final Strategy strategy;
    private final UUID preferredUuid;

    /**
     * @param strategy      选择策略
     * @param preferredUuid 可选的优先 UUID；对于不需要该值的策略可为 null
     */
    public YoCbtSppUuidPolicy(@NonNull Strategy strategy, @Nullable UUID preferredUuid) {
        this.strategy = strategy;
        this.preferredUuid = preferredUuid;
    }

    @NonNull
    public Strategy getStrategy() {
        return strategy;
    }

    @Nullable
    public UUID getPreferredUuid() {
        return preferredUuid;
    }

    /**
     * 创建仅使用标准 SPP UUID 的策略。
     */
    @NonNull
    public static YoCbtSppUuidPolicy standardSpp() {
        return new YoCbtSppUuidPolicy(Strategy.STANDARD_SPP_ONLY, null);
    }

    /**
     * 创建优先使用给定 UUID、并回退到标准 SPP 的策略。
     */
    @NonNull
    public static YoCbtSppUuidPolicy preferredThenSpp(@NonNull UUID preferred) {
        return new YoCbtSppUuidPolicy(Strategy.PREFERRED_THEN_SPP, preferred);
    }
}
