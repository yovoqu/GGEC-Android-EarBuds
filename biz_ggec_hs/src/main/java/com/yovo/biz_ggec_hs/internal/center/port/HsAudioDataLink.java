package com.yovo.biz_ggec_hs.internal.center.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

/**
 * 录音音频数据通道端口契约（GATT 820000 / 820003）。
 */
public interface HsAudioDataLink {

    interface ReadyCallback {
        void onReady();
        void onError(@NonNull String message);
    }

    void bindDevice(@Nullable String bleAddress);

    void startAudioNotify(@Nullable ReadyCallback onReady, @NonNull Consumer<byte[]> onAudioChunk);

    void stopAudioNotify();

    boolean isListening();

    void release();
}
