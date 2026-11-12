package com.yovo.biz_ggec_hs.internal.center.port;

/**
 * 会话连接生命周期端口。协议引擎只往这里报告，不点名上层类型。
 *
 * <p>顺序契约：{@code onConnecting} 是尝试起点，{@code onConnected} / {@code onDisconnected} /
 * {@code onConnectFailed} 是尝试终态；一次尝试至多一个终态。</p>
 */
public interface HsConnectionEvents {

    /** 已发起连接，等待链路就绪。 */
    default void onConnecting(String macAddress) { }

    /** 链路就绪，可以下发命令。 */
    default void onConnected(String macAddress) { }

    /** 已建立的会话结束。 */
    default void onDisconnected(String reason) { }

    /** 尚未就绪即失败。 */
    default void onConnectFailed(String reason) { }
}
