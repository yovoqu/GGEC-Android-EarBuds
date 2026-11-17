package com.yovo.lib_ggec_opus.internal.codec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bes.opus.OpusDecoder;
import com.yovo.lib_ggec_opus.api.YoOpusDecoder;

/**
 * Opus 解码器实现类（包内可见：对外只经 {@link CodecFactory} 构造），封装底层 JNI 句柄生命周期。
 *
 * <p>句柄在构造期建立、在 {@link #release()} 销毁；构造失败与重复销毁由
 * {@link OpusDecoder} 抛 {@link IllegalStateException}，本类不做兜底。</p>
 */
final class YoOpusDecoderImpl implements YoOpusDecoder {

    private final OpusDecoder decoder;
    private boolean released = false;

    YoOpusDecoderImpl() {
        this.decoder = new OpusDecoder();
    }

    @Nullable
    @Override
    public synchronized byte[] decode(@NonNull byte[] opusPacket) {
        if (released) {
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
        decoder.destroy();
    }
}
