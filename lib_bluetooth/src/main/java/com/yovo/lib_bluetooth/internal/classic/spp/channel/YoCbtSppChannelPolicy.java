package com.yovo.lib_bluetooth.internal.classic.spp.channel;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 控制是否及如何允许 RFCOMM 通道（隐藏 API）连接的策略。
 */
public final class YoCbtSppChannelPolicy {

    private final boolean allowHiddenApi;
    private final boolean requireSourceVerification;

    public YoCbtSppChannelPolicy(boolean allowHiddenApi, boolean requireSourceVerification) {
        this.allowHiddenApi = allowHiddenApi;
        this.requireSourceVerification = requireSourceVerification;
    }

    public boolean isAllowHiddenApi() { return allowHiddenApi; }
    public boolean isRequireSourceVerification() { return requireSourceVerification; }

    /** 完全禁用通道连接。 */
    public static YoCbtSppChannelPolicy disabled() {
        return new YoCbtSppChannelPolicy(false, false);
    }

    /** 启用通道连接，但仅允许用户明确批准。 */
    public static YoCbtSppChannelPolicy explicitOnly() {
        return new YoCbtSppChannelPolicy(true, false);
    }

    /** 启用通道连接，来源必须已验证（例如调试工具已确认）。 */
    public static YoCbtSppChannelPolicy verifiedOnly() {
        return new YoCbtSppChannelPolicy(true, true);
    }
}
