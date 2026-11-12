package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HS 会话连接生命周期回调（区别于 {@link com.yovo.biz_ggec_hs.model.HsDeviceObserver} 的设备域事件）。
 *
 * <p>回调统一在主线程派发。{@code onConnected} 表示 GATT 已就绪且通知通道已使能，
 * 此刻才开始接受命令。上层据此驱动连接 UI，无需轮询 {@link YoHsClient#isReady()}。</p>
 */
public interface HsConnectionObserver {

    /** 已发起连接，等待链路就绪。 */
    default void onConnecting(@NonNull String macAddress) {
    }

    /** 链路就绪（GATT READY + 通知已使能），可以下发命令。 */
    default void onConnected(@NonNull String macAddress) {
    }

    /** 已建立的连接断开。 */
    default void onDisconnected(@Nullable String reason) {
    }

    /** 连接建立失败（尚未就绪即失败）。 */
    default void onConnectFailed(@Nullable String reason, boolean willRetry) {
    }
}
