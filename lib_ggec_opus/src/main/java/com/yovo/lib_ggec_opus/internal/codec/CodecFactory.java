package com.yovo.lib_ggec_opus.internal.codec;

import androidx.annotation.NonNull;

import com.yovo.lib_ggec_opus.api.YoOpusDecoder;

/**
 * 解码器构造入口（本包唯一的对外类型）。
 *
 * <p>实现类 {@link YoOpusDecoderImpl} 是包内可见：模块外只能通过门面
 * {@code YoOpusSdk} 或本类拿到 {@link YoOpusDecoder} 契约。</p>
 */
public final class CodecFactory {

    private CodecFactory() {
    }

    @NonNull
    public static YoOpusDecoder openDecoder() {
        return new YoOpusDecoderImpl();
    }
}
