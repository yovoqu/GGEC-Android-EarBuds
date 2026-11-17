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
 *
 * <p>门面持有唯一的 {@link KeepAliveCore} 实例（进程装配实例），全局配置、客户端表、
 * 会话注册表与启动回执表都属于该实例，不再散落为静态可写字段或静态单例。</p>
 */
public final class YoKeepAlive {

    private static final KeepAliveCore CORE = KeepAliveCore.processInstance();

    private YoKeepAlive() {
    }

    public static void install(@NonNull KeepAliveGlobalConfig config) {
        CORE.install(config);
    }

    @NonNull
    public static KeepAliveStartResult start(@NonNull Context context,
                                             @NonNull KeepAliveConfig config,
                                             @NonNull KeepAliveNotificationSpec spec) {
        return CORE.start(context, config, spec);
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
        return CORE.start(context, config, spec, callback);
    }

    @Nullable
    public static KeepAliveSession findSession(@NonNull String sessionId) {
        return CORE.findSession(sessionId);
    }

    @NonNull
    public static KeepAliveResult stop(@NonNull Context context,
                                       @NonNull String sessionId) {
        return CORE.stop(sessionId);
    }

    /**
     * 通知栏「用户请求停止」入口：把停止意图转交给该会话登记的停止处理器。
     *
     * <p>平台侧标准入口是 {@code BaseKeepAliveActionReceiver} 与 {@code BaseKeepAliveService}；
     * 自定义通知 action 也可直接调用本方法。</p>
     *
     * @return true 表示找到会话且已转交；false 表示无会话或无处理器（已在内部记录原因）
     */
    public static boolean dispatchUserStop(@NonNull String sessionId) {
        return CORE.dispatchUserStop(sessionId);
    }

    @NonNull
    public static Client client(@NonNull Context context,
                                @NonNull KeepAliveConfig config) {
        return CORE.client(context, config);
    }

    public static void releaseAll(@NonNull Context context) {
        CORE.releaseAll();
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
         *
         * <p>实现方必须给出真实回执语义，因此本方法没有默认实现：
         * 「无回执」的简化实现会让调用方误以为回调一定到来。</p>
         */
        @NonNull
        KeepAliveStartResult start(@NonNull KeepAliveNotificationSpec spec,
                                   @Nullable StartCallback callback);

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
