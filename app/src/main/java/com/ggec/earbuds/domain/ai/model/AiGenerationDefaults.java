package com.ggec.earbuds.domain.ai.model;

/**
 * 生成参数的默认值：不再开放给用户调整，APP 每次发请求时按这一套填。
 *
 * <p>为什么从"留空让平台决定"改成"我们自己定"：同一句话在不同平台上跑出不同风格，
 * 用户没法判断是模型差异还是自己没设；而设置页那几个滑杆，实际会去调的人几乎没有。
 * 与其留一堆没人动的控件，不如把产品口径收在一处——出问题只查这个文件。</p>
 *
 * <p>这里的值只影响"发出去什么"，不影响用户能改什么：设置页现在只剩系统提示词。</p>
 */
public final class AiGenerationDefaults {

    /** 0.7：比 0 灵活、比 1 稳定，适合口语化问答。 */
    public static final double TEMPERATURE = 0.7;

    /**
     * 1.0 表示不做核采样截断。
     *
     * <p>温度已经在控制随机性，再把概率尾巴也砍掉，两个参数会互相放大，
     * 输出变得难以预期；只留温度一个旋钮更好解释。</p>
     */
    public static final double TOP_P = 1.0;

    /** 4096：与 lib_ai 里 Anthropic 协议的兜底值一致，给推理型模型留出思考空间。 */
    public static final int MAX_OUTPUT_TOKENS = 4096;

    /** 每次请求最多带 20 条历史消息；再多既贵，又容易把很早的话题拖回来。 */
    public static final int CONTEXT_MESSAGES = 20;

    private AiGenerationDefaults() {
    }
}
