package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.WearState;

import org.junit.Test;

public final class LoadDeviceProfilesTest {

    private final LoadDeviceProfiles useCase = new LoadDeviceProfiles(new DeviceProfileCatalogImpl());

    @Test
    public void idleOrDisconnectedReturnsEmptyWithoutHs01Fallback() {
        assertTrue(useCase.profileFor(DeviceSnapshot.idle()).isEmpty());
        DeviceSnapshot disconnected = new DeviceSnapshot(ConnectionState.DISCONNECTED, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "HS01", "", "");
        assertTrue(useCase.profileFor(disconnected).isEmpty());
    }

    @Test
    public void connectedUnknownModelReturnsEmpty() {
        DeviceSnapshot unknown = new DeviceSnapshot(ConnectionState.READY, "UNKNOWN", "v1",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "UNKNOWN", "", "");
        assertTrue(useCase.profileFor(unknown).isEmpty());
    }

    @Test
    public void connectedKnownModelResolvesExactOrDefaultVariant() {
        DeviceSnapshot exact = new DeviceSnapshot(ConnectionState.READY, "HS01", "v2",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "HS01", "", "");
        DeviceProfile p1 = useCase.profileFor(exact).orElseThrow();
        assertEquals("HS01", p1.model().id());
        assertEquals("v2", p1.variant().id());

        DeviceSnapshot unknownVariant = new DeviceSnapshot(ConnectionState.CONNECTED_NOT_READY,
                "HS01", "v99", BatterySnapshot.unknown(), WearState.NOT_WORN, "", "HS01", "", "");
        DeviceProfile p2 = useCase.profileFor(unknownVariant).orElseThrow();
        assertEquals("HS01", p2.model().id());
        assertEquals("v1", p2.variant().id());
    }
}
