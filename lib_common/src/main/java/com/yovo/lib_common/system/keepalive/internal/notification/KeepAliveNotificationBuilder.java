package com.yovo.lib_common.system.keepalive.internal.notification;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.yovo.lib_common.system.keepalive.model.KeepAliveActionSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;

public final class KeepAliveNotificationBuilder {

    private KeepAliveNotificationBuilder() {}

    @NonNull
    public static NotificationCompat.Builder build(@NonNull Context context,
                                                   @NonNull KeepAliveConfig config,
                                                   @NonNull KeepAliveNotificationSpec spec) {
        if (spec.smallIconResId() == 0) {
            throw new IllegalArgumentException("smallIconResId must be set");
        }
        KeepAliveNotificationChannels.ensureChannel(
                context,
                config.notificationChannelId(),
                config.notificationChannelName(),
                config.notificationChannelDescription());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, config.notificationChannelId())
                .setSmallIcon(spec.smallIconResId())
                .setContentTitle(spec.title())
                .setContentText(spec.text())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(spec.text()))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (spec.contentIntent() != null) {
            builder.setContentIntent(spec.contentIntent());
        }
        if (spec.showProgress()) {
            if (spec.indeterminate()) {
                builder.setProgress(0, 0, true);
            } else {
                builder.setProgress(spec.progressMax(), spec.progressValue(), false);
            }
        }
        for (KeepAliveActionSpec action : spec.actions()) {
            builder.addAction(action.iconResId(), action.label(), action.pendingIntent());
        }
        return builder;
    }
}
