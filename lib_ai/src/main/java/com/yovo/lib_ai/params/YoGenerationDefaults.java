package com.yovo.lib_ai.params;

import androidx.annotation.NonNull;

/**
 * SDK 建议的生成参数默认值。
 *
 * <p>这套值是"产品口径"而不是"平台默认"：同一句话在不同平台上跑出不同风格时，
 * 用户没法判断是模型差异还是自己没设。收在一处，出问题只查这个文件。
 *
 * <p>注意上下文条数不在这里。那是"带多少历史消息"的会话策略，不进请求体，
 * 由调用方按自己的产品决定，SDK 不替它定。
 */
public final class YoGenerationDefaults {

    /** 0.7：比 0 灵活、比 1 稳定，适合口语化问答。 */
    public static final double TEMPERATURE = 0.7;

    /**
     * 1.0 表示不做核采样截断。
     *
     * <p>温度已经在控制随机性，再把概率尾巴也砍掉，两个参数会互相放大，
     * 输出变得难以预期；只留温度一个旋钮更好解释。
     */
    public static final double TOP_P = 1.0;

    /** 4096：与 Anthropic 协议的兜底值一致，给推理型模型留出思考空间。 */
    public static final int MAX_OUTPUT_TOKENS = 4096;

    private YoGenerationDefaults() {
    }

    /** 默认参数，可直接用于 {@code resolve} 或直接发请求。 */
    @NonNull
    public static YoGenerationParams params() {
        return YoGenerationParams.of(TEMPERATURE, TOP_P, MAX_OUTPUT_TOKENS);
    }
}
