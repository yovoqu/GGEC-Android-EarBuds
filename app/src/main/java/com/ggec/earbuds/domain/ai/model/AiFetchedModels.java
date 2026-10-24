package com.ggec.earbuds.domain.ai.model;

import java.util.List;
import java.util.Set;

/**
 * 一次"拉取模型"的结果（MOD-06）：远端返回的全部模型（接口顺序、带类型）与其中本平台已添加的 ID。
 */
public record AiFetchedModels(String platformId, List<AiModel> remote, Set<String> alreadyAdded) {

    public AiFetchedModels {
        remote = List.copyOf(remote);
        alreadyAdded = Set.copyOf(alreadyAdded);
    }

    public int selectableCount() {
        int count = 0;
        for (AiModel model : remote) {
            if (!alreadyAdded.contains(model.id())) {
                count++;
            }
        }
        return count;
    }
}
