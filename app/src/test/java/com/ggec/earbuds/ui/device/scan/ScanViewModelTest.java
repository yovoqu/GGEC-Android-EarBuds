package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

public final class ScanViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private static final ScanPermissionPort GRANTED = () -> true;
    private static final ScanPermissionPort DENIED = () -> false;

    private static final DeviceCapabilityCatalog CATALOG = new DeviceCapabilityCatalog() {
        @Override
        public List<DeviceProfileView> models() {
            return List.of(new DeviceProfileView("HS01", "HS01 耳机", List.of()));
        }

        @Override
        public Optional<DeviceProfileView> model(String modelId) {
            return models().stream().filter(m -> m.modelId().equals(modelId)).findFirst();
        }
    };

    @Test
    public void startsIdleWithExpectedUiState() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        ScanViewModel vm = new ScanViewModel(
                new ScanDevices(port),
                new OpenDevice(new DeviceSession(port)),
                CATALOG,
                GRANTED);

        ScanUiState state = vm.uiState().getValue();
        assertFalse(state.scanning());
        assertFalse(state.hasScanned());
        assertEquals(ScanFilterOption.ALL_MODELS, state.selectedFilterModelId());
        assertEquals(2, state.filterOptions().size());
        assertTrue(state.results().isEmpty());
        assertEquals(0, state.totalDiscoveredCount());
        assertEquals(R.string.device_scan_idle_title, state.emptyTitleRes());
        assertEquals(R.string.device_scan_idle_body, state.emptyBodyRes());
        assertEquals(R.string.device_scan_start, state.actionTextRes());
    }

    @Test
    public void toggleScanWhenPermissionDeniedEmitsPermissionRequiredEvent() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        ScanViewModel vm = new ScanViewModel(
                new ScanDevices(port),
                new OpenDevice(new DeviceSession(port)),
                CATALOG,
                DENIED);

        vm.toggleScan();
        assertEquals(ScanEvent.PERMISSION_REQUIRED, vm.events().getValue());
        assertFalse(port.isScanning());
    }

    @Test
    public void toggleScanStartsAndStopsScanning() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        ScanViewModel vm = new ScanViewModel(
                new ScanDevices(port),
                new OpenDevice(new DeviceSession(port)),
                CATALOG,
                GRANTED);

        vm.toggleScan();
        assertTrue(port.isScanning());
        ScanUiState scanningState = vm.uiState().getValue();
        assertTrue(scanningState.scanning());
        assertTrue(scanningState.hasScanned());
        assertEquals(R.string.device_scan_searching, scanningState.emptyTitleRes());
        assertEquals(R.string.device_scan_searching_hint, scanningState.emptyBodyRes());
        assertEquals(R.string.device_scan_stop, scanningState.actionTextRes());

        vm.toggleScan();
        assertFalse(port.isScanning());
        ScanUiState stoppedState = vm.uiState().getValue();
        assertFalse(stoppedState.scanning());
        assertEquals(R.string.device_scan_empty_title, stoppedState.emptyTitleRes());
        assertEquals(R.string.device_scan_empty_body, stoppedState.emptyBodyRes());
        assertEquals(R.string.device_scan_start, stoppedState.actionTextRes());
    }

    @Test
    public void selectFilterUpdatesState() {
        FakeDeviceLinkPort port = new FakeDeviceLinkPort();
        ScanViewModel vm = new ScanViewModel(
                new ScanDevices(port),
                new OpenDevice(new DeviceSession(port)),
                CATALOG,
                GRANTED);

        vm.selectFilter("HS01");
        assertEquals("HS01", vm.uiState().getValue().selectedFilterModelId());
    }
}
