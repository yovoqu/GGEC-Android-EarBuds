package com.ggec.earbuds.domain.device.scan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ScanResult;

import org.junit.Test;

public final class DiscoveredDevicesTest {

    @Test
    public void eachAdvertisementReplacesRssiIncludingSubThresholdChanges() {
        DiscoveredDevices devices = new DiscoveredDevices();
        ScanResult first = device("Vidda V11", -60);
        assertTrue(devices.accept(first));
        assertTrue(devices.accept(device("Vidda V11", -59)));
        assertTrue(devices.accept(device("Vidda V11", -61)));
        assertEquals(-61, devices.snapshot().get(0).rssi());
        assertFalse(devices.accept(device("Vidda V11", -61)));
        assertEquals(1, devices.size());
    }

    @Test
    public void laterAdvertisingNameReplacesPlaceholderAndIsNotWipedByModelId() {
        DiscoveredDevices devices = new DiscoveredDevices();
        devices.accept(new ScanResult("HS01", "v1", "HS01", "11:22:33:44:55:66", -70));
        devices.accept(new ScanResult("HS01", "v1", "GGEC-HS01", "11:22:33:44:55:66", -60));
        devices.accept(new ScanResult("HS01", "v1", "HS01", "11:22:33:44:55:66", -58));

        ScanResult stored = devices.snapshot().get(0);
        assertEquals("GGEC-HS01", stored.bleName());
        assertEquals(-58, stored.rssi());
    }

    @Test
    public void sameBleAddressKeepsSeparateRowsPerModel() {
        DiscoveredDevices devices = new DiscoveredDevices();
        devices.accept(new ScanResult("HS01", "v1", "GGEC-HS01", "11:22:33:44:55:66", -60));
        devices.accept(new ScanResult("HS02", "v1", "Vidda V11", "11:22:33:44:55:66", -48));
        assertEquals(2, devices.size());
    }

    private static ScanResult device(String name, int rssi) {
        return new ScanResult("HS02", "v1", name, "AA:BB:CC:00:00:09", rssi);
    }
}
