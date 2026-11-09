package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.data.device.EmptyDeviceControlsPort;
import com.ggec.earbuds.data.device.IdleDevicePort;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.usecase.EditDeviceControls;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.testing.device.FakeTestDeviceControlsPort;
import com.ggec.earbuds.testing.device.FakeTestDevicePort;
import com.ggec.earbuds.ui.device.DevicePage;

import org.junit.Rule;
import org.junit.Test;

public final class DeviceSettingsViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final DeviceProfileCatalogImpl catalog = new DeviceProfileCatalogImpl();
    private final DeviceSettingsUiMapper mapper = new DeviceSettingsUiMapper();

    @Test
    public void idleStartPublishesUnconnectedStateAndIgnoresAllMutations() {
        IdleDevicePort port = new IdleDevicePort();
        EmptyDeviceControlsPort controls = new EmptyDeviceControlsPort();
        DeviceSettingsViewModel viewModel = createViewModel(DevicePage.MUSIC, port, controls);

        viewModel.start();
        DeviceSettingsUiState state = viewModel.uiState().getValue();
        assertTrue(state.loaded());
        assertFalse(state.connected());
        assertFalse(state.interactive());

        viewModel.choosePreset(0);
        viewModel.chooseQuality(0);
        viewModel.saveCustomEq("Mine", new float[10]);
        viewModel.setLdac(true);
        viewModel.setVolumeAdaptive(true);
        viewModel.onChoice(SettingId.LONG, 0);
        viewModel.onToggle(SettingId.GAME_MODE, true);
        viewModel.onAction(SettingId.RESET_CONTROL);

        assertFalse(viewModel.uiState().getValue().connected());
    }

    @Test
    public void connectedWithUnavailableControlsRejectsMutations() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        FakeTestDeviceControlsPort controls = new FakeTestDeviceControlsPort(false, DeviceControls.empty());
        DeviceSettingsViewModel viewModel = createViewModel(DevicePage.MUSIC, port, controls);
        viewModel.start();
        port.emitSnapshot(readySnapshot("HS02", "v1"));

        viewModel.choosePreset(0);
        viewModel.chooseQuality(1);
        viewModel.setLdac(true);

        assertEquals(0, controls.updateCalls);
        assertEquals(-1, viewModel.uiState().getValue().music().selectedPreset());
        assertEquals(-1, viewModel.uiState().getValue().music().selectedQuality());
        assertFalse(viewModel.uiState().getValue().music().ldac());
    }

    @Test
    public void connectedWithAvailableControlsAppliesUpdatesAndResetClearsToEmpty() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        FakeTestDeviceControlsPort controls = new FakeTestDeviceControlsPort(true, DeviceControls.empty());
        DeviceSettingsViewModel viewModel = createViewModel(DevicePage.MUSIC, port, controls);
        viewModel.start();
        port.emitSnapshot(readySnapshot("HS02", "v1"));

        viewModel.choosePreset(0);
        viewModel.chooseQuality(1);
        viewModel.setLdac(true);

        assertTrue(viewModel.uiState().getValue().interactive());
        assertEquals(0, viewModel.uiState().getValue().music().selectedPreset());
        assertEquals(1, viewModel.uiState().getValue().music().selectedQuality());
        assertTrue(viewModel.uiState().getValue().music().ldac());

        viewModel.onAction(SettingId.RESET_CONTROL);
        assertEquals(-1, viewModel.uiState().getValue().music().selectedPreset());
        assertEquals(-1, viewModel.uiState().getValue().music().selectedQuality());
        assertFalse(viewModel.uiState().getValue().music().ldac());
    }

    private DeviceSettingsViewModel createViewModel(DevicePage page, DevicePort port,
                                                    DeviceControlsPort controlsPort) {
        return new DeviceSettingsViewModel(
                page,
                new LoadDeviceProfiles(catalog),
                new ObserveDevice(port),
                new EditDeviceControls(controlsPort),
                mapper);
    }

    private static DeviceSnapshot readySnapshot(String modelId, String variantId) {
        return new DeviceSnapshot(ConnectionState.READY, modelId, variantId,
                new BatterySnapshot(85, 85, 60, false), WearState.BOTH,
                "1.0.0", modelId, "AA:BB:CC:00:00:01", "");
    }
}
