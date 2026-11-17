package com.yovo.lib_common.system.keepalive.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStopHandler;
import com.yovo.lib_common.system.keepalive.internal.notification.KeepAliveNotificationThrottle;
import com.yovo.lib_common.system.keepalive.internal.power.PartialWakeLockController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话注册表：进程内会话表 + 每个会话的可变装配状态。
 *
 * <p>本类没有静态单例，也没有静态可变表：会话表是实例字段，
 * 唯一实例由 {@link KeepAliveCore} 持有并向下构造注入（客户端、会话句柄、服务端委托）。
 * 需要隔离状态的调用方可以自行 {@code new KeepAliveSessionRegistry()}。</p>
 */
public final class KeepAliveSessionRegistry {

    private final Map<String, SessionEntry> entries = new ConcurrentHashMap<>();

    public KeepAliveSessionRegistry() {}

    @NonNull
    public SessionEntry register(@NonNull KeepAliveConfig config,
                                 @NonNull KeepAliveNotificationSpec initialSpec) {
        SessionEntry entry = new SessionEntry(config, initialSpec);
        entries.put(config.sessionId(), entry);
        return entry;
    }

    @Nullable
    public SessionEntry get(@NonNull String sessionId) {
        return entries.get(sessionId);
    }

    @Nullable
    public SessionEntry remove(@NonNull String sessionId) {
        return entries.remove(sessionId);
    }

    public void clear() {
        entries.clear();
    }

    public static final class SessionEntry {
        @NonNull private final KeepAliveConfig config;
        @NonNull private volatile KeepAliveNotificationSpec latestSpec;
        @Nullable private volatile KeepAliveStopHandler stopHandler;
        private volatile boolean active;
        @Nullable private volatile PartialWakeLockController wakeLockController;
        @NonNull private final KeepAliveNotificationThrottle throttle = new KeepAliveNotificationThrottle();

        SessionEntry(@NonNull KeepAliveConfig config,
                     @NonNull KeepAliveNotificationSpec initialSpec) {
            this.config = config;
            this.latestSpec = initialSpec;
            this.stopHandler = config.stopHandler();
            this.active = true;
        }

        @NonNull
        public KeepAliveConfig config() {
            return config;
        }

        @NonNull
        public KeepAliveNotificationSpec latestSpec() {
            return latestSpec;
        }

        public void updateSpec(@NonNull KeepAliveNotificationSpec spec) {
            this.latestSpec = spec;
        }

        @Nullable
        public KeepAliveStopHandler stopHandler() {
            return stopHandler;
        }

        public void setStopHandler(@Nullable KeepAliveStopHandler stopHandler) {
            this.stopHandler = stopHandler;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        @Nullable
        public PartialWakeLockController wakeLockController() {
            return wakeLockController;
        }

        public void setWakeLockController(@Nullable PartialWakeLockController wakeLockController) {
            this.wakeLockController = wakeLockController;
        }

        @NonNull
        public KeepAliveNotificationThrottle throttle() {
            return throttle;
        }
    }
}
