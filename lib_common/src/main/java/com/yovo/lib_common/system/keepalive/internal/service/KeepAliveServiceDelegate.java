package com.yovo.lib_common.system.keepalive.internal.service;

import android.app.Service;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.internal.compat.ForegroundServiceCompat;
import com.yovo.lib_common.system.keepalive.internal.ack.KeepAliveAckRegistry;
import com.yovo.lib_common.system.keepalive.internal.notification.KeepAliveNotificationBuilder;
import com.yovo.lib_common.system.keepalive.model.WakeLockPolicy;
import com.yovo.lib_common.system.keepalive.internal.power.PartialWakeLockController;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveSessionRegistry;
import com.yovo.lib_common.log.YoLog;

public final class KeepAliveServiceDelegate {

    private final Service service;
    private final Context appContext;

    public KeepAliveServiceDelegate(@NonNull Service service) {
        this.service = service;
        this.appContext = service.getApplicationContext();
    }

    public void promote(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(sessionId);
        if (entry == null) {
            return;
        }
        promote(entry);
    }

    public void promote(@NonNull KeepAliveSessionRegistry.SessionEntry entry) {
        KeepAliveConfig config = entry.config();
        KeepAliveNotificationSpec spec = entry.latestSpec();
        try {
            NotificationCompat.Builder builder =
                    KeepAliveNotificationBuilder.build(appContext, config, spec);
            ForegroundServiceCompat.promoteToForeground(
                    service,
                    config.notificationId(),
                    builder,
                    config.fgsType());
            syncWakeLock(entry, shouldHoldWakeLock(config, true));
            KeepAliveAckRegistry.get().complete(config.sessionId(), true, null);
        } catch (RuntimeException e) {
            // 前台服务类型/通知配置与系统约束不匹配时，绝不能把异常抛回 onStartCommand 崩溃。
            // 将会话标记为失效并停止服务，客户端通过 findSession() 感知状态丢失。
            YoLog.e("KeepAliveServiceDelegate", "提升到前台失败", e);
            KeepAliveAckRegistry.get().complete(
                    config.sessionId(), false, e.getMessage());
            entry.setActive(false);
            releaseWakeLock(entry);
            service.stopSelf();
        }
    }

    public void syncWakeLock(@NonNull String sessionId, boolean shouldHold) {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(sessionId);
        if (entry == null) {
            return;
        }
        syncWakeLock(entry, shouldHold);
    }

    public void syncWakeLock(@NonNull KeepAliveSessionRegistry.SessionEntry entry,
                             boolean shouldHold) {
        KeepAliveConfig config = entry.config();
        // 服务端只负责 WHILE_SESSION 策略；NEVER 不持有，CUSTOM 由客户端显式控制，
        // 避免每次 START/UPDATE 把客户端刚获取的锁释放掉。
        if (config.wakeLockPolicy() != WakeLockPolicy.WHILE_SESSION) {
            return;
        }
        PartialWakeLockController controller = entry.wakeLockController();
        if (controller == null) {
            controller = new PartialWakeLockController(
                    appContext,
                    config.wakeLockTag(),
                    config.wakeLockTimeoutMs());
            entry.setWakeLockController(controller);
        }
        controller.acquire(config.wakeLockTimeoutMs());
    }

    public void releaseWakeLock(@NonNull KeepAliveSessionRegistry.SessionEntry entry) {
        PartialWakeLockController controller = entry.wakeLockController();
        if (controller != null) {
            controller.release();
            entry.setWakeLockController(null);
        }
    }

    public void stop(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().remove(sessionId);
        if (entry != null) {
            entry.setActive(false);
            releaseWakeLock(entry);
            entry.throttle().reset();
        }
        ForegroundServiceCompat.stopForeground(service);
        service.stopSelf();
    }

    private static boolean shouldHoldWakeLock(@NonNull KeepAliveConfig config,
                                              boolean sessionRunning) {
        if (config.wakeLockPolicy() == WakeLockPolicy.NEVER) {
            return false;
        }
        if (config.wakeLockPolicy() == WakeLockPolicy.WHILE_SESSION) {
            return sessionRunning;
        }
        return false;
    }
}
