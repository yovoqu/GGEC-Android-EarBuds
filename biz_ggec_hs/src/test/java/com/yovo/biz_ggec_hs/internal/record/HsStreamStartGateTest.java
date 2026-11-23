package com.yovo.biz_ggec_hs.internal.record;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link HsStreamStartGate} 纯逻辑单测：不依赖 engine / 真实 BLE。
 *
 * <p>覆盖两条关键契约：</p>
 * <ol>
 *   <li>任何一个设备侧信号（命令 SUCCESS / 0x8004 / 首段音频）都能结算成功，且全局只成功一次；</li>
 *   <li>成功之后迟到的命令失败/超时一律被忽略；0x8012 延时信息不改变结算状态。</li>
 * </ol>
 */
public class HsStreamStartGateTest {

    private static HsStreamStartGate.Outcome on(HsStreamStartGate gate, HsStreamStartGate.Event event) {
        return gate.onEvent(event);
    }

    @Test
    public void commandSuccessSettlesOnlyOnce() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.SUCCESS, on(gate, HsStreamStartGate.Event.CMD_SUCCESS));
        assertTrue(gate.hasSucceeded());
        assertTrue(gate.isSettled());

        // 迟到失败被忽略
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CMD_FAILED));
        // 再来的设备侧成功也不重复结算
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CTRL_START));
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.AUDIO_CHUNK));
        assertTrue(gate.hasSucceeded());
    }

    @Test
    public void controlStartSignalSettlesSuccess() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.SUCCESS, on(gate, HsStreamStartGate.Event.CTRL_START));
        assertTrue(gate.hasSucceeded());
        // HS02 场景：控制上报先行成功后，命令通道迟到的 FAILED/timeout 一律忽略。
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CMD_FAILED));
    }

    @Test
    public void firstAudioChunkSettlesSuccess() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.SUCCESS, on(gate, HsStreamStartGate.Event.AUDIO_CHUNK));
        assertTrue(gate.hasSucceeded());
        // 后续音频切片不再重复结算
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.AUDIO_CHUNK));
    }

    @Test
    public void delayIsNeitherSuccessNorFailure() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CTRL_DELAY));
        assertFalse(gate.isSettled());
        assertFalse(gate.hasSucceeded());

        // 延时之后仍可由音频数据结算成功
        assertEquals(HsStreamStartGate.Outcome.SUCCESS, on(gate, HsStreamStartGate.Event.AUDIO_CHUNK));
    }

    @Test
    public void lateFailureAfterSuccessIsIgnored() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.SUCCESS, on(gate, HsStreamStartGate.Event.AUDIO_CHUNK));
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CMD_FAILED));
        // 成功之后不得再按失败终结
        assertFalse(gate.trySettleFailure());
        assertTrue(gate.hasSucceeded());
    }

    @Test
    public void failureSettlesOnlyBeforeAnySuccess() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertTrue(gate.trySettleFailure());
        assertTrue(gate.isSettled());
        assertFalse(gate.hasSucceeded());

        // 已按失败终结后，迟到的设备成功不得翻盘
        assertEquals(HsStreamStartGate.Outcome.NONE, on(gate, HsStreamStartGate.Event.CTRL_START));
        assertFalse(gate.hasSucceeded());
    }

    @Test
    public void nullEventIsNoOp() {
        HsStreamStartGate gate = new HsStreamStartGate();

        assertEquals(HsStreamStartGate.Outcome.NONE, gate.onEvent(null));
        assertFalse(gate.isSettled());
    }
}
