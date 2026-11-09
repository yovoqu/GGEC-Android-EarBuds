package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.port.DeviceProfileCatalog;

import java.util.List;
import java.util.Optional;

/** 载入项目名称与能力清单；只按真实连接设备解析，不做兜底假机型。 */
public final class LoadDeviceProfiles {

    private final DeviceProfileCatalog catalog;

    public LoadDeviceProfiles(DeviceProfileCatalog catalog) {
        this.catalog = catalog;
    }

    public List<DeviceModel> models() {
        return catalog.models();
    }

    public Optional<DeviceProfile> profileFor(DeviceSnapshot snapshot) {
        if (snapshot == null || !snapshot.isConnected()) {
            return Optional.empty();
        }
        return profileFor(snapshot.modelId(), snapshot.variantId());
    }

    public Optional<DeviceProfile> profileFor(String modelId, String variantId) {
        if (modelId == null || modelId.isEmpty()) {
            return Optional.empty();
        }
        Optional<DeviceProfile> exact = catalog.profile(modelId, variantId);
        if (exact.isPresent()) {
            return exact;
        }
        if (catalog.model(modelId).isPresent()) {
            return Optional.of(catalog.defaultProfile(modelId));
        }
        return Optional.empty();
    }
}
