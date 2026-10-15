package com.yovo.lib_bluetooth.internal.classic.diagnostics;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 生成 YoCbt SDK 当前状态的结构化诊断转储报告。
 */
public final class YoCbtDumpReport {

    private final LinkedHashMap<String, String> sections;
    private final long createdAt;

    public YoCbtDumpReport() {
        this.sections = new LinkedHashMap<>();
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 在报告中添加或替换一个章节。
     *
     * @param name    章节名称（如 "Active Connections"）
     * @param content 章节内容（可为多行）
     */
    public void addSection(@NonNull String name, @NonNull String content) {
        sections.put(name, content);
    }

    /**
     * 构建并返回完整的结构化转储报告字符串。
     *
     * <p>报告格式：
     * <pre>
     * ========================================
     *   YoCbt Dump Report
     *   Generated: 2025-06-17T12:34:56.789
     * ========================================
     *
     * --- Section: Active Connections ---
     * {content}
     *
     * --- Section: Device Snapshots ---
     * {content}
     * ...
     * ========================================
     *   End of Report
     * ========================================
     * </pre>
     *
     * @return 格式化后的转储报告
     */
    @NonNull
    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("  YoCbt Dump Report\n");
        sb.append("  Generated: ").append(new java.util.Date(createdAt)).append('\n');
        sb.append("========================================\n");
        sb.append('\n');

        if (sections.isEmpty()) {
            sb.append("  (no sections)\n");
        } else {
            for (Map.Entry<String, String> entry : sections.entrySet()) {
                sb.append("--- Section: ").append(entry.getKey()).append(" ---\n");
                sb.append(entry.getValue());
                if (!entry.getValue().endsWith("\n")) {
                    sb.append('\n');
                }
                sb.append('\n');
            }
        }

        sb.append("========================================\n");
        sb.append("  End of Report\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    /**
     * 返回本报告中的章节数量。
     */
    public int sectionCount() {
        return sections.size();
    }

    /**
     * 返回本报告的创建时间戳。
     */
    public long getCreatedAt() {
        return createdAt;
    }
}
