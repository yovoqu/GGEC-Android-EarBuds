package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.DeviceProfileView;

import java.util.List;
import java.util.Optional;

/**
 * 机型能力目录。只读，不含任何「机型 → 能力」静态表：
 * 唯一实现是 {@code provider/biz_ggec_hs/HsCapabilityCatalogAdapter}，数据来自 biz 设备目录。
 */
public interface DeviceCapabilityCatalog {

    List<DeviceProfileView> models();

    Optional<DeviceProfileView> model(String modelId);
}
