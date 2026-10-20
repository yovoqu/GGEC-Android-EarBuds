package com.ggec.earbuds.domain.ai.model;

import java.util.Objects;

/**
 * 当前选中且可用的模型：平台已配置密钥，模型确实在平台的列表里。
 */
public record AiActiveModel(AiPlatformConfig platform, AiModel model) {

    public AiActiveModel {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(model, "model");
    }
}
