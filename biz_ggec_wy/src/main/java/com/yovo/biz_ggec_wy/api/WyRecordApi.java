package com.yovo.biz_ggec_wy.api;

import androidx.annotation.NonNull;

/**
 * WY 耳机录音业务 API 契约（随录随传）。
 */
public interface WyRecordApi {

    /**
     * 开始随录随传。
     *
     * @param config 录音配置（输出模式、自定义路径或默认落盘）
     * @param listener 状态、PCM 数据流与文件完成监听
     */
    void startStreamRecord(@NonNull StreamRecordConfig config, @NonNull StreamRecordListener listener);

    /**
     * 停止随录随传。
     */
    void stopStreamRecord(@NonNull WyControl.Callback callback);

    /**
     * 当前是否正在随录随传。
     */
    boolean isStreamRecording();
}
