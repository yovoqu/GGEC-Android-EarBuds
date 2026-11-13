package com.yovo.biz_ggec_hs.internal.interact;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceDomainStore;
import com.yovo.biz_ggec_hs.internal.link.HsSessionState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 把会话域状态整理成对外快照：连接态、电量、设备信息与控制值。
 *
 * <p>控制值只收「设备已经报过」的项，键集合随设备上报增长；未上报的能力不出现，
 * 也不拿默认值占位。</p>
 */
public final class HsSnapshotAssembler {

    /** 三项设备信息全都未知时的快照值。 */
    static final HsDevice.Info UNKNOWN_DEVICE_INFO = new HsDevice.Info(
            HsDevice.Info.UNKNOWN_FIRMWARE,
            HsDevice.Info.UNKNOWN_EAR_COLOR,
            HsDevice.Ear.UNKNOWN);

    private final HsCapabilityRouter router;

    public HsSnapshotAssembler(HsCapabilityRouter router) {
        this.router = router;
    }

    public HsDevice.Snapshot assemble(HsSessionState state,
                        DeviceDomainStore domain,
                        HsControl.Dual dual) {
        HsControl.State connection = state.connection();
        HsDevice.Info info = HsModels.info(domain.protocolInfo());
        return new HsDevice.Snapshot(
                connection,
                battery(domain.battery()),
                info == null ? UNKNOWN_DEVICE_INFO : info,
                values(connection, domain, dual));
    }

    private Map<HsDevice.Capability, HsControl.Value> values(HsControl.State connection,
                                                     DeviceDomainStore domain,
                                                     HsControl.Dual dual) {
        if (connection != HsControl.State.READY) {
            return Collections.emptyMap();
        }
        Map<HsDevice.Capability, HsControl.Value> values = new EnumMap<>(HsDevice.Capability.class);
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            HsControl.Value value = router.value(capability, domain, dual);
            if (value != null) {
                values.put(capability, value);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * 协议电平文本到结构化电量的唯一转换点：{@code --} 是未知，其余是十进制。
     * 域状态在写入前已把非协议文本归为未知，因此这里不做第二套容错。
     */
    private static HsDevice.Battery battery(DeviceDomainStore.BatterySnapshot snapshot) {
        return new HsDevice.Battery(
                level(snapshot.getLeftLevel()),
                level(snapshot.getRightLevel()),
                level(snapshot.getCaseLevel()),
                snapshot.isCaseCharging(),
                snapshot.getEarInCaseStatus());
    }

    private static int level(String text) {
        if (text == null || "--".equals(text)) {
            return HsDevice.Battery.UNKNOWN_LEVEL;
        }
        return Integer.parseInt(text);
    }
}
