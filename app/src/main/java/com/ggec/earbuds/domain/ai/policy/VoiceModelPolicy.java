package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * 语音通信用哪些模型：只留能接实时会话的那些。
 *
 * <p>服务商 {@code /v1/models} 返回的是整个账号目录（聊天、向量、语音合成、图像……），
 * 实时语音只需要 {@code realtime} 一类。这个判断属于领域策略，不属于数据网关：
 * 网关只负责"按目标拉原始列表"，筛选规则在这里集中，换服务商/改规则都不用碰 I/O 层。</p>
 */
public final class VoiceModelPolicy {

    /** 原始列表 → 实时语音可用的模型；顺序保持调用方给的顺序。 */
    @NonNull
    public List<AiModel> realtimeOnly(@NonNull List<AiModel> models) {
        List<AiModel> usable = new ArrayList<>(models.size());
        for (AiModel model : models) {
            if (model != null && model.category() == AiModelCategory.REALTIME) {
                usable.add(model);
            }
        }
        return List.copyOf(usable);
    }
}
