package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.ui.device.DevicePage;

import org.junit.Test;

public final class DeviceSettingsUiMapperTest {

    private final DeviceProfileCatalogImpl catalog = new DeviceProfileCatalogImpl();
    private final DeviceSettingsUiMapper mapper = new DeviceSettingsUiMapper();

    @Test
    public void disconnectedOrMissingProfileMapsToUnconnectedStateWithoutNpe() {
        DeviceProfile hs01 = catalog.defaultProfile("HS01");
        DeviceSnapshot connected = readySnapshot("HS01", "v1");

        for (DevicePage page : DevicePage.values()) {
            DeviceSettingsUiState noProfile = mapper.map(page, null, connected, DeviceControls.empty(), true);
            assertTrue(noProfile.loaded());
            assertFalse(noProfile.connected());
            assertFalse(noProfile.interactive());
            assertTrue(noProfile.rows().isEmpty());
            assertNull(noProfile.music());

            DeviceSettingsUiState idle = mapper.map(page, hs01, DeviceSnapshot.idle(), DeviceControls.empty(), false);
            assertTrue(idle.loaded());
            assertFalse(idle.connected());
            assertFalse(idle.interactive());
        }
    }

    @Test
    public void emptyControlsKeepMusicSelectionsUnset() {
        DeviceProfile hs02 = catalog.defaultProfile("HS02");
        DeviceSettingsUiState state = mapper.map(
                DevicePage.MUSIC, hs02, readySnapshot("HS02", "v1"), DeviceControls.empty(), false);
        assertTrue(state.loaded());
        assertTrue(state.connected());
        assertFalse(state.interactive());
        assertNotNull(state.music());
        assertEquals(-1, state.music().selectedPreset());
        assertEquals(-1, state.music().selectedQuality());
        assertFalse(state.music().ldac());
        assertFalse(state.music().volumeAdaptive());
    }

    @Test
    public void emptyControlsKeepControlChoicesUnset() {
        DeviceProfile hs01 = catalog.defaultProfile("HS01");
        DeviceSettingsUiState state = mapper.map(
                DevicePage.CONTROL, hs01, readySnapshot("HS01", "v1"), DeviceControls.empty(), false);
        boolean checkedChoice = false;
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Choice choice) {
                checkedChoice = true;
                assertEquals(0, choice.valueRes());
            }
        }
        assertTrue(checkedChoice);
    }

    private static DeviceSnapshot readySnapshot(String modelId, String variantId) {
        return new DeviceSnapshot(ConnectionState.READY, modelId, variantId,
                new BatterySnapshot(80, 80, 70, false), WearState.NOT_WORN,
                "1.0.0", modelId, "AA:BB:CC:00:00:01", DeviceSnapshot.UNKNOWN_EAR_COLOR);
    }
}
