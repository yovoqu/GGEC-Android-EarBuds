package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.port.AiAudioSourceCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 测试用音频输入源目录：支持动态增删系统/外部源并即时触发目录观察者回调。
 */
public final class FakeAudioSourceCatalog implements AiAudioSourceCatalog {

    private final Map<String, AiAudioSource> systemSources = new LinkedHashMap<>();
    private final Map<String, AiAudioSource> externalSources = new LinkedHashMap<>();
    private final CopyOnWriteArraySet<Observer> observers = new CopyOnWriteArraySet<>();
    @Nullable
    private String preferredDefaultId;

    public FakeAudioSourceCatalog withSource(@NonNull AiAudioSource source) {
        Objects.requireNonNull(source, "source");
        systemSources.put(source.descriptor().id(), source);
        if (preferredDefaultId == null) {
            preferredDefaultId = source.descriptor().id();
        }
        notifyObservers();
        return this;
    }

    public FakeAudioSourceCatalog setDefaultSourceId(@NonNull String sourceId) {
        this.preferredDefaultId = Objects.requireNonNull(sourceId, "sourceId");
        return this;
    }

    public void removeSource(@NonNull String sourceId) {
        boolean removed = systemSources.remove(sourceId) != null;
        if (removed) {
            if (Objects.equals(preferredDefaultId, sourceId)) {
                preferredDefaultId = systemSources.isEmpty()
                        ? null : systemSources.keySet().iterator().next();
            }
            notifyObservers();
        }
    }

    @NonNull
    @Override
    public List<AiAudioSource.Descriptor> availableSources() {
        List<AiAudioSource.Descriptor> list = new ArrayList<>();
        for (AiAudioSource external : externalSources.values()) {
            list.add(external.descriptor());
        }
        for (AiAudioSource system : systemSources.values()) {
            list.add(system.descriptor());
        }
        return List.copyOf(list);
    }

    @Nullable
    @Override
    public AiAudioSource defaultSource() {
        if (!externalSources.isEmpty()) {
            return externalSources.values().iterator().next();
        }
        if (preferredDefaultId != null && systemSources.containsKey(preferredDefaultId)) {
            return systemSources.get(preferredDefaultId);
        }
        return systemSources.isEmpty() ? null : systemSources.values().iterator().next();
    }

    @Nullable
    @Override
    public AiAudioSource resolve(@NonNull String sourceId) {
        AiAudioSource external = externalSources.get(sourceId);
        if (external != null) {
            return external;
        }
        return systemSources.get(sourceId);
    }

    @Override
    public void registerExternal(@NonNull AiAudioSource source) {
        Objects.requireNonNull(source, "source");
        externalSources.put(source.descriptor().id(), source);
        notifyObservers();
    }

    @Override
    public void unregisterExternal(@NonNull String sourceId) {
        if (externalSources.remove(sourceId) != null) {
            notifyObservers();
        }
    }

    @Override
    public void addObserver(@NonNull Observer observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    @Override
    public void removeObserver(@NonNull Observer observer) {
        observers.remove(observer);
    }

    public int observerCount() {
        return observers.size();
    }

    private void notifyObservers() {
        if (observers.isEmpty()) {
            return;
        }
        List<AiAudioSource.Descriptor> snapshot = availableSources();
        for (Observer observer : observers) {
            observer.onSourcesChanged(snapshot);
        }
    }
}
