package com.yovo.biz_ggec_wy.internal.scan;

import com.yovo.biz_ggec_wy.api.WyScan;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class WyNearbyRegistryTest {

    @Test
    public void testRssiSlidingAverageAndSorting() {
        WyNearbyRegistry registry = new WyNearbyRegistry();

        // 插入设备 1，三次 RSSI: -60, -70, -80 -> 平均 -70
        registry.update("WY01", "11:22:33:44:55:66", "WY01-A", -60, 1000L);
        registry.update("WY01", "11:22:33:44:55:66", "WY01-A", -70, 1001L);
        registry.update("WY01", "11:22:33:44:55:66", "WY01-A", -80, 1002L);

        // 插入设备 2，两次 RSSI: -40, -50 -> 平均 -45
        registry.update("WY01", "AA:BB:CC:DD:EE:FF", "WY01-B", -40, 1003L);
        registry.update("WY01", "AA:BB:CC:DD:EE:FF", "WY01-B", -50, 1004L);

        List<WyScan.Found> foundDevices = registry.getFoundDevices();
        assertEquals(2, foundDevices.size());

        // 检查排序：设备 2 (-45) > 设备 1 (-70)
        assertEquals("AA:BB:CC:DD:EE:FF", foundDevices.get(0).radios().classicAddress());
        assertEquals(-45, foundDevices.get(0).rssi());

        assertEquals("11:22:33:44:55:66", foundDevices.get(1).radios().classicAddress());
        assertEquals(-70, foundDevices.get(1).rssi());
    }

    @Test
    public void testBuildViewCounts() {
        WyNearbyRegistry registry = new WyNearbyRegistry();
        registry.update("WY01", "AA:BB:CC:DD:EE:01", "WY01-1", -50, 1000L);
        registry.update("WY01", "AA:BB:CC:DD:EE:02", "WY01-2", -60, 1001L);

        WyScan.View view = registry.buildView(true, WyScan.Rule.none());
        assertEquals(2, view.totalInRange());
        assertEquals(true, view.scanning());
        assertEquals(2, (int) view.countsByModel().get("WY01"));
        assertEquals(2, (int) view.countsByProject().get("WY"));
    }
}
