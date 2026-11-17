package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 读取由测试显式完成的设置仓库，用来验证“页面先显示加载态、后拿到首个快照”。 */
public final class DeferredSettingsRepository implements AiSettingsRepository {

    private final CompletableFuture<AiSettings> loaded = new CompletableFuture<>();
    private final List<Consumer<AiSettings>> observers = new ArrayList<>();
    private final List<Consumer<AiSettingsPersistenceStatus>> statuses = new ArrayList<>();
    private AiSettings current = AiSettings.EMPTY;
    public int loadRequests;

    @Override
    public AiSettings load() {
        return loaded.join();
    }

    @Override
    public CompletableFuture<AiSettings> loadAsync() {
        loadRequests++;
        return loaded;
    }

    @Override
    public boolean ready() {
        return loaded.isDone();
    }

    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        AiSettings next = change.apply(current);
        if (!next.equals(current)) {
            current = next;
            for (Consumer<AiSettings> observer : new ArrayList<>(observers)) {
                observer.accept(next);
            }
            for (Consumer<AiSettingsPersistenceStatus> observer : new ArrayList<>(statuses)) {
                observer.accept(AiSettingsPersistenceStatus.SAVED);
            }
        }
        return current;
    }

    @Override
    public AiPlatformConfig savePlatform(AiPlatformConfig platform, String apiKey) {
        AiPlatformConfig configured = platform.withConnection(platform.name(),
                !apiKey.isBlank(), platform.baseUrl(), platform.protocol());
        update(settings -> settings.withPlatform(configured));
        return configured;
    }

    @Override
    public AiSettings saveVoiceKey(AiVoiceModel model, String apiKey) {
        return update(settings -> settings.withVoice(
                settings.voice().withConfigured(!apiKey.isBlank())));
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }

    @Override
    public Subscription observePersistenceStatus(
            Consumer<AiSettingsPersistenceStatus> observer) {
        statuses.add(observer);
        return () -> statuses.remove(observer);
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

    public void complete(AiSettings settings) {
        current = settings;
        loaded.complete(settings);
    }
}
