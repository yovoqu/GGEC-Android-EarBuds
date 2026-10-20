package com.yovo.lib_ai.probe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 一次测速的汇总。排名只看成功的模型：首字延迟短的在前，相同再比总耗时。
 */
public final class YoProbeSummary {

    private final List<YoProbeResult> results;
    private final boolean cancelled;

    public YoProbeSummary(@NonNull List<YoProbeResult> results, boolean cancelled) {
        this.results = List.copyOf(results);
        this.cancelled = cancelled;
    }

    /**
     * 按完成顺序排列的全部结果。
     */
    @NonNull
    public List<YoProbeResult> results() {
        return results;
    }

    public boolean cancelled() {
        return cancelled;
    }

    /**
     * 成功的在前并按速度排序，失败与超时按完成顺序排在后面。
     */
    @NonNull
    public List<YoProbeResult> ranked() {
        List<YoProbeResult> ok = new ArrayList<>();
        List<YoProbeResult> failed = new ArrayList<>();
        for (YoProbeResult result : results) {
            (result.ok() ? ok : failed).add(result);
        }
        ok.sort(Comparator.comparingLong(YoProbeResult::firstTokenMillis)
                .thenComparingLong(YoProbeResult::totalMillis));
        ok.addAll(failed);
        return ok;
    }

    @Nullable
    public YoProbeResult fastest() {
        List<YoProbeResult> ranked = ranked();
        return ranked.isEmpty() || !ranked.get(0).ok() ? null : ranked.get(0);
    }
}
