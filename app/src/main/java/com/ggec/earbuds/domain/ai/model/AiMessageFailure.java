package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 一条助手消息为什么没有正常结束：只承载分类，随消息保存，重新打开对话时仍能看到原因。
 *
 * <p>结构化标识就是它所属的 {@link AiMessage#id()}：失败与消息一一对应，不需要再存一遍。
 * 技术说明（{@code Throwable#getMessage()} 那类英文串）不允许进领域载荷——界面按
 * {@link AiFailureKind} 出资源文案，诊断定位用消息 id。</p>
 */
public record AiMessageFailure(AiFailureKind kind) {

    public AiMessageFailure {
        Objects.requireNonNull(kind, "kind");
    }
}
