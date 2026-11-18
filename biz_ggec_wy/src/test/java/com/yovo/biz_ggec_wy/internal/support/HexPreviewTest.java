package com.yovo.biz_ggec_wy.internal.support;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link HexPreview} 的格式化契约：Biz 打印真实原始数据时的统一格式。
 */
public final class HexPreviewTest {

    @Test
    public void nullIsLiteralNull() {
        assertEquals("null", HexPreview.of(null, 48));
    }

    @Test
    public void emptyIsEmptyString() {
        assertEquals("", HexPreview.of(new byte[0], 48));
    }

    @Test
    public void formatsUppercaseSpaceSeparatedHex() {
        assertEquals("00 01 AB FF",
                HexPreview.of(new byte[]{0x00, 0x01, (byte) 0xAB, (byte) 0xFF}, 48));
    }

    @Test
    public void truncatesBeyondLimitAndAppendsTotalLength() {
        byte[] data = {0x11, 0x22, 0x33, 0x44, 0x55};
        assertEquals("11 22 33 …(共5B)", HexPreview.of(data, 3));
    }

    @Test
    public void noTruncationSuffixWhenWithinLimit() {
        byte[] data = {0x11, 0x22};
        assertEquals("11 22", HexPreview.of(data, 48));
    }
}
