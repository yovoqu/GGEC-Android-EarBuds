package com.ggec.earbuds.data.ai.settings;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.data.ai.store.StorageGuard;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 设置的唯一持有者：内存里一份快照，每次修改在锁内读-改-写，再整份落盘。
 * 结构化设置写进文档，密钥单独走加密存储，文档里永远没有密钥。
 */
public final class AiSettingsRepositoryImpl implements AiSettingsRepository {

    private static final String DOCUMENT = "settings.json";
    private static final String KEY_PREFIX = "apikey.";

    private final AiDocumentStore documents;
    private final SecretStore secrets;
    private final AiSettingsJson json;
    private final Object lock = new Object();
    private final CopyOnWriteArrayList<Consumer<AiSettings>> observers = new CopyOnWriteArrayList<>();
    private AiSettings current;

    public AiSettingsRepositoryImpl(AiDocumentStore documents, SecretStore secrets, AiSettingsJson json) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.json = Objects.requireNonNull(json, "json");
    }

    /**
     * @throws com.ggec.earbuds.domain.ai.model.AiFailure 存储读不出来时（种类为 STORAGE）
     */
    @Override
    public AiSettings load() {
        synchronized (lock) {
            return StorageGuard.call(this::loaded);
        }
    }

    /**
     * @throws com.ggec.earbuds.domain.ai.model.AiFailure 存储写不进去时（种类为 STORAGE），
     *                                                    此时内存里的设置和观察者都保持原样
     */
    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        AiSettings next;
        synchronized (lock) {
            AiSettings before = StorageGuard.call(this::loaded);
            AiSettings changed = Objects.requireNonNull(change.apply(before), "change");
            if (changed.equals(before)) {
                return before;
            }
            StorageGuard.run(() -> persist(before, changed));
            current = changed;
            next = changed;
        }
        for (Consumer<AiSettings> observer : observers) {
            try {
                observer.accept(next);
            } catch (RuntimeException ignored) {
                // 一个观察者出错不能影响其他观察者，也不能让已经落盘的修改看起来失败。
            }
        }
        return next;
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
        return () -> observers.remove(observer);
    }

    private AiSettings loaded() {
        if (current == null) {
            current = read();
        }
        return current;
    }

    private AiSettings read() {
        String text = documents.read(DOCUMENT);
        AiSettings stored = AiSettings.EMPTY;
        if (text != null && !text.isBlank()) {
            try {
                stored = json.decode(text);
            } catch (JSONException corrupt) {
                stored = AiSettings.EMPTY;
            }
        }
        List<AiPlatformConfig> platforms = new ArrayList<>();
        for (AiPlatformConfig platform : stored.platforms()) {
            String key = secrets.get(KEY_PREFIX + platform.id());
            platforms.add(key == null || key.isBlank()
                    ? platform
                    : platform.withCredentials(platform.name(), key, platform.baseUrl(), platform.protocol()));
        }
        return new AiSettings(platforms, stored.selection(), stored.preferences(), stored.voice());
    }

    private void persist(AiSettings before, AiSettings next) {
        for (AiPlatformConfig platform : next.platforms()) {
            String previous = before.platform(platform.id()).map(AiPlatformConfig::apiKey).orElse("");
            if (platform.apiKey().equals(previous)) {
                continue;
            }
            if (platform.apiKey().isBlank()) {
                secrets.remove(KEY_PREFIX + platform.id());
            } else {
                secrets.put(KEY_PREFIX + platform.id(), platform.apiKey());
            }
        }
        for (AiPlatformConfig platform : before.platforms()) {
            if (next.platform(platform.id()).isEmpty()) {
                secrets.remove(KEY_PREFIX + platform.id());
            }
        }
        documents.write(DOCUMENT, json.encode(next));
    }
}
