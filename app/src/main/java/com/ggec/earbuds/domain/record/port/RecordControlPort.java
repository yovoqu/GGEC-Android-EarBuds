package com.ggec.earbuds.domain.record.port;

import com.ggec.earbuds.domain.record.model.RecordVideoCodec;

/**
 * Record 音视频会话控制端口（领域侧）。由 provider/biz_ggec_record 实现，装配根注入。
 *
 * <p>视频输出：Surface 是 Android 平台句柄，领域层不能引用 {@code android.*}，故此处以不透明
 * {@code Object} 承载，仅由 provider 在实现处向下转型为 {@link android.view.Surface}——这是
 * 明确声明的层边界转换，不是第二套模型。</p>
 */
public interface RecordControlPort {

    void connect(String macAddress, String deviceName);

    void disconnect();

    boolean isReady();

    void setMuted(boolean muted);

    void setVideoCodec(RecordVideoCodec codec);

    /** @param surface 不透明 Android Surface 句柄，传 null 表示停止视频输出。 */
    void setVideoSurface(Object surface);

    void startRecording();

    void stopRecording();

    boolean isRecording();

    void setObserver(RecordObserver observer);

    void close();
}