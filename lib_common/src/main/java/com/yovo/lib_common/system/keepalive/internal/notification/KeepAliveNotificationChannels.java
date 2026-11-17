package com.yovo.lib_common.system.keepalive.internal.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.keepalive.internal.service.ForegroundServiceRules;

public final class KeepAliveNotificationChannels {

    private KeepAliveNotificationChannels() {}

    public static void ensureChannel(@NonNull Context context,
                                     @NonNull String channelId,
                                     @NonNull CharSequence name,
                                     @NonNull CharSequence description) {
        if (!ForegroundServiceRules.notificationChannelRequired()) {
            return;
        }
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        NotificationChannel existing = nm.getNotificationChannel(channelId);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                channelId,
                name,
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(description.toString());
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);
    }
}
