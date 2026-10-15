package com.yovo.lib_bluetooth.internal.common.diagnostics;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 追踪 ID 值对象，用于关联日志与错误上下文。
 */
public final class YoTraceId {
    private YoTraceId() {
    }

    public static String next() {
        return UUID.randomUUID().toString();
    }
}
