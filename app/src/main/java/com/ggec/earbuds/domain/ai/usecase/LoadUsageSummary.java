package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageSummary;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.util.List;
import java.util.Objects;

/**
 * 读回用量流水并聚合成一句能给用户看的话所需的数字。
 *
 * <p>为什么聚合不放这里而放在 {@link AiUsageSummary}：那个类是一组纯函数，可以被单测直接喂数据；
 * 这个用例只做"读盘 + 交给它"。读盘失败时返回空汇总，而不是抛给界面——
 * 用量是参考信息，不该因为它让设置页打不开。</p>
 */
public final class LoadUsageSummary {

    private final AiUsageStore store;

    public LoadUsageSummary(@NonNull AiUsageStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** 全部时间的汇总。 */
    @NonNull
    public AiUsageSummary load() {
        try {
            List<AiUsageRecord> records = store.readAll();
            return AiUsageSummary.summarize(records);
        } catch (RuntimeException unreadable) {
            return AiUsageSummary.summarize(List.of());
        }
    }
}
