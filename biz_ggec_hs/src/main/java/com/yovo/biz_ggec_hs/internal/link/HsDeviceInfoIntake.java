package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.interact.HsCapabilityRouter;

import java.util.List;
import java.util.Objects;

/**
 * 连接就绪后取设备信息：按型号补查首页聚合，并接收主动上报。
 *
 * <p>首页查什么由 {@link HsCapabilityRouter#sessionBootstrapQueries(String)} 决定
 * （HS01 全局状态，HS02 两帧首页，两者都带 LDAC）。耳机颜色由引擎探针补查，
 * 因为首页聚合帧不带颜色。本阶段组合根不安装这一份。</p>
 */
public final class HsDeviceInfoIntake implements HsSessionIntake {

    private final HsCapabilityRouter router;

    public HsDeviceInfoIntake(HsCapabilityRouter router) {
        this.router = Objects.requireNonNull(router, "router");
    }

    @Override
    public List<Command> readyQueries(String modelId) {
        return router.sessionBootstrapQueries(modelId);
    }

    @Override
    public boolean acceptDeviceReports() {
        return true;
    }

    @Override
    public boolean probeEarColorOnReady() {
        return true;
    }
}
