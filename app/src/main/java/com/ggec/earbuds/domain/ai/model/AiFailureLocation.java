package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 持久化失败的结构化坐标：哪份文档、哪个实体、哪个修订号。
 *
 * <p>失败载荷只放这些字段：界面按 {@link AiFailureKind} 出文案，诊断按坐标定位，
 * 不放用户句子，也不放 {@code Throwable#getMessage()} 那种英文技术串。</p>
 */
public record AiFailureLocation(String document, @Nullable String entityId, long revision) {

    /** 与某次提交无关的失败（例如冷启动读取）。 */
    public static final long NO_REVISION = -1L;

    public AiFailureLocation {
        Objects.requireNonNull(document, "document");
    }

    public static AiFailureLocation of(String document) {
        return new AiFailureLocation(document, null, NO_REVISION);
    }

    public static AiFailureLocation of(String document, @Nullable String entityId) {
        return new AiFailureLocation(document, entityId, NO_REVISION);
    }

    public static AiFailureLocation of(String document, @Nullable String entityId, long revision) {
        return new AiFailureLocation(document, entityId, revision);
    }
}
