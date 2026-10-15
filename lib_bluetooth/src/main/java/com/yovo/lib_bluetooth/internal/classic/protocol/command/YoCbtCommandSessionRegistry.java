package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.protocol.codec.YoCbtRawPacketCodec;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;

import java.util.HashMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SDK 生命周期内各 SPP 连接 {@link YoCbtCommandSession} 实例的注册表。
 */
public final class YoCbtCommandSessionRegistry {

    private final YoCbtRuntime runtime;
    private final YoCbtRawPacketCodec codec = new YoCbtRawPacketCodec();
    private final Map<YoCbtSppConnectionId, YoCbtCommandSession> sessions = new HashMap<>();

    public YoCbtCommandSessionRegistry(@NonNull YoCbtRuntime runtime) {
        this.runtime = runtime;
    }

    @NonNull
    public synchronized YoCbtCommandSession getOrCreate(
            @NonNull YoCbtSppConnectionId connectionId,
            @NonNull YoCbtCommandSession.Sender sender) {
        YoCbtCommandSession session = sessions.get(connectionId);
        if (session == null) {
            session = new YoCbtCommandSession(
                    connectionId, codec, runtime.scheduler(), sender, runtime::callback);
            sessions.put(connectionId, session);
        }
        return session;
    }

    @Nullable
    public synchronized YoCbtCommandSession get(@NonNull YoCbtSppConnectionId connectionId) {
        return sessions.get(connectionId);
    }

    public synchronized void remove(@NonNull YoCbtSppConnectionId connectionId) {
        YoCbtCommandSession session = sessions.remove(connectionId);
        if (session != null) session.clear();
    }

    public synchronized void clear() {
        for (YoCbtCommandSession session : sessions.values()) {
            session.clear();
        }
        sessions.clear();
    }
}
