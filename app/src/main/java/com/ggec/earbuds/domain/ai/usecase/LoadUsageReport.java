package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageReport;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 读用量流水并按范围出报告（USE-01…07）。读盘失败按"没有用量"处理：用量是参考信息，
 * 不该让设置页打不开。阻塞调用。
 */
public final class LoadUsageReport {

    private final AiUsageStore store;
    private final AiClock clock;
    private final Supplier<ZoneId> zone;

    public LoadUsageReport(AiUsageStore store, AiClock clock, Supplier<ZoneId> zone) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public AiUsageReport load(AiUsageReport.Range range) {
        List<AiUsageRecord> records;
        try {
            records = store.readAll();
        } catch (RuntimeException unreadable) {
            records = List.of();
        }
        return AiUsageReport.of(records, range, clock.now(), zone.get());
    }

    /** USE-07 清空用量记录。 */
    public void clear() {
        store.clear();
    }
}
