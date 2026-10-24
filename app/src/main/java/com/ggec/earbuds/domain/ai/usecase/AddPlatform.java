package com.ggec.earbuds.domain.ai.usecase;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiPlatformNamePolicy;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 添加平台（PLT-02/03）：选模板即创建。同一模板可建多份，默认名＝模板名，重名加最小未用序号。
 * 接口地址留空＝用模板默认地址；协议取模板的第一个。
 */
public final class AddPlatform {

    private static final int ID_LENGTH = 8;
    private static final int MAX_ID_ATTEMPTS = 8;

    private final AiSettingsRepository repository;
    private final AiPlatformCatalog catalog;
    private final AiIdGenerator ids;

    public AddPlatform(AiSettingsRepository repository, AiPlatformCatalog catalog, AiIdGenerator ids) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public AiPlatformConfig execute(String templateId) {
        AiPlatformTemplate template = catalog.find(templateId)
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
        AtomicReference<AiPlatformConfig> created = new AtomicReference<>();
        repository.update(settings -> {
            List<String> names = new ArrayList<>();
            for (AiPlatformConfig platform : settings.platforms()) {
                names.add(platform.name());
            }
            String name = AiPlatformNamePolicy.defaultName(template.name(), names);
            AiPlatformConfig platform = AiPlatformConfig.create(newId(template.id(), settings), template.id(),
                    name, template.defaultProtocol());
            created.set(platform);
            return settings.withPlatform(platform);
        });
        return created.get();
    }

    /** "{模板}-{8 位随机}"；撞上已有 ID 就换一个，连续撞车用完整随机串，保证一定能结束。 */
    private String newId(String templateId, AiSettings settings) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String raw = ids.next().replace("-", "");
            String id = templateId + "-" + raw.substring(0, Math.min(ID_LENGTH, raw.length()));
            if (settings.platform(id).isEmpty()) {
                return id;
            }
        }
        return templateId + "-" + ids.next().replace("-", "");
    }
}
