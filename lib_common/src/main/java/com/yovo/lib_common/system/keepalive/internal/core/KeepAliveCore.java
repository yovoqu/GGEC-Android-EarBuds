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
import com.yovo.lib_common.system.keepalive.model.KeepAliveStopHandler;
import com.yovo.lib_common.system.keepalive.YoKeepAlive;
import com.yovo.lib_common.system.keepalive.internal.ack.KeepAliveAckRegistry;
import com.yovo.lib_common.system.keepalive.model.KeepAliveLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.yovo.lib_common.log.YoLog;

/**
 * 保活模块唯一推荐入口，风格对齐 {@link com.yovo.lib_common.system.refresh_rate.YoRefreshRate}。
 *
 * <p>全局配置、客户端表、会话注册表与启动回执表都是本实例的状态，由门面 {@link YoKeepAlive} 持有唯一实例；
 * 不再有静态可写字段，也不再有关联表的静态单例。需要隔离装配状态的调用方可自行
 * {@code new KeepAliveCore()}。</p>
 */
public final class KeepAliveCore {

    private static final String TAG = "KeepAliveCore";

    /**
     * 进程装配实例：门面与平台入口（前台 Service、停止广播接收器）共享同一份装配。
     *
     * <p>这是唯一的进程级引用，且为 {@code static final}：它不承载可变表，
     * 会话表/回执表都是 {@link #sessions}/{@link #acks} 的实例字段。</p>
     */
    private static final KeepAliveCore PROCESS = new KeepAliveCore();

    private final Map<String, KeepAliveClient> clients = new ConcurrentHashMap<>();

    private final KeepAliveSessionRegistry sessions = new KeepAliveSessionRegistry();

    private final KeepAliveAckRegistry acks = new KeepAliveAckRegistry();

    private volatile KeepAliveGlobalConfig globalConfig = KeepAliveGlobalConfig.defaults();

    public KeepAliveCore() {}

    /**
     * 进程装配实例（平台入口取用；业务统一走门面 {@link YoKeepAlive}）。
     */
    @NonNull
    public static KeepAliveCore processInstance() {
        return PROCESS;
    }

    @NonNull
    public KeepAliveSessionRegistry sessions() {
        return sessions;
    }

    @NonNull
    public KeepAliveAckRegistry acks() {
        return acks;
    }

    public void install(@NonNull KeepAliveGlobalConfig config) {
        globalConfig = config;
        YoLog.i(TAG, "保活全局配置已安装");
    }

    @NonNull
    public KeepAliveStartResult start(@NonNull Context context,
                                      @NonNull KeepAliveConfig config,
                                      @NonNull KeepAliveNotificationSpec spec) {
        KeepAliveClient client = clientFor(context, config);
        YoLog.d(TAG, "启动保活会话: " + config.sessionId());
        return client.start(spec);
    }

    @NonNull
    public KeepAliveStartResult start(@NonNull Context context,
                                      @NonNull KeepAliveConfig config,
                                      @NonNull KeepAliveNotificationSpec spec,
                                      @Nullable YoKeepAlive.StartCallback callback) {
        KeepAliveClient client = clientFor(context, config);
        YoLog.d(TAG, "启动保活会话（带回调）: " + config.sessionId());
        return client.start(spec, callback);
    }

    @Nullable
    public KeepAliveSession findSession(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry = sessions.get(sessionId);
        KeepAliveClient client = clients.get(sessionId);
        if (entry == null || client == null || !entry.isActive()) {
            return null;
        }
        return client.activeSession();
    }

    @NonNull
    public KeepAliveResult stop(@NonNull String sessionId) {
        YoLog.d(TAG, "停止保活会话: " + sessionId);
        KeepAliveClient client = clients.get(sessionId);
        if (client == null) {
            return KeepAliveResult.of(KeepAliveResult.Code.NOT_ACTIVE);
        }
        return client.stop();
    }

    /**
     * 通知栏「用户请求停止」入口：把停止意图转交给该会话登记的停止处理器。
     *
     * @return true 表示找到会话且已转交处理器；false 表示无会话或无处理器（已记录）
     */
    public boolean dispatchUserStop(@NonNull String sessionId) {
        KeepAliveSessionRegistry.SessionEntry entry = sessions.get(sessionId);
        if (entry == null) {
            YoLog.w(TAG, "用户请求停止，但会话不存在: " + sessionId);
            return false;
        }
        KeepAliveStopHandler handler = entry.stopHandler();
        if (handler == null) {
            YoLog.w(TAG, "用户请求停止，但会话没有停止处理器: " + sessionId);
            return false;
        }
        handler.onStopRequested(sessionId);
        return true;
    }

    @NonNull
    public YoKeepAlive.Client client(@NonNull Context context,
                                     @NonNull KeepAliveConfig config) {
        return clientFor(context, config);
    }

    public void releaseAll() {
        YoLog.i(TAG, "释放全部保活会话，数量=" + clients.size());
        for (KeepAliveClient client : clients.values()) {
            client.release();
        }
        clients.clear();
        sessions.clear();
    }

    @NonNull
    private KeepAliveClient clientFor(@NonNull Context context,
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
                logger(),
                sessions,
                acks);
        clients.put(config.sessionId(), created);
        return created;
    }

    @NonNull
    private KeepAliveLogger logger() {
        return globalConfig.logger();
    }
}
