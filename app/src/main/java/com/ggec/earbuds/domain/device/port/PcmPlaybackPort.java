package com.ggec.earbuds.domain.device.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.PlaybackOutput;

import java.util.List;

/**
 * PCM 音频回放端口抽象。
 *
 * <p>除了「播放 / 停止」，还暴露播放进度与物理输出设备（播放源）的只读事实，
 * 以及把播放路由到指定输出设备的意图。平台实现负责把 Android 的
 * {@code AudioTrack} / {@code AudioManager} 细节翻译成本类型。</p>
 */
public interface PcmPlaybackPort {

    void play(@NonNull String filePath, @NonNull Runnable onCompletion);

    void stop();

    boolean isPlaying();

    @Nullable
    String playingFilePath();

    /** 当前播放进度（毫秒）。没有活动播放时为 0。 */
    long positionMs();

    /** 当前音轨总时长（毫秒）。没有活动播放时为 0。 */
    long durationMs();

    /** 当前可选的物理输出设备（播放源）列表；无活动音轨时也可用于预选。 */
    @NonNull
    List<PlaybackOutput> availableOutputs();

    /** 当前实际正在出声的物理输出设备；无法判定时为 null。 */
    @Nullable
    PlaybackOutput currentOutput();

    /** 把播放路由到指定 id 的输出设备；返回是否已应用（无活动音轨时记录为下次首选）。 */
    boolean selectOutput(@NonNull String outputId);

    void release();
}
