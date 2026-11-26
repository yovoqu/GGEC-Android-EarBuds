package com.ggec.earbuds.provider.biz_ggec_wy.value;

import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.api.WyDevice;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WyValueMapperTest {

    @Test
    public void syncDeviceStateMapsToWySyncAction() {
        Optional<WyControl.Action> mapped = WyValueMapper.tryToWyAction(new DeviceAction.SyncDeviceState());
        assertTrue(mapped.isPresent());
        assertTrue(mapped.get() instanceof WyControl.Action.SyncDeviceState);
    }

    @Test
    public void unsupportedActionAndCapabilityReturnEmptyInsteadOfThrowing() {
        assertTrue(WyValueMapper.tryToWyAction(
                new DeviceAction.FindEar(DeviceAction.FindEar.Side.LEFT)).isEmpty());
        assertTrue(WyValueMapper.tryToWyAction(new DeviceAction.StopFindAlert()).isEmpty());
        assertEquals(Optional.of(WyDevice.Capability.BATTERY), WyValueMapper.tryCapabilityOf("BATTERY"));
        assertTrue(WyValueMapper.tryCapabilityOf("FIND_EAR").isEmpty());
    }
}
