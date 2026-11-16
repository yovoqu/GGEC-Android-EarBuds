package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 设备录音文件同步传输进度监听器。
 */
public interface SyncProgressListener {

    /** 传输进度回调（0 ~ 100） */
    default void onProgress(int percentage, long bytesReceived, long totalBytes) {
    }

    /** 同步落盘成功 */
    void onSuccess(@NonNull File savedFile);

    /** 同步失败 */
    void onError(@NonNull String message);

    /** 同步被取消 */
    default void onCanceled() {
    }
}
