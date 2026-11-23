package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceTransport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 端点集合：机型 → 端点的唯一索引，以及合并后的机型目录。
 *
 * <p>索引在<b>构造期</b>用各端点的 {@code catalog().models()} 建一次：同一 modelId 出现在两个
 * 端点属于装配缺陷，直接抛 {@link IllegalStateException}，不静默取第一个。</p>
 *
 * <p>合并目录的顺序 = 端点装配顺序 + 各端点目录顺序；页面「默认筛选 = 目录首项」依赖这个顺序。</p>
 */
public final class DeviceEndpoints {

    private final List<DeviceEndpoint> endpoints;
    private final Map<String, DeviceEndpoint> byModelId;
    private final Map<DeviceTransport, DeviceEndpoint> byTransport;
    private final List<DeviceProfileView> models;

    public DeviceEndpoints(@NonNull List<DeviceEndpoint> endpoints) {
        this.endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints"));
        if (this.endpoints.isEmpty()) {
            throw new IllegalArgumentException("DeviceEndpoints requires at least one endpoint");
        }
        Map<String, DeviceEndpoint> modelIndex = new LinkedHashMap<>();
        Map<DeviceTransport, DeviceEndpoint> transportIndex = new LinkedHashMap<>();
        Map<String, DeviceProfileView> profiles = new LinkedHashMap<>();
        for (DeviceEndpoint endpoint : this.endpoints) {
            if (transportIndex.put(endpoint.transport(), endpoint) != null) {
                throw new IllegalStateException(
                        "transport " + endpoint.transport() + " is served by two endpoints");
            }
            for (DeviceProfileView profile : endpoint.catalog().models()) {
                DeviceEndpoint previous = modelIndex.put(profile.modelId(), endpoint);
                if (previous != null) {
                    throw new IllegalStateException("modelId " + profile.modelId()
                            + " is declared by two endpoints");
                }
                profiles.put(profile.modelId(), profile);
            }
        }
        this.byModelId = Collections.unmodifiableMap(modelIndex);
        this.byTransport = Collections.unmodifiableMap(transportIndex);
        this.models = List.copyOf(profiles.values());
    }

    /** 全部端点，按装配顺序。 */
    @NonNull
    public List<DeviceEndpoint> all() {
        return endpoints;
    }

    /** 机型所属端点；未登记的机型返回空。 */
    @NonNull
    public Optional<DeviceEndpoint> of(@NonNull String modelId) {
        return Optional.ofNullable(byModelId.get(Objects.requireNonNull(modelId, "modelId")));
    }

    /** 传输所属端点；装配期已保证每种传输只有一个端点。 */
    @NonNull
    public Optional<DeviceEndpoint> of(@NonNull DeviceTransport transport) {
        return Optional.ofNullable(
                byTransport.get(Objects.requireNonNull(transport, "transport")));
    }

    /** 合并后的机型目录，供 {@link DeviceCatalog} 实现领域端口。 */
    @NonNull
    public List<DeviceProfileView> models() {
        return models;
    }

    /** 按装配顺序释放每个端点的 SDK 资源。 */
    public void close() {
        for (DeviceEndpoint endpoint : endpoints) {
            endpoint.close();
        }
    }
}
