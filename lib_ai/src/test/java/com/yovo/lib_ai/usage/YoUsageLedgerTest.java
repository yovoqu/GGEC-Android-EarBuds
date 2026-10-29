package com.yovo.lib_ai.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.chat.YoChatUsage;
import com.yovo.lib_ai.messages.YoMessageUsage;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

public final class YoUsageLedgerTest {

    private static YoUsageRecord record(String platform, String model, YoUsageSource source,
                                        int input, int output, String cost, String currency) {
        return YoUsageRecord.builder()
                .platformId(platform)
                .model(model)
                .source(source)
                .tokens(input, output)
                .cost(cost == null ? null : new BigDecimal(cost))
                .currency(currency)
                .atMillis(1_700_000_000_000L)
                .build();
    }

    @Test
    public void recordsAccumulateIntoLifetimeTotals() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record("openai", "gpt-4o", YoUsageSource.CHAT_COMPLETIONS, 100, 50, "0.01", "USD"));
        ledger.record(record("openai", "gpt-4o", YoUsageSource.CHAT_COMPLETIONS, 200, 80, "0.02", "USD"));

        YoUsageTotals overall = ledger.summary().overall();
        assertEquals(2, overall.requests());
        assertEquals(300, overall.inputTokens());
        assertEquals(130, overall.outputTokens());
        assertEquals(430, overall.totalTokens());
        assertEquals(0, new BigDecimal("0.03").compareTo(overall.cost("USD")));
    }

    @Test
    public void costsAreKeptPerCurrencyAndNeverAddedTogether() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record("openai", "gpt-4o", YoUsageSource.CHAT_COMPLETIONS, 10, 10, "0.50", "USD"));
        ledger.record(record("deepseek", "deepseek-chat", YoUsageSource.CHAT_COMPLETIONS, 10, 10, "1.50", "CNY"));

        YoUsageTotals overall = ledger.summary().overall();
        assertEquals(0, new BigDecimal("0.50").compareTo(overall.cost("USD")));
        assertEquals(0, new BigDecimal("1.50").compareTo(overall.cost("CNY")));
        assertEquals(0, BigDecimal.ZERO.compareTo(overall.cost("EUR")));
        assertEquals(2, overall.costs().size());
    }

    @Test
    public void evictionShrinksTheWindowButNotTheTotals() {
        YoUsageLedger ledger = new YoMemoryUsageLedger(2);
        ledger.record(record("a", "m", YoUsageSource.CHAT_COMPLETIONS, 1, 1, null, null));
        ledger.record(record("a", "m", YoUsageSource.CHAT_COMPLETIONS, 2, 2, null, null));
        ledger.record(record("a", "m", YoUsageSource.CHAT_COMPLETIONS, 3, 3, null, null));

        assertEquals(2, ledger.size());
        List<YoUsageRecord> kept = ledger.records();
        assertEquals(2, kept.get(0).inputTokens());
        assertEquals(3, kept.get(1).inputTokens());
        assertEquals(3, ledger.summary().overall().requests());
        assertEquals(6, ledger.summary().overall().inputTokens());
    }

    @Test
    public void breakdownsEachReconcileWithTheOverallTotals() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record("openai", "gpt-4o", YoUsageSource.CHAT_COMPLETIONS, 100, 50, "0.01", "USD"));
        ledger.record(record("anthropic", "claude", YoUsageSource.ANTHROPIC_MESSAGES, 40, 20, "0.02", "USD"));
        ledger.record(record("openai", "gpt-4o-mini", YoUsageSource.RESPONSES, 10, 5, "0.03", "USD"));

        YoUsageSummary summary = ledger.summary();
        assertEquals(110, summary.byPlatform("openai").inputTokens());
        assertEquals(40, summary.byPlatform("anthropic").inputTokens());
        assertEquals(100, summary.byModel("gpt-4o").inputTokens());
        assertEquals(100, summary.bySource(YoUsageSource.CHAT_COMPLETIONS).inputTokens());
        assertEquals(10, summary.bySource(YoUsageSource.RESPONSES).inputTokens());
        assertEquals(summary.overall().inputTokens(), summary.bySource(YoUsageSource.ANTHROPIC_MESSAGES)
                .inputTokens() + summary.bySource(YoUsageSource.CHAT_COMPLETIONS).inputTokens()
                + summary.bySource(YoUsageSource.RESPONSES).inputTokens());
    }

    @Test
    public void missingPlatformAndModelFallUnderUnspecified() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record(null, null, YoUsageSource.PROBE, 8, 0, null, null));

        YoUsageSummary summary = ledger.summary();
        assertEquals(1, summary.byPlatform(YoUsageRecord.UNSPECIFIED).requests());
        assertEquals(1, summary.byModel(YoUsageRecord.UNSPECIFIED).requests());
        assertEquals(1, summary.overall().requests());
        assertFalse(summary.overall().hasCosts());
    }

    @Test
    public void clearResetsBothWindowAndTotals() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record("a", "m", YoUsageSource.CHAT_COMPLETIONS, 5, 5, "1.00", "USD"));
        ledger.clear();

        assertEquals(0, ledger.size());
        assertTrue(ledger.records().isEmpty());
        assertTrue(ledger.summary().isEmpty());
        assertTrue(ledger.summary().byPlatform().isEmpty());
    }

    @Test
    public void chatFactoryMapsPromptCompletionAndCacheFields() {
        YoChatUsage usage = new YoChatUsage(120, 30, 150, 64, 16, new BigDecimal("0.04"));
        YoUsageRecord record = YoUsageRecords.chat(usage, YoUsageSource.CHAT_COMPLETIONS,
                "openai", "gpt-4o", "usd");

        assertEquals(120, record.inputTokens());
        assertEquals(30, record.outputTokens());
        assertEquals(150, record.totalTokens());
        assertEquals(Integer.valueOf(64), record.cachedInputTokens());
        assertEquals(Integer.valueOf(16), record.cacheCreationInputTokens());
        assertEquals(0, new BigDecimal("0.04").compareTo(record.cost()));
        assertEquals("USD", record.currency());
        assertEquals("openai", record.platformId());
    }

    @Test
    public void messageFactoryDerivesTotalBecauseAnthropicOmitsIt() {
        YoMessageUsage usage = new YoMessageUsage(70, 20, 10, null, new BigDecimal("0.09"));
        YoUsageRecord record = YoUsageRecords.message(usage, YoUsageSource.ANTHROPIC_MESSAGES,
                "anthropic", "claude-3-5-sonnet", "USD");

        assertEquals(90, record.totalTokens());
        assertEquals(Integer.valueOf(10), record.cachedInputTokens());
        assertNull(record.cacheCreationInputTokens());
    }

    @Test
    public void recordWithoutReportedCostKeepsTheCurrencyButNotTheMoney() {
        YoUsageRecord record = record("openai", "gpt-4o", YoUsageSource.CHAT_COMPLETIONS, 10, 10, null, "USD");

        assertFalse(record.hasCost());
        assertNull(record.cost());
        assertEquals("USD", record.currency());
    }

    @Test
    public void summaryOfRecomputedFromRecordsMatchesTheLedger() {
        YoUsageLedger ledger = new YoMemoryUsageLedger();
        ledger.record(record("a", "m1", YoUsageSource.CHAT_COMPLETIONS, 10, 5, "0.10", "USD"));
        ledger.record(record("b", "m2", YoUsageSource.PROBE, 3, 0, "0.01", "USD"));

        YoUsageSummary recomputed = YoUsageSummary.of(ledger.records());
        assertEquals(ledger.summary().overall().requests(), recomputed.overall().requests());
        assertEquals(ledger.summary().overall().inputTokens(), recomputed.overall().inputTokens());
        assertEquals(0, ledger.summary().overall().cost("USD")
                .compareTo(recomputed.overall().cost("USD")));
        assertEquals(ledger.summary().byModel().keySet(), recomputed.byModel().keySet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTokensAreRejected() {
        YoUsageRecord.builder()
                .source(YoUsageSource.CHAT_COMPLETIONS)
                .tokens(-1, 0)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void ledgerCapacityMustBePositive() {
        new YoMemoryUsageLedger(0);
    }
}
