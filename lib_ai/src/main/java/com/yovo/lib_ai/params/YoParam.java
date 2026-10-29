package com.yovo.lib_ai.params;

/**
 * 可以单独解析和裁剪的生成参数。
 *
 * <p>只有这三项：它们会真正出现在请求体里，且各协议的取值范围不同。
 * 上下文条数是"发多少历史"，属于调用方的会话策略，不在请求参数里，因此不在此列。
 */
public enum YoParam {
    /** 采样温度。 */
    TEMPERATURE,
    /** 核采样累积概率。 */
    TOP_P,
    /** 最大输出 token。 */
    MAX_OUTPUT
}
