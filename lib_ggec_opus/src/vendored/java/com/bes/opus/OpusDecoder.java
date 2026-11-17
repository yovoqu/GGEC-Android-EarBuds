package com.bes.opus;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;

/**
 * Opus decoder wrapper (vendored, JNI binding package must stay {@code com.bes.opus}).
 *
 * <p>Input is a single Opus packet of arbitrary length; output is raw PCM for 16 kHz mono
 * with 20 ms frames (640 bytes per frame).</p>
 */
public class OpusDecoder {

    /** Native decoder handle; {@code 0} means released or never created. */
    private long decoder;

    static {
        System.loadLibrary("besOpus");
    }

    /**
     * Creates a decoder fixed at 16 kHz / 1ch with {@code PCM_FRAME_BYTES} output size.
     * Call {@link #decode(byte[])} with one Opus packet at a time.
     */
    public OpusDecoder() {
        decoder = init_native(-1, 0, 0, YoOpusAudioContract.PCM_FRAME_BYTES, 0);
        if (decoder == 0L) {
            throw new IllegalStateException("Opus decoder init failed: native handle is 0");
        }
    }

    private static native long init_native(int framePeriod, int singleFrameInputSize,
                                           int totalFrameInputSize, int pcmOutputSize, int bitRate);

    private native short[] decode_native(long decoder, byte[] decodeDataIn, int encodedLength);

    private native void destory_native(long decoder);

    /**
     * Decodes one Opus packet to PCM.
     *
     * @return PCM bytes, or {@code null} when the native decoder rejects this frame
     * @throws IllegalStateException when the decoder has been released
     */
    @Nullable
    public byte[] decode(@NonNull byte[] packet) {
        if (decoder == 0L) {
            throw new IllegalStateException("Opus decoder already released");
        }
        int inLen = YoOpusAudioContract.decodeInputLength(packet);
        short[] shorts = decode_native(decoder, packet, inLen);
        if (shorts == null) {
            return null;
        }
        return OpusUtils.Shorts2Bytes(shorts);
    }

    /**
     * Releases the native handle. Idempotency is not granted: a second call throws.
     *
     * @throws IllegalStateException when the decoder has already been released
     */
    public synchronized void destroy() {
        long handle = decoder;
        if (handle == 0L) {
            throw new IllegalStateException("Opus decoder already released");
        }
        decoder = 0L;
        destory_native(handle);
    }
}
