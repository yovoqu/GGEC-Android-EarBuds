package com.yovo.lib_bluetooth.internal.classic.runtime;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description yocbt 运行时进程内事件总线，按事件类型发布与订阅跨模块消息。
 */
public final class YoCbtEventHub {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> listeners;

    public YoCbtEventHub() {
        this.listeners = new ConcurrentHashMap<>();
    }

    /**
     * 向指定事件类型的所有订阅者发布事件。
     *
     * @param eventType 事件类型标识
     * @param payload   事件载荷，可为 null
     */
    public void publish(@NonNull String eventType, Object payload) {
        CopyOnWriteArrayList<Consumer<Object>> subscribers = listeners.get(eventType);
        if (subscribers == null || subscribers.isEmpty()) return;
        for (Consumer<Object> listener : subscribers) {
            listener.accept(payload);
        }
    }

    /**
     * 订阅指定事件类型。
     *
     * @param eventType 要订阅的事件类型
     * @param listener  事件发布时调用的消费者
     */
    public void subscribe(@NonNull String eventType, @NonNull Consumer<Object> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /**
     * 取消订阅。使用引用相等性，必须传入与订阅时相同的 {@code Consumer} 实例。
     *
     * @param eventType 要取消订阅的事件类型
     * @param listener  要移除的消费者
     */
    public void unsubscribe(@NonNull String eventType, @NonNull Consumer<Object> listener) {
        CopyOnWriteArrayList<Consumer<Object>> subscribers = listeners.get(eventType);
        if (subscribers != null) {
            subscribers.remove(listener);
            if (subscribers.isEmpty()) {
                listeners.remove(eventType, subscribers);
            }
        }
    }

    /**
     * 移除所有事件类型的全部订阅者。
     */
    public void clear() {
        listeners.clear();
    }

    /**
     * 返回当前有活跃订阅的事件类型数量。
     */
    public int eventTypeCount() {
        return listeners.size();
    }

    /**
     * 返回所有事件类型的订阅者总数。
     */
    public int subscriberCount() {
        int count = 0;
        for (CopyOnWriteArrayList<Consumer<Object>> subscribers : listeners.values()) {
            count += subscribers.size();
        }
        return count;
    }
}
