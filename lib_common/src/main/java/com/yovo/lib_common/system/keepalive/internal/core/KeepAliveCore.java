package com.yovo.lib_common.system.keepalive.internal.core;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.keepalive.model.KeepAliveConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveGlobalConfig;
import com.yovo.lib_common.system.keepalive.model.KeepAliveNotificationSpec;
import com.yovo.lib_common.system.keepalive.model.KeepAliveResult;
import com.yovo.lib_common.system.keepalive.model.KeepAliveSession;
import com.yovo.lib_common.system.keepalive.model.KeepAliveStartResult;
import com.yovo.lib_common.system.keepalive.YoKeepAlive;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveClient;
import com.yovo.lib_common.system.keepalive.model.KeepAliveLogger;
import com.yovo.lib_common.system.keepalive.internal.core.KeepAliveSessionRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.yovo.lib_common.log.YoLog;

/**
 * 保活模块唯一推荐入口，风格对齐 {@link com.yovo.lib_common.system.refresh_rate.SystemRefreshRate}。
 */
public final class KeepAliveCore {

    private static final String TAG = "KeepAliveCore";

    private static volatile KeepAliveGlobalConfig globalConfig = KeepAliveGlobalConfig.defaults();
    private static final Map<String, KeepAliveClient> clients = new ConcurrentHashMap<>();

    private KeepAliveCore() {}

    public static void install(@NonNull KeepAliveGlobalConfig config) {
        globalConfig = config;
        YoLog.i(TAG, "保活全局配置已安装");
    }

    @NonNull
    public static KeepAliveStartResult start(@NonNull Context context,
                                             @NonNull KeepAliveConfig config,
                                             @NonNull KeepAliveNotificationSpec spec) {
        KeepAliveClient client = clientFor(context, config);
        YoLog.d(TAG, "启动保活会话: " + config.sessionId());
        return client.start(spec);
    }

    @NonNull
    public static KeepAliveStartResult start(@NonNull Context context,
                                             @NonNull KeepAliveConfig config,
                                             @NonNull KeepAliveNotificationSpec spec,
                                             @Nullable YoKeepAlive.StartCallback callback) {
        KeepAliveClient client = clientFor(context, config);
        YoLog.d(TAG, "启动保活会话（带回调）: " + config.sessionId());
        return client.start(spec, callback);
    }

    @Nullable
    public static KeepAliveSession findSession(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry = KeepAliveSessionRegistry.get().get(sessionId);
        KeepAliveClient client = clients.get(sessionId);
        if (entry == null || client == null || !entry.isActive()) {
            return null;
        }
        return client.activeSession();
    }

    @NonNull
    public static KeepAliveResult stop(@NonNull Context context, @NonNull String sessionId) {
        YoLog.d(TAG, "停止保活会话: " + sessionId);
        KeepAliveClient client = clients.get(sessionId);
        if (client == null) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        return client.stop();
    }

    @NonNull
    public static YoKeepAlive.Client client(@NonNull Context context,
                                            @NonNull KeepAliveConfig config) {
        return clientFor(context, config);
    }

    public static void releaseAll(@NonNull Context context) {
        YoLog.i(TAG, "释放全部保活会话，数量=" + clients.size());
        for (KeepAliveClient client : clients.values()) {
            client.release();
        }
        clients.clear();
        KeepAliveSessionRegistry.get().clear();
    }

    @NonNull
    private static KeepAliveClient clientFor(@NonNull Context context,
                                             @NonNull KeepAliveConfig config) {
        KeepAliveClient existing = clients.get(config.sessionId());
        if (existing != null) {
            if (!existing.config().sameRuntimeConfig(config)) {
                existing.release();
                clients.remove(config.sessionId());
            } else {
                return existing;
            }
        }
        KeepAliveClient created = new KeepAliveClient(
                context.getApplicationContext(),
                config,
                logger());
        clients.put(config.sessionId(), created);
        return created;
    }

    @NonNull
    private static KeepAliveLogger logger() {
        return globalConfig.logger();
    }
}
