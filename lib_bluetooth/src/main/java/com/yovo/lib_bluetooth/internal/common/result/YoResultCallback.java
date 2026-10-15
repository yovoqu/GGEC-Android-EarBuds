package com.yovo.lib_bluetooth.internal.common.result;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoFailure;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 异步结果回调接口，分别处理成功值与失败信息。
 */
public interface YoResultCallback<T> {
    void onSuccess(T value);

    void onFailure(@NonNull YoFailure failure);
}
