package com.yovo.lib_common.system.keepalive.service;

import com.yovo.lib_common.system.keepalive.internal.service.KeepAliveServiceDelegate;
import com.yovo.lib_common.system.keepalive.internal.service.KeepAliveIntentParser;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveContracts;
import com.yovo.lib_common.system.keepalive.model.KeepAliveSession;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveCore;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveActionDispatcher;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveSessionRegistry;

public abstract class BaseKeepAliveService extends Service {

    private KeepAliveServiceDelegate delegate;

    @Override
    public void onCreate() {
        super.onCreate();
        delegate = new KeepAliveServiceDelegate(this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = KeepAliveIntentParser.parseAction(intent);
        String sessionId = KeepAliveIntentParser.parseSessionId(intent);
        if (sessionId == null) {
            return START_NOT_STICKY;
        }
        if (KeepAliveContracts.ACTION_STOP.equals(action)) {
            delegate.stop(sessionId);
            return START_NOT_STICKY;
        }
        if (KeepAliveContracts.ACTION_USER_STOP.equals(action)) {
            KeepAliveActionDispatcher.dispatchUserStop(sessionId);
            return START_NOT_STICKY;
        }

        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(sessionId);
        if (entry == null) {
            return START_NOT_STICKY;
        }

        KeepAliveSession session = KeepAliveCore.findSession(sessionId);
        if (session == null) {
            return START_NOT_STICKY;
        }
        KeepAliveNotificationSpec spec = resolveNotificationSpec(intent, session);
        if (spec != null) {
            entry.updateSpec(spec);
        }
        delegate.promote(entry);
        onSessionCommand(sessionId, action, session);
        return START_NOT_STICKY;
    }

    @Nullable
    protected KeepAliveNotificationSpec resolveNotificationSpec(
            @Nullable Intent intent,
            @NonNull KeepAliveSession session) {
        return session.latestNotificationSpec();
    }

    protected void onSessionCommand(@NonNull String sessionId,
                                    @NonNull String action,
                                    @NonNull KeepAliveSession session) {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
