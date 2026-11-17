package com.ggec.earbuds.data.ai.settings;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.EncryptedCredentialStore;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.SecretCipherException;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.data.store.DocumentStore;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentReadException;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentWriteException;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFailureLocation;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 设置的唯一持有者与唯一写入口。
 *
 * <p>内存里一份快照：{@link #update} 在锁内读-改-写并立即发布，落盘交给注入的串行执行器，
 * 多次变更只写最后一次（合并到最新 revision）。主线程不承担 JSON 编码、文件写入与 fsync。</p>
 *
 * <p><b>一次业务提交一个方法</b>：{@link #savePlatform} / {@link #saveVoiceKey}
 * 把密钥文档与设置文档收进同一提交点。密钥在调用线程上先落密文（密钥文档很小），
 * 设置文档随后由串行写者落盘；设置文档写失败时，本次提交的密钥按写入前的值回滚，
 * 所以"密钥写进去了、设置没落盘"的中间态不会留在磁盘上。</p>
 *
 * <p><b>configured 单点重算</b>：{@link #decode} 只解码，{@code configured} 一律由
 * {@link #deriveCredentials} 按密钥文档的真实内容重算，内存快照不会和密钥库脱节。</p>
 *
 * <p>所有数据层失败都归一成 {@link AiFailureKind#STORAGE}，载荷是文档坐标
 * （{@link AiFailureLocation}），不带英文技术串。</p>
 */
public final class AiSettingsRepositoryImpl implements AiSettingsRepository {

    private static final String DOCUMENT = "settings.json";

    private final DocumentStore documents;
    private final SecretStore secrets;
    private final AiSettingsJson json;
    private final Executor persistenceExecutor;
    private final Object lock = new Object();
    private final Object persistenceLock = new Object();
    private final CopyOnWriteArrayList<Consumer<AiSettings>> observers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<AiFailure>> failureObservers =
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

    public AiSettingsRepositoryImpl(DocumentStore documents,
                                    SecretStore secrets,
                                    AiSettingsJson json,
                                    Executor persistenceExecutor) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.json = Objects.requireNonNull(json, "json");
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
    }

    /**
     * @throws AiFailure 存储读不出来时（种类为 STORAGE），文档保持原样
     */
    @Override
    public AiSettings load() {
        synchronized (lock) {
            AiSettings stored = loaded();
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
                failLoad(waiter, storageFailure(AiFailureLocation.of(DOCUMENT), rejected));
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
     * @throws AiFailure 存储写不进去时（种类为 STORAGE），此时内存里的设置和观察者都保持原样
     */
    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        return commit(change, List.of());
    }

    @Override
    public AiPlatformConfig savePlatform(AiPlatformConfig platform, String apiKey) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(apiKey, "apiKey");
        String key = apiKey.strip();
        AiPlatformConfig target = platform.withConnection(platform.name(), !key.isEmpty(),
                platform.baseUrl(), platform.protocol());
        SecretUndo undo = writeSecret(EncryptedCredentialStore.platformAlias(platform.id()), key,
                platform.id());
        try {
            commit(settings -> settings.withPlatform(target), List.of(undo));
        } catch (RuntimeException rejected) {
            undo.restore();
            throw rejected;
        }
        return target;
    }

    @Override
    public AiSettings saveVoiceKey(AiVoiceModel model, String apiKey) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(apiKey, "apiKey");
        String key = apiKey.strip();
        SecretUndo undo = writeSecret(EncryptedCredentialStore.voiceAlias(model), key, model.id());
        try {
            return commit(settings -> settings.withVoice(
                    settings.voice().withConfigured(!key.isEmpty())), List.of(undo));
        } catch (RuntimeException rejected) {
            undo.restore();
            throw rejected;
        }
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
        return () -> observers.remove(observer);
    }

    @Override
    public Subscription observePersistenceStatus(Consumer<AiSettingsPersistenceStatus> observer) {
        persistenceObservers.add(Objects.requireNonNull(observer, "observer"));
        return () -> persistenceObservers.remove(observer);
    }

    @Override
    public Subscription observePersistenceFailures(Consumer<AiFailure> observer) {
        failureObservers.add(Objects.requireNonNull(observer, "observer"));
        return () -> failureObservers.remove(observer);
    }

    @Override
    public CompletableFuture<Void> flush() {
        synchronized (lock) {
            if (persistedRevision >= revision) {
                if (lastFailure != null && lastFailureRevision >= revision) {
                    return failedFuture(lastFailure);
                }
                return CompletableFuture.completedFuture(null);
            }
            if (pendingWrite == null && !draining && lastFailure != null
                    && lastFailureRevision >= revision) {
                return failedFuture(lastFailure);
            }
            CompletableFuture<Void> waiter = new CompletableFuture<>();
            flushWaiters.put(revision, waiter);
            return waiter;
        }
    }

    /** API 21+ 兼容的“已失败 future”构造（CompletableFuture.failedFuture 需 API 31）。 */
    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private AiSettings loaded() {
        if (current == null) {
            AiSettings decoded = decode();
            persisted = decoded;
            current = deriveCredentials(decoded);
        }
        return current;
    }

    /** 只解码：不碰密钥、不算 configured。 */
    private AiSettings decode() {
        Optional<String> text;
        try {
            text = documents.read(DOCUMENT);
        } catch (DocumentReadException unreadable) {
            throw storageFailure(AiFailureLocation.of(DOCUMENT), unreadable);
        }
        if (text.isEmpty() || text.get().isBlank()) {
            return AiSettings.EMPTY;
        }
        try {
            return json.decode(text.get());
        } catch (DocumentFormatException corrupt) {
            // 损坏的文档按存储失败上报并保留原文件；当成空设置再写回会静默清空用户配置。
            throw storageFailure(AiFailureLocation.of(DOCUMENT), corrupt);
        }
    }

    private void completeLoad(CompletableFuture<AiSettings> waiter) {
        AiSettings value;
        try {
            synchronized (lock) {
                value = load();
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

    private AiSettings commit(UnaryOperator<AiSettings> change, List<SecretUndo> undos) {
        AiSettings next;
        long nextRevision;
        synchronized (lock) {
            AiSettings before = loaded();
            AiSettings changed = deriveCredentials(
                    Objects.requireNonNull(change.apply(before), "change"));
            if (changed.equals(before)) {
                return before;
            }
            current = changed;
            next = changed;
            nextRevision = ++revision;
        }
        notifyObservers(next);
        notifyPersistenceStatus(AiSettingsPersistenceStatus.SAVING);
        enqueuePersistence(new PendingWrite(nextRevision, next, undos));
        return next;
    }

    /** 用密钥存储的真实存在情况重算"已配置"标记；这是它唯一的产生点。 */
    private AiSettings deriveCredentials(AiSettings settings) {
        List<AiPlatformConfig> platforms = new ArrayList<>(settings.platforms().size());
        boolean platformChanged = false;
        for (AiPlatformConfig platform : settings.platforms()) {
            String key = secret(EncryptedCredentialStore.platformAlias(platform.id()), platform.id());
            boolean configured = key != null && !key.isBlank();
            AiPlatformConfig derived = platform.configured() == configured ? platform
                    : platform.withConnection(platform.name(), configured,
                    platform.baseUrl(), platform.protocol());
            platformChanged |= derived != platform;
            platforms.add(derived);
        }
        Set<AiVoiceModel> configured = EnumSet.noneOf(AiVoiceModel.class);
        for (AiVoiceModel model : AiVoiceModel.values()) {
            String key = secret(EncryptedCredentialStore.voiceAlias(model), model.id());
            if (key != null && !key.isBlank()) {
                configured.add(model);
            }
        }
        AiVoiceConfig voice = settings.voice();
        boolean voiceChanged = !voice.configuredProviders().equals(configured);
        if (voiceChanged) {
            voice = voice.withConfiguredProviders(configured);
        }
        if (!platformChanged && !voiceChanged) {
            return settings;
        }
        return new AiSettings(platformChanged ? platforms : settings.platforms(),
                settings.textSelection(), voice, settings.preferences());
    }

    @Nullable
    private String secret(String alias, String entityId) {
        try {
            return secrets.get(alias);
        } catch (DocumentFormatException | DocumentReadException broken) {
            throw storageFailure(AiFailureLocation.of(EncryptedSecretStore.DOCUMENT, entityId), broken);
        }
    }

    /** 写入一份密钥并返回"写前的值"，供设置文档落盘失败时回滚。 */
    private SecretUndo writeSecret(String alias, String value, String entityId) {
        String previous;
        try {
            previous = secrets.get(alias);
            if (value.isEmpty()) {
                secrets.remove(alias);
            } else {
                secrets.put(alias, value);
            }
        } catch (DocumentFormatException | DocumentReadException | DocumentWriteException
                 | SecretCipherException broken) {
            throw storageFailure(AiFailureLocation.of(EncryptedSecretStore.DOCUMENT, entityId), broken);
        }
        return new SecretUndo(alias, previous, entityId);
    }

    private void enqueuePersistence(PendingWrite write) {
        boolean shouldStart;
        synchronized (persistenceLock) {
            pendingWrite = pendingWrite == null ? write : pendingWrite.merge(write);
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
            PendingWrite dropped;
            synchronized (persistenceLock) {
                draining = false;
                dropped = pendingWrite;
                pendingWrite = null;
            }
            if (dropped != null) {
                dropped.rollback();
            }
            failRevision(write.revision(), storageFailure(
                    AiFailureLocation.of(DOCUMENT, null, write.revision()), rejected));
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
            writeOnce(write);
        }
    }

    private void writeOnce(PendingWrite write) {
        AiSettings before;
        synchronized (lock) {
            before = persisted;
        }
        try {
            documents.write(DOCUMENT, json.encode(write.state()));
        } catch (RuntimeException unwritable) {
            // 文档没落盘：本次提交的密钥按写前的值回滚，磁盘上不留半对状态。
            write.rollback();
            failRevision(write.revision(), storageFailure(
                    AiFailureLocation.of(DOCUMENT, null, write.revision()), unwritable));
            return;
        }
        synchronized (lock) {
            persisted = write.state();
            persistedRevision = write.revision();
            lastFailure = null;
            lastFailureRevision = -1;
        }
        completeWaiters(write.revision(), null);
        notifyPersistenceStatus(AiSettingsPersistenceStatus.SAVED);
        removeOrphanedSecrets(before, write.state());
    }

    /** 平台被删掉后清掉它的密钥。文档已经落盘，所以这里失败只上报，不回滚设置。 */
    private void removeOrphanedSecrets(AiSettings before, AiSettings next) {
        for (AiPlatformConfig platform : before.platforms()) {
            if (next.platform(platform.id()).isPresent()) {
                continue;
            }
            try {
                secrets.remove(EncryptedCredentialStore.platformAlias(platform.id()));
            } catch (DocumentFormatException | DocumentReadException | DocumentWriteException broken) {
                notifyPersistenceFailures(storageFailure(
                        AiFailureLocation.of(EncryptedSecretStore.DOCUMENT, platform.id()), broken));
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
        notifyPersistenceFailures(failure);
    }

    private void completeWaiters(long completedRevision, @Nullable AiFailure failure) {
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
                // 单个观察者出错不能拦住内存提交到达其他观察者。
            }
        }
    }

    private void notifyPersistenceStatus(AiSettingsPersistenceStatus status) {
        for (Consumer<AiSettingsPersistenceStatus> observer : persistenceObservers) {
            try {
                observer.accept(status);
            } catch (RuntimeException ignored) {
                // 状态通知不能打断串行写者。
            }
        }
    }

    private void notifyPersistenceFailures(AiFailure failure) {
        for (Consumer<AiFailure> observer : failureObservers) {
            try {
                observer.accept(failure);
            } catch (RuntimeException ignored) {
                // 失败上报不能覆盖原始失败。
            }
        }
    }

    private static AiFailure storageFailure(AiFailureLocation location, Throwable cause) {
        return new AiFailure(AiFailureKind.STORAGE, location, cause);
    }

    private record PendingWrite(long revision, AiSettings state, List<SecretUndo> undos) {

        PendingWrite merge(PendingWrite newer) {
            List<SecretUndo> all = new ArrayList<>(undos);
            all.addAll(newer.undos);
            return new PendingWrite(newer.revision, newer.state, List.copyOf(all));
        }

        /** 按写入的倒序回滚，最早那次写前的值最终生效。 */
        void rollback() {
            for (int index = undos.size() - 1; index >= 0; index--) {
                undos.get(index).restore();
            }
        }
    }

    /** 一次密钥写入的回滚点：把别名恢复成写前的值（{@code null} 表示删掉）。 */
    private final class SecretUndo {

        private final String alias;
        private final String previous;
        private final String entityId;

        SecretUndo(String alias, @Nullable String previous, String entityId) {
            this.alias = alias;
            this.previous = previous;
            this.entityId = entityId;
        }

        void restore() {
            try {
                if (previous == null) {
                    secrets.remove(alias);
                } else {
                    secrets.put(alias, previous);
                }
            } catch (DocumentFormatException | DocumentReadException | DocumentWriteException
                     | SecretCipherException unrestorable) {
                // 回滚本身失败：原始失败已经上报过，这里只把回滚失败也报出去。
                notifyPersistenceFailures(storageFailure(
                        AiFailureLocation.of(EncryptedSecretStore.DOCUMENT, entityId), unrestorable));
            }
        }
    }
}
