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
        assertTrue(state.connected());
        assertTrue(state.controlsAvailable());
        assertEquals("HS02", state.modelId());
        assertEquals("80%", state.leftText());
        assertEquals("70%", state.rightText());
        assertEquals(DeviceFormats.UNKNOWN, state.caseText());
        assertEquals(R.string.device_state_ready, state.connectionTextRes());
        assertEquals(R.string.device_wear_none, state.wearTextRes());
        assertEquals(R.string.device_charging_on, state.chargingTextRes());
    }

    @Test
    public void connectingSnapshotShowsConnectingCard() {
        DeviceProfile profile = catalog.defaultProfile("HS01");
        DeviceSnapshot snapshot = new DeviceSnapshot(ConnectionState.CONNECTING, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "", "AA:BB:CC:00:00:09", DeviceSnapshot.UNKNOWN_EAR_COLOR);
        DeviceHomeUiState state = mapper.map(profile, snapshot, DeviceControls.empty(), false);
        assertTrue(state.connected());
        assertEquals("HS01", state.modelId());
        assertEquals(R.string.device_state_connecting, state.connectionTextRes());
    }

    @Test
    public void disconnectedSnapshotMapsToEmptyState() {
        DeviceProfile profile = catalog.defaultProfile("HS01");
        DeviceHomeUiState state = mapper.map(profile, DeviceSnapshot.idle(), DeviceControls.empty(), false);
        assertFalse(state.connected());
        assertFalse(state.controlsAvailable());
        assertEquals("", state.modelId());
        assertTrue(state.entries().isEmpty());
    }

    @Test
    public void missingProfileMapsToEmptyState() {
        DeviceSnapshot snapshot = connectedSnapshot("HS01", "v1");
        DeviceHomeUiState state = mapper.map(null, snapshot, DeviceControls.empty(), true);
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

    @Test
    public void unknownWearStateIsNotReportedAsNotWorn() {
        DeviceProfile profile = catalog.defaultProfile("HS01");
        DeviceSnapshot unknownWear = new DeviceSnapshot(ConnectionState.READY, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.UNKNOWN, "", "HS01", "AA:BB:CC:00:00:10",
                DeviceSnapshot.UNKNOWN_EAR_COLOR);
        DeviceSnapshot knownNotWorn = new DeviceSnapshot(ConnectionState.READY, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "HS01", "AA:BB:CC:00:00:11",
                DeviceSnapshot.UNKNOWN_EAR_COLOR);

        // 两个分支各自一句文案：没有数据不能显示成确定的「未佩戴」。
        assertEquals(R.string.device_wear_unknown,
                mapper.map(profile, unknownWear, DeviceControls.empty(), false).wearTextRes());
        assertEquals(R.string.device_wear_none,
                mapper.map(profile, knownNotWorn, DeviceControls.empty(), false).wearTextRes());
    }

    @Test
    public void idleSnapshotCarriesUnknownWearInsteadOfNotWorn() {
        // 空态没有佩戴数据，默认值必须是未知：NOT_WORN 只能表示确知未佩戴。
        assertEquals(WearState.UNKNOWN, DeviceSnapshot.idle().wear());
    }

    private static DeviceSnapshot connectedSnapshot(String modelId, String variantId) {
        return new DeviceSnapshot(ConnectionState.READY, modelId, variantId,
                new BatterySnapshot(80, 70, -1, true), WearState.NOT_WORN,
                "1.2.0.5", modelId, "AA:BB:CC:00:00:04", 3);
    }

    private static List<DevicePage> pages(List<DeviceHomeUiState.Entry> entries) {
        return entries.stream().map(DeviceHomeUiState.Entry::page).toList();
    }
}