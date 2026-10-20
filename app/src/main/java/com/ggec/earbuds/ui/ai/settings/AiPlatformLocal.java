package com.ggec.earbuds.ui.ai.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 只属于平台详情页、不需要保存的临时状态：搜索词、排序方式、拉取进度、测速进度。
 * 不可变，ViewModel 每次变化都换一个新的。
 */
public record AiPlatformLocal(
        String query,
        boolean sortBySpeed,
        AiPlatformUiState.Fetch fetch,
        Run run) {

    public static AiPlatformLocal initial() {
        return new AiPlatformLocal("", false, AiPlatformUiState.Fetch.IDLE, Run.IDLE);
    }

    public AiPlatformLocal withQuery(String query) {
        return new AiPlatformLocal(query, sortBySpeed, fetch, run);
    }

    public AiPlatformLocal withSortBySpeed(boolean sortBySpeed) {
        return new AiPlatformLocal(query, sortBySpeed, fetch, run);
    }

    public AiPlatformLocal withFetch(AiPlatformUiState.Fetch fetch) {
        return new AiPlatformLocal(query, sortBySpeed, fetch, run);
    }

    public AiPlatformLocal withRun(Run run) {
        return new AiPlatformLocal(query, sortBySpeed, fetch, run);
    }

    /**
     * 一次测速的进度：还在排队的、正在请求的、已经出结果的各有多少。
     */
    public record Run(boolean active, Set<String> queued, Set<String> running, int done, int total) {

        public static final Run IDLE = new Run(false, Set.of(), Set.of(), 0, 0);

        public Run {
            queued = Set.copyOf(queued);
            running = Set.copyOf(running);
        }

        public static Run started(List<String> modelIds) {
            return new Run(true, Set.copyOf(modelIds), Set.of(), 0, modelIds.size());
        }

        public Run began(String modelId) {
            Set<String> nextQueued = new HashSet<>(queued);
            nextQueued.remove(modelId);
            Set<String> nextRunning = new HashSet<>(running);
            nextRunning.add(modelId);
            return new Run(active, nextQueued, nextRunning, done, total);
        }

        public Run finished(String modelId) {
            Set<String> nextQueued = new HashSet<>(queued);
            nextQueued.remove(modelId);
            Set<String> nextRunning = new HashSet<>(running);
            nextRunning.remove(modelId);
            return new Run(active, nextQueued, nextRunning, Math.min(done + 1, total), total);
        }
    }
}
