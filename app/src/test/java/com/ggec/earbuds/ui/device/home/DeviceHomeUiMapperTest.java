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
        DeviceSnapshot snapshot = connectedSnapshot("HS02", "v1");
        DeviceHomeUiState state = mapper.map(profile, snapshot, DeviceControls.empty(), true);
        assertTrue(state.loaded());
        assertTrue(state.connected());
        assertTrue(state.controlsAvailable());
        assertEquals("HS02", state.modelId());
        assertEquals("80%", state.leftText());
        assertEquals("70%", state.rightText());
        assertEquals(DeviceFormats.UNKNOWN, state.caseText());
        assertEquals(R.string.device_state_ready, state.connectionTextRes());
        assertEquals(R.string.device_wear_both, state.wearTextRes());
        assertEquals(R.string.device_charging_on, state.chargingTextRes());
    }

    @Test
    public void disconnectedSnapshotMapsToLoadedEmptyState() {
        DeviceProfile profile = catalog.defaultProfile("HS01");
        DeviceHomeUiState state = mapper.map(profile, DeviceSnapshot.idle(), DeviceControls.empty(), false);
        assertTrue(state.loaded());
        assertFalse(state.connected());
        assertFalse(state.controlsAvailable());
        assertEquals("", state.modelId());
        assertTrue(state.entries().isEmpty());
    }

    @Test
    public void missingProfileMapsToLoadedEmptyState() {
        DeviceSnapshot snapshot = connectedSnapshot("HS01", "v1");
        DeviceHomeUiState state = mapper.map(null, snapshot, DeviceControls.empty(), true);
        assertTrue(state.loaded());
        assertFalse(state.connected());
        assertTrue(state.entries().isEmpty());
    }

    @Test
    public void entriesFollowCapabilities() {
        DeviceProfile hs01 = catalog.defaultProfile("HS01");
        DeviceProfile hs02 = catalog.defaultProfile("HS02");
        List<DeviceHomeUiState.Entry> hs01Entries = mapper.map(
                hs01, connectedSnapshot("HS01", "v1"), DeviceControls.empty(), false).entries();
        List<DeviceHomeUiState.Entry> hs02Entries = mapper.map(
                hs02, connectedSnapshot("HS02", "v1"), DeviceControls.empty(), false).entries();

        assertFalse(pages(hs01Entries).contains(DevicePage.DUAL));
        assertTrue(pages(hs02Entries).contains(DevicePage.DUAL));
        assertTrue(pages(hs02Entries).contains(DevicePage.MUSIC));
        assertTrue(pages(hs02Entries).contains(DevicePage.ABOUT));
    }

    @Test
    public void togglesReflectStateAndAvailability() {
        DeviceProfile profile = catalog.defaultProfile("HS02");
        DeviceControls controls = DeviceControls.empty()
                .withSwitch(DeviceSwitch.GAME_MODE, true)
                .withSwitch(DeviceSwitch.WEAR_DETECT, false);
        DeviceHomeUiState state = mapper.map(
                profile, connectedSnapshot("HS02", "v1"), controls, false);
        assertFalse(state.controlsAvailable());
        assertTrue(state.gameMode());
        assertFalse(state.wearDetect());
    }

    private static DeviceSnapshot connectedSnapshot(String modelId, String variantId) {
        return new DeviceSnapshot(ConnectionState.READY, modelId, variantId,
                new BatterySnapshot(80, 70, -1, true), WearState.BOTH,
                "1.2.0.5", modelId, "AA:BB:CC:00:00:04", "");
    }

    private static List<DevicePage> pages(List<DeviceHomeUiState.Entry> entries) {
        return entries.stream().map(DeviceHomeUiState.Entry::page).toList();
    }
}
