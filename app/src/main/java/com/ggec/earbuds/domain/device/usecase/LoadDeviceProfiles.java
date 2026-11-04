package com.ggec.earbuds.domain.device.usecase;

import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.port.DevicePreferences;
import com.ggec.earbuds.domain.device.port.DeviceProfileCatalog;

import java.util.List;

/** 载入项目名称与能力清单。 */
public final class LoadDeviceProfiles {

    private final DeviceProfileCatalog catalog;
    private final DevicePreferences preferences;

    public LoadDeviceProfiles(DeviceProfileCatalog catalog, DevicePreferences preferences) {
        this.catalog = catalog;
        this.preferences = preferences;
    }

    public List<DeviceModel> models() {
        return catalog.models();
    }

    public String selectedModelId() {
        String selected = preferences.selectedModelId();
        if (selected != null && catalog.model(selected).isPresent()) {
            return selected;
        }
        List<DeviceModel> models = catalog.models();
        return models.isEmpty() ? "" : models.get(0).id();
    }

    public void selectModel(String modelId) {
        preferences.selectModel(modelId);
    }

    public DeviceProfile selectedProfile() {
        return catalog.defaultProfile(selectedModelId());
    }

    public DeviceProfile profileFor(String modelId, String variantId) {
        return catalog.profile(modelId, variantId)
                .orElseGet(() -> catalog.defaultProfile(modelId));
    }
}
