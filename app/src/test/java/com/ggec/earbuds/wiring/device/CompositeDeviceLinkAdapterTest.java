package com.ggec.earbuds.wiring.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CompositeDeviceLinkAdapterTest {

    private static final class RecordingLinkPort implements DeviceLinkPort {
        private final FakeDeviceLinkPort delegate = new FakeDeviceLinkPort();
        private Set<String> lastFilterModels = Set.of();

        @Override
        public DeviceSnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public Subscription observe(Consumer<DeviceSnapshot> listener) {
            return delegate.observe(listener);
        }

        @Override
        public void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled) {
            delegate.connect(target, onSettled);
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
        }

        @Override
        public void filterModels(Set<String> modelIds) {
            this.lastFilterModels = modelIds;
            delegate.filterModels(modelIds);
        }

        public Set<String> lastFilterModels() {
            return lastFilterModels;
        }

        public List<DeviceTarget> connects() {
            return delegate.connects;
        }

        @Override
        public void startScanBatch(Consumer<List<ScanResult>> onResults,
                                   Consumer<DeviceFailure> onFailed,
                                   Runnable onFinished,
                                   Consumer<ScanResult> onRemoved) {
            delegate.startScanBatch(onResults, onFailed, onFinished, onRemoved);
        }

        @Override
        public void stopScan() {
            delegate.stopScan();
        }

        @Override
        public boolean isScanning() {
            return delegate.isScanning();
        }

        @Override
        public void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult> callback) {
            delegate.write(capabilityId, value, callback);
        }

        @Override
        public void perform(DeviceAction action, Consumer<DeviceWriteResult> callback) {
            delegate.perform(action, callback);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class TestDriver implements DeviceDriver {
        private final Set<String> models;
        private final RecordingLinkPort link = new RecordingLinkPort();
        private boolean closed;

        TestDriver(Set<String> models) {
            this.models = models;
        }

        @Override
        public boolean supportsModel(String modelId) {
            return models.contains(modelId);
        }

        @Override
        public Set<String> supportedModels() {
            return models;
        }

        @Override
        public DeviceCapabilityCatalog catalog() {
            return new DeviceCapabilityCatalog() {
                @Override
                public List<DeviceProfileView> models() {
                    List<DeviceProfileView> list = new ArrayList<>();
                    for (String m : models) {
                        list.add(new DeviceProfileView(m, m, List.of()));
                    }
                    return list;
                }

                @Override
                public Optional<DeviceProfileView> model(String modelId) {
                    return models.contains(modelId)
                            ? Optional.of(new DeviceProfileView(modelId, modelId, List.of()))
                            : Optional.empty();
                }
            };
        }

        @Override
        public DeviceLinkPort link() {
            return link;
        }

        @Override
        public RecordPort record() {
            return new RecordPort() {
                @Override
                public void startStreamRecord(String customFileName, File customSaveDir, @NonNull StreamRecordObserver observer) {}

                @Override
                public void stopStreamRecord(@NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public boolean isStreamRecording() { return false; }

                @Override
                public void startDeviceRecord(@NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public void stopDeviceRecord(@NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public void refreshDeviceRecords(@NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {}

                @Override
                public void cancelSyncDeviceRecord(@NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<Boolean> onComplete) { onComplete.accept(true); }

                @Override
                public List<SavedRecordItem> listLocalSavedFiles() { return List.of(); }

                @Override
                public boolean deleteLocalSavedFile(@NonNull String filePath) { return false; }

                @NonNull
                @Override
                public java.util.Set<com.ggec.earbuds.domain.device.model.RecordMode> supportedModes() {
                    return java.util.Set.of(com.ggec.earbuds.domain.device.model.RecordMode.STREAM);
                }
            };
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    public void filterModelsSwitchesActiveScanningDriver() {
        TestDriver hsDriver = new TestDriver(Set.of("HS01", "HS02"));
        TestDriver wyDriver = new TestDriver(Set.of("WY01"));

        CompositeDeviceLinkAdapter composite = new CompositeDeviceLinkAdapter(List.of(hsDriver, wyDriver));

        List<List<ScanResult>> resultsReceived = new ArrayList<>();
        composite.startScanBatch(
                resultsReceived::add,
                failure -> {},
                () -> {},
                removed -> {}
        );

        assertTrue("HS driver should be scanning initially for HS02", hsDriver.link.isScanning());
        assertFalse("WY driver should be idle", wyDriver.link.isScanning());

        // 用户在下拉菜单切换至 WY01
        composite.filterModels(Set.of("WY01"));

        assertFalse("HS driver should be stopped", hsDriver.link.isScanning());
        assertTrue("WY driver should now be scanning", wyDriver.link.isScanning());
        assertEquals("WY driver should receive filter rule", Set.of("WY01"), wyDriver.link.lastFilterModels());

        // 停止扫描
        composite.stopScan();
        assertFalse(wyDriver.link.isScanning());
    }

    @Test
    public void connectRoutesToCorrectDriverByTargetModelId() {
        TestDriver hsDriver = new TestDriver(Set.of("HS01", "HS02"));
        TestDriver wyDriver = new TestDriver(Set.of("WY01"));

        CompositeDeviceLinkAdapter composite = new CompositeDeviceLinkAdapter(List.of(hsDriver, wyDriver));

        DeviceTarget wyTarget = new DeviceTarget(
                new DeviceId("WY01", "11:22:33:44:55:66"),
                new DeviceIdentity("", "11:22:33:44:55:66", "WY01-Test")
        );

        AtomicBoolean settled = new AtomicBoolean(false);
        composite.connect(wyTarget, outcome -> settled.set(true));

        assertEquals(1, wyDriver.link.connects().size());
        assertEquals("WY01", wyDriver.link.connects().get(0).id().modelId());
    }
}
