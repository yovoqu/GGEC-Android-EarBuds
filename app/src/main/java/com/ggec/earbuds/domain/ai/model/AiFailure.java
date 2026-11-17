package com.ggec.earbuds.domain.ai.model;

import androidx.annotation.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * 领域失败：只有分类，加上两类互斥的结构化载荷。
 *
 * <ul>
 *   <li>平台/传输失败：载荷是网关给出的技术说明（已脱敏、可空），例如 401 的原因；</li>
 *   <li>数据层失败：载荷是文档坐标 {@link AiFailureLocation}（文档名、实体 id、修订号），
 *       不带任何英文技术串。</li>
 * </ul>
 *
 * <p>用户可见文案由界面按 {@link #kind()} 出，本类不带句子。</p>
 */
public final class AiFailure extends RuntimeException {

    private final AiFailureKind kind;
    @Nullable
    private final AiFailureLocation location;

    /** 平台/传输失败。 */
    public AiFailure(AiFailureKind kind, @Nullable String technicalDetail) {
        this(kind, technicalDetail, (Throwable) null);
    }

    /** 平台/传输失败。 */
    public AiFailure(AiFailureKind kind, @Nullable String technicalDetail, @Nullable Throwable cause) {
        super(technicalDetail, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.location = null;
    }

    /** 数据层失败：载荷是文档坐标，不是技术说明。 */
    public AiFailure(AiFailureKind kind, AiFailureLocation location, @Nullable Throwable cause) {
        super(null, cause);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.location = Objects.requireNonNull(location, "location");
    }

    public AiFailureKind kind() {
        return kind;
    }

    /** 数据层失败才有坐标；平台/传输失败是 {@link Optional#empty()}。 */
    public Optional<AiFailureLocation> location() {
        return Optional.ofNullable(location);
    }
}
