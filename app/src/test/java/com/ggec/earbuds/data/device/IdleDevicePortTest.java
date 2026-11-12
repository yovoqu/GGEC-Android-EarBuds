package com.ggec.earbuds.data.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.port.Subscription;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IdleDevicePortTest {

    @Test
    public void staysIdleWithoutFakeDevicesOrScanResults() {
        IdleDevicePort port = new IdleDevicePort();
        List<DeviceSnapshot> snapshots = new ArrayList<>();
        Subscription sub = port.observe(snapshots::add);

        assertEquals(ConnectionState.IDLE, port.snapshot().connection());
        assertEquals(1, snapshots.size());
        assertFalse(snapshots.get(0).isConnected());

        port.connect("HS01", "v1", "AA:BB:CC:00:00:01", "HS01");
        assertEquals(ConnectionState.IDLE, port.snapshot().connection());

        List<ScanResult> results = new ArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        port.startScanBatch(results::addAll, failure -> {
        }, () -> finished.set(true), ignored -> {
        });
        assertTrue(finished.get());
        assertTrue(results.isEmpty());
        assertFalse(port.isScanning());

        port.close();
        assertFalse(port.isScanning());

        sub.unsubscribe();
    }
}
