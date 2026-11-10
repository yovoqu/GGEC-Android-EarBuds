package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.data.device.IdleDevicePort;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.testing.device.FakeTestDevicePort;
import com.ggec.earbuds.testing.device.MemoryTestDevicePreferences;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScanViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    @Test
    public void startsIdleAndIdlePortFinishesWithoutFakeResults() {
        IdleDevicePort port = new IdleDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()));

        ScanUiState initial = viewModel.uiState().getValue();
        assertFalse(initial.scanning());
        assertFalse(initial.hasScanned());
        assertEquals(ScanFilterOption.ALL_MODELS, initial.selectedFilterModelId());
        assertEquals(3, initial.filterOptions().size());
        assertTrue(initial.results().isEmpty());
        assertEquals(0, initial.totalDiscoveredCount());

        viewModel.startScan();
        ScanUiState after = viewModel.uiState().getValue();
        assertFalse(after.scanning());
        assertTrue(after.hasScanned());
        assertTrue(after.results().isEmpty());
    }

    @Test
    public void scriptedPortTracksScanningResultsDeduplicationAndCompletion() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()));

        viewModel.startScan();
        assertTrue(viewModel.uiState().getValue().scanning());
        assertTrue(viewModel.uiState().getValue().hasScanned());

        ScanResult first = new ScanResult("HS01", "v1", "HS01", "AA:BB:CC:00:00:01", -50, true);
        ScanResult duplicate = new ScanResult("HS01", "v1", "HS01", "aa:bb:cc:00:00:01", -49, true);
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
                new ConnectDevice(port, new MemoryTestDevicePreferences()),
                loadProfiles);

        ScanUiState initial = viewModel.uiState().getValue();
        assertEquals(List.of(
                new ScanFilterOption(ScanFilterOption.ALL_MODELS, ""),
                new ScanFilterOption("HS01", "HS01"),
                new ScanFilterOption("HS02", "HS02")), initial.filterOptions());

        viewModel.startScan();
        ScanResult hs01 = new ScanResult("HS01", "v1", "HS01-Demo", "AA:BB:CC:00:00:01", -62, false);
        ScanResult hs02 = new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:02", -51, true);
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
    public void keepsOldAndNewGenerationResultsWithSameMacAndUpgradesAdvertisedName() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()),
                new LoadDeviceProfiles(new DeviceProfileCatalogImpl()));

        viewModel.startScan();
        ScanResult hs01Fallback = new ScanResult("HS01", "v1", "HS01", "11:22:33:44:55:66", -70, false);
        ScanResult hs01Named = new ScanResult("HS01", "v1", "GGEC-HS01", "11:22:33:44:55:66", -60, false);
        ScanResult hs02SameMac = new ScanResult("HS02", "v1", "Vidda V11", "11:22:33:44:55:66", -48, true);
        port.emitScanResults(List.of(hs01Fallback, hs01Named, hs02SameMac));

        List<ScanItem> items = viewModel.uiState().getValue().results();
        assertEquals(2, items.size());
        assertEquals("HS01", items.get(0).modelId());
        assertEquals("GGEC-HS01", items.get(0).name());
        assertEquals(-60, items.get(0).rssi());
        assertEquals("HS02", items.get(1).modelId());
        assertEquals("Vidda V11", items.get(1).name());
        assertEquals(-48, items.get(1).rssi());
        assertTrue(items.get(1).paired());
    }

    @Test
    public void scanFailureStopsScanningAndEmitsErrorMessageRes() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()));

        viewModel.startScan();
        assertTrue(viewModel.uiState().getValue().scanning());

        port.emitScanFailure(DevicePort.ScanFailure.BLUETOOTH_DISABLED);
        assertFalse(viewModel.uiState().getValue().scanning());
        assertEquals(Integer.valueOf(R.string.device_scan_error_bluetooth_disabled),
                viewModel.events().getValue());
    }

    @Test
    public void suppressesSubThresholdRssiJitterAndCoalescesSignificantRssiUpdates() {
        FakeTestDevicePort port = new FakeTestDevicePort();
        ManualPublishScheduler scheduler = new ManualPublishScheduler();
        ScanViewModel viewModel = new ScanViewModel(
                new ScanDevices(port),
                new ConnectDevice(port, new MemoryTestDevicePreferences()),
                null,
                scheduler);

        AtomicInteger emissions = new AtomicInteger();
        viewModel.uiState().observeForever(state -> emissions.incrementAndGet());
        assertEquals(1, emissions.get());

        viewModel.startScan();
        assertEquals(2, emissions.get());

        // 新设备出现立即发布一次。
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -60, false)));
        assertEquals(3, emissions.get());
        assertEquals(-60, viewModel.uiState().getValue().results().get(0).rssi());
        assertEquals(0, scheduler.pendingCount());

        // 小于 3 dBm 的信号抖动直接抑制，不触发任何 UI 重绘或定时器。
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -58, false)));
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -62, false)));
        assertEquals(3, emissions.get());
        assertEquals(0, scheduler.pendingCount());

        // 连续两次 >= 3 dBm 的信号变化合并为单次 300ms 延迟刷新。
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -54, false)));
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -49, false)));
        assertEquals(3, emissions.get());
        assertEquals(1, scheduler.pendingCount());

        scheduler.runNext();
        assertEquals(4, emissions.get());
        assertEquals(-49, viewModel.uiState().getValue().results().get(0).rssi());

        // 切换筛选或停扫时立即取消挂起定时器并同步刷新最新快照。
        port.emitScanResults(List.of(
                new ScanResult("HS02", "v1", "Vidda V11", "AA:BB:CC:00:00:09", -42, false)));
        assertEquals(1, scheduler.pendingCount());
        viewModel.selectFilter("HS02");
        assertEquals(0, scheduler.pendingCount());
        assertEquals(5, emissions.get());
        assertEquals(-42, viewModel.uiState().getValue().results().get(0).rssi());
    }

    private static final class ManualPublishScheduler implements ScanViewModel.PublishScheduler {

        private static final class Task {
            private final Runnable runnable;
            private boolean cancelled;

            private Task(Runnable runnable) {
                this.runnable = runnable;
            }
        }

        private final List<Task> tasks = new ArrayList<>();

        @Override
        public Cancellable schedule(Runnable task, long delayMillis) {
            Task scheduled = new Task(task);
            tasks.add(scheduled);
            return () -> {
                scheduled.cancelled = true;
                tasks.remove(scheduled);
            };
        }

        int pendingCount() {
            int count = 0;
            for (Task task : tasks) {
                if (!task.cancelled) {
                    count++;
                }
            }
            return count;
        }

        void runNext() {
            while (!tasks.isEmpty()) {
                Task next = tasks.remove(0);
                if (!next.cancelled) {
                    next.runnable.run();
                    return;
                }
            }
        }
    }
}
