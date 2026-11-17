package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 合并目录：把 {@link DeviceEndpoints#models()} 的单一清单实现成 {@link DeviceCapabilityCatalog}。
 *
 * <p>{@code model(null)} 是调用方缺陷，直接抛；未登记的机型返回 {@link Optional#empty()}，
 * 由用例决定「这台机器型没有能力入口」（页面空态）。</p>
 */
public final class DeviceCatalog implements DeviceCapabilityCatalog {

    private final List<DeviceProfileView> models;
    private final Map<String, DeviceProfileView> byModelId;

    public DeviceCatalog(@NonNull List<DeviceProfileView> models) {
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        Map<String, DeviceProfileView> index = new LinkedHashMap<>();
        for (DeviceProfileView profile : this.models) {
            index.put(profile.modelId(), profile);
        }
        this.byModelId = Map.copyOf(index);
    }

    @NonNull
    @Override
    public List<DeviceProfileView> models() {
        return models;
    }

    @NonNull
    @Override
    public Optional<DeviceProfileView> model(@NonNull String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        return Optional.ofNullable(byModelId.get(modelId));
    }
}
