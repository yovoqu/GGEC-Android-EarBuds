package com.ggec.earbuds.wiring.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class DeviceGatewayTest {

    private static final List<String> HS_MODELS = List.of("M01", "M02");
    private static final List<String> WY_MODELS = List.of("W01");

    @Test
    public void unknownModelFailsWithDeviceNotFound() {
        DeviceGateway gateway = gateway();
        List<ConnectSettlement> settled = new ArrayList<>();

        gateway.connect(targetOf("UNKNOWN"), settled::add);

        assertTrue(settled.get(0) instanceof ConnectSettlement.Failed failed
                && failed.failure() == DeviceFailure.DEVICE_NOT_FOUND);
    }

    @Test
    public void writeAndPerformWithoutConnectionFailNotReady() {
        DeviceGateway gateway = gateway();
        List<DeviceWriteResult> writes = new ArrayList<>();
        List<DeviceWriteResult> actions = new ArrayList<>();

        gateway.write("EQ", new ControlValue.Toggle(true), writes::add);
        gateway.perform(new DeviceAction.Reboot(), actions::add);

        assertTrue(writes.get(0) instanceof DeviceWriteResult.Failed failed
                && failed.failure() == DeviceFailure.NOT_READY);
        assertTrue(actions.get(0) instanceof DeviceWriteResult.Failed failed
                && failed.failure() == DeviceFailure.NOT_READY);
    }

    @Test
    public void writeWithProgressWithoutConnectionFailsNotReady() {
        DeviceGateway gateway = gateway();
        List<DeviceWriteResult> results = new ArrayList<>();
        int[] started = {0};

        gateway.writeWithProgress("EQ", new ControlValue.Toggle(true),
                () -> started[0]++, results::add);

        assertTrue(results.get(0) instanceof DeviceWriteResult.Failed failed
                && failed.failure() == DeviceFailure.NOT_READY);
        assertEquals("未连接不进入中间阶段", 0, started[0]);
    }

    @Test
    public void writeWithProgressForwardsStartedAndResultToConnectedEndpoint() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        DeviceGateway gateway = gateway(hs);
        gateway.connect(targetOf("M01"), settlement -> {
        });

        int[] started = {0};
        List<DeviceWriteResult> results = new ArrayList<>();
        gateway.writeWithProgress("ANC", new ControlValue.Toggle(true),
                () -> started[0]++, results::add);

        assertEquals("ANC", hs.link.lastWriteCapability);
        assertTrue("中间阶段必须被网关转发到端点", hs.link.lastOnStarted != null);
        hs.link.lastOnStarted.run();
        assertEquals("onStarted 已透传到调用方", 1, started[0]);
        hs.link.lastWriteCallback.accept(new DeviceWriteResult.Ok());
        assertEquals(1, results.size());
    }

    @Test
    public void snapshotIsIdleWithoutConnection() {
        DeviceGateway gateway = gateway();

        assertEquals(DeviceSnapshot.idle(), gateway.snapshot());
    }

    @Test
    public void connectRoutesToTheEndpointOwningTheModel() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);

        gateway.connect(targetOf("W01"), settlement -> {
        });

        assertEquals(1, wy.link.connects.size());
        assertEquals(0, hs.link.connects.size());
        assertEquals("W01", wy.link.connects.get(0).id().modelId());
    }

    @Test
    public void scanBatchStartsEveryEndpointAndMergesByAddress() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);
        List<List<ScanResult>> batches = new ArrayList<>();
        int[] finished = {0};

        gateway.startScanBatch(batches::add, failure -> {
        }, () -> finished[0]++, removed -> {
        });

        assertEquals(1, hs.link.startScanCalls);
        assertEquals(1, wy.link.startScanCalls);

        ScanResult shared = new ScanResult("W01", "Buds", "AA:BB:CC:DD:EE:FF", -40);
        hs.link.emitBatch(List.of(new ScanResult("M01", "Buds", "AA:BB:CC:DD:EE:01", -50),
                shared));
        wy.link.emitBatch(List.of(shared));

        assertEquals(2, batches.get(batches.size() - 1).size());

        hs.link.finish();
        assertEquals("一个端点结束不算整批结束", 0, finished[0]);
        wy.link.finish();
        assertEquals(1, finished[0]);
    }

    @Test
    public void filterModelsSwitchesActiveEndpointsAndIsolatesHardware() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);
        List<List<ScanResult>> results = new ArrayList<>();
        gateway.startScanBatch(results::add, failure -> {
        }, () -> {
        }, removed -> {
        });

        // 初始不限筛选时，两者均开扫
        assertEquals(1, hs.link.startScanCalls);
        assertEquals(1, wy.link.startScanCalls);

        // 用户切换筛选为 HS02 (M02)
        gateway.filterModels(Set.of("M02"));

        assertEquals(List.of(Set.of("M02")), hs.link.filters);
        assertEquals("HS 保持扫描", 0, hs.link.stopScanCalls);
        assertEquals("非 M02 驱动的 WY 经典蓝牙停止扫描，杜绝 SPP 配对设备与经典扫描侵入", 1, wy.link.stopScanCalls);

        // 切换为 WY01 (W01)
        gateway.filterModels(Set.of("W01"));
        assertEquals("HS 停止扫描", 1, hs.link.stopScanCalls);
        assertEquals(List.of(Set.of("W01")), wy.link.filters);
        assertEquals("WY 重新开扫", 2, wy.link.startScanCalls);
    }

    @Test
    public void filterModelsRemovesInactiveEndpointBatchesImmediately() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);
        List<List<ScanResult>> results = new ArrayList<>();
        gateway.startScanBatch(results::add, failure -> {
        }, () -> {
        }, removed -> {
        });

        ScanResult hsDev = new ScanResult("M01", "HS-Buds", "AA:BB:CC:11:22:01", -50);
        ScanResult wyBonded = new ScanResult("W01", "WY-Paired", "AA:BB:CC:11:22:02", 0);
        hs.link.emitBatch(List.of(hsDev));
        wy.link.emitBatch(List.of(wyBonded));

        assertEquals(2, results.get(results.size() - 1).size());

        // 切换为 HS 机型
        gateway.filterModels(Set.of("M01"));

        // 切换后，属于 WY 的已配对设备立刻被剔除，结果中只保留 HS 设备
        List<ScanResult> latest = results.get(results.size() - 1);
        assertEquals(1, latest.size());
        assertEquals("M01", latest.get(0).modelId());
    }

    @Test
    public void snapshotsAreForwardedOnlyForTheConnectedTransport() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);
        List<DeviceSnapshot> seen = new ArrayList<>();
        gateway.observe(seen::add);
        gateway.connect(targetOf("M01"), settlement -> {
        });
        seen.clear();

        wy.link.emit(snapshotOf(ConnectionState.READY, "W01"));
        assertEquals(0, seen.size());

        hs.link.emit(snapshotOf(ConnectionState.READY, "M01"));
        assertEquals(1, seen.size());
    }

    @Test
    public void recordPortRoutesToConnectedEndpointAndFailsWhenUnconnected() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);

        assertTrue(gateway.supportedModes().isEmpty());
        List<RecordOutcome> unconnected = new ArrayList<>();
        gateway.stopStreamRecord(unconnected::add);
        assertTrue(unconnected.get(0) instanceof RecordOutcome.Failed failed
                && failed.failure() == RecordFailure.NOT_CONNECTED);

        gateway.connect(targetOf("M01"), settlement -> {
        });

        assertEquals(Set.of(RecordMode.STREAM, RecordMode.LOCAL), gateway.supportedModes());
        assertTrue(gateway.deleteLocalSavedFile("path") instanceof RecordOutcome.Ok);
        assertEquals(1, hs.record.deleteLocalCalls);
        assertEquals(0, wy.record.deleteLocalCalls);
    }

    @Test
    public void disconnectClearsRouting() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        DeviceGateway gateway = gateway(hs);

        gateway.connect(targetOf("M01"), settlement -> {
        });
        gateway.disconnect();

        assertEquals(1, hs.link.disconnects);
        assertEquals(DeviceSnapshot.idle(), gateway.snapshot());
        assertTrue(gateway.supportedModes().isEmpty());
    }

    @Test
    public void stopScanStopsEveryEndpoint() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);
        gateway.startScanBatch(results -> {
        }, failure -> {
        }, () -> {
        }, removed -> {
        });

        gateway.stopScan();

        assertEquals(1, hs.link.stopScanCalls);
        assertEquals(1, wy.link.stopScanCalls);
        assertFalse(gateway.isScanning());
    }

    @Test
    public void closeUnsubscribesAndClosesEveryEndpoint() {
        FakeEndpoint hs = new FakeEndpoint(DeviceTransport.BLE, HS_MODELS);
        FakeEndpoint wy = new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS);
        DeviceGateway gateway = gateway(hs, wy);

        gateway.close();

        assertTrue(hs.closed);
        assertTrue(wy.closed);
        assertEquals("解订阅后端点不再有观察者", 0, hs.link.observerCount());
        assertEquals(0, wy.link.observerCount());
    }

    @NonNull
    private static DeviceGateway gateway() {
        return gateway(new FakeEndpoint(DeviceTransport.BLE, HS_MODELS),
                new FakeEndpoint(DeviceTransport.CLASSIC, WY_MODELS));
    }

    @NonNull
    private static DeviceGateway gateway(@NonNull DeviceEndpoint... endpoints) {
        return new DeviceGateway(new DeviceEndpoints(List.of(endpoints)));
    }

    @NonNull
    private static DeviceTarget targetOf(@NonNull String modelId) {
        return new DeviceTarget(new DeviceId(modelId, "AA:BB:CC:DD:EE:FF"),
                new DeviceIdentity("Buds", "11:22:33:44:55:66", "Buds"));
    }

    @NonNull
    private static DeviceSnapshot snapshotOf(@NonNull ConnectionState connection,
                                             @NonNull String modelId) {
        return new DeviceSnapshot(connection, modelId,
                BatterySnapshot.unknown(), WearState.UNKNOWN, "",
                "AA:BB:CC:DD:EE:FF", "Buds", "", "",
                DeviceSnapshot.UNKNOWN_EAR_COLOR, new DeviceState(Map.of()),
                Optional.empty(), List.of());
    }

    /** 一个端点 = 一种传输 + 该传输的目录、链路、录音端口。 */
    private static final class FakeEndpoint implements DeviceEndpoint {

        private final DeviceTransport transport;
        private final List<DeviceProfileView> models;
        final FakeLink link = new FakeLink();
        final FakeRecord record = new FakeRecord();
        boolean closed;

        FakeEndpoint(@NonNull DeviceTransport transport, @NonNull List<String> modelIds) {
            this.transport = transport;
            List<DeviceProfileView> profiles = new ArrayList<>(modelIds.size());
            for (String modelId : modelIds) {
                profiles.add(new DeviceProfileView(modelId, modelId, List.of()));
            }
            this.models = List.copyOf(profiles);
        }

        @NonNull
        @Override
        public DeviceTransport transport() {
            return transport;
        }

        @NonNull
        @Override
        public DeviceCapabilityCatalog catalog() {
            return new DeviceCapabilityCatalog() {
                @Override
                public List<DeviceProfileView> models() {
                    return models;
                }

                @Override
                public Optional<DeviceProfileView> model(String modelId) {
                    return models.stream().filter(m -> m.modelId().equals(modelId)).findFirst();
                }
            };
        }

        @NonNull
        @Override
        public DeviceLinkPort link() {
            return link;
        }

        @NonNull
        @Override
        public RecordPort record() {
            return record;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 记录调用、由测试驱动快照与扫描回调的链路端口。 */
    private static final class FakeLink implements DeviceLinkPort {

        final List<DeviceTarget> connects = new ArrayList<>();
        final List<Set<String>> filters = new ArrayList<>();
        final List<Consumer<DeviceSnapshot>> observers = new ArrayList<>();
        int startScanCalls;
        int stopScanCalls;
        int disconnects;
        boolean scanning;

        private Consumer<List<ScanResult>> onResults;
        private Runnable onFinished;
        private DeviceSnapshot current = DeviceSnapshot.idle();

        @NonNull
        @Override
        public DeviceSnapshot snapshot() {
            return current;
        }

        @NonNull
        @Override
        public Subscription observe(@NonNull Consumer<DeviceSnapshot> listener) {
            observers.add(listener);
            listener.accept(current);
            return () -> observers.remove(listener);
        }

        @Override
        public void connect(@NonNull DeviceTarget target,
                            @NonNull Consumer<ConnectSettlement> onSettled) {
            connects.add(target);
        }

        @Override
        public void disconnect() {
            disconnects++;
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
            startScanCalls++;
            this.onResults = results;
            this.onFinished = onFinished;
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

        String lastWriteCapability;
        Runnable lastOnStarted;
        Consumer<DeviceWriteResult> lastWriteCallback;

        @Override
        public void writeWithProgress(@NonNull String capabilityId, @NonNull ControlValue value,
                                      @NonNull Runnable onStarted,
                                      @NonNull Consumer<DeviceWriteResult> callback) {
            lastWriteCapability = capabilityId;
            lastOnStarted = onStarted;
            lastWriteCallback = callback;
        }

        @Override
        public void perform(@NonNull DeviceAction action,
                            @NonNull Consumer<DeviceWriteResult> callback) {
        }

        @Override
        public void close() {
            observers.clear();
        }

        int observerCount() {
            return observers.size();
        }

        void emitBatch(@NonNull List<ScanResult> batch) {
            Consumer<List<ScanResult>> sink = onResults;
            if (sink != null) {
                sink.accept(batch);
            }
        }

        void finish() {
            Runnable sink = onFinished;
            if (sink != null) {
                sink.run();
            }
        }

        void emit(@NonNull DeviceSnapshot snapshot) {
            current = snapshot;
            for (Consumer<DeviceSnapshot> observer : List.copyOf(observers)) {
                observer.accept(snapshot);
            }
        }
    }

    /** 记录调用的录音端口：录音能力用本地模式集合表达。 */
    private static final class FakeRecord implements RecordPort {

        int deleteLocalCalls;

        @NonNull
        @Override
        public Set<RecordMode> supportedModes() {
            return Set.of(RecordMode.STREAM, RecordMode.LOCAL);
        }

        @Override
        public void startStreamRecord(@NonNull StreamRecordObserver observer) {
        }

        @Override
        public void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public boolean isStreamRecording() {
            return false;
        }

        @Override
        public void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void syncDeviceRecord(@NonNull String fileId,
                                     @NonNull SyncProgressObserver observer) {
        }

        @Override
        public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void deleteDeviceRecord(@NonNull String fileId,
                                       @NonNull Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @NonNull
        @Override
        public List<SavedRecordItem> listLocalSavedFiles() {
            return List.of();
        }

        @NonNull
        @Override
        public RecordOutcome deleteLocalSavedFile(@NonNull String filePath) {
            deleteLocalCalls++;
            return RecordOutcome.ok();
        }
    }
}
