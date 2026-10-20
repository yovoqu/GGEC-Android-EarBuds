package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import com.yovo.lib_ai.models.YoModelInfo;

import java.util.List;

/**
 * 列出一个平台当前账号可用的模型。翻页在内部走完，返回合并后的完整列表。
 * 在调用线程上阻塞，不要在主线程调用。
 */
public interface YoModels {

    /**
     * 返回平台给出的全部模型，包含非对话模型；用 {@link YoModelInfo#kind()} 自行筛选。
     */
    @NonNull
    List<YoModelInfo> list();
}
