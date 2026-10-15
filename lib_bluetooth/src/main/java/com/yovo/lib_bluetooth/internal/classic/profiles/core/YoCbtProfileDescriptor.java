package com.yovo.lib_bluetooth.internal.classic.profiles.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 单个蓝牙 Profile 的不可变元数据描述符（名称、是否需系统代理等）。
 */
public final class YoCbtProfileDescriptor {

    private final YoCbtProfileType profileType;
    private final String displayName;
    private final String description;
    private final boolean requiresProxy;
    private final boolean extensionOnly;

    /**
     * 构造新的 Profile 描述符。
     *
     * @param profileType  蓝牙 Profile 类型，不可为 null。
     * @param displayName  可读短名称（如 "A2DP"）。
     * @param description  Profile 用途的简要说明。
     * @param requiresProxy  是否依赖系统级 Bluetooth 代理服务进行状态跟踪。
     * @param extensionOnly  是否仅作为 SDK 扩展可用，且不由平台原生 Profile 代理层暴露。
     */
    public YoCbtProfileDescriptor(
            @NonNull final YoCbtProfileType profileType,
            @NonNull final String displayName,
            @NonNull final String description,
            final boolean requiresProxy,
            final boolean extensionOnly) {
        this.profileType = profileType;
        this.displayName = displayName;
        this.description = description;
        this.requiresProxy = requiresProxy;
        this.extensionOnly = extensionOnly;
    }

    // -- Getter --------------------------------------------------------------------------------

    /** 返回本描述符所代表的蓝牙 Profile 类型。 */
    @NonNull
    public YoCbtProfileType getProfileType() {
        return profileType;
    }

    /** 返回 Profile 的可读短名称。 */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /** 返回 Profile 用途的简要说明。 */
    @NonNull
    public String getDescription() {
        return description;
    }

    /** 返回本 Profile 是否需要系统级 Bluetooth 代理服务。 */
    public boolean isRequiresProxy() {
        return requiresProxy;
    }

    /**
     * 返回本 Profile 是否仅作为 SDK 扩展可用，且不由平台 Profile 代理层原生暴露。
     */
    public boolean isExtensionOnly() {
        return extensionOnly;
    }

    // -- 静态工厂 -------------------------------------------------------------------------

    /**
     * 返回给定 Profile 类型的预配置描述符。
     *
     * @param type Profile 类型，可为 null（映射为 UNKNOWN）。
     * @return 完整填充的描述符；永不为 null。
     */
    @NonNull
    public static YoCbtProfileDescriptor forProfile(@Nullable final YoCbtProfileType type) {
        if (type == null) {
            return unknownDescriptor();
        }
        switch (type) {
            case SPP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.SPP,
                        "SPP", "Serial Port Profile", false, false);
            case A2DP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.A2DP,
                        "A2DP", "Advanced Audio Distribution Profile", true, false);
            case HEADSET:
                return new YoCbtProfileDescriptor(YoCbtProfileType.HEADSET,
                        "Headset", "Headset / Hands-Free Profile", true, false);
            case HFP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.HFP,
                        "HFP", "Hands-Free capability (runtime state is HEADSET)", false, false);
            case HSP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.HSP,
                        "HSP", "Headset capability (runtime state is HEADSET)", false, false);
            case AVRCP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.AVRCP,
                        "AVRCP", "Audio/Video Remote Control Profile", false, true);
            case HID_HOST:
                return new YoCbtProfileDescriptor(YoCbtProfileType.HID_HOST,
                        "HID", "Human Interface Device", true, false);
            case PAN:
                return new YoCbtProfileDescriptor(YoCbtProfileType.PAN,
                        "PAN", "Personal Area Networking", true, false);
            case PBAP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.PBAP,
                        "PBAP", "Phone Book Access Profile", true, false);
            case MAP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.MAP,
                        "MAP", "Message Access Profile", false, true);
            case OPP:
                return new YoCbtProfileDescriptor(YoCbtProfileType.OPP,
                        "OPP", "Object Push Profile", false, true);
            case HEARING_AID:
                return new YoCbtProfileDescriptor(YoCbtProfileType.HEARING_AID,
                        "Hearing Aid", "Hearing Aid Profile", true, false);
            case LE_AUDIO:
                return new YoCbtProfileDescriptor(YoCbtProfileType.LE_AUDIO,
                        "LE Audio", "Low Energy Audio", true, false);
            case UNKNOWN:
            default:
                return unknownDescriptor();
        }
    }

    @NonNull
    private static YoCbtProfileDescriptor unknownDescriptor() {
        return new YoCbtProfileDescriptor(
                YoCbtProfileType.UNKNOWN, "Unknown", "", false, false);
    }

    // -- Object 重写 -----------------------------------------------------------------------

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtProfileDescriptor)) return false;
        final YoCbtProfileDescriptor that = (YoCbtProfileDescriptor) o;
        return profileType == that.profileType;
    }

    @Override
    public int hashCode() {
        return profileType.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return "YoCbtProfileDescriptor{"
                + "profileType=" + profileType
                + ", displayName='" + displayName + '\''
                + ", requiresProxy=" + requiresProxy
                + ", extensionOnly=" + extensionOnly
                + '}';
    }
}
