package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;

/**
 * 软件 MATCH_LOST 是否生效。
 *
 * <p>Android {@code ScanSettings} 在 {@code CALLBACK_TYPE_ALL_MATCHES} 下原生不产生
 * {@code MATCH_LOST}，而本库需要“末次广播超时即移除设备”的产品语义。因此只要配置了
 * 正的 {@link BleScanConfig#matchLostTimeoutMillis()}，无论回调类型都启用软件 lost 清扫；
 * 阈值为 0 表示不启用软件 lost。</p>
 */
public final class YoBleScanLostPolicy {

    private YoBleScanLostPolicy() {
    }

    public static boolean softwareLostEnabled(@NonNull BleScanConfig config) {
        return config.matchLostTimeoutMillis() > 0L;
    }
}
