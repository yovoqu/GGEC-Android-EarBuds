package com.ggec.earbuds.ui.device.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
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
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.EditDeviceControls;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.testing.device.FakeTestDeviceControlsPort;
import com.ggec.earbuds.testing.device.FakeTestDevicePort;

import org.junit.Rule;
import org.junit.Test;

public final class DeviceHomeViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final DeviceProfileCatalogImpl catalog = new DeviceProfileCatalogImpl();
    private final DeviceHomeUiMapper mapper = new DeviceHomeUiMapper();

    @Test
    public void idleStartPublishesEmptyStateAndIgnoresToggle() {
        IdleDevicePort port = new IdleDevicePort();
        EmptyDeviceControlsPort controls = new EmptyDeviceControlsPort();
        DeviceHomeViewModel viewModel = createViewModel(port, controls);

        viewModel.start();
        DeviceHomeUiState state = viewModel.uiState().getValue();
        assertFalse(state.connected());
        assertFalse(state.controlsAvailable());
        assertTrue(state.entries().isEmpty());

        viewModel.setToggle(HomeToggle.GAME_MODE, true);
        assertFalse(viewModel.uiState().getValue().gameMode());
    }

    @Test
    public void connectedWithUnavailableControlsKeepsReadOnlyState() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        FakeTestDeviceControlsPort controls = new FakeTestDeviceControlsPort(false, DeviceControls.empty());
        DeviceHomeViewModel viewModel = createViewModel(port, controls);
        viewModel.start();

        port.emitSnapshot(readySnapshot("HS01", "v1"));
        DeviceHomeUiState state = viewModel.uiState().getValue();
        assertTrue(state.connected());
        assertFalse(state.controlsAvailable());

        viewModel.setToggle(HomeToggle.GAME_MODE, true);
        assertEquals(0, controls.updateCalls);
        assertFalse(viewModel.uiState().getValue().gameMode());
    }

    @Test
    public void connectedWithAvailableControlsUpdatesToggleAndHandlesDisconnect() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        FakeTestDeviceControlsPort controls = new FakeTestDeviceControlsPort(true, DeviceControls.empty());
        DeviceHomeViewModel viewModel = createViewModel(port, controls);
        viewModel.start();

        port.emitSnapshot(readySnapshot("HS02", "v1"));
        assertTrue(viewModel.uiState().getValue().controlsAvailable());

        viewModel.setToggle(HomeToggle.GAME_MODE, true);
        assertTrue(viewModel.uiState().getValue().gameMode());

        viewModel.disconnect();
        assertFalse(viewModel.uiState().getValue().connected());
    }

    @Test
    public void connectingSnapshotResolvesProfileAndShowsConnectingCard() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        DeviceHomeViewModel viewModel = createViewModel(port, new EmptyDeviceControlsPort());
        viewModel.start();

        port.emitSnapshot(new DeviceSnapshot(ConnectionState.CONNECTING, "HS01", "v1",
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", "", "AA:BB:CC:00:00:01", DeviceSnapshot.UNKNOWN_EAR_COLOR));
        DeviceHomeUiState state = viewModel.uiState().getValue();
        assertTrue(state.connected());
        assertEquals("HS01", state.modelId());
        assertEquals(R.string.device_state_connecting, state.connectionTextRes());
    }

    private DeviceHomeViewModel createViewModel(DevicePort port, DeviceControlsPort controlsPort) {
        return new DeviceHomeViewModel(
                new LoadDeviceProfiles(catalog),
                new ObserveDevice(port),
                new ConnectDevice(port),
                new EditDeviceControls(controlsPort),
                mapper);
    }

    private static DeviceSnapshot readySnapshot(String modelId, String variantId) {
        return new DeviceSnapshot(ConnectionState.READY, modelId, variantId,
                new BatterySnapshot(90, 90, 80, false), WearState.NOT_WORN,
                "1.0.0", modelId, "AA:BB:CC:00:00:01", DeviceSnapshot.UNKNOWN_EAR_COLOR);
    }
}