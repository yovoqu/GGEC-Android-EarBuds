package com.ggec.earbuds.data.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.domain.device.model.GestureSlot;
import com.ggec.earbuds.domain.device.port.Subscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class EmptyDeviceControlsPortTest {

    @Test
    public void returnsEmptyControlsAndIgnoresMutations() {
        EmptyDeviceControlsPort port = new EmptyDeviceControlsPort();
        assertFalse(port.isAvailable());

        DeviceControls current = port.current();
        assertNull(current.eqPreset());
        assertNull(current.soundQuality());
        assertNull(current.gesture(GestureSlot.LONG));
        assertFalse(current.enabled(DeviceSwitch.GAME_MODE));
        assertFalse(current.enabled(DeviceSwitch.WEAR_DETECT));
        assertFalse(current.enabled(DeviceSwitch.DUAL_CONNECTION));

        List<DeviceControls> observed = new ArrayList<>();
        Subscription sub = port.observe(observed::add);
        port.update(c -> c.withSwitch(DeviceSwitch.GAME_MODE, true));

        assertEquals(1, observed.size());
        assertFalse(port.current().enabled(DeviceSwitch.GAME_MODE));
        sub.unsubscribe();
    }
}
