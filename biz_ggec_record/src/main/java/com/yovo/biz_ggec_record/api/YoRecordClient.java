package com.yovo.biz_ggec_record.api;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_record.model.RecordAsrModel;
import com.yovo.biz_ggec_record.model.RecordAudioCodec;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;

/**
 * Ambiq BLE 音视频 / ASR 会话。
 * 调用方只提交业务动作和接收观察者事件，不接触 GATT、特征 UUID 或 lib_bluetooth。
 */
public interface YoRecordClient {

    void connect(@NonNull String macAddress);

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

    void setAsrEnabled(boolean enabled);

    void setAsrModel(@NonNull RecordAsrModel model);

    /** 向设备 RX 特征写入 US-ASCII 文本。未就绪时忽略。 */
    void sendText(@NonNull String text);
}
