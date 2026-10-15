package com.yovo.lib_common.system.keepalive.internal.compat;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.yovo.lib_common.system.keepalive.model.ForegroundServiceTypePolicy;

/**
 * 前台服务多版本策略（各 API 走该版本最优路径，而非统一降级）：
 * <ul>
 *   <li>API 34+：androidx {@link ServiceCompat#startForeground} 四参数（行为最完整）</li>
 *   <li>API 29–33：平台 {@code Service.startForeground(id, notification, type)}</li>
 *   <li>API &lt; 29：经典双参数 startForeground</li>
 *   <li>启动服务：API 26+ startForegroundService，否则 startService</li>
 * </ul>
 */
public final class ForegroundServiceCompat {

    private ForegroundServiceCompat() {}

    public static void startKeepAliveService(@NonNull Context context, @NonNull Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void promoteToForeground(@NonNull Service service,
                                           int notificationId,
                                           @NonNull NotificationCompat.Builder builder,
                                           @NonNull ForegroundServiceTypePolicy fgsType) {
        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && fgsType != ForegroundServiceTypePolicy.NONE) {
            int type = fgsType.serviceType();
            if (Build.VERSION.SDK_INT >= 34) {
                // API 34+：ServiceCompat 四参数为最优路径
                Api34.startForeground(service, notificationId, notification, type);
            } else {
                // API 29–33：平台带 type 的最优 API
                Api29.startForeground(service, notificationId, notification, type);
            }
        } else {
            service.startForeground(notificationId, notification);
        }
    }

    public static void stopForeground(@NonNull Service service) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            service.stopForeground(true);
        }
    }

    /** API 29–33：平台带 foregroundServiceType 的 startForeground。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class Api29 {
        private Api29() {
        }

        static void startForeground(@NonNull Service service,
                                    int notificationId,
                                    @NonNull Notification notification,
                                    int foregroundServiceType) {
            service.startForeground(notificationId, notification, foregroundServiceType);
        }
    }

    /** API 34+：androidx ServiceCompat 四参数。 */
    private static final class Api34 {
        private Api34() {
        }

        static void startForeground(@NonNull Service service,
                                    int notificationId,
                                    @NonNull Notification notification,
                                    int foregroundServiceType) {
            ServiceCompat.startForeground(
                    service, notificationId, notification, foregroundServiceType);
        }
    }
}
