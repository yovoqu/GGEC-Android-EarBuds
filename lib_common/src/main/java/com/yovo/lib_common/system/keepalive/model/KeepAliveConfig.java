package com.yovo.lib_common.system.keepalive.model;

import android.app.Service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.ForegroundServiceTypePolicy;
import com.yovo.lib_common.system.keepalive.model.NotificationUpdatePolicy;
import com.yovo.lib_common.system.keepalive.model.WakeLockPolicy;

public final class KeepAliveConfig {

    private static final long DEFAULT_WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L;

    @NonNull private final String sessionId;
    @NonNull private final Class<? extends Service> serviceClass;
    @NonNull private final ForegroundServiceTypePolicy fgsType;
    @NonNull private final String notificationChannelId;
    @NonNull private final CharSequence notificationChannelName;
    @NonNull private final CharSequence notificationChannelDescription;
    private final int notificationId;
    @NonNull private final WakeLockPolicy wakeLockPolicy;
    private final long wakeLockTimeoutMs;
    @NonNull private final String wakeLockTag;
    @NonNull private final NotificationUpdatePolicy updatePolicy;
    private final boolean strictPreconditionCheck;
    @Nullable private final KeepAliveStopHandler stopHandler;

    private KeepAliveConfig(@NonNull Builder builder) {
        this.sessionId = builder.sessionId;
        this.serviceClass = builder.serviceClass;
        this.fgsType = builder.fgsType;
        this.notificationChannelId = builder.notificationChannelId;
        this.notificationChannelName = builder.notificationChannelName;
        this.notificationChannelDescription = builder.notificationChannelDescription;
        this.notificationId = builder.notificationId;
        this.wakeLockPolicy = builder.wakeLockPolicy;
        this.wakeLockTimeoutMs = builder.wakeLockTimeoutMs;
        this.wakeLockTag = builder.wakeLockTag;
        this.updatePolicy = builder.updatePolicy;
        this.strictPreconditionCheck = builder.strictPreconditionCheck;
        this.stopHandler = builder.stopHandler;
    }

    @NonNull
    public String sessionId() {
        return sessionId;
    }

    @NonNull
    public Class<? extends Service> serviceClass() {
        return serviceClass;
    }

    @NonNull
    public ForegroundServiceTypePolicy fgsType() {
        return fgsType;
    }

    @NonNull
    public String notificationChannelId() {
        return notificationChannelId;
    }

    @NonNull
    public CharSequence notificationChannelName() {
        return notificationChannelName;
    }

    @NonNull
    public CharSequence notificationChannelDescription() {
        return notificationChannelDescription;
    }

    public int notificationId() {
        return notificationId;
    }

    @NonNull
    public WakeLockPolicy wakeLockPolicy() {
        return wakeLockPolicy;
    }

    public long wakeLockTimeoutMs() {
        return wakeLockTimeoutMs;
    }

    @NonNull
    public String wakeLockTag() {
        return wakeLockTag;
    }

    @NonNull
    public NotificationUpdatePolicy updatePolicy() {
        return updatePolicy;
    }

    public boolean strictPreconditionCheck() {
        return strictPreconditionCheck;
    }

    /**
     * 判断两次配置是否绑定同一个服务实例（同一会话不能静默切换服务/通知槽位）。
     */
    public boolean sameRuntimeConfig(@NonNull KeepAliveConfig other) {
        return serviceClass.equals(other.serviceClass)
                && notificationId == other.notificationId
                && fgsType == other.fgsType
                && wakeLockPolicy == other.wakeLockPolicy
                && notificationChannelId.equals(other.notificationChannelId);
    }

    @Nullable
    public KeepAliveStopHandler stopHandler() {
        return stopHandler;
    }

    @NonNull
    public static Builder builder(@NonNull String sessionId,
                                  @NonNull Class<? extends Service> serviceClass) {
        return new Builder(sessionId, serviceClass);
    }

    public static final class Builder {
        @NonNull private final String sessionId;
        @NonNull private final Class<? extends Service> serviceClass;
        @NonNull private ForegroundServiceTypePolicy fgsType =
                ForegroundServiceTypePolicy.connectedDeviceDefault();
        @NonNull private String notificationChannelId = "keepalive_default";
        @NonNull private CharSequence notificationChannelName = "Keep Alive";
        @NonNull private CharSequence notificationChannelDescription = "";
        private int notificationId;
        @NonNull private WakeLockPolicy wakeLockPolicy = WakeLockPolicy.WHILE_SESSION;
        private long wakeLockTimeoutMs = DEFAULT_WAKE_LOCK_TIMEOUT_MS;
        @NonNull private String wakeLockTag = "YoKeepAlive";
        @NonNull private NotificationUpdatePolicy updatePolicy =
                NotificationUpdatePolicy.noThrottle();
        private boolean strictPreconditionCheck;
        @Nullable private KeepAliveStopHandler stopHandler;

        private Builder(@NonNull String sessionId,
                        @NonNull Class<? extends Service> serviceClass) {
            this.sessionId = sessionId;
            this.serviceClass = serviceClass;
        }

        @NonNull
        public Builder fgsType(@NonNull ForegroundServiceTypePolicy fgsType) {
            this.fgsType = fgsType;
            return this;
        }

        @NonNull
        public Builder notificationChannel(@NonNull String channelId,
                                           @NonNull CharSequence name,
                                           @NonNull CharSequence description) {
            this.notificationChannelId = channelId;
            this.notificationChannelName = name;
            this.notificationChannelDescription = description;
            return this;
        }

        @NonNull
        public Builder notificationId(int notificationId) {
            this.notificationId = notificationId;
            return this;
        }

        @NonNull
        public Builder wakeLockPolicy(@NonNull WakeLockPolicy wakeLockPolicy) {
            this.wakeLockPolicy = wakeLockPolicy;
            return this;
        }

        @NonNull
        public Builder wakeLockTimeoutMs(long wakeLockTimeoutMs) {
            this.wakeLockTimeoutMs = wakeLockTimeoutMs;
            return this;
        }

        @NonNull
        public Builder wakeLockTag(@NonNull String wakeLockTag) {
            this.wakeLockTag = wakeLockTag;
            return this;
        }

        @NonNull
        public Builder updatePolicy(@NonNull NotificationUpdatePolicy updatePolicy) {
            this.updatePolicy = updatePolicy;
            return this;
        }

        @NonNull
        public Builder strictPreconditionCheck(boolean strictPreconditionCheck) {
            this.strictPreconditionCheck = strictPreconditionCheck;
            return this;
        }

        @NonNull
        public Builder stopHandler(@Nullable KeepAliveStopHandler stopHandler) {
            this.stopHandler = stopHandler;
            return this;
        }

        @NonNull
        public KeepAliveConfig build() {
            if (notificationId == 0) {
                throw new IllegalStateException("notificationId must be set");
            }
            return new KeepAliveConfig(this);
        }
    }
}
