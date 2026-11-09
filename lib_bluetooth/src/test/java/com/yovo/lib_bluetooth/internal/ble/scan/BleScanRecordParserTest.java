package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleScanRecord;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * BLE-007 纯 Java AD TLV (0x01..0xFF) 广播载荷解析与畸形输入防崩溃测试。
 */
public class BleScanRecordParserTest {

    @Test
    public void parsesAllStandardAdTypesAccurately() {
        byte[] packet = concat(
                // Flags 0x06
                tlv(0x01, new byte[]{0x06}),
                // 16-bit Service UUID: 0x180D (Heart Rate)
                tlv(0x03, new byte[]{0x0D, 0x18}),
                // 32-bit Service UUID: 0x12345678
                tlv(0x05, new byte[]{0x78, 0x56, 0x34, 0x12}),
                // 128-bit Service UUID: 11223344-5566-7788-99aa-bbccddeeff00 (LE on air)
                tlv(0x07, new byte[]{
                        0x00, (byte) 0xFF, (byte) 0xEE, (byte) 0xDD,
                        (byte) 0xCC, (byte) 0xBB, (byte) 0xAA, (byte) 0x99,
                        (byte) 0x88, 0x77, 0x66, 0x55,
                        0x44, 0x33, 0x22, 0x11
                }),
                // Short name then Complete name -> complete name must win
                tlv(0x08, "Yo".getBytes(StandardCharsets.UTF_8)),
                tlv(0x09, "YoBuds-Pro".getBytes(StandardCharsets.UTF_8)),
                // Tx Power Level = -12 dBm
                tlv(0x0A, new byte[]{(byte) -12}),
                // 16-bit Solicitation UUID: 0x1812 (HID)
                tlv(0x14, new byte[]{0x12, 0x18}),
                // 16-bit Service Data: UUID 0x180F + payload {95}
                tlv(0x16, new byte[]{0x0F, 0x18, 95}),
                // Two segments for same Manufacturer ID 0x004C -> merged safely
                tlv(0xFF, new byte[]{0x4C, 0x00, 0x02, 0x15}),
                tlv(0xFF, new byte[]{0x4C, 0x00, (byte) 0xAA, (byte) 0xBB})
        );

        BleScanRecord record = YoBleScanRecordParser.parse(packet);
        assertEquals(0x06, record.advertiseFlags());
        assertEquals("YoBuds-Pro", record.deviceName());
        assertEquals(-12, record.txPowerLevel());

        assertTrue(record.serviceUuids().contains(
                UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")));
        assertTrue(record.serviceUuids().contains(
                UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")));
        assertTrue(record.serviceUuids().contains(
                UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00")));
        assertTrue(record.serviceSolicitationUuids().contains(
                UUID.fromString("00001812-0000-1000-8000-00805f9b34fb")));

        assertArrayEquals(new byte[]{95},
                record.getServiceData(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")));
        assertArrayEquals(new byte[]{0x02, 0x15, (byte) 0xAA, (byte) 0xBB},
                record.getManufacturerSpecificData(0x004C));
    }

    @Test
    public void malformedInputs_nullEmptyZeroTerminatorAndTruncated_neverCrash() {
        BleScanRecord nullRec = YoBleScanRecordParser.parse(null);
        assertNull(nullRec.deviceName());
        assertEquals(0, nullRec.rawBytes().length);

        BleScanRecord emptyRec = YoBleScanRecordParser.parse(new byte[0]);
        assertTrue(emptyRec.serviceUuids().isEmpty());

        // length=0 终止符后的垃圾字节忽略；前面已解析的 Flags 保留
        BleScanRecord zeroTerm = YoBleScanRecordParser.parse(new byte[]{
                0x02, 0x01, 0x04,
                0x00,
                0x05, 0x09, 'B', 'A', 'D', 'X'
        });
        assertEquals(0x04, zeroTerm.advertiseFlags());
        assertNull(zeroTerm.deviceName());

        // 越界长度声明 len=50 > remaining
        BleScanRecord truncated = YoBleScanRecordParser.parse(new byte[]{
                0x02, 0x01, 0x02,
                50, 0x09, 'A', 'B'
        });
        assertEquals(0x02, truncated.advertiseFlags());
        assertNull(truncated.deviceName());
    }

    private static byte[] tlv(int type, byte[] value) {
        byte[] out = new byte[2 + value.length];
        out[0] = (byte) (value.length + 1);
        out[1] = (byte) type;
        System.arraycopy(value, 0, out, 2, value.length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
