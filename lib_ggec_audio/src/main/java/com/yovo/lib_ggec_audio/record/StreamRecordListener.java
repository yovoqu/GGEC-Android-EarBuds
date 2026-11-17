package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 随录随传运行时监听器：产品模块的 api 层直接继承本接口，不再各抄一份。
 */
public interface StreamRecordListener {

    /** 录音已启动。 */
    default void onStarted() {
    }

    /**
     * 实时 PCM 数据帧到达（16kHz / 16bit / 单声道）。
     *
     * @param pcmData PCM 字节数组
     * @param amplitude 归一化振幅（0.0f ~ 1.0f），可直接供 UI 波形绘制
     */
    default void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
    }

    /** 录音文件生成并落盘完成。 */
    default void onFileSaved(@NonNull File file, long durationMs, long fileSizeBytes) {
    }

    /** 录音过程发生错误。 */
    default void onError(@NonNull String message) {
    }

    /** 录音已完全停止。 */
    default void onStopped() {
    }
}
