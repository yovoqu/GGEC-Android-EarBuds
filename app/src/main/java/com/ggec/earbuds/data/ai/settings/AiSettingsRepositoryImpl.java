package com.ggec.earbuds.data.ai.settings;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.data.ai.store.StorageGuard;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 设置的唯一持有者：内存里一份快照，每次修改在锁内读-改-写，再整份落盘。
 * 结构化设置写进文档，密钥单独走加密存储，文档里永远没有密钥。
 *
 * <p>密钥分两套别名：文本平台用 {@code apikey.<platformId>}，语音预设用
 * {@code voicekey.<voiceModelId>}。两套互不覆盖——删掉文本平台不会把语音 Key 一起带走。</p>
 *
 * <p>内存提交与磁盘持久化分离：{@link #update} 只做短事务并立即发布新快照；
 * 写入由单线程执行器串行执行，并自动合并到最新 revision。这样主线程不再承担
 * JSON 编码、文件写入和 {@code fsync}。</p>
 */
public final class AiSettingsRepositoryImpl implements AiSettingsRepository {

    private static final String DOCUMENT = "settings.json";
    private static final String KEY_PREFIX = "apikey.";
    private static final String VOICE_KEY_PREFIX = "voicekey.";

    private final AiDocumentStore documents;
    private final SecretStore secrets;
    private final AiSettingsJson json;
    private final Executor persistenceExecutor;
    private final Object lock = new Object();
    private final Object persistenceLock = new Object();
    private final CopyOnWriteArrayList<Consumer<AiSettings>> observers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> failureObservers =
            new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<AiSettingsPersistenceStatus>> persistenceObservers =
            new CopyOnWriteArrayList<>();
    private final TreeMap<Long, CompletableFuture<Void>> flushWaiters = new TreeMap<>();
    private AiSettings current;
    private AiSettings persisted;
    private long revision;
    private long persistedRevision;
    private PendingWrite pendingWrite;
    private boolean draining;
    private AiFailure lastFailure;
    private long lastFailureRevision = -1;
    private CompletableFuture<AiSettings> loading;

    public AiSettingsRepositoryImpl(AiDocumentStore documents,
                                    SecretStore secrets,
                                    AiSettingsJson json,
                                    Executor persistenceExecutor) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.json = Objects.requireNonNull(json, "json");
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
    }

    /**
     * @throws com.ggec.earbuds.domain.ai.model.AiFailure 存储读不出来时（种类为 STORAGE）
     */
    @Override
    public AiSettings load() {
        synchronized (lock) {
            AiSettings stored = StorageGuard.call(this::loaded);
            AiSettings derived = deriveCredentials(stored);
            if (derived != stored) {
                current = derived;
            }
            return current;
        }
    }

    @Override
    public CompletableFuture<AiSettings> loadAsync() {
        CompletableFuture<AiSettings> waiter;
        boolean shouldStart = false;
        synchronized (lock) {
            if (current != null) {
                return CompletableFuture.completedFuture(current);
            }
            if (loading == null) {
                loading = new CompletableFuture<>();
                shouldStart = true;
            }
            waiter = loading;
        }
        if (shouldStart) {
            try {
                persistenceExecutor.execute(() -> completeLoad(waiter));
            } catch (RuntimeException rejected) {
                failLoad(waiter, rejected);
            }
        }
        return waiter;
    }

    @Override
    public boolean ready() {
        synchronized (lock) {
            return current != null;
        }
    }

    /**
     * @throws com.ggec.earbuds.domain.ai.model.AiFailure 存储写不进去时（种类为 STORAGE），
     *                                                    此时内存里的设置和观察者都保持原样
     */
    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        AiSettings next;
        long nextRevision;
        synchronized (lock) {
            AiSettings before = StorageGuard.call(this::loaded);
            AiSettings changed = deriveCredentials(Objects.requireNonNull(change.apply(before), "change"));
            if (changed.equals(before)) {
                return before;
            }
            current = changed;
            next = changed;
            nextRevision = ++revision;
        }
        notifyObservers(next);
        notifyPersistenceStatus(AiSettingsPersistenceStatus.SAVING);
        enqueuePersistence(new PendingWrite(nextRevision, next));
        return next;
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
        return () -> observers.remove(observer);
    }

    @Override
    public Subscription observePersistenceFailures(Consumer<Throwable> observer) {
        failureObservers.add(Objects.requireNonNull(observer, "observer"));
        return () -> failureObservers.remove(observer);
    }

    @Override
    public Subscription observePersistenceStatus(
            Consumer<AiSettingsPersistenceStatus> observer) {
        persistenceObservers.add(Objects.requireNonNull(observer, "observer"));
        return () -> persistenceObservers.remove(observer);
    }

    @Override
    public CompletableFuture<Void> flush() {
        synchronized (lock) {
            if (persistedRevision >= revision) {
                if (lastFailure != null && lastFailureRevision >= revision) {
                    return CompletableFuture.failedFuture(lastFailure);
                }
                return CompletableFuture.completedFuture(null);
            }
            if (pendingWrite == null && !draining && lastFailure != null
                    && lastFailureRevision >= revision) {
                return CompletableFuture.failedFuture(lastFailure);
            }
            CompletableFuture<Void> waiter = new CompletableFuture<>();
            flushWaiters.put(revision, waiter);
            return waiter;
        }
    }

    private AiSettings loaded() {
        if (current == null) {
            current = read();
            persisted = current;
        }
        return current;
    }

    private void completeLoad(CompletableFuture<AiSettings> waiter) {
        AiSettings value;
        try {
            synchronized (lock) {
                value = StorageGuard.call(this::loaded);
                loading = null;
            }
        } catch (RuntimeException failure) {
            failLoad(waiter, failure);
            return;
        }
        waiter.complete(value);
    }

    private void failLoad(CompletableFuture<AiSettings> waiter, RuntimeException failure) {
        synchronized (lock) {
            if (loading != null && loading == waiter) {
                loading = null;
            }
        }
        waiter.completeExceptionally(failure);
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
            boolean configured = key != null && !key.isBlank();
            platforms.add(platform.withConnection(platform.name(), configured,
                    platform.baseUrl(), platform.protocol()));
        }
        Set<AiVoiceModel> configuredVoices = EnumSet.noneOf(AiVoiceModel.class);
        for (AiVoiceModel model : AiVoiceModel.values()) {
            String key = secrets.get(VOICE_KEY_PREFIX + model.id());
            if (key != null && !key.isBlank()) {
                configuredVoices.add(model);
            }
        }
        AiVoiceConfig storedVoice = stored.voice();
        AiVoiceConfig voice = new AiVoiceConfig(storedVoice.model(), configuredVoices, storedVoice.providers());
        return new AiSettings(platforms, stored.textSelection(), voice,
                stored.preferences());
    }

    /** 用密钥存储的真实存在情况重算"已配置"标记，内存快照才不会和磁盘/密钥库脱节。 */
    private AiSettings deriveCredentials(AiSettings settings) {
        List<AiPlatformConfig> platforms = new ArrayList<>(settings.platforms().size());
        boolean platformChanged = false;
        for (AiPlatformConfig platform : settings.platforms()) {
            String key = secrets.get(KEY_PREFIX + platform.id());
            boolean configured = key != null && !key.isBlank();
            AiPlatformConfig derived = platform.configured() == configured ? platform
                    : platform.withConnection(platform.name(), configured,
                    platform.baseUrl(), platform.protocol());
            platformChanged |= derived != platform;
            platforms.add(derived);
        }
        Set<AiVoiceModel> configured = EnumSet.noneOf(AiVoiceModel.class);
        for (AiVoiceModel model : AiVoiceModel.values()) {
            String key = secrets.get(VOICE_KEY_PREFIX + model.id());
            if (key != null && !key.isBlank()) {
                configured.add(model);
            }
        }
        AiVoiceConfig voice = settings.voice();
        boolean voiceChanged = !voice.configuredProviders().equals(configured);
        if (voiceChanged) {
            voice = new AiVoiceConfig(voice.model(), configured, voice.providers());
        }
        if (!platformChanged && !voiceChanged) {
            return settings;
        }
        return new AiSettings(platformChanged ? platforms : settings.platforms(),
                settings.textSelection(), voice, settings.preferences());
    }

    private void persist(AiSettings before, AiSettings next) {
        // 删平台时顺手清掉它的密钥；密钥的写入由 SavePlatform 用例负责，仓库从不落明文。
        for (AiPlatformConfig platform : before.platforms()) {
            if (next.platform(platform.id()).isEmpty()) {
                secrets.remove(KEY_PREFIX + platform.id());
            }
        }
        documents.write(DOCUMENT, json.encode(next));
    }

    private void enqueuePersistence(PendingWrite write) {
        boolean shouldStart;
        synchronized (persistenceLock) {
            if (pendingWrite == null || pendingWrite.revision < write.revision) {
                pendingWrite = write;
            }
            shouldStart = !draining;
            if (shouldStart) {
                draining = true;
            }
        }
        if (!shouldStart) {
            return;
        }
        try {
            persistenceExecutor.execute(this::drainPersistence);
        } catch (RuntimeException rejected) {
            synchronized (persistenceLock) {
                draining = false;
            }
            AiFailure failure = new AiFailure(
                    com.ggec.earbuds.domain.ai.model.AiFailureKind.STORAGE,
                    "settings persistence executor rejected the write", rejected);
            failRevision(write.revision, failure);
        }
    }

    private void drainPersistence() {
        while (true) {
            PendingWrite write;
            synchronized (persistenceLock) {
                write = pendingWrite;
                pendingWrite = null;
                if (write == null) {
                    draining = false;
                    return;
                }
            }
            AiSettings before;
            synchronized (lock) {
                before = persisted;
            }
            try {
                StorageGuard.run(() -> persist(before, write.state));
                synchronized (lock) {
                    persisted = write.state;
                    persistedRevision = write.revision;
                    lastFailure = null;
                    lastFailureRevision = -1;
                }
                completeWaiters(write.revision, null);
                notifyPersistenceStatus(AiSettingsPersistenceStatus.SAVED);
            } catch (AiFailure failure) {
                failRevision(write.revision, failure);
            } catch (RuntimeException unexpected) {
                AiFailure failure = new AiFailure(
                        com.ggec.earbuds.domain.ai.model.AiFailureKind.STORAGE,
                        "settings persistence failed", unexpected);
                failRevision(write.revision, failure);
            }
        }
    }

    private void failRevision(long failedRevision, AiFailure failure) {
        synchronized (lock) {
            lastFailure = failure;
            lastFailureRevision = failedRevision;
        }
        completeWaiters(failedRevision, failure);
        notifyPersistenceStatus(AiSettingsPersistenceStatus.FAILED);
        for (Consumer<Throwable> observer : failureObservers) {
            try {
                observer.accept(failure);
            } catch (RuntimeException ignored) {
                // Persistence failure reporting must not mask the original failure.
            }
        }
    }

    private void completeWaiters(long completedRevision, AiFailure failure) {
        List<CompletableFuture<Void>> ready = new ArrayList<>();
        synchronized (lock) {
            while (!flushWaiters.isEmpty()
                    && flushWaiters.firstKey() <= completedRevision) {
                ready.add(flushWaiters.pollFirstEntry().getValue());
            }
        }
        for (CompletableFuture<Void> waiter : ready) {
            if (failure == null) {
                waiter.complete(null);
            } else {
                waiter.completeExceptionally(failure);
            }
        }
    }

    private void notifyObservers(AiSettings next) {
        for (Consumer<AiSettings> observer : observers) {
            try {
                observer.accept(next);
            } catch (RuntimeException ignored) {
                // One observer must not prevent the memory commit from reaching the others.
            }
        }
    }

    private void notifyPersistenceStatus(AiSettingsPersistenceStatus status) {
        for (Consumer<AiSettingsPersistenceStatus> observer : persistenceObservers) {
            try {
                observer.accept(status);
            } catch (RuntimeException ignored) {
                // Persistence reporting must not interrupt the serial writer.
            }
        }
    }

    private record PendingWrite(long revision, AiSettings state) {
    }
}
