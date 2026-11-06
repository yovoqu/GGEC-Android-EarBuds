package com.ggec.earbuds.domain.ai.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 双工策略矩阵：输出路由 × AEC 能力 → 双工方式。
 *
 * <p>这张表是"能不能语音打断"的唯一判据，必须穷举。</p>
 */
public class VoiceDuplexPolicyTest {

    @Test
    public void privateOutputAlwaysAllowsFullDuplex() {
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolve(true, false));
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolve(true, true));
    }

    @Test
    public void speakerNeedsEchoCancellationForFullDuplex() {
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.resolve(false, false));
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolve(false, true));
    }

    @Test
    public void requestedHalfIsNeverUpgraded() {
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.confirmRequested(VoiceDuplex.HALF_DUPLEX, true, true));
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.confirmRequested(VoiceDuplex.HALF_DUPLEX, false, true));
    }

    @Test
    public void requestedFullIsDowngradedOnSpeakerWithoutAec() {
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.confirmRequested(VoiceDuplex.FULL_DUPLEX, false, false));
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.confirmRequested(VoiceDuplex.FULL_DUPLEX, false, true));
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.confirmRequested(VoiceDuplex.FULL_DUPLEX, true, false));
    }
}
