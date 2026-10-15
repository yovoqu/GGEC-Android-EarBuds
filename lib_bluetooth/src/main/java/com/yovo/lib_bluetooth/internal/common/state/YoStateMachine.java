package com.yovo.lib_bluetooth.internal.common.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 线程安全有限状态机，支持迁移校验、守卫条件、进入/退出动作与观察者通知。
 *
 * @param <S> 状态枚举类型
 */
public final class YoStateMachine<S extends Enum<S>> {

    private final S initialState;
    private final Map<S, Set<S>> allowedTransitions;
    private final Map<S, List<Runnable>> entryActions;
    private final Map<S, List<Runnable>> exitActions;
    private final Map<YoStateTransition<S>, List<YoStateGuard>> guardMap;
    private final CopyOnWriteArrayList<YoStateObserver<S>> observers;
    private final List<YoStateTransitionListener<S>> transitionListeners;

    private S state;
    private S previousState;
    private long lastTransitionTimestampMillis;

    private YoStateMachine(Builder<S> builder) {
        this.initialState = builder.initialState;
        this.state = builder.initialState;
        this.previousState = null;
        this.lastTransitionTimestampMillis = System.currentTimeMillis();
        this.allowedTransitions = Collections.unmodifiableMap(copyAllowedMap(builder.allowedTransitions));
        this.entryActions = Collections.unmodifiableMap(copyActionMap(builder.entryActions));
        this.exitActions = Collections.unmodifiableMap(copyActionMap(builder.exitActions));
        this.guardMap = Collections.unmodifiableMap(copyGuardMap(builder.guardMap));
        this.observers = new CopyOnWriteArrayList<>(builder.observers);
        this.transitionListeners = new ArrayList<>(builder.transitionListeners);
    }

    // ── State query ──────────────────────────────────────────────

    @NonNull
    public synchronized S state() {
        return state;
    }

    @Nullable
    public synchronized S previousState() {
        return previousState;
    }

    @NonNull
    public S initialState() {
        return initialState;
    }

    public synchronized long lastTransitionTimestampMillis() {
        return lastTransitionTimestampMillis;
    }

    public synchronized boolean is(@NonNull S candidate) {
        return state == candidate;
    }

    public synchronized boolean isAny(@NonNull S first, @NonNull S... rest) {
        if (state == first) return true;
        for (S s : rest) {
            if (state == s) return true;
        }
        return false;
    }

    // ── 迁移校验 ────────────────────────────────────

    /**
     * 检查从当前状态到 {@code target} 的迁移是否允许。
     */
    public synchronized boolean canTransitionTo(@NonNull S target) {
        Set<S> allowed = allowedTransitions.get(state);
        return allowed != null && allowed.contains(target);
    }

    // ── 迁移执行 ─────────────────────────────────────

    /**
     * 尝试迁移到 {@code next}。若迁移不允许或守卫拒绝则静默失败（返回当前状态）。
     *
     * @return 尝试后的状态（可能未变）
     */
    @NonNull
    public synchronized S transitionTo(@NonNull S next) {
        return transitionTo(next, null);
    }

    /**
     * 携带事件载荷尝试迁移到 {@code next}。
     * 守卫会收到事件；成功时通知 entry/exit 动作与观察者。
     *
     * @param next  目标状态
     * @param event 与此迁移关联的可选事件
     * @return 尝试后的状态
     */
    @NonNull
    public synchronized S transitionTo(@NonNull S next, @Nullable Object event) {
        if (state == next) return state;

        // 检查允许的迁移
        Set<S> allowed = allowedTransitions.get(state);
        if (allowed == null || !allowed.contains(next)) {
            return state;
        }

        // 评估守卫
        YoStateTransition<S> transition = new YoStateTransition<>(state, next);
        List<YoStateGuard> guards = guardMap.get(transition);
        if (guards != null) {
            for (YoStateGuard guard : guards) {
                if (!guard.allow(state, next, event)) {
                    return state;
                }
            }
        }

        // 执行 exit 动作
        List<Runnable> exits = exitActions.get(state);
        if (exits != null) {
            for (Runnable action : exits) {
                try {
                    action.run();
                } catch (Throwable ignored) {
                }
            }
        }

        S from = state;
        state = next;
        previousState = from;
        lastTransitionTimestampMillis = System.currentTimeMillis();

        // 执行 entry 动作
        List<Runnable> entries = entryActions.get(next);
        if (entries != null) {
            for (Runnable action : entries) {
                try {
                    action.run();
                } catch (Throwable ignored) {
                }
            }
        }

        // 通知观察者
        YoStateTransition<S> completed = new YoStateTransition<>(from, next);
        for (YoStateObserver<S> observer : observers) {
            try {
                observer.onStateChanged(from, next, event);
            } catch (Throwable ignored) {
            }
        }
        for (YoStateTransitionListener<S> listener : transitionListeners) {
            try {
                listener.onTransition(completed, event);
            } catch (Throwable ignored) {
            }
        }

        return state;
    }

    // ── 强制迁移（绕过守卫，供内部使用） ───────

    /**
     * 强制迁移到 {@code next}，绕过守卫谓词。
     * 仍须满足允许迁移规则。仅用于 teardown/release 路径。
     */
    @NonNull
    public synchronized S forceTransitionTo(@NonNull S next) {
        if (state == next) return state;

        Set<S> allowed = allowedTransitions.get(state);
        if (allowed == null || !allowed.contains(next)) {
            return state;
        }

        List<Runnable> exits = exitActions.get(state);
        if (exits != null) {
            for (Runnable action : exits) {
                try {
                    action.run();
                } catch (Throwable ignored) {
                }
            }
        }

        S from = state;
        state = next;
        previousState = from;
        lastTransitionTimestampMillis = System.currentTimeMillis();

        List<Runnable> entries = entryActions.get(next);
        if (entries != null) {
            for (Runnable action : entries) {
                try {
                    action.run();
                } catch (Throwable ignored) {
                }
            }
        }

        for (YoStateObserver<S> observer : observers) {
            try {
                observer.onStateChanged(from, next, null);
            } catch (Throwable ignored) {
            }
        }

        return state;
    }

    // ── Observer management ──────────────────────────────────────

    public void addObserver(@NonNull YoStateObserver<S> observer) {
        observers.addIfAbsent(observer);
    }

    public void removeObserver(@NonNull YoStateObserver<S> observer) {
        observers.remove(observer);
    }

    public void addTransitionListener(@NonNull YoStateTransitionListener<S> listener) {
        synchronized (transitionListeners) {
            transitionListeners.add(listener);
        }
    }

    public void removeTransitionListener(@NonNull YoStateTransitionListener<S> listener) {
        synchronized (transitionListeners) {
            transitionListeners.remove(listener);
        }
    }

    public int observerCount() {
        return observers.size();
    }

    // ── Builder ──────────────────────────────────────────────────

    public static <S extends Enum<S>> Builder<S> builder(@NonNull S initialState) {
        return new Builder<>(initialState);
    }

    public static final class Builder<S extends Enum<S>> {
        final S initialState;
        final Map<S, Set<S>> allowedTransitions;
        final Map<S, List<Runnable>> entryActions;
        final Map<S, List<Runnable>> exitActions;
        final Map<YoStateTransition<S>, List<YoStateGuard>> guardMap;
        final List<YoStateObserver<S>> observers;
        final List<YoStateTransitionListener<S>> transitionListeners;

        private Builder(S initialState) {
            this.initialState = initialState;
            this.allowedTransitions = new EnumMap<>(initialState.getDeclaringClass());
            this.entryActions = new EnumMap<>(initialState.getDeclaringClass());
            this.exitActions = new EnumMap<>(initialState.getDeclaringClass());
            this.guardMap = new java.util.HashMap<>();
            this.observers = new ArrayList<>();
            this.transitionListeners = new ArrayList<>();
        }

        /** 允许从 {@code from} 到 {@code to} 的迁移。每种有效配对需重复调用。 */
        public Builder<S> allow(@NonNull S from, @NonNull S to) {
            allowedTransitions.computeIfAbsent(from, k -> EnumSet.noneOf(from.getDeclaringClass())).add(to);
            return this;
        }

        /** 允许从 {@code from} 到 {@code targets} 中每个状态的迁移。 */
        @SafeVarargs
        public final Builder<S> allowFrom(@NonNull S from, @NonNull S... targets) {
            Set<S> set = allowedTransitions.computeIfAbsent(from,
                    k -> EnumSet.noneOf(from.getDeclaringClass()));
            Collections.addAll(set, targets);
            return this;
        }

        /** 允许从 {@code sources} 中每个状态到 {@code to} 的迁移。 */
        @SafeVarargs
        public final Builder<S> allowTo(@NonNull S to, @NonNull S... sources) {
            for (S source : sources) {
                allowedTransitions.computeIfAbsent(source,
                        k -> EnumSet.noneOf(source.getDeclaringClass())).add(to);
            }
            return this;
        }

        /** 为特定迁移注册守卫谓词。守卫须返回 true 迁移才会继续。 */
        public Builder<S> guard(@NonNull S from, @NonNull S to, @NonNull YoStateGuard guard) {
            guardMap.computeIfAbsent(new YoStateTransition<>(from, to), k -> new ArrayList<>()).add(guard);
            return this;
        }

        /** 注册进入 {@code state} 时执行的动作。 */
        public Builder<S> onEntry(@NonNull S state, @NonNull Runnable action) {
            entryActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
            return this;
        }

        /** 注册退出 {@code state} 时执行的动作。 */
        public Builder<S> onExit(@NonNull S state, @NonNull Runnable action) {
            exitActions.computeIfAbsent(state, k -> new ArrayList<>()).add(action);
            return this;
        }

        /** 添加状态观察者。 */
        public Builder<S> observer(@NonNull YoStateObserver<S> observer) {
            observers.add(observer);
            return this;
        }

        /** 添加迁移监听器。 */
        public Builder<S> transitionListener(@NonNull YoStateTransitionListener<S> listener) {
            transitionListeners.add(listener);
            return this;
        }

        public YoStateMachine<S> build() {
            return new YoStateMachine<>(this);
        }
    }

    // ── Copy helpers ─────────────────────────────────────────────

    private static <S extends Enum<S>> Map<S, Set<S>> copyAllowedMap(Map<S, Set<S>> source) {
        Map<S, Set<S>> copy = new EnumMap<>(source);
        for (Map.Entry<S, Set<S>> entry : copy.entrySet()) {
            entry.setValue(EnumSet.copyOf(entry.getValue()));
        }
        return copy;
    }

    private static <S extends Enum<S>> Map<S, List<Runnable>> copyActionMap(Map<S, List<Runnable>> source) {
        Map<S, List<Runnable>> copy = new EnumMap<>(source);
        for (Map.Entry<S, List<Runnable>> entry : copy.entrySet()) {
            entry.setValue(new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    private static <S extends Enum<S>> Map<YoStateTransition<S>, List<YoStateGuard>> copyGuardMap(
            Map<YoStateTransition<S>, List<YoStateGuard>> source) {
        Map<YoStateTransition<S>, List<YoStateGuard>> copy = new java.util.HashMap<>();
        for (Map.Entry<YoStateTransition<S>, List<YoStateGuard>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }
}
