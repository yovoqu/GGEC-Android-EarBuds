package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 成功的模型在前，首字延迟短的优先，相同再比总耗时；失败和超时按给定顺序排在最后。
 */
public final class AiSpeedRanking {

    public List<AiSpeedResult> rank(Collection<AiSpeedResult> results) {
        List<AiSpeedResult> ok = new ArrayList<>();
        List<AiSpeedResult> failed = new ArrayList<>();
        for (AiSpeedResult result : results) {
            (result.ok() ? ok : failed).add(result);
        }
        ok.sort(Comparator.comparingLong(AiSpeedResult::firstTokenMillis)
                .thenComparingLong(AiSpeedResult::totalMillis));
        ok.addAll(failed);
        return ok;
    }

    public Optional<AiSpeedResult> fastest(Collection<AiSpeedResult> results) {
        List<AiSpeedResult> ranked = rank(results);
        return ranked.isEmpty() || !ranked.get(0).ok() ? Optional.empty() : Optional.of(ranked.get(0));
    }
}
