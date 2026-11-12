package com.yovo.lib_bluetooth.internal.ble.scan;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleScanRecord;

import org.junit.Test;

/**
 * {@link YoBleScanRecordParser} 边界契约：AD TLV 负载长度非负由调用点保证，负值按编程错误抛出，
 * 不再用 {@code Math.max(0, length)} 静默产出空数组；截断结构按既有规则停止解析且不抛错。
 */
public final class YoBleScanRecordParserTest {

    @Test
    public void extractRejectsNegativeLengthInsteadOfReturningEmptyArray() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> YoBleScanRecordParser.extract(new byte[]{0x01, 0x02, 0x03}, 0, -1));
        assertTrue(error.getMessage().contains("payload length"));
        assertTrue(error.getMessage().contains("-1"));
    }

    @Test
    public void extractOfZeroLengthIsAnEmptyArray() {
        byte[] out = YoBleScanRecordParser.extract(new byte[]{0x01, 0x02, 0x03}, 1, 0);
        assertEquals(0, out.length);
    }

    @Test
    public void extractCopiesThePayloadRange() {
        byte[] out = YoBleScanRecordParser.extract(
                new byte[]{0x0A, 0x0B, 0x0C, 0x0D}, 1, 2);
        assertArrayEquals(new byte[]{0x0B, 0x0C}, out);
    }

    @Test
    public void parsesManufacturerSpecificDataPayload() {
        // AD: length=6, type=0xFF, manufacturerId=0x0059 (LE), payload=11 22 33
        byte[] raw = new byte[]{0x06, (byte) 0xFF, 0x59, 0x00, 0x11, 0x22, 0x33};
        BleScanRecord record = YoBleScanRecordParser.parse(raw);

        byte[] manufacturerData = record.getManufacturerSpecificData(0x59);
        assertNotNull("厂商数据必须解析出来", manufacturerData);
        assertArrayEquals(new byte[]{0x11, 0x22, 0x33}, manufacturerData);
    }

    @Test
    public void truncatedStructureStopsParsingWithoutThrowing() {
        // 先给一个合法 flags 段（length=2, type=0x01, value=0x06），随后是声明 5 字节但载荷缺失的段。
        byte[] raw = new byte[]{0x02, 0x01, 0x06, 0x05, (byte) 0xFF, 0x59, 0x00};
        BleScanRecord record = YoBleScanRecordParser.parse(raw);

        assertEquals(0x06, record.advertiseFlags());
        assertNull("截断段不得产出厂商数据", record.getManufacturerSpecificData(0x59));
    }
}
