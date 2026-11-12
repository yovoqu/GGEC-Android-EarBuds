package com.yovo.lib_bluetooth.api;

import com.yovo.lib_bluetooth.model.ble.BleAdapterState;

/**
 * 扫描能力快照：由平台真实查询得到，供上层决定"能不能把过滤/批处理下推到硬件"。
 *
 * @param offloadedFiltering   硬件卸载过滤是否可用（不可用时只能客户端按帧过滤）
 * @param batching             硬件批量上报是否可用
 * @param extendedAdvertising  扩展广播（LE Extended Advertising）是否可用
 */
public record BleScanCapabilities(boolean offloadedFiltering,
                                 boolean batching,
                                 boolean extendedAdvertising) {
}
