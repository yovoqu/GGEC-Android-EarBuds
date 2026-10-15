package com.yovo.biz_appupdate.internal.contract;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.LaunchDecision;

/**
 * 启动决策跨页透传端口（Intent 序列化由实现负责）。
 */
public interface LaunchPromptBridge {

    void attach(@NonNull Intent intent, @NonNull LaunchDecision decision);

    @Nullable
    LaunchDecision consume(@Nullable Intent intent);
}
