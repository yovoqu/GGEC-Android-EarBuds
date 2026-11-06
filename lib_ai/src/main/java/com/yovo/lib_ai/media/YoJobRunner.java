package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoJob;
import com.yovo.lib_ai.api.YoJobStatus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * 异步任务统一层：把"提交 → 轮询 → 超时 → 结果"收敛到一处，图像/视频共用。
 *
 * <p>{@link #poll} 是阻塞的：内部按 {@code intervalMillis} 反复询问 {@link Poller}，
 * 直到成功、失败或超过 {@code timeoutMillis}。之所以阻塞而不是起后台线程：调用方本来就在
 * 后台线程调生成接口，多一条线程只会让取消和收尾更难对齐；{@code timeoutMillis} 保证它一定会结束。
 *
 * <p>测试通过注入 {@link LongSupplier} 时钟与 {@link Sleeper} 让轮询确定化，不需要真的 sleep。
 */
public final class YoJobRunner {

    private YoJobRunner() {
    }

    /** 一次轮询的结果。 */
    public interface Poller<T> {
        @NonNull
        Poll<T> poll();
    }

    /** 睡眠器，测试可替换成推进假时钟的空操作。 */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** 一次轮询快照。 */
    public static final class Poll<T> {

        @NonNull
        private final YoJobStatus status;
        @Nullable
        private final T result;
        @Nullable
        private final String message;

        private Poll(@NonNull YoJobStatus status, @Nullable T result, @Nullable String message) {
            this.status = status;
            this.result = result;
            this.message = message;
        }

        @NonNull
        public static <T> Poll<T> queued() {
            return new Poll<>(YoJobStatus.QUEUED, null, null);
        }

        @NonNull
        public static <T> Poll<T> running() {
            return new Poll<>(YoJobStatus.RUNNING, null, null);
        }

        @NonNull
        public static <T> Poll<T> succeeded(@NonNull T result) {
            return new Poll<>(YoJobStatus.SUCCEEDED, result, null);
        }

        @NonNull
        public static <T> Poll<T> failed(@Nullable String message) {
            return new Poll<>(YoJobStatus.FAILED, null, message);
        }

        @NonNull
        public YoJobStatus status() {
            return status;
        }
    }

    /** 已经成功完成的任务（同步方言用）。 */
    @NonNull
    public static <T> YoJob<T> completed(@Nullable String id, @NonNull T result) {
        return new JobImpl<>(id, YoJobStatus.SUCCEEDED, result, null);
    }

    /** 直接失败的任务。 */
    @NonNull
    public static <T> YoJob<T> failed(@Nullable String id, @Nullable String message) {
        return new JobImpl<>(id, YoJobStatus.FAILED, null, message == null ? "job failed" : message);
    }

    /**
     * 轮询直到终态或超时。
     *
     * @param timeoutMillis 总预算；超过即折成 FAILED 并给出可读原因
     * @param intervalMillis 两次轮询之间的间隔
     */
    @NonNull
    static <T> YoJob<T> poll(
            @Nullable String id,
            @NonNull Poller<T> poller,
            long timeoutMillis,
            long intervalMillis,
            @NonNull LongSupplier clock,
            @NonNull Sleeper sleeper) {
        long start = clock.getAsLong();
        while (true) {
            Poll<T> snapshot = poller.poll();
            YoJobStatus status = snapshot.status;
            if (status == YoJobStatus.SUCCEEDED) {
                return new JobImpl<>(id, YoJobStatus.SUCCEEDED, snapshot.result, null);
            }
            if (status == YoJobStatus.FAILED) {
                return new JobImpl<>(id, YoJobStatus.FAILED, null,
                        snapshot.message == null ? "job failed" : snapshot.message);
            }
            if (status == YoJobStatus.CANCELLED) {
                return new JobImpl<>(id, YoJobStatus.CANCELLED, null, null);
            }
            if (clock.getAsLong() - start >= timeoutMillis) {
                return new JobImpl<>(id, YoJobStatus.FAILED, null,
                        "job timed out after " + timeoutMillis + "ms");
            }
            try {
                sleeper.sleep(intervalMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new JobImpl<>(id, YoJobStatus.CANCELLED, null, null);
            }
        }
    }

    /** 默认时钟/睡眠器（真实实现用）。 */
    @NonNull
    static LongSupplier systemClock() {
        return System::currentTimeMillis;
    }

    @NonNull
    static Sleeper threadSleeper() {
        return Thread::sleep;
    }

    private static final class JobImpl<T> implements YoJob<T> {

        @Nullable
        private final String id;
        private final AtomicReference<YoJobStatus> status;
        @Nullable
        private final T result;
        @Nullable
        private final String message;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private JobImpl(
                @Nullable String id,
                @NonNull YoJobStatus status,
                @Nullable T result,
                @Nullable String message) {
            this.id = id;
            this.status = new AtomicReference<>(status);
            this.result = result;
            this.message = message;
        }

        @Nullable
        @Override
        public String id() {
            return id;
        }

        @NonNull
        @Override
        public YoJobStatus status() {
            return status.get();
        }

        @Nullable
        @Override
        public T result() {
            return status.get() == YoJobStatus.SUCCEEDED ? result : null;
        }

        @Nullable
        @Override
        public String failureMessage() {
            return status.get() == YoJobStatus.FAILED ? message : null;
        }

        @Override
        public boolean isDone() {
            YoJobStatus current = status.get();
            return current == YoJobStatus.SUCCEEDED
                    || current == YoJobStatus.FAILED
                    || current == YoJobStatus.CANCELLED;
        }

        @Override
        public void cancel() {
            if (!isDone() && cancelled.compareAndSet(false, true)) {
                status.set(YoJobStatus.CANCELLED);
            }
        }
    }
}
