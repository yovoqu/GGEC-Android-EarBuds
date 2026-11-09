package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * BLE 内部事件总线：按事件类型管理强类型订阅者，释放时全量清理。
 */
public final class YoBleEventHub {

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();

    public <T> void subscribe(@NonNull Class<T> eventType, @NonNull Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .addIfAbsent(listener);
    }

    public <T> void unsubscribe(@NonNull Class<T> eventType, @NonNull Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(eventType);
        if (list != null) {
            list.remove(listener);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(@NonNull T event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Consumer<?> raw : list) {
            try {
                ((Consumer<T>) raw).accept(event);
            } catch (Throwable ignored) {
            }
        }
    }

    public void clear() {
        listeners.clear();
    }
}
