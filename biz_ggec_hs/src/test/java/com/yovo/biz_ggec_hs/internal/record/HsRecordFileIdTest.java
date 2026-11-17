package com.yovo.biz_ggec_hs.internal.record;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 设备文件 ID 契约：非十进制必须被判违约，绝不能回落成下标 0。
 */
public final class HsRecordFileIdTest {

    @Test
    public void acceptsPureDecimalIndex() {
        assertEquals(Integer.valueOf(0), HsRecordFileId.indexOf("0"));
        assertEquals(Integer.valueOf(1), HsRecordFileId.indexOf("1"));
        assertEquals(Integer.valueOf(42), HsRecordFileId.indexOf("42"));
        assertEquals(Integer.valueOf(7), HsRecordFileId.indexOf("007"));
    }

    @Test
    public void rejectsNonDecimalInsteadOfDefaultingToZero() {
        assertNull(HsRecordFileId.indexOf(null));
        assertNull(HsRecordFileId.indexOf(""));
        assertNull(HsRecordFileId.indexOf("abc"));
        assertNull(HsRecordFileId.indexOf("1a"));
        assertNull(HsRecordFileId.indexOf("-1"));
        assertNull(HsRecordFileId.indexOf("+1"));
        assertNull(HsRecordFileId.indexOf(" 1"));
        assertNull(HsRecordFileId.indexOf("1 "));
        assertNull(HsRecordFileId.indexOf("1.0"));
    }

    @Test
    public void rejectsOverlongIndexToAvoidOverflow() {
        assertNull(HsRecordFileId.indexOf("1234567890"));
        assertNull(HsRecordFileId.indexOf("99999999999999999999"));
    }
}
