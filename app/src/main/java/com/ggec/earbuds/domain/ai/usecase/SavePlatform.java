package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 新增或修改一个平台的连接信息。内置平台一类只有一个实例，自定义平台每次新增都生成新实例。
 * 换了主机地址会清掉旧的模型缓存，换密钥不会。
 */
public final class SavePlatform {

    private static final String CUSTOM_PREFIX = "custom-";
    private static final int ID_LENGTH = 8;
    private static final int MAX_ID_ATTEMPTS = 8;
    private static final int MAX_NAME_LENGTH = 40;

    public record Command(
            @Nullable String platformId,
            String templateId,
            @Nullable String name,
            String apiKey,
            @Nullable String baseUrl,
            @Nullable AiProtocol protocol) {
    }

    private final AiSettingsRepository repository;
    private final AiPlatformCatalog catalog;
    private final AiApiKeyPolicy keyPolicy;
    private final AiBaseUrlPolicy urlPolicy;
    private final AiIdGenerator ids;
    private final AiCredentialStore credentials;

    public SavePlatform(
            AiSettingsRepository repository,
            AiPlatformCatalog catalog,
            AiApiKeyPolicy keyPolicy,
            AiBaseUrlPolicy urlPolicy,
            AiIdGenerator ids,
            AiCredentialStore credentials) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.keyPolicy = Objects.requireNonNull(keyPolicy, "keyPolicy");
        this.urlPolicy = Objects.requireNonNull(urlPolicy, "urlPolicy");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
    }

    public AiPlatformConfig execute(Command command) {
        AiPlatformTemplate template = catalog.find(command.templateId())
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
        String key = keyPolicy.normalize(command.apiKey());
        String baseUrl = urlPolicy.normalize(command.baseUrl(), template.custom());
        AiProtocol protocol = command.protocol() == null ? template.defaultProtocol() : command.protocol();
        if (!template.protocols().contains(protocol)) {
            throw new AiValidationException(AiValidationError.PROTOCOL_UNSUPPORTED);
        }
        String name = displayName(template, command.name());
        AiSettings existing = repository.load();
        String platformId = resolveId(command, template, existing);
        // 密钥先进凭证库；配置对象只记录"已配置"这个事实。
        credentials.putPlatformKey(platformId, key);
        boolean configured = !key.isBlank();
        AtomicReference<AiPlatformConfig> saved = new AtomicReference<>();
        repository.update(settings -> {
            AiPlatformConfig base = settings.platform(platformId)
                    .orElseGet(() -> AiPlatformConfig.create(platformId, template.id(), name, protocol));
            AiPlatformConfig updated = base.withConnection(name, configured, baseUrl, protocol);
            saved.set(updated);
            return settings.withPlatform(updated);
        });
        return saved.get();
    }

    private String resolveId(Command command, AiPlatformTemplate template, AiSettings existing) {
        if (command.platformId() != null) {
            if (existing.platform(command.platformId()).isEmpty()) {
                throw new AiValidationException(AiValidationError.PLATFORM_UNKNOWN);
            }
            return command.platformId();
        }
        if (template.custom()) {
            return newCustomId(existing.platforms());
        }
        return template.id();
    }

    private static String displayName(AiPlatformTemplate template, @Nullable String requested) {
        if (!template.custom()) {
            return template.name();
        }
        String name = requested == null ? "" : requested.strip();
        if (name.isEmpty()) {
            throw new AiValidationException(AiValidationError.NAME_EMPTY);
        }
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /**
     * 取随机 id 的前 8 位，撞上已有的就换一个；连续撞车（几乎不可能）就用完整 id，保证一定能结束。
     */
    private String newCustomId(List<AiPlatformConfig> existing) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String raw = ids.next().replace("-", "");
            String id = CUSTOM_PREFIX + raw.substring(0, Math.min(ID_LENGTH, raw.length()));
            if (!taken(existing, id)) {
                return id;
            }
        }
        return CUSTOM_PREFIX + ids.next().replace("-", "");
    }

    private static boolean taken(List<AiPlatformConfig> existing, String id) {
        for (AiPlatformConfig platform : existing) {
            if (platform.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
