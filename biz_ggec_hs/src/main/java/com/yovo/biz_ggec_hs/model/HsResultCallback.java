package com.yovo.biz_ggec_hs.model;

import androidx.annotation.NonNull;

/**
 * 命令完成回调。实现方会在主线程收到结果。
 */
public interface HsResultCallback {
    void onCompleted(@NonNull HsCommandResult result);
}
