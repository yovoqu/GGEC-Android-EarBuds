package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 发现结果过滤器，可按名称、设备类型与 RSSI 阈值筛选观测设备。
 */
public final class YoCbtDiscoveryFilter {

    private final boolean requireName;
    private final Set<YoCbtDeviceType> allowedDeviceTypes;
    private final int minRssi;

    private YoCbtDiscoveryFilter(@NonNull Builder builder) {
        this.requireName = builder.requireName;
        this.allowedDeviceTypes = builder.allowedDeviceTypes.isEmpty()
                ? Collections.<YoCbtDeviceType>emptySet()
                : Collections.unmodifiableSet(new HashSet<>(builder.allowedDeviceTypes));
        this.minRssi = builder.minRssi;
    }

    /**
     * 判断观测设备是否满足全部已配置的过滤条件。
     *
     * @param device 发现过程中观测到的设备
     * @return 通过全部条件返回 true，否则 false
     */
    public boolean accept(@NonNull YoCbtObservedDevice device) {
        // 名称要求
        if (requireName) {
            String name = device.getIdentity().getDisplayName();
            if (name == null || name.trim().isEmpty()) {
                return false;
            }
        }

        // 允许的设备类型（指定时生效）
        if (!allowedDeviceTypes.isEmpty()) {
            YoCbtDeviceType inferredType = device.inferDeviceType();
            if (!allowedDeviceTypes.contains(inferredType)) {
                return false;
            }
        }

        // 最低 RSSI 阈值
        if (device.getRssi() < minRssi) {
            return false;
        }

        return true;
    }

    // ── 构建器 ─────────────────────────────────────────────────────

    /**
     * 返回用于构建 {@link YoCbtDiscoveryFilter} 的新 {@link Builder}。
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@link YoCbtDiscoveryFilter} 的构建器。
     *
     * <p>所有条件均为可选。默认不应用过滤（接受所有设备）。
     */
    public static final class Builder {
        private boolean requireName;
        private final Set<YoCbtDeviceType> allowedDeviceTypes = new HashSet<>();
        private int minRssi = Integer.MIN_VALUE;

        /**
         * 为 true 时仅接受具有非空显示名的设备。默认 false。
         */
        @NonNull
        public Builder requireName(boolean requireName) {
            this.requireName = requireName;
            return this;
        }

        /**
         * 限制可接受的设备类型。未指定时（默认）接受所有类型。
         * 类型匹配使用 {@link YoCbtObservedDevice#inferDeviceType()}。
         */
        @NonNull
        public Builder allowedDeviceType(@NonNull YoCbtDeviceType... deviceTypes) {
            this.allowedDeviceTypes.addAll(Arrays.asList(deviceTypes));
            return this;
        }

        /**
         * 设置最低 RSSI 阈值，低于该值的设备将被拒绝。默认 {@link Integer#MIN_VALUE}（接受所有信号强度）。
         */
        @NonNull
        public Builder minRssi(int minRssi) {
            this.minRssi = minRssi;
            return this;
        }

        /**
         * 构建不可变的 {@link YoCbtDiscoveryFilter}。
         */
        @NonNull
        public YoCbtDiscoveryFilter build() {
            return new YoCbtDiscoveryFilter(this);
        }
    }
}
