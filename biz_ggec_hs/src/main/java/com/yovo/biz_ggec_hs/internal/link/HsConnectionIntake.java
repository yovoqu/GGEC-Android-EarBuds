package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;

import java.util.List;

/**
 * 只建立连接。不补查首页、不补查耳机颜色、不接收主动上报。
 *
 * <p>本阶段组合根安装这一份。设备信息仍由 {@link HsDeviceInfoIntake} 描述，
 * 不在这里留半套查询。</p>
 */
public final class HsConnectionIntake implements HsSessionIntake {

    @Override
    public List<Command> readyQueries(String modelId) {
        return List.of();
    }

    @Override
    public boolean acceptDeviceReports() {
        return false;
    }

    @Override
    public boolean probeEarColorOnReady() {
        return false;
    }
}
