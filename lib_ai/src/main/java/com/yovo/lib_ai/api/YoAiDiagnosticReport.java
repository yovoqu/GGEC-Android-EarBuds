package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * lib_ai 结构化只读诊断快照（供工单排查与自动化断言）。
 */
public final class YoAiDiagnosticReport {

    private final long generatedAtMillis;
    @NonNull
    private final List<String> timeline;
    @NonNull
    private final String dumpText;

    public YoAiDiagnosticReport(long generatedAtMillis,
                                @NonNull List<String> timeline,
                                @NonNull String dumpText) {
        this.generatedAtMillis = generatedAtMillis;
        this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
        this.dumpText = Objects.requireNonNull(dumpText, "dumpText");
    }

    public long generatedAtMillis() {
        return generatedAtMillis;
    }

    @NonNull
    public List<String> timeline() {
        return timeline;
    }

    @NonNull
    public String dumpText() {
        return dumpText;
    }
}
