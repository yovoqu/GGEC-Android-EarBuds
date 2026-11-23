package com.yovo.lib_ggec_audio.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link RecordFileName} 的命名规则单测。
 */
public final class RecordFileNameTest {

    private static final long SAMPLE_EPOCH = 1_763_541_600_000L;

    @Test
    public void streamNameHasCodeTypeTimestampAndExtension() {
        String name = RecordFileName.build("ABC01", RecordFileName.TYPE_STREAM,
                SAMPLE_EPOCH, "pcm");
        assertTrue(name, name.matches("ABC01_stream_\\d{8}_\\d{6}\\.pcm"));
    }

    @Test
    public void syncNameCarriesSourceTag() {
        String name = RecordFileName.build("ABC01", RecordFileName.TYPE_SYNC, "3",
                SAMPLE_EPOCH, "pcm");
        assertTrue(name, name.matches("ABC01_sync_3_\\d{8}_\\d{6}\\.pcm"));
    }

    @Test
    public void missingCodeFallsBackToUnknown() {
        String name = RecordFileName.build(null, RecordFileName.TYPE_AV,
                SAMPLE_EPOCH, "mp4");
        assertTrue(name, name.startsWith(RecordFileName.UNKNOWN_CODE + "_av_"));
        assertTrue(name, name.endsWith(".mp4"));
    }

    @Test
    public void missingTypeFallsBackToRec() {
        String name = RecordFileName.build("ABC01", "  ", SAMPLE_EPOCH, "pcm");
        assertTrue(name, name.startsWith("ABC01_" + RecordFileName.FALLBACK_TYPE + "_"));
    }

    @Test
    public void illegalCharactersAreFoldedIntoDash() {
        assertEquals("my-device-1",
                RecordFileName.sanitize("  my device:/1  ", "X", 32));
    }

    @Test
    public void sanitizeTrimsEdgeDashes() {
        assertEquals("mid", RecordFileName.sanitize("--mid--", "X", 32));
    }

    @Test
    public void sanitizeEmptyUsesFallback() {
        assertEquals("X", RecordFileName.sanitize("   ", "X", 32));
        assertEquals("X", RecordFileName.sanitize(null, "X", 32));
    }

    @Test
    public void sanitizeCapsLength() {
        assertEquals("abcdefghij", RecordFileName.sanitize("abcdefghijklmnop", "X", 10));
    }

    @Test
    public void extensionIsNormalized() {
        assertEquals("pcm", RecordFileName.normalizeExtension(".PCM"));
        assertEquals("mp4", RecordFileName.normalizeExtension(" MP4 "));
        assertEquals("", RecordFileName.normalizeExtension(null));
    }

    @Test
    public void emptyExtensionLeavesNoTrailingDot() {
        String name = RecordFileName.build("ABC01", RecordFileName.TYPE_STREAM,
                SAMPLE_EPOCH, null);
        assertFalse(name, name.endsWith("."));
        assertTrue(name, name.matches("ABC01_stream_\\d{8}_\\d{6}"));
    }
}
