package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles.proxy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 按 {@link YoCbtProfileType} 索引的活动 Profile 代理会话内存缓存。
 */
final class YoCbtProfileProxyCache {

    private final Map<YoCbtProfileType, YoCbtProfileProxySession> sessions = new HashMap<>();

    @Nullable
    synchronized YoCbtProfileProxySession get(@NonNull YoCbtProfileType profileType) {
        return sessions.get(profileType);
    }

    @NonNull
    synchronized YoCbtProfileProxySession create(@NonNull YoCbtProfileType profileType) {
        YoCbtProfileProxySession session = new YoCbtProfileProxySession(profileType);
        sessions.put(profileType, session);
        return session;
    }

    synchronized void remove(@NonNull YoCbtProfileType profileType) {
        sessions.remove(profileType);
    }

    @NonNull
    synchronized List<YoCbtProfileProxySession> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    synchronized void clear() {
        sessions.clear();
    }
}
