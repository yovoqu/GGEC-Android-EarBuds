package com.ggec.earbuds.domain.ai.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 双工初始化建议测试：
 * 验证在不同声学环境下的初始默认建议。
 */
public class VoiceDuplexPolicyTest {

    @Test
    public void privateOutputDefaultsToFullDuplex() {
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolveDefault(true, false));
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolveDefault(true, true));
    }

    @Test
    public void speakerWithoutAecDefaultsToHalfDuplex() {
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.resolveDefault(false, false));
    }

    @Test
    public void speakerWithAecDefaultsToFullDuplex() {
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolveDefault(false, true));
    }

    @Test
    public void resolveDelegatesToResolveDefault() {
        assertEquals(VoiceDuplex.FULL_DUPLEX,
                VoiceDuplexPolicy.resolve(true, false));
        assertEquals(VoiceDuplex.HALF_DUPLEX,
                VoiceDuplexPolicy.resolve(false, false));
    }
}
