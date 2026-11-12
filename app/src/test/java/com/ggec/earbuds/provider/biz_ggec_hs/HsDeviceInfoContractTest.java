package com.ggec.earbuds.provider.biz_ggec_hs;


import com.yovo.biz_ggec_hs.api.HsDevice;
import static org.junit.Assert.assertEquals;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import org.junit.Test;

/**
 * 跨模块「未知标记」同值契约。
 *
 * <p>领域快照直接承载协议原值：耳机颜色与电量未知都用负数哨兵。两个模块各自声明常量，
 * 这里锁死它们相等——任何一侧改值都会在这里失败，而不是让界面把「未知」画成 0% 或某个色号。</p>
 */
public final class HsDeviceInfoContractTest {

    @Test
    public void unknownEarColorIsTheSameSentinelOnBothSides() {
        assertEquals(HsDevice.Info.UNKNOWN_EAR_COLOR, DeviceSnapshot.UNKNOWN_EAR_COLOR);
    }

    @Test
    public void unknownBatteryLevelIsTheSameSentinelOnBothSides() {
        assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, BatterySnapshot.UNKNOWN);
    }

    @Test
    public void unknownFirmwareIsTheEmptyStringOnBothSides() {
        assertEquals(HsDevice.Info.UNKNOWN_FIRMWARE, "");
    }
}
