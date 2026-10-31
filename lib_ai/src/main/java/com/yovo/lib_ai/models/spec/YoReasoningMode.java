package com.yovo.lib_ai.models.spec;

import androidx.annotation.NonNull;

/**
 * 模型怎么"想"。把"会不会推理"和"能不能关"放在一起，是因为调用方要的就是这两件事：
 * 前者决定显示不显示思考过程，后者决定请求里能不能带"关闭思考"。
 */
public enum YoReasoningMode {
    /** 没人说过。 */
    UNKNOWN("未知"),
    /** 明确不具备推理。 */
    NONE("无推理"),
    /** 会推理，但能不能关、怎么开关没人说清。 */
    SUPPORTED("支持推理"),
    /** 只会思考，关不掉（deepseek-reasoner、o 系列这一类）。 */
    ALWAYS("强制思考"),
    /** 混合思考：同一个模型可以开，也可以关。 */
    SWITCHABLE("可开关");

    private final String label;

    YoReasoningMode(String label) {
        this.label = label;
    }

    @NonNull
    public String label() {
        return label;
    }

    /**
     * 会产出思考内容。
     */
    public boolean reasons() {
        return this == SUPPORTED || this == ALWAYS || this == SWITCHABLE;
    }
}
