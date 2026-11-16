package com.yovo.lib_ggec_opus.internal.codec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bes.opus.OpusDecoder;
import com.yovo.lib_ggec_opus.api.YoOpusDecoder;

/**
 * Opus 解码器实现类，封装底层 JNI 句柄生命周期。
 */
public final class YoOpusDecoderImpl implements YoOpusDecoder {

    private final OpusDecoder decoder;
    private volatile boolean released = false;

    public YoOpusDecoderImpl() {
        OpusDecoder.init();
        this.decoder = new OpusDecoder();
    }

    @Nullable
    @Override
    public synchronized byte[] decode(@NonNull byte[] opusPacket) {
        if (released || opusPacket.length == 0) {
            return null;
        }
        return decoder.decode(opusPacket);
    }

    @Override
    public synchronized void release() {
        if (released) {
            return;
        }
        released = true;
        try {
            decoder.descory();
        } catch (Throwable ignored) {
        }
    }
}
