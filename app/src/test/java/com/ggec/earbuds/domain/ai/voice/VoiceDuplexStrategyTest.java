package com.ggec.earbuds.domain.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证全双工 / 半双工从标识到行为策略的解耦关系：
 * <ul>
 *   <li>{@link VoiceDuplex} 只承担稳定标识与持久化编解码；</li>
 *   <li>{@link VoiceDuplexStrategy} 独立封装“助手发声期间是否上行”与“VAD SpeechStarted 是否自动打断”。</li>
 * </ul>
 */
public class VoiceDuplexStrategyTest {

    @Test
    public void defaultModeAndIdRoundTripWorkCleanly() {
        assertEquals(VoiceDuplex.HALF_DUPLEX, VoiceDuplex.defaultMode());
        assertEquals("full_duplex", VoiceDuplex.FULL_DUPLEX.id());
        assertEquals("half_duplex", VoiceDuplex.HALF_DUPLEX.id());

        assertEquals(VoiceDuplex.FULL_DUPLEX, VoiceDuplex.fromId("full_duplex"));
        assertEquals(VoiceDuplex.FULL_DUPLEX, VoiceDuplex.fromId("FULL_DUPLEX"));
        assertEquals(VoiceDuplex.HALF_DUPLEX, VoiceDuplex.fromId(" half_duplex "));
        assertNull(VoiceDuplex.fromId(null));
        assertNull(VoiceDuplex.fromId("unknown"));
    }

    @Test
    public void fullDuplexStrategyKeepsUplinkOpenAndInterruptsWhenAssistantActive() {
        VoiceDuplexStrategy strategy = VoiceDuplex.FULL_DUPLEX.strategy();
        assertSame(FullDuplexStrategy.INSTANCE, strategy);
        assertSame(strategy, VoiceDuplexStrategy.forMode(VoiceDuplex.FULL_DUPLEX));
        assertEquals(VoiceDuplex.FULL_DUPLEX, strategy.mode());

        assertTrue(strategy.allowsUplink(false));
        assertTrue(strategy.allowsUplink(true));
        assertFalse("聆听期自己开口不构成打断", strategy.shouldInterruptOnSpeech(false));
        assertTrue("助手活跃期收到 SpeechStarted 立即打断", strategy.shouldInterruptOnSpeech(true));
    }

    @Test
    public void halfDuplexStrategyClosesUplinkAndIgnoresSpeechWhenAssistantActive() {
        VoiceDuplexStrategy strategy = VoiceDuplex.HALF_DUPLEX.strategy();
        assertSame(HalfDuplexStrategy.INSTANCE, strategy);
        assertSame(strategy, VoiceDuplexStrategy.forMode(VoiceDuplex.HALF_DUPLEX));
        assertEquals(VoiceDuplex.HALF_DUPLEX, strategy.mode());

        assertTrue(strategy.allowsUplink(false));
        assertFalse("助手活跃期关闭上行闸门", strategy.allowsUplink(true));
        assertFalse(strategy.shouldInterruptOnSpeech(false));
        assertFalse("助手活跃期不把 VAD 误判作插话", strategy.shouldInterruptOnSpeech(true));
    }
}
