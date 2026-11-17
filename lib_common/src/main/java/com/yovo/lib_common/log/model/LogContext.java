package com.yovo.lib_common.log.model;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用点上下文（类 MDC）：ThreadLocal 快照，跨异步屏障前必须已捕获。
 *
 * <p>上下文存储属于实例状态：由门面 {@link com.yovo.lib_common.log.YoLog} 持有的
 * 日志核心实例创建并持有唯一一份，不再以静态字段承载。</p>
 */
public final class LogContext {

    private final ThreadLocal<Map<String, String>> holder =
            ThreadLocal.withInitial(HashMap::new);

    public LogContext() {
    }

    public void put(@NonNull String key, @NonNull String value) {
        holder.get().put(key, value);
    }

    public void remove(@NonNull String key) {
        holder.get().remove(key);
    }

    public void clear() {
        holder.remove();
    }

    /** 返回不可变副本；业务线程边界必须 clear，防止线程池串味。 */
    @NonNull
    public Map<String, String> snapshot() {
        Map<String, String> current = holder.get();
        return current.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(current));
    }
}
