package com.yovo.biz_ggec_hs.internal.center.exchange;

import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.EventCatalog;

/**
 * 入站帧先按目录归类，再决定进事件通道、应答通道，还是两边都进。
 *
 * <p>同形双投（目录里标明可与命令应答同形）仍是 {@link HsTraffic#DEVICE_REPORT}，
 * 同时允许当前命令认领。认不出的帧按一发一收交给命令系统，由它决定是否消费。</p>
 */
public final class HsFrameRoute {

    private final HsTraffic traffic;
    private final boolean commandMayClaim;

    private HsFrameRoute(HsTraffic traffic, boolean commandMayClaim) {
        this.traffic = traffic;
        this.commandMayClaim = commandMayClaim;
    }

    public static HsFrameRoute of(String frame) {
        if (EventCatalog.contains(frame)) {
            return new HsFrameRoute(HsTraffic.DEVICE_REPORT, EventCatalog.allowsDual(frame));
        }
        return new HsFrameRoute(HsTraffic.REQUEST_REPLY, true);
    }

    public HsTraffic traffic() {
        return traffic;
    }

    public boolean commandMayClaim() {
        return commandMayClaim;
    }
}
