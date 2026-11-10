package com.yovo.lib_bluetooth.internal.common.diagnostics;

import androidx.annotation.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 蓝牙模块统一运行时事件总线（BLE 与 Classic 共用）。
 *
 * <p>按事件类型（字符串常量）发布 / 订阅跨组件、跨能力域的进程内事件。
 * 由各运行时（{@code YoCbtRuntime} / {@code YoBleRuntime}）持有独立实例，随运行时释放而清空。
 * 本类取代原 Classic 私有的 {@code YoCbtEventHub}，消除重复实现并让 BLE 具备对称的事件通道。</p>
 */
public final class BluetoothEventBus {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<Object>>> listeners;

    public BluetoothEventBus() {
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
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
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

    /** 移除所有事件类型的全部订阅者。 */
    public void clear() {
        listeners.clear();
    }

    /** 返回当前有活跃订阅的事件类型数量。 */
    public int eventTypeCount() {
        return listeners.size();
    }

    /** 返回所有事件类型的订阅者总数。 */
    public int subscriberCount() {
        int count = 0;
        for (Map.Entry<String, CopyOnWriteArrayList<Consumer<Object>>> entry : listeners.entrySet()) {
            count += entry.getValue().size();
        }
        return count;
    }
}
