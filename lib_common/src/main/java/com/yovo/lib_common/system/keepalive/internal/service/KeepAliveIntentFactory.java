package com.yovo.lib_common.system.keepalive.internal.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveContracts;

public final class KeepAliveIntentFactory {

    private KeepAliveIntentFactory() {}

    @NonNull
    public static Intent createStart(@NonNull Context context,
                                     @NonNull KeepAliveConfig config) {
        return baseIntent(context, config)
                .setAction(KeepAliveContracts.ACTION_START);
    }

    @NonNull
    public static Intent createUpdate(@NonNull Context context,
                                      @NonNull KeepAliveConfig config) {
        return baseIntent(context, config)
                .setAction(KeepAliveContracts.ACTION_UPDATE);
    }

    @NonNull
    public static Intent createStop(@NonNull Context context,
                                    @NonNull KeepAliveConfig config) {
        return baseIntent(context, config)
                .setAction(KeepAliveContracts.ACTION_STOP);
    }

    @NonNull
    public static PendingIntent createUserStopPendingIntent(@NonNull Context context,
                                                            @NonNull String sessionId,
                                                            @NonNull Class<?> receiverClass,
                                                            int requestCode) {
        Intent intent = new Intent(context, receiverClass);
        intent.setAction(KeepAliveContracts.ACTION_USER_STOP);
        intent.putExtra(KeepAliveContracts.EXTRA_SESSION_ID, sessionId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    @NonNull
    private static Intent baseIntent(@NonNull Context context,
                                     @NonNull KeepAliveConfig config) {
        return new Intent(context, config.serviceClass())
                .putExtra(KeepAliveContracts.EXTRA_SESSION_ID, config.sessionId());
    }
}
