package com.ggec.earbuds.ui.device.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DevicePage;

import org.junit.Test;

import java.util.List;

public final class DeviceHomeUiMapperTest {

    private final DeviceProfileCatalogImpl catalog = new DeviceProfileCatalogImpl();
    private final DeviceHomeUiMapper mapper = new DeviceHomeUiMapper();

    @Test
    public void mapsConnectedSnapshot() {
        DeviceProfile profile = catalog.defaultProfile("HS02");
        DeviceSnapshot snapshot = new DeviceSnapshot(ConnectionState.READY, "HS02", "v1",
                new BatterySnapshot(80, 70, -1, true), WearState.BOTH,
                "1.2.0.5", "HS02", "AA:BB:CC:00:00:04", "");
        DeviceHomeUiState state = mapper.map(profile, snapshot, DeviceControls.initial());
        assertTrue(state.loaded());
        assertEquals("HS02", state.modelId());
        assertEquals("80%", state.leftText());
        assertEquals("70%", state.rightText());
        assertEquals(DeviceFormats.UNKNOWN, state.caseText());
        assertEquals(R.string.device_state_ready, state.connectionTextRes());
        assertEquals(R.string.device_wear_both, state.wearTextRes());
        assertEquals(R.string.device_charging_on, state.chargingTextRes());
    }

    @Test
    public void entriesFollowCapabilities() {
        DeviceProfile hs01 = catalog.defaultProfile("HS01");
        DeviceProfile hs02 = catalog.defaultProfile("HS02");
        List<DeviceHomeUiState.Entry> hs01Entries =
                mapper.map(hs01, DeviceSnapshot.idle(), DeviceControls.initial()).entries();
        List<DeviceHomeUiState.Entry> hs02Entries =
                mapper.map(hs02, DeviceSnapshot.idle(), DeviceControls.initial()).entries();

        assertFalse(pages(hs01Entries).contains(DevicePage.DUAL));
        assertTrue(pages(hs02Entries).contains(DevicePage.DUAL));
        assertTrue(pages(hs02Entries).contains(DevicePage.MUSIC));
        assertTrue(pages(hs02Entries).contains(DevicePage.ABOUT));
    }

    @Test
    public void togglesReflectState() {
        DeviceProfile profile = catalog.defaultProfile("HS02");
        DeviceControls controls = DeviceControls.initial()
                .withSwitch(DeviceSwitch.GAME_MODE, true)
                .withSwitch(DeviceSwitch.WEAR_DETECT, false);
        DeviceHomeUiState state = mapper.map(profile, DeviceSnapshot.idle(), controls);
        assertTrue(state.gameMode());
        assertFalse(state.wearDetect());
    }

    private static List<DevicePage> pages(List<DeviceHomeUiState.Entry> entries) {
        return entries.stream().map(DeviceHomeUiState.Entry::page).toList();
    }
}
