package com.yovo.biz_ggec_hs.internal.scan;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 设备表语义：按地址去重、RSSI 窗口平均、TTL 淘汰、lost 唯一来源。
 */
public final class HsNearbyRegistryTest {

    private static final String ADDRESS = "11:22:33:44:55:66";

    private final HsNearbyRegistry registry = new HsNearbyRegistry();

    @Test
    public void keepsOneEntryPerAddressAndRefreshesIt() {
        registry.record(advert(ADDRESS, -60, 1_000L), device(ADDRESS, "HS01", -60), model("HS01"));
        boolean createdAgain = registry.record(
                advert(ADDRESS, -50, 2_000L), device(ADDRESS, "HS02", -50), model("HS02"));

        assertFalse("同地址不得新增第二条记录", createdAgain);
        assertEquals(1, registry.size());
        HsNearbyRegistry.Entry entry = registry.entries().get(0);
        assertEquals("HS02", entry.modelId());
        assertEquals(2_000L, entry.lastSeenAtMillis());
        assertEquals(-55, entry.rssi());
    }

    @Test
    public void averagesRssiOverTheWindowOfEight() {
        // 前两次的 -100 会被窗口淘汰，均值只算最后 8 帧。
        registry.record(advert(ADDRESS, -100, 1L), device(ADDRESS, "HS01", -100), model("HS01"));
        registry.record(advert(ADDRESS, -100, 2L), device(ADDRESS, "HS01", -100), model("HS01"));
        for (int index = 0; index < 8; index++) {
            registry.record(advert(ADDRESS, -50, 10L + index),
                    device(ADDRESS, "HS01", -50), model("HS01"));
        }
        assertEquals(-50, registry.entries().get(0).rssi());
    }

    @Test
    public void ageDoesNotDropEntries() {
        registry.record(advert(ADDRESS, -60, 1_000L), device(ADDRESS, "HS01", -60), model("HS01"));
        assertEquals(1, registry.size());
    }

    @Test
    public void metadataComesFromTheCatalogModel() {
        registry.record(advert(ADDRESS, -60, 1L), device(ADDRESS, "HS01", -60), model("HS01"));
        HsNearbyRegistry.Entry entry = registry.entries().get(0);
        assertEquals(HsDevice.Type.EARBUDS, entry.deviceType());
        assertEquals("HS", entry.projectId());
        assertTrue(entry.connectable());
    }

    @Test
    public void unregisteredModelHasNoTypeOrProject() {
        registry.record(advert(ADDRESS, -60, 1L), device(ADDRESS, "", -60), null);
        HsNearbyRegistry.Entry entry = registry.entries().get(0);
        assertNull("未登记型号没有设备类型", entry.deviceType());
        assertNull("未登记型号没有项目归属", entry.projectId());
        assertEquals("", entry.toDevice().modelId());
        assertEquals("buds", entry.toDevice().radios().bleName());
    }

    @Test
    public void removeAndClearAreTheOnlyOtherWaysOut() {
        registry.record(advert(ADDRESS, -60, 1L), device(ADDRESS, "HS01", -60), model("HS01"));
        assertNotNull(registry.remove(ADDRESS));
        assertNull("同一地址只能被移除一次", registry.remove(ADDRESS));

        registry.record(advert(ADDRESS, -60, 1L), device(ADDRESS, "HS01", -60), model("HS01"));
        registry.clear();
        assertEquals(0, registry.size());
    }

    private static HsRawAdvert advert(String address, int rssi, long observedAtMillis) {
        return new HsRawAdvert(address, "buds", rssi, Collections.emptyList(), new byte[0],
                true, observedAtMillis);
    }

    private static HsScan.Found device(String address, String modelId, int rssi) {
        return new HsScan.Found(modelId, new HsDevice.Radios(address, "buds", "", ""), rssi);
    }

    private static HsDevice.Product model(String modelId) {
        return new HsDevice.Product(modelId, modelId, "HS", HsDevice.Type.EARBUDS,
                Set.of(HsDevice.Capability.BATTERY));
    }
}
