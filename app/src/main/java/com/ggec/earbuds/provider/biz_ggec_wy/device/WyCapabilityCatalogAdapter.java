package com.ggec.earbuds.provider.biz_ggec_wy.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.provider.biz_ggec_wy.value.WyValueMapper;
import com.yovo.biz_ggec_wy.YoWySdk;
import com.yovo.biz_ggec_wy.api.WyDevice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 把 WY 产品表投影成 APP 的能力目录。
 */
public final class WyCapabilityCatalogAdapter implements DeviceCapabilityCatalog {

    private final List<DeviceProfileView> profiles;
    private final Map<String, DeviceProfileView> profilesByModelId;

    public WyCapabilityCatalogAdapter(@NonNull YoWySdk sdk) {
        Objects.requireNonNull(sdk, "sdk");
        this.profiles = project(sdk);
        Map<String, DeviceProfileView> index = new LinkedHashMap<>();
        for (DeviceProfileView profile : profiles) {
            index.put(profile.modelId(), profile);
        }
        this.profilesByModelId = Map.copyOf(index);
    }

    @NonNull
    @Override
    public List<DeviceProfileView> models() {
        return profiles;
    }

    @NonNull
    @Override
    public Optional<DeviceProfileView> model(String modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("modelId must not be null");
        }
        return Optional.ofNullable(profilesByModelId.get(modelId));
    }

    @NonNull
    private static List<DeviceProfileView> project(@NonNull YoWySdk sdk) {
        List<DeviceProfileView> projected = new ArrayList<>();
        for (WyDevice.Product product : sdk.products()) {
            List<DeviceCapabilityView> capabilities = new ArrayList<>();
            for (WyDevice.Capability capability : product.capabilities()) {
                capabilities.add(WyValueMapper.toCapabilityView(
                        sdk.feature(product.modelId(), capability)));
            }
            projected.add(new DeviceProfileView(
                    product.modelId(), product.displayName(), List.copyOf(capabilities)));
        }
        return List.copyOf(projected);
    }
}
