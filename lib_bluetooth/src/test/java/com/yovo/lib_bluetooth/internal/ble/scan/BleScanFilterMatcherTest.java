package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanConfig;
import com.yovo.lib_bluetooth.model.ble.BleScanFilter;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BLE-008 多维 ScanFilter 硬件+软件过滤匹配校验：
 * 验证 MAC / Name 精确与子串 / ServiceUuid(+Mask) / SolicitationUuid(+Mask) / ServiceData(+Mask)
 * / ManufacturerData(+Mask) / MinRssi / RequireConnectable，以及 Filter 间 OR 与字段间 AND。
 */
public class BleScanFilterMatcherTest {

    private static final UUID SVC_MAIN = UUID.fromString("12345678-0000-1000-8000-00805f9b34fb");
    private static final UUID SVC_MASK = UUID.fromString("FFFF0000-0000-1000-8000-00805f9b34fb");
    private static final UUID SVC_CAND = UUID.fromString("12349999-0000-1000-8000-00805f9b34fb");
    private static final UUID SOLICIT_UUID = UUID.fromString("00001812-0000-1000-8000-00805f9b34fb");
    private static final UUID DATA_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");

    @Test
    public void matchesAllFilterDimensionsAndWildcardMasks() {
        BleScanResult candidate = buildResult(
                "AA:BB:CC:DD:EE:01",
                "YOVO-EarBuds-L",
                -70,
                true,
                SVC_CAND,
                SOLICIT_UUID,
                DATA_UUID, new byte[]{0x10, 0x22, 0x33},
                0x004C, new byte[]{0x01, 0x09, (byte) 0xAB});

        BleScanFilter fullMatch = BleScanFilter.builder()
                .deviceAddress("aa:bb:cc:dd:ee:01")
                .deviceName("YOVO-EarBuds-L")
                .deviceNameContains("EarBuds")
                .serviceUuid(SVC_MAIN, SVC_MASK)
                .solicitationUuid(SOLICIT_UUID)
                .serviceData(DATA_UUID, new byte[]{0x10, 0x00, 0x33}, new byte[]{(byte) 0xFF, 0x00, (byte) 0xFF})
                .manufacturerData(0x004C, new byte[]{0x01, 0x00, (byte) 0xAB}, new byte[]{(byte) 0xFF, 0x00, (byte) 0xFF})
                .minRssi(-70)
                .requireConnectable(true)
                .build();

        assertTrue(YoBleScanFilterMatcher.matchesFilter(fullMatch, candidate));

        // 任一 AND 字段不满足则整体拒绝（如 RSSI -71 < -70）
        BleScanResult weakRssi = buildResult(
                "AA:BB:CC:DD:EE:01", "YOVO-EarBuds-L", -71, true,
                SVC_CAND, SOLICIT_UUID, DATA_UUID, new byte[]{0x10, 0x22, 0x33},
                0x004C, new byte[]{0x01, 0x09, (byte) 0xAB});
        assertFalse(YoBleScanFilterMatcher.matchesFilter(fullMatch, weakRssi));

        // 非可连接设备在 requireConnectable=true 时拒收
        BleScanResult nonConn = buildResult(
                "AA:BB:CC:DD:EE:01", "YOVO-EarBuds-L", -50, false,
                SVC_CAND, SOLICIT_UUID, DATA_UUID, new byte[]{0x10, 0x22, 0x33},
                0x004C, new byte[]{0x01, 0x09, (byte) 0xAB});
        assertFalse(YoBleScanFilterMatcher.matchesFilter(fullMatch, nonConn));
    }

    @Test
    public void multipleFilters_followOrAcrossFiltersAndWithinFilter() {
        BleScanFilter f1 = BleScanFilter.builder()
                .deviceNameContains("EarBuds")
                .minRssi(-65)
                .build();
        BleScanFilter f2 = BleScanFilter.builder()
                .manufacturerData(0x0059, new byte[]{0x0A, 0x0B})
                .build();

        BleScanConfig cfg = BleScanConfig.builder()
                .addFilter(f1)
                .addFilter(f2)
                .build();

        BleScanResult matchF1Only = buildResult("11:22:33:44:55:01", "MyEarBuds", -60, true,
                SVC_CAND, SOLICIT_UUID, DATA_UUID, new byte[]{1}, 0x0001, new byte[]{0});
        assertTrue(YoBleScanFilterMatcher.matchesConfig(cfg, matchF1Only));

        BleScanResult matchF2Only = buildResult("11:22:33:44:55:02", "Other", -85, true,
                SVC_CAND, SOLICIT_UUID, DATA_UUID, new byte[]{1}, 0x0059, new byte[]{0x0A, 0x0B});
        assertTrue(YoBleScanFilterMatcher.matchesConfig(cfg, matchF2Only));

        // 虽名含 EarBuds 但 RSSI=-80（不满足 f1 AND）且厂商不匹配 f2 -> false
        BleScanResult failBoth = buildResult("11:22:33:44:55:03", "MyEarBuds", -80, true,
                SVC_CAND, SOLICIT_UUID, DATA_UUID, new byte[]{1}, 0x0002, new byte[]{0});
        assertFalse(YoBleScanFilterMatcher.matchesConfig(cfg, failBoth));
    }

    private static BleScanResult buildResult(String mac, String name, int rssi, boolean connectable,
                                             UUID svcUuid, UUID solicitUuid,
                                             UUID svcDataUuid, byte[] svcData,
                                             int manuId, byte[] manuData) {
        Map<UUID, byte[]> sd = new LinkedHashMap<>();
        sd.put(svcDataUuid, svcData);
        Map<Integer, byte[]> md = new LinkedHashMap<>();
        md.put(manuId, manuData);
        BleScanRecord rec = new BleScanRecord(
                0x06,
                Collections.singletonList(svcUuid),
                Collections.singletonList(solicitUuid),
                sd, md, -10, name, new byte[]{1, 2});
        return new BleScanResult(
                new BleDeviceIdentity(new BleDeviceId(mac), name, 1000L),
                rssi, rec, 1000L, connectable, true, BlePhy.LE_1M, BlePhy.UNKNOWN,
                0xFF, 127, 0, BleScanCallbackType.ALL_MATCHES);
    }
}
