package com.yovo.lib_common.system.keepalive;

import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveCore;

import android.app.PendingIntent;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveGlobalConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveResult;
import com.yovo.lib_common.system.keepalive.model.KeepAliveSession;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStartResult;
import com.yovo.lib_common.system.keepalive.internal.service.KeepAliveIntentFactory;

/**
 * 保活域对外门面。
 */
public final class YoKeepAlive {

    private YoKeepAlive() {
    }

    public static void install(@NonNull KeepAliveGlobalConfig config) {
        KeepAliveCore.install(config);
    }

    @NonNull
    public static KeepAliveStartResult start(@NonNull Context context,
                                             @NonNull KeepAliveConfig config,
                                             @NonNull KeepAliveNotificationSpec spec) {
        return KeepAliveCore.start(context, config, spec);
    }

    /**
     * 启动保活并在服务端确认（promote 成功/失败）后收到回执。
     * 同步返回值只表示“已成功派发启动请求”，不代表前台服务已就绪。
     */
    @NonNull
    public static KeepAliveStartResult start(@NonNull Context context,
                                             @NonNull KeepAliveConfig config,
                                             @NonNull KeepAliveNotificationSpec spec,
                                             @Nullable StartCallback callback) {
        return KeepAliveCore.start(context, config, spec, callback);
    }

    @Nullable
    public static KeepAliveSession findSession(@NonNull String sessionId) {
        return KeepAliveCore.findSession(sessionId);
    }

    @NonNull
    public static KeepAliveResult stop(@NonNull Context context,
                                       @NonNull String sessionId) {
        return KeepAliveCore.stop(context, sessionId);
    }

    @NonNull
    public static Client client(@NonNull Context context,
                                @NonNull KeepAliveConfig config) {
        return KeepAliveCore.client(context, config);
    }

    public static void releaseAll(@NonNull Context context) {
        KeepAliveCore.releaseAll(context);
    }

    @NonNull
    public static PendingIntent createUserStopPendingIntent(@NonNull Context context,
                                                            @NonNull String sessionId,
                                                            @NonNull Class<?> receiverClass,
                                                            int requestCode) {
        return KeepAliveIntentFactory.createUserStopPendingIntent(
                context, sessionId, receiverClass, requestCode);
    }

    public interface Client {

        @NonNull
        KeepAliveStartResult start(@NonNull KeepAliveNotificationSpec spec);

        /**
         * 带回执的启动：服务端 promote 完成后回调 {@link StartCallback}，
         * 超时（默认 5s）或派发异常也会回调失败。
         */
        @NonNull
        default KeepAliveStartResult start(@NonNull KeepAliveNotificationSpec spec,
                                           @Nullable StartCallback callback) {
            return start(spec);
        }

        @NonNull
        KeepAliveResult stop();

        void release();
    }

    /**
     * 保活启动回执。仅在本次启动确实派发了服务启动请求时才会触发。
     */
    public interface StartCallback {

        /** 服务端已成功把会话提升为前台服务（通知已展示）。 */
        void onForegroundStarted(@NonNull String sessionId);

        /** 服务端 promote 失败、启动派发异常或等待超时。 */
        void onStartFailed(@NonNull String sessionId, @NonNull KeepAliveResult result);
    }
}
