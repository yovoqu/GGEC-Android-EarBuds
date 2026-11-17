package com.yovo.lib_common.system.keepalive.internal.service;

import android.os.Build;

import androidx.annotation.ChecksSdkIntAtLeast;

/**
 * 前台服务的平台能力：本模块版本差异的唯一查询点。
 *
 * <p>minSdk 24 起已恒成立的能力（如 {@code stopForeground(int)} 的 remove 标记）不设查询，
 * 调用点直接使用该 API。</p>
 */
public final class ForegroundServiceRules {

    private ForegroundServiceRules() {
    }

    /** API 26 起启动前台服务必须走 {@code startForegroundService}，否则系统抛异常。 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static boolean startForegroundServiceRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /** API 26 起通知必须归属渠道，否则前台服务通知不展示。 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    public static boolean notificationChannelRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    /** API 29 起 {@code startForeground} 才接受 foregroundServiceType 形参。 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean foregroundServiceTypeSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    /** API 34 起带类型的 startForeground 必须走 androidx ServiceCompat 四参数入口。 */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public static boolean serviceCompatForegroundRequired() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }
}
