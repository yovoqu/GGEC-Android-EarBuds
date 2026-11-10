package com.yovo.lib_ai.api;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 诊断数据来源抽象（由装配根提供实现，内部绑定 {@code YoAiDiagnosticsBus}）。
 * 放在公开包是为了让 {@link YoAiClient} 不直接依赖 internal 实现。
 */
public interface YoAiDiagnosticSource {

    /** 近期诊断事件时间线（按时间顺序，最新在末尾）。 */
    @NonNull
    List<String> timeline();

    /** 结构化诊断快照文本。 */
    @NonNull
    String dumpReport();
}
