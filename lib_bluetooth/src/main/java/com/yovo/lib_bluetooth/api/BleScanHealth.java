package com.yovo.lib_bluetooth.api;

import com.yovo.lib_bluetooth.model.ble.BleAdapterState;

/**
 * 扫描健康度快照：把"平台静默限流 / 已经收不到帧"从不可见变成可观测。
 *
 * <p>只报事实，不产出任何告警文案或业务判定。</p>
 *
 * @param scanning            当前是否有活动扫描会话
 * @param startedAtMillis     当前（或最近一次）扫描会话的启动时刻；无会话时为 {@code 0}
 * @param startsInLastWindow  限流窗口（默认 30s）内已发起的平台扫描启动次数
 * @param lastFrameAtMillis   最近一次收到平台帧的时刻；本次会话尚未收到帧时为 {@code 0}
 * @param adapterState        当前蓝牙适配器状态
 */
public record BleScanHealth(boolean scanning,
                            long startedAtMillis,
                            int startsInLastWindow,
                            long lastFrameAtMillis,
                            BleAdapterState adapterState) {
}
