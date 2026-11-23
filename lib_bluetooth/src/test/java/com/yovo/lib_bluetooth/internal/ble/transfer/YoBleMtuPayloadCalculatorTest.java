package com.yovo.lib_bluetooth.internal.ble.transfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import org.junit.Test;

/**
 * {@link YoBleMtuPayloadCalculator} 纯函数契约测试。
 *
 * <p>覆盖 {@code chunkEnd} 的正常切片/末片/整除/越界/非法退化，以及
 * {@code effectiveMaxChunkSize} 的默认钳位与 512 上限钳位（按实际常量计算）。</p>
 */
public final class YoBleMtuPayloadCalculatorTest {

    // ── chunkEnd：正常切片 ────────────────────────────────────────────────────

    @Test
    public void chunkEnd_returnsOffsetPlusMaxChunkForMiddleChunk() {
        assertEquals(20, YoBleMtuPayloadCalculator.chunkEnd(100, 0, 20));
        assertEquals(60, YoBleMtuPayloadCalculator.chunkEnd(100, 40, 20));
    }

    @Test
    public void chunkEnd_clampsToPayloadLengthOnLastPartialChunk() {
        // 100 = 20*5，末片正好整除
        assertEquals(100, YoBleMtuPayloadCalculator.chunkEnd(100, 80, 20));
        // 90 + 20 = 110 > 100 → 截到 100
        assertEquals(100, YoBleMtuPayloadCalculator.chunkEnd(100, 90, 20));
    }

    @Test
    public void chunkEnd_handlesSingleExactChunkAndOffsetEqualToLength() {
        assertEquals(100, YoBleMtuPayloadCalculator.chunkEnd(100, 0, 100));
        assertEquals(100, YoBleMtuPayloadCalculator.chunkEnd(100, 100, 20));
    }

    @Test
    public void chunkEnd_clampsWhenOffsetBeyondPayload() {
        assertEquals(100, YoBleMtuPayloadCalculator.chunkEnd(100, 150, 20));
    }

    // ── chunkEnd：非法入参退化（不抛异常） ─────────────────────────────────────

    @Test
    public void chunkEnd_degradesWhenMaxChunkNonPositive() {
        // min(max(0,100), max(0,40)) = 40
        assertEquals(40, YoBleMtuPayloadCalculator.chunkEnd(100, 40, 0));
        assertEquals(40, YoBleMtuPayloadCalculator.chunkEnd(100, 40, -5));
    }

    @Test
    public void chunkEnd_degradesWhenPayloadLengthZeroOrNegative() {
        assertEquals(0, YoBleMtuPayloadCalculator.chunkEnd(0, 0, 20));
        assertEquals(0, YoBleMtuPayloadCalculator.chunkEnd(0, 0, 0));
        // min(max(0,-10)=0, max(0,5)=5) = 0
        assertEquals(0, YoBleMtuPayloadCalculator.chunkEnd(-10, 5, 20));
    }

    @Test
    public void chunkEnd_degradesWhenOffsetNegative() {
        // min(max(0,100), max(0,-5)=0) = 0
        assertEquals(0, YoBleMtuPayloadCalculator.chunkEnd(100, -5, 20));
        assertEquals(0, YoBleMtuPayloadCalculator.chunkEnd(100, -5, 0));
    }

    // ── chunkEnd：切片不变量（逐片推进恰好覆盖整段载荷） ────────────────────────

    @Test
    public void chunkEnd_progressCoversWholePayloadWithoutGaps() {
        int[] payloads = {0, 1, 19, 20, 21, 100, 513};
        int[] chunks = {1, 3, 20, 512};
        for (int payloadLength : payloads) {
            for (int maxChunk : chunks) {
                int offset = 0;
                int guard = 0;
                while (offset < payloadLength) {
                    int end = YoBleMtuPayloadCalculator.chunkEnd(payloadLength, offset, maxChunk);
                    assertTrue("每片必须前进且不越过载荷", end > offset && end <= payloadLength);
                    offset = end;
                    assertTrue("切片必须收敛", ++guard <= payloadLength + 1);
                }
                assertEquals("逐片推进应恰好覆盖整段载荷", payloadLength, offset);
            }
        }
    }

    // ── effectiveMaxChunkSize：默认 / 覆盖 / 上限钳位（按实际常量计算） ──────────

    @Test
    public void effectiveMaxChunkSize_defaultMtuIsMinusThreeOverhead() {
        // mtu=23（DEFAULT_ATT_MTU）→ 23 - 3 = 20
        assertEquals(20, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                23, null, BleWriteType.WITHOUT_RESPONSE));
        assertEquals(20, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                23, null, BleWriteType.WITH_RESPONSE));
    }

    @Test
    public void effectiveMaxChunkSize_signedUsesFifteenByteOverhead() {
        // mtu=23 - 15 = 8
        assertEquals(8, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                23, null, BleWriteType.SIGNED));
    }

    @Test
    public void effectiveMaxChunkSize_clampsBelowDefaultMtu() {
        // 协商 mtu < 23 抬到 23 → 20
        assertEquals(20, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                10, null, BleWriteType.WITHOUT_RESPONSE));
        // 覆盖值同样受 DEFAULT_ATT_MTU 下限钳位
        assertEquals(20, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                500, 10, BleWriteType.WITHOUT_RESPONSE));
    }

    @Test
    public void effectiveMaxChunkSize_overrideTakesPrecedence() {
        // 覆盖 100 - 3 = 97（negotiated 被忽略）
        assertEquals(97, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                23, 100, BleWriteType.WITHOUT_RESPONSE));
    }

    @Test
    public void effectiveMaxChunkSize_clampsToMaxAttWritePayload() {
        // 1000 - 3 = 997 → min(997, 512) = 512
        assertEquals(BleConnectionSnapshot.MAX_ATT_WRITE_PAYLOAD,
                YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                        1000, null, BleWriteType.WITHOUT_RESPONSE));
        // 600 - 15 = 585 → min(585, 512) = 512
        assertEquals(BleConnectionSnapshot.MAX_ATT_WRITE_PAYLOAD,
                YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                        600, null, BleWriteType.SIGNED));
        // 515 - 3 = 512（正好命中上限）
        assertEquals(512, YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                515, null, BleWriteType.WITHOUT_RESPONSE));
    }
}
