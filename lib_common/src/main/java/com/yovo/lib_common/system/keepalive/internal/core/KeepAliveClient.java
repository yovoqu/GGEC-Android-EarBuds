package com.yovo.lib_common.system.keepalive.internal.core;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveResult;
import com.yovo.lib_common.system.keepalive.model.KeepAliveSession;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStartResult;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStopHandler;
import com.yovo.lib_common.system.keepalive.YoKeepAlive;
import com.yovo.lib_common.system.keepalive.internal.compat.ForegroundServiceCompat;
import com.yovo.lib_common.system.keepalive.model.KeepAliveLogger;
import com.yovo.lib_common.system.keepalive.internal.KeepAlivePreconditionChecker;
import com.yovo.lib_common.system.keepalive.internal.ack.KeepAliveAckRegistry;
import com.yovo.lib_common.system.keepalive.internal.service.KeepAliveIntentFactory;
import com.yovo.lib_common.system.keepalive.model.WakeLockPolicy;
import com.yovo.lib_common.system.keepalive.internal.power.PartialWakeLockController;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveSessionRegistry;

public final class KeepAliveClient implements YoKeepAlive.Client {

    private static final String TAG = "SystemKeepAlive";

    @NonNull private final Context appContext;
    @NonNull private final KeepAliveConfig config;
    @NonNull private final KeepAliveLogger logger;
    @Nullable private KeepAliveSessionImpl session;

    public KeepAliveClient(@NonNull Context context,
                           @NonNull KeepAliveConfig config,
                           @NonNull KeepAliveLogger logger) {
        this.appContext = context.getApplicationContext();
        this.config = config;
        this.logger = logger;
    }

    @NonNull
    public KeepAliveStartResult start(@NonNull KeepAliveNotificationSpec spec) {
        return start(spec, null);
    }

    @NonNull
    public KeepAliveStartResult start(@NonNull KeepAliveNotificationSpec spec,
                                      @Nullable YoKeepAlive.StartCallback callback) {
        if (session != null && session.isActive()) {
            session.update(spec);
            return new KeepAliveStartResult(
                    KeepAliveResult.of(KeepAliveResult.Code.ALREADY_ACTIVE),
                    session);
        }
        // 系统硬性前置条件（前台服务类型权限）无条件检查；
        // 通知权限等软性条件仅由 strict 模式拦截。
        KeepAliveResult hardPrecondition =
                KeepAlivePreconditionChecker.checkHardRequirements(appContext, config);
        if (!hardPrecondition.isSuccess()) {
            logger.warn(TAG, "hard precondition failed: "
                    + hardPrecondition.detailMessage(), null);
            return new KeepAliveStartResult(hardPrecondition, null);
        }
        if (config.strictPreconditionCheck()) {
            KeepAliveResult softPrecondition =
                    KeepAlivePreconditionChecker.checkSoftRequirements(appContext, config);
            if (!softPrecondition.isSuccess()) {
                logger.warn(TAG, "precondition failed: "
                        + softPrecondition.detailMessage(), null);
                return new KeepAliveStartResult(softPrecondition, null);
            }
        }
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().register(config, spec);
        session = new KeepAliveSessionImpl(config, this);
        if (callback != null) {
            KeepAliveAckRegistry.get().await(config.sessionId(),
                    (sessionId, success, detail) -> {
                        if (success) {
                            callback.onForegroundStarted(sessionId);
                        } else {
                            KeepAliveSessionRegistry.get().remove(config.sessionId());
                            if (session != null) {
                                session.markInactive();
                            }
                            session = null;
                            callback.onStartFailed(sessionId,
                                    KeepAliveResult.of(
                                            KeepAliveResult.Code.START_FAILED, detail));
                        }
                    });
        }
        try {
            ForegroundServiceCompat.startKeepAliveService(
                    appContext, KeepAliveIntentFactory.createStart(appContext, config));
            return new KeepAliveStartResult(KeepAliveResult.of(KeepAliveResult.Code.SUCCESS), session);
        } catch (Throwable error) {
            KeepAliveAckRegistry.get().complete(
                    config.sessionId(), false, error.getMessage());
            KeepAliveSessionRegistry.get().remove(config.sessionId());
            session = null;
            logger.error(TAG, "start keep alive failed", error);
            return new KeepAliveStartResult(
                    KeepAliveResult.of(KeepAliveResult.Code.START_FAILED, error.getMessage()),
                    null);
        }
    }

    @NonNull
    KeepAliveResult updateInternal(@NonNull KeepAliveNotificationSpec spec, boolean force) {
        KeepAliveSessionRegistry.SessionEntry entry = requireActiveEntry();
        if (entry == null) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        if (!force && entry.throttle().shouldSkip(config.updatePolicy(), spec)) {
            entry.updateSpec(spec);
            return KeepAliveResult.of(KeepAliveResult.Code.THROTTLED);
        }
        entry.updateSpec(spec);
        try {
            appContext.startService(KeepAliveIntentFactory.createUpdate(appContext, config));
            return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
        } catch (Throwable error) {
            logger.error(TAG, "refresh keep alive failed", error);
            return KeepAliveResult.of(KeepAliveResult.Code.ERROR, error.getMessage());
        }
    }

    @NonNull
    KeepAliveResult syncWakeLockInternal(boolean shouldHold) {
        KeepAliveSessionRegistry.SessionEntry entry = requireActiveEntry();
        if (entry == null) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        if (config.wakeLockPolicy() != WakeLockPolicy.CUSTOM) {
            return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
        }
        PartialWakeLockController controller = entry.wakeLockController();
        if (controller == null) {
            controller = new PartialWakeLockController(
                    appContext,
                    config.wakeLockTag(),
                    config.wakeLockTimeoutMs());
            entry.setWakeLockController(controller);
        }
        controller.sync(shouldHold, config.wakeLockTimeoutMs());
        return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
    }

    @NonNull
    public KeepAliveResult stop() {
        KeepAliveAckRegistry.get().cancel(config.sessionId());
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().remove(config.sessionId());
        if (entry != null) {
            entry.setActive(false);
            releaseWakeLock(entry);
            entry.throttle().reset();
        }
        if (session != null) {
            session.markInactive();
        }
        try {
            appContext.startService(KeepAliveIntentFactory.createStop(appContext, config));
            return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
        } catch (Throwable error) {
            // 本地会话已清理；若无法投递 STOP（如后台启动限制），直接停止服务实例。
            logger.warn(TAG, "deliver stop intent failed, stopping service directly", error);
            try {
                appContext.stopService(new android.content.Intent(appContext, config.serviceClass()));
                return KeepAliveResult.of(KeepAliveResult.Code.SUCCESS);
            } catch (Throwable fallbackError) {
                logger.error(TAG, "stop keep alive failed", fallbackError);
                return KeepAliveResult.of(KeepAliveResult.Code.ERROR, fallbackError.getMessage());
            }
        }
    }

    public void release() {
        KeepAliveAckRegistry.get().cancel(config.sessionId());
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().remove(config.sessionId());
        if (entry != null) {
            entry.setActive(false);
            releaseWakeLock(entry);
        }
        if (session != null) {
            session.markInactive();
            session = null;
        }
        try {
            appContext.stopService(new Intent(appContext, config.serviceClass()));
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    private KeepAliveSessionRegistry.SessionEntry requireActiveEntry() {
        KeepAliveSessionRegistry.SessionEntry entry =
                KeepAliveSessionRegistry.get().get(config.sessionId());
        if (entry == null || !entry.isActive()) {
            return null;
        }
        return entry;
    }

    private void releaseWakeLock(@NonNull KeepAliveSessionRegistry.SessionEntry entry) {
        PartialWakeLockController controller = entry.wakeLockController();
        if (controller != null) {
            controller.release();
            entry.setWakeLockController(null);
        }
    }

    void clearSessionHandle() {
        session = null;
    }

    @Nullable
    public KeepAliveSessionImpl activeSession() {
        return session;
    }

    @NonNull
    public KeepAliveConfig config() {
        return config;
    }
}
