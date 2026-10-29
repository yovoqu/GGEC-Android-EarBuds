package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;

/**
 * 模型在服务端具备的、可以单独开关或不具备的能力。
 *
 * <p>这个枚举只放"调用时能多用一手"的能力。旧版把它们和模态（看图、听音）、
 * 服务类别（实时、转写、合成、向量）混在同一个枚举里，一个"全模态"模型根本无从表达；
 * 现在各归各位：模态在 {@link YoModality}，类别在 {@link YoModelCategory}，推理单列在 {@link YoReasoning}。</p>
 *
 * <p>平台没声明不代表不支持，所以是否具备用 {@link YoFact} 三态表达，不是简单的集合成员。</p>
 */
public enum YoModelCapability {
    /** 函数调用（tools / tool_choice）。 */
    FUNCTION_CALLING("函数调用"),
    /** 结构化输出（JSON 模式或按 JSON Schema 约束）。 */
    STRUCTURED_OUTPUT("结构化输出"),
    /** 联网搜索（服务端自带检索，不是调用方自己接的工具）。 */
    WEB_SEARCH("联网搜索"),
    /** 前缀续写：把助手消息的开头固定下来，让模型接着往下写。 */
    PREFIX_COMPLETION("前缀续写"),
    /** 上下文缓存（Prompt Caching），重复前缀命中后更快更省。 */
    CONTEXT_CACHE("上下文缓存"),
    /** 批量推理（Batch API），异步提交一批请求。 */
    BATCH("批量推理");

    private final String label;

    YoModelCapability(String label) {
        this.label = label;
    }

    /**
     * 拼错误提示和界面标签用的人话名。
     */
    @NonNull
    public String label() {
        return label;
    }
}
