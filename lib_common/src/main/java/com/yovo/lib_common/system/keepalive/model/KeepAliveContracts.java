package com.yovo.lib_common.system.keepalive.model;

/**
 * 保活模块统一 Intent / Extra 常量。
 */
public final class KeepAliveContracts {

    public static final String ACTION_START = "com.yovo.lib_common.keepalive.START";
    public static final String ACTION_UPDATE = "com.yovo.lib_common.keepalive.UPDATE";
    public static final String ACTION_STOP = "com.yovo.lib_common.keepalive.STOP";
    public static final String ACTION_USER_STOP = "com.yovo.lib_common.keepalive.USER_STOP";

    public static final String EXTRA_SESSION_ID = "session_id";

    private KeepAliveContracts() {}
}
