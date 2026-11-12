package com.ggec.earbuds.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.WearState;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;

import org.junit.Test;

/**
 * 锁死领域与 biz 模型之间共享的「未知耳机颜色」约定，以及在仓状态的协议取值语义。
 *
 * <p>两端各有一份定义：领域不依赖 {@code biz_ggec_hs}，biz 也不依赖领域，因此这是
 * 分层规则里允许的契约孪生。孪生的代价是两边可以各自漂移——协议色号是正整数，
 * 一旦某一端把未知值改成别的负数，展示层的 {@code < 0} 兜底与设备信息的「未回报」判定
 * 会悄悄对不上。本用例用断言把两端钉成同值，改哪一端都会红。</p>
 */
public final class HsDeviceInfoContractTest {

    @Test
    public void unknownEarColorIsTheSameValueOnBothSidesOfTheContract() {
        assertEquals(DeviceSnapshot.UNKNOWN_EAR_COLOR, HsDeviceInfo.UNKNOWN_EAR_COLOR);
        assertEquals(-1, HsDeviceInfo.UNKNOWN_EAR_COLOR);
    }

    @Test
    public void unknownEarColorStaysOutOfTheProtocolRange() {
        // 协议色号为正整数：未知值必须落在负数区间，biz 的 hasEarColor() 与展示层的兜底才一致。
        assertTrue(HsDeviceInfo.UNKNOWN_EAR_COLOR < 0);
        assertFalse(new HsDeviceInfo("", HsDeviceInfo.UNKNOWN_EAR_COLOR,
                HsDeviceInfo.UNKNOWN_EAR_IN_CASE).hasEarColor());
    }

    @Test
    public void protocolEarInCaseValuesKeepTheirMeaning() {
        // 佩戴推导只认这两个取值：两耳都在仓 = 3（确知未佩戴），未上报 = -1（未知）。
        // 协议状态 0–3 全部是真实状态，未知值必须落在负数侧，否则「都不在仓」会被读成「没有数据」。
        assertEquals(3, HsDeviceInfo.EAR_IN_CASE_BOTH);
        assertEquals(-1, HsDeviceInfo.UNKNOWN_EAR_IN_CASE);
        assertTrue(HsDeviceInfo.UNKNOWN_EAR_IN_CASE < 0);
    }

    @Test
    public void deviceSnapshotKeepsTheProtocolValueInsteadOfClampingIt() {
        // 领域快照只保存协议原值：不再钳位到 UNKNOWN_EAR_COLOR，否则两端分叉会被静默抹平、
        // 契约漂移在运行期永远看不出来。
        DeviceSnapshot snapshot = new DeviceSnapshot(ConnectionState.READY, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.UNKNOWN, "", "HS01", "AA:BB:CC:00:00:01", -2);
        assertEquals(-2, snapshot.earColor());
    }
}
