package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;

import java.util.HashMap;
import java.util.Map;

/**
 * GET 命令的结果缓存（按查询身份索引，带过期时间）。
 *
 * <p>只负责存取与过期判定，不碰超时、合并或调度。原 {@code CommandScheduler} 内的
 * {@code cache} 映射与 {@code CacheEntry}。</p>
 */
final class ResponseCache {
    private final Map<String, Entry> entries = new HashMap<>();

    /** 命中且未过期则返回缓存结果；过期则删除并返回 {@code null}。 */
    CommandCompletion get(String identity, long nowMs) {
        Entry cached = entries.get(identity);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtMs >= nowMs) {
            return cached.completion;
        }
        entries.remove(identity);
        return null;
    }

    void put(String identity, CommandCompletion completion, long expiresAtMs) {
        entries.put(identity, new Entry(completion, expiresAtMs));
    }

    void clear() {
        entries.clear();
    }

    private static final class Entry {
        private final CommandCompletion completion;
        private final long expiresAtMs;

        private Entry(CommandCompletion completion, long expiresAtMs) {
            this.completion = completion;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
