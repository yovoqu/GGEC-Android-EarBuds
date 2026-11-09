package com.yovo.lib_bluetooth.model.ble;

/**
 * Android 7+ 每 30 秒最多 5 次 BLE 扫描的系统限流防护策略。
 */
public enum BleScanThrottlePolicy {
    /** 若滑窗已满，自动延迟等待窗口空出后再启动底层扫描（默认推荐）。 */
    DELAY_UNTIL_WINDOW_FREE,
    /** 滑窗已满时立即返回 {@link BleErrorCode#SCAN_THROTTLED}（附建议等待时间）。 */
    FAIL_FAST,
    /** 不启用 SDK 侧滑窗拦截（直接透传到底层系统）。 */
    UNRESTRICTED
}
