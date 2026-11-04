package com.ggec.earbuds.domain.ai.model;

/**
 * 规格里类别之外的两根轴：能不能看图，会不会推理。
 * 类别本身在 {@link AiModelCategory}，不在这里再编一档。
 */
public enum AiModelFeature {

    VISION,

    REASONING
}
