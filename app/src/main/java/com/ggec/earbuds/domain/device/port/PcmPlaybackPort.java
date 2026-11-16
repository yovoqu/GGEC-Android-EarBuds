package com.ggec.earbuds.domain.device.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * PCM 音频回放端口抽象。
 */
public interface PcmPlaybackPort {

    void play(@NonNull String filePath, @NonNull Runnable onCompletion);

    void stop();

    boolean isPlaying();

    @Nullable
    String playingFilePath();

    void release();
}
