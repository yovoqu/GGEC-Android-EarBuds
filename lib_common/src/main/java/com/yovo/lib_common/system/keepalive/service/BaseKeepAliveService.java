package com.yovo.lib_common.system.keepalive.service;

import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveCore;
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
import com.yovo.lib_common.system.keepalive.YoKeepAlive;

/**
 * 保活前台服务基类：命令解析、会话提升与用户停止都在这里收敛，
 * 子类只登记自己的停止处理器并实现 {@link #onSessionCommand}。
 *
 * <p>服务端委托由本类构造时注入进程装配实例（{@link KeepAliveCore#processInstance()}），
 * 会话表/回执表都是该装配实例的字段，没有静态注册表入口。</p>
 */
public abstract class BaseKeepAliveService extends Service {

    private KeepAliveServiceDelegate delegate;

    @Override
    public void onCreate() {
        super.onCreate();
        delegate = new KeepAliveServiceDelegate(this, KeepAliveCore.processInstance());
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
            YoKeepAlive.dispatchUserStop(sessionId);
            return START_NOT_STICKY;
        }

        KeepAliveSession session = YoKeepAlive.findSession(sessionId);
        if (session == null) {
            return START_NOT_STICKY;
        }
        KeepAliveNotificationSpec spec = resolveNotificationSpec(intent, session);
        if (!delegate.promote(sessionId, spec)) {
            return START_NOT_STICKY;
        }
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
