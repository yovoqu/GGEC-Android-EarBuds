package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用点上下文（类 MDC）：ThreadLocal 快照，跨异步屏障前必须已捕获。
 */
public final class LogContext {

    private static final ThreadLocal<Map<String, String>> HOLDER =
            ThreadLocal.withInitial(HashMap::new);

    private LogContext() {
    }

    public static void put(@NonNull String key, @NonNull String value) {
        HOLDER.get().put(key, value);
    }

    public static void remove(@NonNull String key) {
        HOLDER.get().remove(key);
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 返回不可变副本；业务线程边界必须 clear，防止线程池串味。 */
    @NonNull
    public static Map<String, String> snapshot() {
        Map<String, String> current = HOLDER.get();
        return current.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(current));
    }
}
