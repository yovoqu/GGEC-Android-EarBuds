package com.yovo.lib_common.system.keepalive.internal.ack;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保活启动回执注册表：客户端在派发启动 Intent 前登记一次等待，
 * 服务端在 {@code onStartCommand} 的 promote 成功/失败后回填结果。
 *
 * <p>这是进程内真事件通道，不是轮询也不是降级猜测：
 * <ul>
 *     <li>promote 成功 → {@code onForegroundStarted}；</li>
 *     <li>promote 失败 / 派发异常 / 超时 → {@code onStartFailed}；</li>
 *     <li>stop/release 会取消未完成的等待，避免幽灵超时回调。</li>
 * </ul>
 */
public final class KeepAliveAckRegistry {

    private static final long DEFAULT_TIMEOUT_MS = 5_000L;

    private static final KeepAliveAckRegistry INSTANCE = new KeepAliveAckRegistry();

    public interface Listener {
        void onAck(@NonNull String sessionId, boolean success, @Nullable String detail);
    }

    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private KeepAliveAckRegistry() {
    }

    @NonNull
    public static KeepAliveAckRegistry get() {
        return INSTANCE;
    }

    /**
     * 登记一次等待；同一 sessionId 重复登记时，前一个等待被取消（最新一次为准）。
     */
    public void await(@NonNull String sessionId, @NonNull Listener listener) {
        await(sessionId, listener, DEFAULT_TIMEOUT_MS);
    }

    public void await(@NonNull String sessionId,
                      @NonNull Listener listener,
                      long timeoutMs) {
        Pending created = new Pending(listener);
        Pending previous = pending.put(sessionId, created);
        if (previous != null) {
            previous.cancel();
        }
        created.scheduleTimeout(sessionId, timeoutMs);
    }

    /** 服务端/派发链路回填结果；无等待者时为 no-op。 */
    public void complete(@NonNull String sessionId,
                         boolean success,
                         @Nullable String detail) {
        Pending current = pending.remove(sessionId);
        if (current != null) {
            current.deliver(sessionId, success, detail);
        }
    }

    /** 取消等待（stop/release 时调用），不触发回调。 */
    public void cancel(@NonNull String sessionId) {
        Pending current = pending.remove(sessionId);
        if (current != null) {
            current.cancel();
        }
    }

    private final class Pending {

        private final Listener listener;
        private volatile String sessionIdForTimeout;
        private boolean done;
        private final Runnable timeoutRunnable;

        private Pending(@NonNull Listener listener) {
            this.listener = listener;
            this.timeoutRunnable = () -> {
                synchronized (Pending.this) {
                    if (done) {
                        return;
                    }
                    done = true;
                    pending.remove(sessionIdForTimeout);
                    listener.onAck(sessionIdForTimeout, false, "ack timeout");
                }
            };
        }

        void scheduleTimeout(@NonNull String sessionId, long timeoutMs) {
            sessionIdForTimeout = sessionId;
            mainHandler.postDelayed(timeoutRunnable, Math.max(1L, timeoutMs));
        }

        void deliver(@NonNull String sessionId, boolean success, @Nullable String detail) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                mainHandler.removeCallbacks(timeoutRunnable);
                listener.onAck(sessionId, success, detail);
            }
        }

        void cancel() {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                mainHandler.removeCallbacks(timeoutRunnable);
            }
        }
    }
}
