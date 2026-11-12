package com.yovo.biz_ggec_hs.internal.center.protocol.event;

import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按协议命令字路由未被当前请求消费的设备帧。
 */
public final class ProtocolEventRouter {
    private static final String TAG = "ProtocolEventRouter";

    public interface FrameHandler {
        boolean handle(String frame);
    }

    private final Map<String, List<FrameHandler>> handlers = new HashMap<>();

    public ProtocolEventRouter register(String prefix, FrameHandler handler) {
        if (prefix == null || prefix.length() != 6 || handler == null) {
            throw new IllegalArgumentException("事件路由注册参数无效");
        }
        handlers.computeIfAbsent(
                prefix.toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(handler);
        return this;
    }

    public boolean route(String frame) {
        if (frame == null || frame.length() < 6) {
            return false;
        }
        List<FrameHandler> matched =
                handlers.get(frame.substring(0, 6).toUpperCase(Locale.ROOT));
        if (matched == null) {
            return false;
        }
        boolean handled = false;
        for (FrameHandler handler : matched) {
            try {
                handled |= handler.handle(frame);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "主动上报处理器执行失败: " + frame, e);
            }
        }
        return handled;
    }
}
