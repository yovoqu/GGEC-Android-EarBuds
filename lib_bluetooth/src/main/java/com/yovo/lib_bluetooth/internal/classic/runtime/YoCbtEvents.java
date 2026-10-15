package com.yovo.lib_bluetooth.internal.classic.runtime;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtEventHub} 使用的事件类型常量定义。
 */
public final class YoCbtEvents {

    public static final String ACL_LINK_CHANGED = "yocbt.acl.link.changed";
    public static final String PROFILE_STATE_CHANGED = "yocbt.profile.state.changed";
    public static final String DISCOVERY = "yocbt.discovery";
    public static final String SPP_CONNECTION = "yocbt.spp.connection";
    public static final String SDP = "yocbt.sdp";

    private YoCbtEvents() {
    }
}
