package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.data.device.IdleDevicePort;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.testing.device.FakeTestDevicePort;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScanViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private static final ScanPermissionPort GRANTED = () -> true;
    private static final ScanPermissionPort NOT_GRANTED = () -> false;

    @Test
    public void startsIdleAndIdlePortFinishesWithoutFakeResults() {
        IdleDevicePort port = new IdleDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                null,
                GRANTED);

        ScanUiState initial = viewModel.uiState().getValue();
        assertFalse(initial.scanning());
        assertFalse(initial.hasScanned());
        assertEquals(ScanFilterOption.ALL_MODELS, initial.selectedFilterModelId());
        assertEquals(1, initial.filterOptions().size());
        assertEquals(ScanFilterOption.ALL_MODELS, initial.filterOptions().get(0).modelId());
        assertTrue(initial.results().isEmpty());
        assertEquals(0, initial.totalDiscoveredCount());

        viewModel.toggleScan();
        ScanUiState after = viewModel.uiState().getValue();
        assertFalse(after.scanning());
        assertTrue(after.hasScanned());
        assertTrue(after.results().isEmpty());
    }

    @Test
    public void scriptedPortTracksScanningResultsDeduplicationAndCompletion() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        assertTrue(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());

        ScanResult first = new ScanResult("HS01", "v1", "HS01", "AA:BB:CC:00:00:01", -50);
        ScanResult duplicate = new ScanResult("HS01", "v1", "HS01", "aa:bb:cc:00:00:01", -49);
        port.emitScanResults(List.of(first, duplicate));
        assertEquals(1, viewModel.uiState().getValue().results().size());
        assertEquals(-49, viewModel.uiState().getValue().results().get(0).rssi());

        port.finishScan();
        assertFalse(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());
        assertEquals(1, viewModel.uiState().getValue().results().size());
    }

    @Test
    public void filtersDiscoveredDevicesBySelectedModelWithoutLosingOtherGeneration() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        LoadDeviceProfiles loadProfiles = new LoadDeviceProfiles(new DeviceProfileCatalogImpl());
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                loadProfiles,
                GRANTED);

        ScanUiState initial = viewModel.uiState().getValue();
        assertEquals(List.of(
                new ScanFilterOption(ScanFilterOption.ALL_MODELS, ""),
                new ScanFilterOption("HS01", "HS01"),
                new ScanFilterOption("HS02", "HS02")), initial.filterOptions());

        viewModel.toggleScan();
        ScanResult hs01 = new ScanResult("HS01", "v1", "HS01-Demo", "AA:BB:CC:00:00:01", -62);
        ScanResult hs02 = new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:02", -51);
        port.emitScanResults(List.of(hs01, hs02));

        ScanUiState allVisible = viewModel.uiState().getValue();
        assertEquals(2, allVisible.results().size());
        assertEquals(2, allVisible.totalDiscoveredCount());

        viewModel.selectFilter("HS01");
        ScanUiState hs01Only = viewModel.uiState().getValue();
        assertEquals("HS01", hs01Only.selectedFilterModelId());
        assertEquals(1, hs01Only.results().size());
        assertEquals("HS01", hs01Only.results().get(0).modelId());
        assertEquals(2, hs01Only.totalDiscoveredCount());

        viewModel.selectFilter("HS02");
        ScanUiState hs02Only = viewModel.uiState().getValue();
        assertEquals("HS02", hs02Only.selectedFilterModelId());
        assertEquals(1, hs02Only.results().size());
        assertEquals("HS02", hs02Only.results().get(0).modelId());
        assertEquals(2, hs02Only.totalDiscoveredCount());

        viewModel.selectFilter(ScanFilterOption.ALL_MODELS);
        assertEquals(2, viewModel.uiState().getValue().results().size());
    }

    @Test
    public void removesExpiredDeviceFromResults() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        ScanResult a = new ScanResult("HS01", "v1", "A", "AA:BB:CC:00:00:01", -50);
        ScanResult b = new ScanResult("HS02", "v1", "B", "AA:BB:CC:00:00:02", -45);
        port.emitScanResults(List.of(a, b));
        assertEquals(2, viewModel.uiState().getValue().results().size());

        port.emitScanRemoved(a);
        assertEquals(1, viewModel.uiState().getValue().results().size());
        assertEquals("HS02", viewModel.uiState().getValue().results().get(0).modelId());
    }

    @Test
    public void filtersByModelIdEvenWhenCatalogOptionsAbsent() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        port.emitScanResults(List.of(
                new ScanResult("HS01", "v1", "A", "AA:BB:CC:00:00:01", -50),
                new ScanResult("HS02", "v1", "B", "AA:BB:CC:00:00:02", -45)));

        viewModel.selectFilter("HS02");
        ScanUiState state = viewModel.uiState().getValue();
        assertEquals("HS02", state.selectedFilterModelId());
        assertEquals(1, state.results().size());
        assertEquals("HS02", state.results().get(0).modelId());
    }

    @Test
    public void keepsOldAndNewGenerationResultsWithSameMacAndUpgradesAdvertisedName() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                new LoadDeviceProfiles(new DeviceProfileCatalogImpl()),
                GRANTED);

        viewModel.toggleScan();
        ScanResult hs01Fallback = new ScanResult("HS01", "v1", "HS01", "11:22:33:44:55:66", -70);
        ScanResult hs01Named = new ScanResult("HS01", "v1", "GGEC-HS01", "11:22:33:44:55:66", -60);
        ScanResult hs02SameMac = new ScanResult("HS02", "v1", "Vidda V11", "11:22:33:44:55:66", -48);
        port.emitScanResults(List.of(hs01Fallback, hs01Named, hs02SameMac));

        List<ScanItem> items = viewModel.uiState().getValue().results();
        assertEquals(2, items.size());
        assertEquals("HS01", items.get(0).modelId());
        assertEquals("GGEC-HS01", items.get(0).bleName());
        assertEquals(-60, items.get(0).rssi());
        assertEquals("HS02", items.get(1).modelId());
        assertEquals("Vidda V11", items.get(1).bleName());
        assertEquals(-48, items.get(1).rssi());
    }

    @Test
    public void scanFailureStopsScanningAndEmitsEvent() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        assertTrue(viewModel.uiState().getValue().scanning());

        port.emitScanFailure(DevicePort.ScanFailure.BLUETOOTH_DISABLED);
        assertFalse(viewModel.uiState().getValue().scanning());
        assertEquals(ScanEvent.BLUETOOTH_DISABLED, viewModel.events().getValue());
    }

    @Test
    public void eachAdvertisementUpdatesTheVisibleRssiImmediately() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        AtomicInteger emissions = new AtomicInteger();
        viewModel.uiState().observeForever(state -> emissions.incrementAndGet());
        assertEquals(1, emissions.get());

        viewModel.toggleScan();
        assertEquals(2, emissions.get());

        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -60)));
        assertEquals(3, emissions.get());
        assertEquals(-60, viewModel.uiState().getValue().results().get(0).rssi());

        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -58)));
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -62)));
        assertEquals(5, emissions.get());
        assertEquals(-62, viewModel.uiState().getValue().results().get(0).rssi());

        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -49)));
        assertEquals(6, emissions.get());
        assertEquals(-49, viewModel.uiState().getValue().results().get(0).rssi());

        viewModel.selectFilter("HS02");
        assertEquals(-49, viewModel.uiState().getValue().results().get(0).rssi());
    }

    @Test
    public void selectingFilterDoesNotRestartOrRescan() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        port.emitScanResults(List.of(
                new ScanResult("HS01", "v1", "A", "AA:BB:CC:00:00:01", -50),
                new ScanResult("HS02", "v1", "B", "AA:BB:CC:00:00:02", -45)));
        assertEquals(1, port.startScanCalls);

        viewModel.selectFilter("HS01");

        ScanUiState filtered = viewModel.uiState().getValue();
        assertEquals(1, port.startScanCalls);
        assertTrue(port.isScanning());
        assertTrue(filtered.scanning());
        assertEquals(1, filtered.results().size());
        assertEquals("HS01", filtered.results().get(0).modelId());
        assertEquals(2, filtered.totalDiscoveredCount());
    }

    @Test
    public void toggleWithoutPermissionAsksHostAndKeepsScanningOff() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                null,
                NOT_GRANTED);

        viewModel.toggleScan();

        assertEquals(ScanEvent.PERMISSION_REQUIRED, viewModel.events().getValue());
        assertEquals(0, port.startScanCalls);
        assertFalse(viewModel.uiState().getValue().scanning());
        assertFalse(viewModel.uiState().getValue().hasScanned());
    }

    @Test
    public void permissionAnswerStartsTheScanOnlyWhenGranted() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                null,
                NOT_GRANTED);

        viewModel.toggleScan();
        viewModel.onPermissionAnswer(false);
        assertFalse(viewModel.uiState().getValue().scanning());
        assertFalse(viewModel.uiState().getValue().hasScanned());
        assertEquals(0, port.startScanCalls);

        viewModel.onPermissionAnswer(true);
        ScanUiState started = viewModel.uiState().getValue();
        assertTrue(started.scanning());
        assertTrue(started.hasScanned());
        assertEquals(1, port.startScanCalls);
        assertTrue(port.isScanning());
    }

    @Test
    public void toggleScanStopsWhenScanningWithoutRestarting() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        viewModel.toggleScan();
        assertTrue(viewModel.uiState().getValue().scanning());
        assertEquals(1, port.startScanCalls);

        viewModel.toggleScan();
        assertFalse(viewModel.uiState().getValue().scanning());
        assertFalse(port.isScanning());
        assertEquals(1, port.startScanCalls);
    }

    @Test
    public void enterScanStartsAtMostOneScanPerPageInstance() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = scanningViewModel(port);

        // 进页即扫走幂等入口：配置变更重建会再调一次，正在进行的扫描不能被翻停。
        viewModel.enterScan();
        viewModel.enterScan();

        assertEquals(1, port.startScanCalls);
        assertTrue(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());
        assertTrue(port.isScanning());
    }

    @Test
    public void enterScanWithoutPermissionOnlyAsksTheHost() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                null,
                NOT_GRANTED);

        viewModel.enterScan();

        assertEquals(ScanEvent.PERMISSION_REQUIRED, viewModel.events().getValue());
        assertEquals(0, port.startScanCalls);
        assertFalse(viewModel.uiState().getValue().scanning());
    }

    private static ScanViewModel scanningViewModel(FakeTestDevicePort port) {
        return new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port),
                null,
                GRANTED);
    }
}
