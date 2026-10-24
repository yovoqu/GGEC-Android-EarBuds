package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiConnectionResult;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiConnectionPolicy;
import com.ggec.earbuds.domain.ai.policy.AiFingerprint;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 平台页的后台任务：连接测试（CON-02/07/08）与测速（SPD-B-01…08）。
 *
 * <p>应用级单例：离开平台页后任务继续跑完并写结果，回到页面能看到（CON-08）。
 * 同一平台同时只有一个连接测试；同一模型同时只测一次（SPD-B-03）。结果一到就写回设置，
 * 每次请求记一条用量（来源"连接测试"/"测速"）；取消的不写结果、不记用量。</p>
 *
 * <p>线程：网关回调线程不做保证，内部状态由本对象的锁保护，监听器在锁外回调。</p>
 */
public final class AiPlatformJobs {

    /** 一次批量测速的进度与结果（单条测速不产生批次）。 */
    public record Batch(long id, int total, List<AiSpeedResult> results, boolean finished, boolean cancelled) {
        public Batch {
            results = List.copyOf(results);
        }

        public int done() {
            return results.size();
        }
    }

    /** 某个平台此刻的任务情况。 */
    public record PlatformJobs(boolean testingConnection, Set<String> speedTesting, @Nullable Batch batch) {
        public static final PlatformJobs IDLE = new PlatformJobs(false, Set.of(), null);

        public PlatformJobs {
            speedTesting = Set.copyOf(speedTesting);
        }

        public boolean batchRunning() {
            return batch != null && !batch.finished();
        }
    }

    private final AiSettingsRepository repository;
    private final AiSpeedTestGateway gateway;
    private final AiClock clock;
    private final AiUsageStore usage;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final Object lock = new Object();
    private final Map<String, ConnectionJob> connections = new HashMap<>();
    private final Map<String, List<SpeedJob>> speeds = new HashMap<>();
    private final Map<String, Batch> batches = new HashMap<>();
    private long nextBatchId = 1;

    public AiPlatformJobs(AiSettingsRepository repository, AiSpeedTestGateway gateway, AiClock clock,
                          AiUsageStore usage) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.usage = Objects.requireNonNull(usage, "usage");
    }

    public Subscription observe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public PlatformJobs state(String platformId) {
        synchronized (lock) {
            Set<String> testing = new LinkedHashSet<>();
            for (SpeedJob job : speeds.getOrDefault(platformId, List.of())) {
                testing.addAll(job.pending);
            }
            ConnectionJob connection = connections.get(platformId);
            Batch batch = batches.get(platformId);
            if (connection == null && testing.isEmpty() && batch == null) {
                return PlatformJobs.IDLE;
            }
            return new PlatformJobs(connection != null, testing, batch);
        }
    }

    // ---------------------------------------------------------------- 连接测试

    /**
     * 开始测试。已在测试中则忽略。没有可用的测试模型抛 {@link AiValidationError#MODEL_UNKNOWN}。
     * 前置条件（API Key、地址、离线）由调用方按 {@link AiConnectionPolicy#blocker} 先挡住。
     */
    public void testConnection(String platformId) {
        AiSettings settings = repository.load();
        AiPlatformConfig platform = settings.platform(platformId)
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
        String modelId = AiConnectionPolicy.testModel(platform, settings.selection());
        if (modelId == null) {
            throw new AiValidationException(AiValidationError.MODEL_UNKNOWN);
        }
        String fingerprint = AiFingerprint.connection(platform, modelId);
        ConnectionJob job = new ConnectionJob();
        synchronized (lock) {
            if (connections.containsKey(platformId)) {
                return;
            }
            connections.put(platformId, job);
        }
        notifyListeners();
        AiCancellable handle;
        try {
            handle = gateway.run(platform, List.of(modelId), new AiSpeedTestListener() {
                @Override
                public void onResult(AiSpeedResult result) {
                    if (job.cancelled) {
                        return;
                    }
                    long now = clock.now();
                    AiConnectionResult connection = result.ok()
                            ? AiConnectionResult.success(modelId, result.firstTokenMillis(), now, fingerprint)
                            : AiConnectionResult.failure(modelId, result.effectiveFailure(), result.message(), now,
                            fingerprint);
                    writeConnection(platformId, connection);
                    record(platformId, modelId, result, AiUsageSource.CONNECTION_TEST);
                }

                @Override
                public void onComplete(boolean cancelled) {
                    finishConnection(platformId, job);
                }
            });
        } catch (AiFailure failure) {
            writeConnection(platformId, AiConnectionResult.failure(modelId, failure.kind(), failure.getMessage(),
                    clock.now(), fingerprint));
            finishConnection(platformId, job);
            return;
        } catch (RuntimeException unexpected) {
            writeConnection(platformId, AiConnectionResult.failure(modelId, AiFailureKind.UNKNOWN,
                    unexpected.getMessage(), clock.now(), fingerprint));
            finishConnection(platformId, job);
            return;
        }
        boolean cancelNow;
        synchronized (lock) {
            job.handle = handle;
            cancelNow = job.cancelled;
        }
        if (cancelNow) {
            handle.cancel();
        }
    }

    /** 取消：立即回到测试前的状态，不写结果、不记用量（CON-07）。 */
    public void cancelConnection(String platformId) {
        ConnectionJob job;
        synchronized (lock) {
            job = connections.remove(platformId);
            if (job == null) {
                return;
            }
            job.cancelled = true;
        }
        AiCancellable handle = job.handle;
        if (handle != null) {
            handle.cancel();
        }
        notifyListeners();
    }

    private void finishConnection(String platformId, ConnectionJob job) {
        boolean changed;
        synchronized (lock) {
            changed = connections.get(platformId) == job;
            if (changed) {
                connections.remove(platformId);
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    private void writeConnection(String platformId, AiConnectionResult result) {
        repository.update(settings -> settings.platform(platformId)
                .map(platform -> settings.withPlatform(platform.withConnection(result)))
                .orElse(settings));
    }

    // ---------------------------------------------------------------- 测速

    /**
     * 测速。只测对话模型、跳过已在测的；{@code batch} 为真时记一个批次（标题行进度与结论）。
     *
     * @return 实际开始测的模型数
     */
    public int speedTest(String platformId, Collection<String> modelIds, boolean batch) {
        AiPlatformConfig platform = repository.load().platform(platformId)
                .orElseThrow(() -> new AiValidationException(AiValidationError.PLATFORM_UNKNOWN));
        String fingerprint = AiFingerprint.speed(platform);
        SpeedJob job = new SpeedJob(platformId, fingerprint, batch);
        synchronized (lock) {
            Set<String> running = new LinkedHashSet<>();
            for (SpeedJob other : speeds.getOrDefault(platformId, List.of())) {
                running.addAll(other.pending);
            }
            for (String id : modelIds) {
                boolean chat = platform.model(id).map(AiModel::chat).orElse(false);
                if (chat && !running.contains(id)) {
                    job.pending.add(id);
                }
            }
            if (job.pending.isEmpty()) {
                return 0;
            }
            job.total = job.pending.size();
            speeds.computeIfAbsent(platformId, key -> new ArrayList<>()).add(job);
            if (batch) {
                job.batchId = nextBatchId++;
                batches.put(platformId, new Batch(job.batchId, job.total, List.of(), false, false));
            }
        }
        notifyListeners();
        List<String> models = List.copyOf(job.pending);
        AiCancellable handle;
        try {
            handle = gateway.run(platform, models, new AiSpeedTestListener() {
                @Override
                public void onResult(AiSpeedResult result) {
                    onSpeedResult(job, result);
                }

                @Override
                public void onComplete(boolean cancelled) {
                    finishSpeed(job, cancelled);
                }
            });
        } catch (AiFailure failure) {
            for (String id : models) {
                onSpeedResult(job, new AiSpeedResult(id, AiSpeedResult.Status.FAILED, -1L, 0L, failure.kind(),
                        failure.getMessage(), 0L));
            }
            finishSpeed(job, false);
            return models.size();
        }
        boolean cancelNow;
        synchronized (lock) {
            job.handle = handle;
            cancelNow = job.cancelled;
        }
        if (cancelNow) {
            handle.cancel();
        }
        return models.size();
    }

    /** 取消本平台全部测速：未开始的不测、进行中的不写结果、已完成的保留（SPD-B-05）。 */
    public void cancelSpeed(String platformId) {
        List<SpeedJob> jobs;
        synchronized (lock) {
            jobs = speeds.remove(platformId);
            if (jobs == null) {
                return;
            }
            for (SpeedJob job : jobs) {
                job.cancelled = true;
                job.pending.clear();
                if (job.batch) {
                    Batch current = batches.get(platformId);
                    if (current != null && current.id() == job.batchId) {
                        batches.put(platformId, new Batch(current.id(), current.total(), current.results(),
                                true, true));
                    }
                }
            }
        }
        for (SpeedJob job : jobs) {
            AiCancellable handle = job.handle;
            if (handle != null) {
                handle.cancel();
            }
        }
        notifyListeners();
    }

    /** 页面离开时丢掉已结束批次的结论（SPD-B-06"保留到离开页面"）。 */
    public void clearFinishedBatch(String platformId) {
        boolean changed;
        synchronized (lock) {
            Batch batch = batches.get(platformId);
            changed = batch != null && batch.finished();
            if (changed) {
                batches.remove(platformId);
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    private void onSpeedResult(SpeedJob job, AiSpeedResult raw) {
        AiSpeedResult stamped = raw.stamped(clock.now(), job.fingerprint);
        synchronized (lock) {
            if (job.cancelled) {
                return;
            }
            job.pending.remove(raw.modelId());
            if (job.batch) {
                Batch current = batches.get(job.platformId);
                if (current != null && current.id() == job.batchId) {
                    List<AiSpeedResult> results = new ArrayList<>(current.results());
                    results.add(stamped);
                    batches.put(job.platformId, new Batch(current.id(), current.total(), results, false, false));
                }
            }
        }
        repository.update(settings -> settings.platform(job.platformId)
                .filter(platform -> platform.model(stamped.modelId()).isPresent())
                .map(platform -> settings.withPlatform(platform.withSpeedResult(stamped)))
                .orElse(settings));
        record(job.platformId, stamped.modelId(), stamped, AiUsageSource.SPEED_TEST);
        notifyListeners();
    }

    private void finishSpeed(SpeedJob job, boolean cancelled) {
        boolean changed = false;
        synchronized (lock) {
            List<SpeedJob> jobs = speeds.get(job.platformId);
            if (jobs != null && jobs.remove(job)) {
                changed = true;
                if (jobs.isEmpty()) {
                    speeds.remove(job.platformId);
                }
            }
            job.pending.clear();
            if (job.batch) {
                Batch current = batches.get(job.platformId);
                if (current != null && current.id() == job.batchId && !current.finished()) {
                    batches.put(job.platformId, new Batch(current.id(), current.total(), current.results(), true,
                            cancelled || job.cancelled));
                    changed = true;
                }
            }
        }
        if (changed) {
            notifyListeners();
        }
    }

    // ---------------------------------------------------------------- 共用

    private void record(String platformId, String modelId, AiSpeedResult result, AiUsageSource source) {
        AiUsageStatus status = result.ok() ? AiUsageStatus.SUCCESS : AiUsageStatus.FAILED;
        AiFailureKind failure = result.effectiveFailure();
        AiUsageRecord record = AiUsageRecord.request(clock.now(), null, platformId, modelId, null,
                result.ok() ? result.firstTokenMillis() : result.totalMillis(), status,
                failure == null ? null : failure.name()).withSource(source);
        try {
            usage.append(record);
        } catch (RuntimeException ignored) {
            // 账本写不进去不影响测试结果。
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // 一个监听器出错不影响其他监听器。
            }
        }
    }

    private static final class ConnectionJob {
        volatile boolean cancelled;
        volatile AiCancellable handle;
    }

    private static final class SpeedJob {
        final String platformId;
        final String fingerprint;
        final boolean batch;
        final Set<String> pending = new LinkedHashSet<>();
        int total;
        long batchId;
        volatile boolean cancelled;
        volatile AiCancellable handle;

        SpeedJob(String platformId, String fingerprint, boolean batch) {
            this.platformId = platformId;
            this.fingerprint = fingerprint;
            this.batch = batch;
        }
    }
}
