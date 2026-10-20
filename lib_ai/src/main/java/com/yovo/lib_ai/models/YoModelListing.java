package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.platform.YoServiceTarget;

/**
 * 一个平台怎样列出自己的模型：请求发往哪台主机、路径与翻页参数、响应怎样解码。
 * 报文差异只放在这里，列表请求的执行和翻页循环由基础设施统一负责。
 */
public interface YoModelListing {

    @NonNull
    YoServiceTarget target();

    /**
     * 请求路径，可带查询串。{@code cursor} 为空表示第一页。
     */
    @NonNull
    String path(@Nullable String cursor);

    @NonNull
    YoModelPage decode(@NonNull String body);
}
