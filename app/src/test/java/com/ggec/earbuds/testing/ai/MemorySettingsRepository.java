package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.data.ai.store.EncryptedCredentialStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 领域测试用的设置仓库：没有磁盘，只保证"读-改-写-通知"的语义。
 *
 * <p>密钥写进注入的 {@link SecretStore}，与生产完全同形：同一个提交点里既写密文、又算出
 * "已配置"事实。需要让凭证读取端看到同一份密钥时，用同一个 SecretStore 构造
 * {@code EncryptedCredentialStore} 注入即可。</p>
 */
public final class MemorySettingsRepository implements AiSettingsRepository {

    private final SecretStore secrets;
    private AiSettings current;
    private final List<Consumer<AiSettings>> observers = new ArrayList<>();
    public int updates;

    public MemorySettingsRepository() {
        this(AiSettings.EMPTY, new MemorySecretStore());
    }

    public MemorySettingsRepository(AiSettings initial) {
        this(initial, new MemorySecretStore());
    }

    public MemorySettingsRepository(AiSettings initial, SecretStore secrets) {
        this.current = initial;
        this.secrets = Objects.requireNonNull(secrets, "secrets");
    }

    @Override
    public AiSettings load() {
        return current;
    }

    @Override
    public CompletableFuture<AiSettings> loadAsync() {
        return CompletableFuture.completedFuture(current);
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        AiSettings next = change.apply(current);
        if (!next.equals(current)) {
            current = next;
            updates++;
            for (Consumer<AiSettings> observer : new ArrayList<>(observers)) {
                observer.accept(next);
            }
        }
        return current;
    }

    @Override
    public AiPlatformConfig savePlatform(AiPlatformConfig platform, String apiKey) {
        putSecret(EncryptedCredentialStore.platformAlias(platform.id()), apiKey);
        AiPlatformConfig configured = platform.withConnection(platform.name(),
                secrets.get(EncryptedCredentialStore.platformAlias(platform.id())) != null,
                platform.baseUrl(), platform.protocol());
        update(settings -> settings.withPlatform(configured));
        return configured;
    }

    @Override
    public AiSettings saveVoiceKey(AiVoiceModel model, String apiKey) {
        putSecret(EncryptedCredentialStore.voiceAlias(model), apiKey);
        return update(settings -> settings.withVoice(
                settings.voice().withConfigured(!apiKey.isBlank())));
    }

    private void putSecret(String alias, String value) {
        if (value.isBlank()) {
            secrets.remove(alias);
        } else {
            secrets.put(alias, value.strip());
        }
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    @Override
    public Subscription observePersistenceStatus(
            Consumer<AiSettingsPersistenceStatus> observer) {
        return () -> {
        };
    }

    @Override
    public Subscription observePersistenceFailures(Consumer<AiFailure> observer) {
        return () -> {
        };
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.completedFuture(null);
    }
}
