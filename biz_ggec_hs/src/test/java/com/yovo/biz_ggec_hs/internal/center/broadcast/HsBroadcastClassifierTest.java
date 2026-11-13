package com.yovo.biz_ggec_hs.internal.center.broadcast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * 广播识别：代际判定、单帧优先级、跨帧组装与「认不出就是认不出」。
 * 这层只做识别，不暴露给模块外。
 */
public class HsBroadcastClassifierTest {
    private static final String CN_FEATURE_CODE = "86103800900000100000fffe";

    @Test
    public void classifiesHs01FromServiceUuid() {
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.singletonList(cnUuid()),
                new byte[0],
                "HS01-Demo"));

        assertEquals(HsBroadcastReport.Status.IDENTIFIED, report.status());
        assertTrue(report.identity() instanceof Hs01Identity);
        Hs01Identity identity = (Hs01Identity) report.identity();
        assertEquals(HsGeneration.HS01, identity.generation());
        assertTrue(identity.headset());
        assertEquals(CN_FEATURE_CODE, identity.featureCode());
        assertEquals("02", identity.color());
        assertEquals("AA:BB:CC:DD:EE:FF", identity.classicAddress());
        assertEquals(HsMarket.CN, identity.marketId());
    }

    @Test
    public void sameBleAddressUsesDifferentClassicStrategies() {
        String ble = "11:22:33:44:55:66";
        Hs01Identity hs01 = (Hs01Identity) HsBroadcastRuntime.classify(HsScanSnapshot.of(
                ble, Collections.singletonList(cnUuid()), new byte[0], "HS01-Demo")).identity();
        Hs02Identity hs02 = identifiedHs02(HsBroadcastRuntime.classify(HsScanSnapshot.of(
                ble, Collections.emptyList(), hs02Packet("Vidda V11", "01:02:03:04:05:06"), null)));

        assertEquals("AA:BB:CC:DD:EE:FF", hs01.classicAddress());
        assertEquals("01:02:03:04:05:06", hs02.classicAddress());
        assertFalse(ble.equals(hs01.classicAddress()));
        assertFalse(hs01.classicAddress().equals(hs02.classicAddress()));
    }

    @Test
    public void classifiesHs02FromOneCompletePacket() {
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                hs02Packet("Vidda V11"),
                null));

        Hs02Identity identity = identifiedHs02(report);
        assertEquals(Hs02Identity.DEVICE_TYPE_HEADSET, identity.deviceType());
        assertTrue(identity.headset());
        assertEquals(1, identity.productModel());
        assertEquals("00", identity.color());
        assertEquals("AA:BB:CC:DD:EE:FF", identity.classicAddress());
        assertEquals(HsMarket.CN, identity.marketId());
        assertEquals("Vidda V11", identity.name());
        assertTrue(identity.pairable());
        assertFalse(identity.reconnect());
    }

    @Test
    public void manufacturerFrameWithoutNameIsIncompleteHs02() {
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                manufacturerOnly(),
                null));

        assertEquals(HsBroadcastReport.Status.INCOMPLETE, report.status());
        assertEquals(HsGeneration.HS02, report.generation());
        assertNull(report.identity());
        assertFalse(report.isHsDevice());
    }

    @Test
    public void hs02FrameDoesNotFallBackToHs01() {
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.singletonList(cnUuid()),
                hs02Packet("Vidda V11"),
                null));

        assertTrue(report.identity() instanceof Hs02Identity);
        assertEquals("Vidda V11", ((Hs02Identity) report.identity()).name());
    }

    @Test
    public void incompleteHs02DoesNotBecomeHs01() {
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.singletonList(cnUuid()),
                manufacturerOnly(),
                null));

        assertEquals(HsBroadcastReport.Status.INCOMPLETE, report.status());
        assertEquals(HsGeneration.HS02, report.generation());
        assertNull(report.identity());
    }

    @Test
    public void unrelatedBroadcastIsNotAnHsDevice() {
        UUID other = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
        HsBroadcastReport report = HsBroadcastRuntime.classify(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.singletonList(other),
                new byte[]{0x02, 0x01, 0x06},
                "Keyboard"));

        assertEquals(HsBroadcastReport.Status.NOT_HS, report.status());
        assertNull(report.generation());
        assertNull(report.identity());
    }

    @Test
    public void sessionJoinsManufacturerAndLaterName() {
        HsBroadcastSession session = HsBroadcastRuntime.openSession();
        HsBroadcastReport first = session.observe(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                manufacturerOnly(),
                null));
        assertEquals(HsBroadcastReport.Status.INCOMPLETE, first.status());

        Hs02Identity identity = identifiedHs02(session.observe(HsScanSnapshot.of(
                "11:22:33:44:55:66",
                Collections.emptyList(),
                completeName("Vidda V11"),
                null)));
        assertEquals("AA:BB:CC:DD:EE:FF", identity.classicAddress());
        assertEquals("Vidda V11", identity.name());
        assertEquals(HsMarket.CN, identity.marketId());
    }

    private static Hs02Identity identifiedHs02(HsBroadcastReport report) {
        assertEquals(HsBroadcastReport.Status.IDENTIFIED, report.status());
        assertTrue(report.identity() instanceof Hs02Identity);
        return (Hs02Identity) report.identity();
    }

    private static UUID cnUuid() {
        byte[] prefix = hexToBytes(CN_FEATURE_CODE.substring(0, 18));
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
        return hs02Packet(name, "AA:BB:CC:DD:EE:FF");
    }

    private static byte[] hs02Packet(String name, String classic) {
        byte[] nameField = completeName(name);
        byte[] manufacturer = manufacturerOnly(classic);
        byte[] packet = new byte[nameField.length + manufacturer.length];
        System.arraycopy(nameField, 0, packet, 0, nameField.length);
        System.arraycopy(manufacturer, 0, packet, nameField.length, manufacturer.length);
        return packet;
    }

    private static byte[] completeName(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] field = new byte[nameBytes.length + 2];
        field[0] = (byte) (nameBytes.length + 1);
        field[1] = 0x09;
        System.arraycopy(nameBytes, 0, field, 2, nameBytes.length);
        return field;
    }

    private static byte[] manufacturerOnly() {
        return manufacturerOnly("AA:BB:CC:DD:EE:FF");
    }

    private static byte[] manufacturerOnly(String classic) {
        String[] parts = classic.split(":");
        byte[] manufacturer = new byte[]{
                (byte) 0xFF, (byte) 0xFF,
                0x48, 0x43,
                0x01,
                0x02,
                0x12,
                0x01, 0x00,
                0x00, 0x00,
                0x0A, 0x06,
                (byte) Integer.parseInt(parts[0], 16),
                (byte) Integer.parseInt(parts[1], 16),
                (byte) Integer.parseInt(parts[2], 16),
                (byte) Integer.parseInt(parts[3], 16),
                (byte) Integer.parseInt(parts[4], 16),
                (byte) Integer.parseInt(parts[5], 16),
                0x0B, 0x01, 0x00
        };
        byte[] packet = new byte[manufacturer.length + 2];
        packet[0] = (byte) (manufacturer.length + 1);
        packet[1] = (byte) 0xFF;
        System.arraycopy(manufacturer, 0, packet, 2, manufacturer.length);
        return packet;
    }

    private static byte[] hexToBytes(String hex) {
        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(hex.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }
}
