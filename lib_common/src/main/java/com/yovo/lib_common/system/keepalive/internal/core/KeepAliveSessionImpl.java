package com.yovo.lib_common.system.keepalive.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveResult;
import com.yovo.lib_common.system.keepalive.model.KeepAliveSession;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStopHandler;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveSessionRegistry;

final class KeepAliveSessionImpl implements KeepAliveSession {

    @NonNull private final KeepAliveConfig config;
    @NonNull private final KeepAliveClient client;
    private volatile boolean active = true;

    KeepAliveSessionImpl(@NonNull KeepAliveConfig config, @NonNull KeepAliveClient client) {
        this.config = config;
        this.client = client;
    }

    @NonNull
    @Override
    public String sessionId() {
        return config.sessionId();
    }

    @Override
    public boolean isActive() {
        if (!active) {
            return false;
        }
        // 服务端 promote 失败会将会话标记为失效，这里必须与注册表保持一致，
        // 避免客户端仍认为会话活跃而继续推送。
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(config.sessionId());
        return entry != null && entry.isActive();
    }

    @NonNull
    @Override
    public KeepAliveNotificationSpec latestNotificationSpec() {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(config.sessionId());
        return entry != null ? entry.latestSpec() : KeepAliveNotificationSpec.builder().build();
    }

    @NonNull
    @Override
    public KeepAliveResult update(@NonNull KeepAliveNotificationSpec spec) {
        if (!active) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        return client.updateInternal(spec, false);
    }

    @NonNull
    public KeepAliveResult updateForced(@NonNull KeepAliveNotificationSpec spec) {
        if (!active) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        return client.updateInternal(spec, true);
    }

    @NonNull
    @Override
    public KeepAliveResult syncWakeLock(boolean shouldHold) {
        if (!active) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        return client.syncWakeLockInternal(shouldHold);
    }

    @NonNull
    @Override
    public KeepAliveResult stop() {
        if (!active) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        KeepAliveResult result = client.stop();
        markInactive();
        return result;
    }

    @Override
    public void setStopHandler(@Nullable KeepAliveStopHandler handler) {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(config.sessionId());
        if (entry != null) {
            entry.setStopHandler(handler);
        }
    }

    void markInactive() {
        active = false;
        client.clearSessionHandle();
    }
}
