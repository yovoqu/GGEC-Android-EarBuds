package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 设备主动事件前缀目录；allowsDual 表示可与命令应答同形双投。
 */
public final class EventCatalog {
    public static final class Entry {
        private final String prefix;
        private final boolean allowsDual;

        Entry(String prefix, boolean allowsDual) {
            this.prefix = prefix;
            this.allowsDual = allowsDual;
        }

        public String getPrefix() {
            return prefix;
        }

        public boolean allowsDual() {
            return allowsDual;
        }
    }

    private static final Map<String, Entry> ENTRIES;

    static {
        Map<String, Entry> map = new LinkedHashMap<>();
        // 电池
        put(map, "99EC80", true);
        put(map, "99EC95", true);
        put(map, "99EC96", true);
        // EQ / 服务状态
        put(map, "99EC83", true);
        put(map, "99ECB5", true);
        // 功能
        put(map, "99EC89", true);
        put(map, "99EC9F", true);
        put(map, "99ECCB", true);
        put(map, "99ECD2", true);
        put(map, "99ECD7", true);
        // 配对列表
        put(map, "99ECA7", true);
        ENTRIES = Collections.unmodifiableMap(map);
    }

    private EventCatalog() {
    }

    public static boolean contains(String frameOrPrefix) {
        return lookup(frameOrPrefix) != null;
    }

    public static boolean allowsDual(String frameOrPrefix) {
        Entry entry = lookup(frameOrPrefix);
        return entry != null && entry.allowsDual();
    }

    public static Entry lookup(String frameOrPrefix) {
        String prefix = prefixOf(frameOrPrefix);
        if (prefix == null) {
            return null;
        }
        return ENTRIES.get(prefix);
    }

    public static Set<String> prefixes() {
        return ENTRIES.keySet();
    }

    public static String prefixOf(String frameOrPrefix) {
        if (frameOrPrefix == null || frameOrPrefix.length() < 6) {
            return null;
        }
        return frameOrPrefix.substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private static void put(Map<String, Entry> map, String prefix, boolean allowsDual) {
        String key = prefix.toUpperCase(Locale.ROOT);
        map.put(key, new Entry(key, allowsDual));
    }
}
