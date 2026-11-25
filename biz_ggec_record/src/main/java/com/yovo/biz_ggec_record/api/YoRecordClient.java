package com.yovo.biz_ggec_record.api;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.model.RecordAudioCodec;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;

/**
 * Ambiq BLE 音视频会话。
 * 调用方只提交业务动作和接收观察者事件，不接触 GATT、特征 UUID 或 lib_bluetooth。
 */
public interface YoRecordClient {

    /**
     * 连接设备。
     *
     * @param macAddress 设备 MAC
     * @param deviceCode 录音文件命名用的设备标识（型号/广播名），可为 {@code null}（命名回落 UNKNOWN）
     */
    void connect(@NonNull String macAddress, @Nullable String deviceCode);

    /** 兼容重载：不提供设备标识，录音文件命名回落 UNKNOWN。 */
    default void connect(@NonNull String macAddress) {
        connect(macAddress, null);
    }

    void disconnect();

    void close();

    boolean isReady();

    void setConnectionObserver(@Nullable RecordConnectionObserver observer);

    void setSessionObserver(@Nullable RecordSessionObserver observer);

    /** 传入 TextureView/SurfaceView 的 Surface；传 null 停止视频输出。 */
    void setVideoSurface(@Nullable Surface surface);

    void setVideoCodec(@NonNull RecordVideoCodec codec);

    void setAudioConfig(@NonNull RecordAudioCodec codec, int sampleRateHz, int channelCount);

    void setMuted(boolean muted);

    /** 向设备 RX 特征写入 US-ASCII 文本。未就绪时忽略。 */
    void sendText(@NonNull String text);

    /**
     * 边预览边录制：旁路封装当前压缩码流为 MP4。
     * 默认写入应用专属目录 {@code record/<设备标识>_av_<yyyyMMdd_HHmmss>.mp4}；
     * 未提供设备标识时回落 {@code UNKNOWN_av_<yyyyMMdd_HHmmss>.mp4}。
     * 未就绪或已在录制时通过 {@link RecordSessionObserver#onRecordingFailed} 回报。
     */
    void startRecording();

    /**
     * 录制到指定绝对路径；父目录不存在时会尝试创建。
     *
     * @param absolutePath 目标文件绝对路径（建议以 .mp4 结尾）
     */
    void startRecording(@NonNull String absolutePath);

    void stopRecording();

    boolean isRecording();
}