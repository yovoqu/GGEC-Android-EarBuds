package com.ggec.earbuds.ui.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class ScanViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private static final ScanPermissionPort GRANTED = () -> true;
    private static final ScanPermissionPort DENIED = () -> false;

    @Test
    public void startsIdleWithFirstCatalogEntryAsFilter() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01", "M02"), GRANTED);

        ScanUiState state = vm.uiState().getValue();
        assertFalse(state.phase().scanning());
        assertEquals("M01", state.selectedFilterModelId());
        assertEquals(2, state.filterOptions().size());
        assertTrue(state.results().isEmpty());
        assertEquals(R.string.device_scan_idle_title, state.emptyTitleRes());
        assertEquals(R.string.device_scan_idle_body, state.emptyBodyRes());
        assertEquals(R.string.device_scan_start, state.actionTextRes());
        assertEquals(List.of(Set.of("M01")), port.filters);
    }

    @Test
    public void emptyCatalogMeansUnrestrictedFilter() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog(), GRANTED);

        ScanUiState state = vm.uiState().getValue();
        assertTrue(state.filterOptions().isEmpty());
        assertEquals("", state.selectedFilterModelId());
        assertEquals(List.of(Set.<String>of()), port.filters);
    }

    @Test
    public void toggleScanWhenPermissionDeniedEmitsPermissionRequiredEvent() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), DENIED);

        vm.toggleScan();
        assertEquals(ScanEvent.PERMISSION_REQUIRED, vm.events().getValue());
        assertFalse(port.isScanning());
    }

    @Test
    public void toggleScanStartsAndStopsScanning() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), GRANTED);

        vm.toggleScan();
        assertTrue(port.isScanning());
        ScanUiState scanningState = vm.uiState().getValue();
        assertTrue(scanningState.phase().scanning());
        assertEquals(R.string.device_scan_searching, scanningState.emptyTitleRes());
        assertEquals(R.string.device_scan_searching_hint, scanningState.emptyBodyRes());
        assertEquals(R.string.device_scan_stop, scanningState.actionTextRes());

        vm.toggleScan();
        assertFalse(port.isScanning());
        ScanUiState stoppedState = vm.uiState().getValue();
        assertTrue(stoppedState.phase() instanceof ScanPhase.Stopped);
        assertEquals(R.string.device_scan_empty_title, stoppedState.emptyTitleRes());
        assertEquals(R.string.device_scan_empty_body, stoppedState.emptyBodyRes());
        assertEquals(R.string.device_scan_start, stoppedState.actionTextRes());
    }

    @Test
    public void selectFilterUpdatesStateWithoutRestartingScan() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01", "M02"), GRANTED);
        vm.toggleScan();
        int stopsAfterStart = port.stopScanCalls;

        vm.selectFilter("M02");

        assertEquals("M02", vm.uiState().getValue().selectedFilterModelId());
        assertEquals(Set.of("M02"), port.filters.get(port.filters.size() - 1));
        assertEquals("切筛选不重启扫描", stopsAfterStart, port.stopScanCalls);
        assertTrue(port.isScanning());
    }

    @Test
    public void selectFilterRejectsEmptyModelId() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), GRANTED);

        assertThrows(IllegalArgumentException.class, () -> vm.selectFilter("  "));
    }

    @Test
    public void scanBatchReachesStateAndConnectMovesToConnecting() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), GRANTED);
        vm.toggleScan();

        port.emitBatch(List.of(new ScanResult("M01", "Buds", "AA:BB:CC:DD:EE:FF", -50)));
        assertEquals(1, vm.uiState().getValue().results().size());

        vm.connect(vm.uiState().getValue().results().get(0));

        assertTrue(vm.uiState().getValue().phase() instanceof ScanPhase.Connecting);
        assertTrue(vm.isConnecting());
        assertFalse(port.isScanning());
        assertEquals(1, port.connects.size());
    }

    @Test
    public void removedDeviceLeavesTheListAndRepublishesState() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), GRANTED);
        vm.toggleScan();
        ScanResult gone = new ScanResult("M01", "Buds A", "aa:bb:cc:dd:ee:01", -50);
        ScanResult kept = new ScanResult("M01", "Buds B", "AA:BB:CC:DD:EE:02", -60);
        port.emitBatch(List.of(gone, kept));
        assertEquals(2, vm.uiState().getValue().results().size());

        port.emitRemoved(gone);

        ScanUiState afterRemoval = vm.uiState().getValue();
        assertEquals("过期设备只摘掉自己那一条", 1, afterRemoval.results().size());
        assertEquals("AA:BB:CC:DD:EE:02", afterRemoval.results().get(0).address());
        assertTrue("摘掉一行不改变扫描阶段", afterRemoval.phase().scanning());

        port.emitRemoved(new ScanResult("M01", "Ghost", "AA:BB:CC:DD:EE:09", -90));
        assertEquals("不在列表里的地址不改状态", 1, vm.uiState().getValue().results().size());
    }

    @Test
    public void failedConnectReturnsToIdlePhaseAndResumesScan() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("M01"), GRANTED);
        vm.toggleScan();
        port.emitBatch(List.of(new ScanResult("M01", "Buds", "AA:BB:CC:DD:EE:FF", -50)));
        vm.connect(vm.uiState().getValue().results().get(0));

        port.settleFailed(DeviceFailure.TIMEOUT);

        assertEquals(new ScanConnectOutcome.Failed(DeviceFailure.TIMEOUT),
                vm.connectOutcomes().getValue());
        assertFalse(vm.isConnecting());
        assertTrue("失败后重新开扫", port.isScanning());
    }

    @Test
    public void recordLaunchModelEmitsOpenRecordWithoutSessionConnect() {
        RecordingLinkPort port = new RecordingLinkPort();
        ScanViewModel vm = viewModel(port, catalog("AMBIQ", "M01"), GRANTED, Set.of("AMBIQ"));
        vm.toggleScan();
        port.emitBatch(List.of(new ScanResult("AMBIQ", "Glasses", "AA:BB:CC:DD:EE:FF", -40)));

        vm.connect(vm.uiState().getValue().results().get(0));

        assertEquals(new ScanConnectOutcome.OpenRecord("AA:BB:CC:DD:EE:FF", "Glasses"),
                vm.connectOutcomes().getValue());
        assertFalse(vm.isConnecting());
        assertTrue(vm.uiState().getValue().phase() instanceof ScanPhase.Idle);
        assertTrue(port.connects.isEmpty());
        assertFalse(port.isScanning());
    }

    @NonNull
    private static ScanViewModel viewModel(@NonNull RecordingLinkPort port,
                                           @NonNull DeviceCapabilityCatalog catalog,
                                           @NonNull ScanPermissionPort permission) {
        return viewModel(port, catalog, permission, Set.of());
    }

    @NonNull
    private static ScanViewModel viewModel(@NonNull RecordingLinkPort port,
                                           @NonNull DeviceCapabilityCatalog catalog,
                                           @NonNull ScanPermissionPort permission,
                                           @NonNull Set<String> recordLaunchModelIds) {
        DeviceSession session = new DeviceSession(port);
        return new ScanViewModel(new ScanDevices(port), new OpenDevice(session), catalog, permission,
                recordLaunchModelIds);
    }

    @NonNull
    private static DeviceCapabilityCatalog catalog(@NonNull String... modelIds) {
        List<DeviceProfileView> models = new ArrayList<>(modelIds.length);
        for (String modelId : modelIds) {
            models.add(new DeviceProfileView(modelId, modelId, List.of()));
        }
        List<DeviceProfileView> fixed = List.copyOf(models);
        return new DeviceCapabilityCatalog() {
            @Override
            public List<DeviceProfileView> models() {
                return fixed;
            }

            @Override
            public Optional<DeviceProfileView> model(String modelId) {
                return fixed.stream().filter(m -> m.modelId().equals(modelId)).findFirst();
            }
        };
    }

    /** 记录筛选下发与扫描启停、并允许测试驱动扫描结果的链路端口。 */
    private static final class RecordingLinkPort implements DeviceLinkPort {

        final List<Set<String>> filters = new ArrayList<>();
        final List<DeviceTarget> connects = new ArrayList<>();
        int stopScanCalls;

        private Consumer<List<ScanResult>> onResults;
        private Consumer<ScanResult> onRemoved;
        private Consumer<ConnectSettlement> settlement;
        private boolean scanning;

        @Override
        public DeviceSnapshot snapshot() {
            return DeviceSnapshot.idle();
        }

        @Override
        public Subscription observe(Consumer<DeviceSnapshot> listener) {
            listener.accept(DeviceSnapshot.idle());
            return () -> {
            };
        }

        @Override
        public void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
            connects.add(target);
            settlement = onSettled;
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void filterModels(@NonNull Set<String> modelIds) {
            filters.add(modelIds);
        }

        @Override
        public void startScanBatch(@NonNull Consumer<List<ScanResult>> results,
                                   @NonNull Consumer<DeviceFailure> onFailed,
                                   @NonNull Runnable onFinished,
                                   @NonNull Consumer<ScanResult> onRemoved) {
            this.onResults = results;
            this.onRemoved = onRemoved;
            this.scanning = true;
        }

        @Override
        public void stopScan() {
            stopScanCalls++;
            scanning = false;
        }

        @Override
        public boolean isScanning() {
            return scanning;
        }

        @Override
        public void write(@NonNull String capabilityId, @NonNull ControlValue value,
                          @NonNull Consumer<DeviceWriteResult> callback) {
        }

        @Override
        public void perform(@NonNull DeviceAction action,
                            @NonNull Consumer<DeviceWriteResult> callback) {
        }

        @Override
        public void close() {
        }

        void emitBatch(@NonNull List<ScanResult> batch) {
            Consumer<List<ScanResult>> sink = onResults;
            if (sink != null) {
                sink.accept(batch);
            }
        }

        void emitRemoved(@NonNull ScanResult removed) {
            Consumer<ScanResult> sink = onRemoved;
            if (sink != null) {
                sink.accept(removed);
            }
        }

        void settleFailed(@NonNull DeviceFailure failure) {
            Consumer<ConnectSettlement> pending = settlement;
            if (pending != null) {
                pending.accept(new ConnectSettlement.Failed(failure));
            }
        }
    }
}
