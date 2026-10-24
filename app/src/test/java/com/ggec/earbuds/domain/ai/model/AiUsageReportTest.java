package com.ggec.earbuds.domain.ai.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.usecase.LoadUsageReport;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 用量页（USE-02…07）。 */
public class AiUsageReportTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final long NOW = at(2025, 10, 24, 15, 0);

    @Test
    public void rangesStartAtLocalMidnight() {
        assertEquals(at(2025, 10, 24, 0, 0), AiUsageReport.rangeStart(AiUsageReport.Range.TODAY, NOW, ZONE));
        assertEquals(at(2025, 10, 18, 0, 0), AiUsageReport.rangeStart(AiUsageReport.Range.LAST_7_DAYS, NOW, ZONE));
        assertEquals(at(2025, 9, 25, 0, 0), AiUsageReport.rangeStart(AiUsageReport.Range.LAST_30_DAYS, NOW, ZONE));

        List<AiUsageRecord> records = List.of(
                record(at(2025, 10, 24, 0, 0), "a", 1, 1, null),
                record(at(2025, 10, 23, 23, 59), "a", 1, 1, null),
                record(at(2025, 10, 18, 0, 0), "a", 1, 1, null),
                record(at(2025, 10, 17, 23, 59), "a", 1, 1, null),
                record(at(2024, 1, 1, 0, 0), "a", 1, 1, null));
        assertEquals(1, report(records, AiUsageReport.Range.TODAY).requests());
        assertEquals(3, report(records, AiUsageReport.Range.LAST_7_DAYS).requests());
        assertEquals(4, report(records, AiUsageReport.Range.LAST_30_DAYS).requests());
        assertEquals(5, report(records, AiUsageReport.Range.ALL).requests());
    }

    @Test
    public void totalsCountFailuresAndShowCostOnlyWhenSomeoneReportedIt() {
        List<AiUsageRecord> records = new ArrayList<>(List.of(
                record(NOW - 10, "a", 100, 20, null),
                failed(NOW - 5, "a")));
        AiUsageReport noCost = report(records, AiUsageReport.Range.TODAY);
        assertEquals(2, noCost.requests());
        assertEquals(1, noCost.failedRequests());
        assertEquals(100L, noCost.inputTokens());
        assertEquals(20L, noCost.outputTokens());
        assertFalse(noCost.showCost());

        records.add(record(NOW - 1, "b", 1, 1, new BigDecimal("0.25")));
        records.add(record(NOW - 1, "b", 1, 1, new BigDecimal("0.50")));
        AiUsageReport priced = report(records, AiUsageReport.Range.TODAY);
        assertTrue(priced.showCost());
        assertEquals(0, new BigDecimal("0.75").compareTo(priced.cost()));
    }

    @Test
    public void sevenModelsBecomeFiveRowsAndOthers() {
        List<AiUsageRecord> records = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            records.add(record(NOW - i, "m" + i, i * 100, 0, null));
        }
        AiUsageReport report = report(records, AiUsageReport.Range.ALL);

        assertEquals(5, report.models().size());
        assertEquals(List.of("m7", "m6", "m5", "m4", "m3"),
                report.models().stream().map(AiUsageReport.ModelRow::modelId).toList());
        assertEquals(2, report.others().requests());
        assertEquals(300L, report.others().tokens());

        records.remove(0);
        records.remove(0);
        assertNull(report(records, AiUsageReport.Range.ALL).others());
    }

    @Test
    public void requestsAreCountedPerSource() {
        List<AiUsageRecord> records = List.of(
                record(NOW, "a", 1, 1, null),
                record(NOW, "a", 1, 1, null).withSource(AiUsageSource.CALL),
                record(NOW, "a", 1, 1, null).withSource(AiUsageSource.SPEED_TEST),
                record(NOW, "a", 1, 1, null).withSource(AiUsageSource.SPEED_TEST),
                record(NOW, "a", 1, 1, null).withSource(AiUsageSource.CONNECTION_TEST));
        AiUsageReport report = report(records, AiUsageReport.Range.TODAY);
        assertEquals(1, report.requests(AiUsageSource.CHAT));
        assertEquals(1, report.requests(AiUsageSource.CALL));
        assertEquals(2, report.requests(AiUsageSource.SPEED_TEST));
        assertEquals(1, report.requests(AiUsageSource.CONNECTION_TEST));
    }

    @Test
    public void loadUsesTheClockAndZoneAndClearEmptiesTheLedger() {
        MemoryUsageStore store = new MemoryUsageStore();
        ManualClock clock = new ManualClock();
        clock.now = NOW;
        store.append(record(NOW - 1, "a", 1, 1, null));
        store.append(record(at(2025, 10, 1, 0, 0), "a", 1, 1, null));
        LoadUsageReport load = new LoadUsageReport(store, clock, () -> ZONE);

        assertEquals(1, load.load(AiUsageReport.Range.TODAY).requests());
        assertEquals(2, load.load(AiUsageReport.Range.ALL).requests());
        load.clear();
        assertTrue(load.load(AiUsageReport.Range.ALL).empty());
    }

    private static AiUsageReport report(List<AiUsageRecord> records, AiUsageReport.Range range) {
        return AiUsageReport.of(records, range, NOW, ZONE);
    }

    private static AiUsageRecord record(long at, String model, int in, int out, BigDecimal cost) {
        return new AiUsageRecord(at, null, "p", model, in, out, 0, cost, 10, AiUsageStatus.SUCCESS, null);
    }

    private static AiUsageRecord failed(long at, String model) {
        return new AiUsageRecord(at, null, "p", model, 0, 0, 0, null, 10, AiUsageStatus.FAILED, "NETWORK");
    }

    private static long at(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ZONE).toInstant().toEpochMilli();
    }
}
