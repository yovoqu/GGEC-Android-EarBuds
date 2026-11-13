package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;

import com.yovo.biz_ggec_hs.internal.center.exchange.HsFrameRoute;
import com.yovo.biz_ggec_hs.internal.center.exchange.HsTraffic;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

import java.util.Locale;

/**
 * 入站帧扇出：事件目录进事件通道，其余帧询问当前命令是否认领。
 * 会话是否仍然有效由调用方注入，不依赖任何 BLE 连接对象。
 */
public final class InboundFrameDemux {
    private static final String TAG = "InboundFrameDemux";

    public interface CommandResponsePort {
        /**
         * @return true 表示帧被当前命令消费（含写窗口缓冲）
         */
        boolean onResponseCandidate(long sessionId, String frame);
    }

    public interface EventPort {
        boolean onEvent(long sessionId, String frame);
    }

    public interface SessionGate {
        boolean isCurrent(long sessionId);
    }

    private final EventPort eventPort;
    private final CommandResponsePort commandPort;
    private final Runnable pulse;
    private final SessionGate sessionGate;

    public InboundFrameDemux(EventPort eventPort,
                             CommandResponsePort commandPort,
                             Runnable pulse,
                             SessionGate sessionGate) {
        if (eventPort == null || commandPort == null || sessionGate == null) {
            throw new IllegalArgumentException("入站分流依赖不能为空");
        }
        this.eventPort = eventPort;
        this.commandPort = commandPort;
        this.pulse = pulse == null ? () -> { } : pulse;
        this.sessionGate = sessionGate;
    }

    public void onFrame(long sessionId, String frame) {
        if (frame == null) {
            return;
        }
        if (sessionId <= 0L || !sessionGate.isCurrent(sessionId)) {
            HsLog.d(TAG, "丢弃过期入站帧 session=" + sessionId);
            return;
        }

        String normalized = frame.toUpperCase(Locale.ROOT);
        try {
            pulse.run();
        } catch (RuntimeException e) {
            HsLog.e(TAG, "入站续命失败", e);
        }
        HsFrameRoute route = HsFrameRoute.of(normalized);
        boolean eventHandled = false;
        if (route.traffic() == HsTraffic.DEVICE_REPORT) {
            try {
                eventHandled = eventPort.onEvent(sessionId, normalized);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "事件通道异常，不影响应答通道: " + normalized, e);
            }
        }

        boolean claimed = false;
        if (route.commandMayClaim()) {
            try {
                claimed = commandPort.onResponseCandidate(sessionId, normalized);
            } catch (RuntimeException e) {
                HsLog.e(TAG, "应答通道异常: " + normalized, e);
            }
        }

        if (route.traffic() == HsTraffic.REQUEST_REPLY && !claimed) {
            HsLog.d(TAG, "未识别入站帧: " + normalized
                    + " eventHandled=" + eventHandled);
        }
    }
}
