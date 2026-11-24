package com.ggec.earbuds.provider.biz_ggec_record.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.yovo.biz_ggec_record.YoRecordSdk;
import com.yovo.biz_ggec_record.model.RecordProduct;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 把 Record 产品目录投影成 APP 能力目录。录播机型无耳机设置项，能力列表为空。
 */
public final class RecordCapabilityCatalogAdapter implements DeviceCapabilityCatalog {

    private final List<DeviceProfileView> profiles;
    private final Map<String, DeviceProfileView> byModelId;

    public RecordCapabilityCatalogAdapter() {
        List<DeviceProfileView> projected = new ArrayList<>();
        for (RecordProduct product : YoRecordSdk.products()) {
            projected.add(new DeviceProfileView(
                    product.modelId(), product.displayName(), List.of()));
        }
        this.profiles = List.copyOf(projected);
        Map<String, DeviceProfileView> index = new LinkedHashMap<>();
        for (DeviceProfileView profile : profiles) {
            index.put(profile.modelId(), profile);
        }
        this.byModelId = Map.copyOf(index);
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
        return Optional.ofNullable(byModelId.get(modelId));
    }
}
