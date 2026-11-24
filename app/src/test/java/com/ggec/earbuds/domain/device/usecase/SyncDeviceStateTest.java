package com.ggec.earbuds.domain.device.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.Subscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SyncDeviceStateTest {

    @Test
    public void executeDelegatesToPortSyncState() {
        FakeLinkPort port = new FakeLinkPort();
        SyncDeviceState useCase = new SyncDeviceState(port);

        AtomicBoolean settled = new AtomicBoolean(false);
        useCase.execute(result -> {
            assertTrue(result instanceof DeviceWriteResult.Ok);
            settled.set(true);
        });

        assertTrue(port.syncCalled);
        assertTrue(settled.get());
    }

    private static final class FakeLinkPort implements DeviceLinkPort {
        boolean syncCalled = false;

        @NonNull
        @Override
        public DeviceSnapshot snapshot() {
            return DeviceSnapshot.idle();
        }

        @NonNull
        @Override
        public Subscription observe(@NonNull Consumer<DeviceSnapshot> listener) {
            return () -> {};
        }

        @Override
        public void connect(@NonNull DeviceTarget target, @NonNull Consumer<ConnectSettlement> onSettled) {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void filterModels(@NonNull Set<String> modelIds) {
        }

        @Override
        public void startScanBatch(@NonNull Consumer<List<ScanResult>> onResults,
                                   @NonNull Consumer<com.ggec.earbuds.domain.device.model.DeviceFailure> onFailed,
                                   @NonNull Runnable onFinished,
                                   @NonNull Consumer<ScanResult> onRemoved) {
        }

        @Override
        public void stopScan() {
        }

        @Override
        public boolean isScanning() {
            return false;
        }

        @Override
        public void write(@NonNull String capabilityId, @NonNull ControlValue value,
                          @NonNull Consumer<DeviceWriteResult> callback) {
        }

        @Override
        public void perform(@NonNull DeviceAction action, @NonNull Consumer<DeviceWriteResult> callback) {
            if (action instanceof DeviceAction.SyncDeviceState) {
                syncCalled = true;
                callback.accept(new DeviceWriteResult.Ok());
            }
        }

        @Override
        public void syncState(@NonNull Consumer<DeviceWriteResult> callback) {
            syncCalled = true;
            callback.accept(new DeviceWriteResult.Ok());
        }

        @Override
        public void close() {
        }
    }
}
