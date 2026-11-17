package com.yovo.lib_common.system.keepalive.internal.service;

import android.app.Notification;
import android.app.Service;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.yovo.lib_common.system.keepalive.model.ForegroundServiceTypePolicy;

/**
 * 前台服务的进入/退出：带类型的前台服务按版本走该版本最完整的入口。
 *
 * <p>版本分档只经 {@link ForegroundServiceRules} 判定，本类不自行比较 API 号；
 * minSdk 24 起 {@code stopForeground(int)} 的 remove 标记恒可用，直接使用。</p>
 */
public final class ForegroundServicePromoter {

    private ForegroundServicePromoter() {
    }

    public static void promote(@NonNull Service service,
                               int notificationId,
                               @NonNull NotificationCompat.Builder builder,
                               @NonNull ForegroundServiceTypePolicy fgsType) {
        Notification notification = builder.build();
        if (fgsType != ForegroundServiceTypePolicy.NONE
                && ForegroundServiceRules.foregroundServiceTypeSupported()) {
            startWithType(service, notificationId, notification, fgsType.serviceType());
            return;
        }
        service.startForeground(notificationId, notification);
    }

    public static void stop(@NonNull Service service) {
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
    }

    /** API 29+：带 foregroundServiceType 的 startForeground 入口。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private static void startWithType(@NonNull Service service,
                                      int notificationId,
                                      @NonNull Notification notification,
                                      int foregroundServiceType) {
        if (ForegroundServiceRules.serviceCompatForegroundRequired()) {
            ServiceCompat.startForeground(
                    service, notificationId, notification, foregroundServiceType);
            return;
        }
        service.startForeground(notificationId, notification, foregroundServiceType);
    }
}
