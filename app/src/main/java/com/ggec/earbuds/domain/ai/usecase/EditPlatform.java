package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiPlatformNamePolicy;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 平台页的即时保存（PLT-04/08、ADV-02/04、CON-03）：每个方法只改一项，失焦或选择后立即落盘。
 * 不合法时抛 {@link AiValidationException}，存储不变。模板不可改（PLT-05）。
 */
public final class EditPlatform {

    private final AiSettingsRepository repository;
    private final AiPlatformCatalog catalog;
    private final AiApiKeyPolicy keyPolicy;
    private final AiBaseUrlPolicy urlPolicy;

    public EditPlatform(AiSettingsRepository repository, AiPlatformCatalog catalog, AiApiKeyPolicy keyPolicy,
                        AiBaseUrlPolicy urlPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.keyPolicy = Objects.requireNonNull(keyPolicy, "keyPolicy");
        this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
    }

    public void rename(String platformId, String raw) {
        update(platformId, (settings, platform) -> {
            List<String> others = new ArrayList<>();
            for (AiPlatformConfig other : settings.platforms()) {
                if (!other.id().equals(platformId)) {
                    others.add(other.name());
                }
            }
            AiPlatformNamePolicy.Error error = AiPlatformNamePolicy.validate(raw, others);
            if (error != null) {
                throw new AiValidationException(switch (error) {
                    case EMPTY -> AiValidationError.NAME_EMPTY;
                    case TOO_LONG -> AiValidationError.NAME_TOO_LONG;
                    case DUPLICATE -> AiValidationError.NAME_DUPLICATE;
                });
            }
            return platform.withName(raw.strip());
        });
    }

    /** 空字符串＝清除 API Key。 */
    public void setApiKey(String platformId, @Nullable String raw) {
        String key = raw == null || raw.isBlank() ? "" : keyPolicy.normalize(raw);
        update(platformId, (settings, platform) -> platform.withApiKey(key));
    }

    /**
     * 改接口地址。等于模板默认地址或为空（非自定义模板）时存为"用默认"。
     */
    public void setBaseUrl(String platformId, @Nullable String raw) {
        update(platformId, (settings, platform) -> {
            AiPlatformTemplate template = template(platform);
            String value = raw == null ? "" : raw.strip();
            String normalized = urlPolicy.normalize(value, template.custom());
            if (normalized != null && !template.custom() && sameAsDefault(normalized, template)) {
                normalized = null;
            }
            return platform.withBaseUrl(normalized);
        });
    }

    public void resetBaseUrl(String platformId) {
        update(platformId, (settings, platform) -> platform.withBaseUrl(null));
    }

    public void setProtocol(String platformId, AiProtocol protocol) {
        update(platformId, (settings, platform) -> {
            if (!template(platform).protocols().contains(protocol)) {
                throw new AiValidationException(AiValidationError.PROTOCOL_UNSUPPORTED);
            }
            return platform.withProtocol(protocol);
        });
    }

    /** 连接卡"测试模型"（CON-03）。传空回到默认规则。 */
    public void setTestModel(String platformId, @Nullable String modelId) {
        update(platformId, (settings, platform) -> {
            if (modelId != null) {
                AiModel model = platform.model(modelId)
                        .orElseThrow(() -> new AiValidationException(AiValidationError.MODEL_UNKNOWN));
                if (!model.chat()) {
                    throw new AiValidationException(AiValidationError.MODEL_NOT_CHAT);
                }
            }
            return platform.withTestModel(modelId);
        });
    }

    private boolean sameAsDefault(String normalized, AiPlatformTemplate template) {
        try {
            return normalized.equals(catalog.normalizeBaseUrl(template.defaultBaseUrl()));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private AiPlatformTemplate template(AiPlatformConfig platform) {
        return catalog.find(platform.templateId())
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
    }

    private interface Change {
        AiPlatformConfig apply(AiSettings settings, AiPlatformConfig platform);
    }

    private void update(String platformId, Change change) {
        UnaryOperator<AiSettings> operator = settings -> {
            AiPlatformConfig platform = settings.platform(platformId)
                    .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
            AiPlatformConfig next = change.apply(settings, platform);
            return next.equals(platform) ? settings : settings.withPlatform(next);
        };
        repository.update(operator);
    }
}
