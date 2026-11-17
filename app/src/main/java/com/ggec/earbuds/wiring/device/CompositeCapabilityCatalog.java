package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 聚合机型能力目录：动态汇聚所有已安装驱动的产品目录。
 */
public final class CompositeCapabilityCatalog implements DeviceCapabilityCatalog {

    private final List<DeviceDriver> drivers;
    private final List<DeviceProfileView> cachedModels;
    private final Map<String, DeviceProfileView> modelsById;

    public CompositeCapabilityCatalog(@NonNull List<DeviceDriver> drivers) {
        this.drivers = List.copyOf(Objects.requireNonNull(drivers, "drivers"));
        List<DeviceProfileView> all = new ArrayList<>();
        Map<String, DeviceProfileView> map = new LinkedHashMap<>();
        for (DeviceDriver driver : drivers) {
            for (DeviceProfileView view : driver.catalog().models()) {
                all.add(view);
                map.put(view.modelId(), view);
            }
        }
        this.cachedModels = List.copyOf(all);
        this.modelsById = Collections.unmodifiableMap(map);
    }

    @NonNull
    @Override
    public List<DeviceProfileView> models() {
        return cachedModels;
    }

    @NonNull
    @Override
    public Optional<DeviceProfileView> model(String modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelsById.get(modelId));
    }
}
