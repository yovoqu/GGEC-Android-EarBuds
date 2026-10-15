package com.yovo.lib_bluetooth.internal.classic.service.cache;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 控制何时可以使用缓存的 SDP 记录。
 */
public final class YoCbtServiceCachePolicy {

    private final long maxAgeMillis;
    private final boolean allowBondedCache;
    private final boolean allowLiveCache;

    public YoCbtServiceCachePolicy(long maxAgeMillis,
                                   boolean allowBondedCache,
                                   boolean allowLiveCache) {
        this.maxAgeMillis = maxAgeMillis <= 0 ? 86_400_000L : maxAgeMillis;
        this.allowBondedCache = allowBondedCache;
        this.allowLiveCache = allowLiveCache;
    }

    public long getMaxAgeMillis() {
        return maxAgeMillis;
    }

    public boolean isAllowBondedCache() {
        return allowBondedCache;
    }

    public boolean isAllowLiveCache() {
        return allowLiveCache;
    }

    @NonNull
    public static YoCbtServiceCachePolicy defaults() {
        return new YoCbtServiceCachePolicy(86_400_000L, true, true);
    }
}
