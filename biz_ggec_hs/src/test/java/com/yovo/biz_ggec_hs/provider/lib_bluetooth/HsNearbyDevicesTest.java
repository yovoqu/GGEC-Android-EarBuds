package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.YoHsBroadcast;
import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BlePhy;
import com.yovo.lib_bluetooth.model.ble.BleScanCallbackType;
import com.yovo.lib_bluetooth.model.ble.BleScanRecord;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class HsNearbyDevicesTest {

    private static final String FEATURE_CN = "86103800900000100000fffe";
    private static final String FEATURE_TVS = "1c7b8471e31707bcad54f4c6";
    private static final String FEATURE_AUSTRALIA = "865099000040000009200000";

    @Test
    public void mapsHs01ServiceUuidMarketsAndDisplayName() {
        Lens lens = new Lens();

        HsNearbyDevice cn = lens.map(
                bleResult("11:22:33:44:55:01", -58, List.of(hs01Uuid(FEATURE_CN)), new byte[0], "HS01-CN"));
        assertNotNull(cn);
        assertEquals("HS01", cn.modelId());
        assertEquals("v1", cn.variantId());
        assertEquals("HS01-CN", cn.bleName());
        assertEquals("11:22:33:44:55:01", cn.bleMac());
        assertFalse(cn.bleMac().equalsIgnoreCase("AA:BB:CC:DD:EE:FF"));
        assertEquals(-58, cn.rssi());

        HsNearbyDevice tvs = lens.map(
                bleResult("11:22:33:44:55:02", -61, List.of(hs01Uuid(FEATURE_TVS)), new byte[0], null));
        assertNotNull(tvs);
        assertEquals("HS01", tvs.modelId());
        assertEquals("v2", tvs.variantId());
        assertEquals("HS01", tvs.bleName());

        HsNearbyDevice tvsBondedOnBle = lens.map(
                bleResult("11:22:33:44:55:02", -61, List.of(hs01Uuid(FEATURE_TVS)), new byte[0], null));
        assertNotNull(tvsBondedOnBle);

        HsNearbyDevice au = lens.map(
                bleResult("11:22:33:44:55:03", -64, List.of(hs01Uuid(FEATURE_AUSTRALIA)), new byte[0], "GGEC-AU"));
        assertNotNull(au);
        assertEquals("HS01", au.modelId());
        assertEquals("v3", au.variantId());
        assertEquals("GGEC-AU", au.bleName());
    }

    @Test
    public void mapsHs02CompletePacketAndSplitAdvWithScanResponse() {
        Lens lens = new Lens();

        HsNearbyDevice direct = lens.map(
                bleResult("11:22:33:44:55:10", -49, Collections.emptyList(), hs02Packet("Vidda V11"), null));
        assertNotNull(direct);
        assertEquals("HS02", direct.modelId());
        assertEquals("v1", direct.variantId());
        assertEquals("Vidda V11", direct.bleName());
        assertEquals(-49, direct.rssi());

        HsNearbyDevice incompleteAdv = lens.map(
                bleResult("11:22:33:44:55:11", -55, Collections.emptyList(), manufacturerPacket(0x02), null));
        assertNull(incompleteAdv);

        HsNearbyDevice assembledRsp = lens.map(
                bleResult("11:22:33:44:55:11", -53, Collections.emptyList(), completeName("HS02-Split"), "HS02-Split"));
        assertNotNull(assembledRsp);
        assertEquals("HS02", assembledRsp.modelId());
        assertEquals("v1", assembledRsp.variantId());
        assertEquals("HS02-Split", assembledRsp.bleName());
    }

    @Test
    public void classifiesBothOldAndNewPacketsSimultaneouslyEvenFromSameMacRegardlessOfOrder() {
        Lens lens = new Lens();

        HsNearbyDevice hs02First = lens.map(
                bleResult("22:33:44:55:66:77", -46, Collections.emptyList(), hs02Packet("Vidda New"), "Vidda New"));
        HsNearbyDevice hs01AfterHs02 = lens.map(
                bleResult("22:33:44:55:66:77", -48, List.of(hs01Uuid(FEATURE_CN)), new byte[0], "Legacy Old"));

        assertNotNull(hs02First);
        assertNotNull(hs01AfterHs02);
        assertEquals("HS02", hs02First.modelId());
        assertEquals("Vidda New", hs02First.bleName());
        assertEquals("HS01", hs01AfterHs02.modelId());
        assertEquals("Legacy Old", hs01AfterHs02.bleName());
    }

    @Test
    public void filtersOutNonHeadsetHs02AndUnrelatedBleDevices() {
        Lens lens = new Lens();

        byte[] speakerPacket = concat(completeName("HS02-Speaker"), manufacturerPacket(0x01));
        HsNearbyDevice nonHeadset = lens.map(
                bleResult("11:22:33:44:55:20", -50, Collections.emptyList(), speakerPacket, "HS02-Speaker"));
        assertNull(nonHeadset);

        UUID genericUuid = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
        HsNearbyDevice unrelated = lens.map(
                bleResult("11:22:33:44:55:21", -60, List.of(genericUuid), new byte[]{0x02, 0x01, 0x06}, "Other"));
        assertNull(unrelated);
    }

    @Test
    public void completeHs02PacketIsDecodedOnceWithoutSessionAssembly() {
        CountingSession session = new CountingSession();

        HsNearbyDevice device = HsNearbyDevices.map(
                bleResult("11:22:33:44:55:31", -50, Collections.emptyList(), hs02Packet("Vidda One"), null),
                session);

        assertNotNull(device);
        assertEquals("HS02", device.modelId());
        assertEquals(0, session.observeCount);
    }

    @Test
    public void hs01ServiceUuidIsDecodedOnceWithoutSessionAssembly() {
        CountingSession session = new CountingSession();

        HsNearbyDevice device = HsNearbyDevices.map(
                bleResult("11:22:33:44:55:32", -58, List.of(hs01Uuid(FEATURE_CN)), new byte[0], "HS01-CN"),
                session);

        assertNotNull(device);
        assertEquals("HS01", device.modelId());
        assertEquals(0, session.observeCount);
    }

    @Test
    public void splitHs02FramesUseSessionAssemblyUntilComplete() {
        CountingSession session = new CountingSession();

        HsNearbyDevice incomplete = HsNearbyDevices.map(
                bleResult("11:22:33:44:55:33", -55, Collections.emptyList(), manufacturerPacket(0x02), null),
                session);
        assertNull(incomplete);
        assertEquals(1, session.observeCount);

        HsNearbyDevice assembled = HsNearbyDevices.map(
                bleResult("11:22:33:44:55:33", -53, Collections.emptyList(), completeName("HS02-Split"), "HS02-Split"),
                session);
        assertNotNull(assembled);
        assertEquals("HS02", assembled.modelId());
        assertEquals(2, session.observeCount);
    }

    private static final class CountingSession implements YoHsBroadcastSession {
        private final YoHsBroadcastSession delegate = YoHsBroadcast.openSession();
        private int observeCount;

        @Override
        public HsBroadcastReport observe(HsScanSnapshot snapshot) {
            observeCount++;
            return delegate.observe(snapshot);
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }

    private static final class Lens {
        private final YoHsBroadcastSession session = YoHsBroadcast.openSession();

        private HsNearbyDevice map(BleScanResult result) {
            return HsNearbyDevices.map(result, session);
        }
    }

    private static BleScanResult bleResult(String mac,
                                           int rssi,
                                           List<UUID> uuids,
                                           byte[] rawBytes,
                                           String deviceName) {
        BleDeviceIdentity identity = new BleDeviceIdentity(
                new BleDeviceId(mac),
                deviceName == null ? "" : deviceName,
                1000L);
        BleScanRecord record = new BleScanRecord(
                -1,
                uuids,
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Integer.MIN_VALUE,
                deviceName,
                rawBytes);
        return new BleScanResult(
                identity,
                rssi,
                record,
                1000L,
                true,
                true,
                BlePhy.LE_1M,
                BlePhy.UNKNOWN,
                BleScanResult.SID_NOT_PRESENT,
                BleScanResult.TX_POWER_NOT_PRESENT,
                BleScanResult.PERIODIC_INTERVAL_NOT_PRESENT,
                BleScanCallbackType.ALL_MATCHES);
    }

    private static UUID hs01Uuid(String featureCode) {
        byte[] prefix = hexToBytes(featureCode.substring(0, 18));
        byte[] uuidBytes = new byte[16];
        uuidBytes[0] = (byte) 0xFF;
        uuidBytes[1] = (byte) 0xEE;
        uuidBytes[2] = (byte) 0xDD;
        uuidBytes[3] = (byte) 0xCC;
        uuidBytes[4] = (byte) 0xBB;
        uuidBytes[5] = (byte) 0xAA;
        uuidBytes[6] = 0x02;
        for (int index = 0; index < prefix.length; index++) {
            uuidBytes[15 - index] = prefix[index];
        }
        ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] hs02Packet(String name) {
        return concat(completeName(name), manufacturerPacket(0x02));
    }

    private static byte[] completeName(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] field = new byte[nameBytes.length + 2];
        field[0] = (byte) (nameBytes.length + 1);
        field[1] = 0x09;
        System.arraycopy(nameBytes, 0, field, 2, nameBytes.length);
        return field;
    }

    private static byte[] manufacturerPacket(int deviceType) {
        byte[] manufacturer = new byte[]{
                (byte) 0xFF, (byte) 0xFF,
                0x48, 0x43,
                0x01,
                (byte) deviceType,
                0x12,
                0x01, 0x00,
                0x00, 0x00,
                0x0A, 0x06,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC,
                (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x0B, 0x01, 0x00
        };
        byte[] packet = new byte[manufacturer.length + 2];
        packet[0] = (byte) (manufacturer.length + 1);
        packet[1] = (byte) 0xFF;
        System.arraycopy(manufacturer, 0, packet, 2, manufacturer.length);
        return packet;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
