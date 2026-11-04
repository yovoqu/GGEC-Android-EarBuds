package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceVariant;

import java.util.List;
import java.util.Optional;

/** 项目名称 / 变种 / 能力清单目录。 */
public interface DeviceProfileCatalog {

    List<DeviceModel> models();

    Optional<DeviceModel> model(String modelId);

    List<DeviceVariant> variants(String modelId);

    Optional<DeviceProfile> profile(String modelId, String variantId);

    DeviceProfile defaultProfile(String modelId);
}
