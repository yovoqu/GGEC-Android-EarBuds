package com.ggec.earbuds.domain.device.model;

import java.util.List;
import java.util.Objects;

/**
 * 一个机型在 APP 侧的完整呈现：机型标识、展示名、能力清单。
 *
 * <p>数据全部来自 biz 设备目录；APP 不新增、不合并、不裁剪能力。</p>
 */
public record DeviceProfileView(String modelId, String displayName,
                                List<DeviceCapabilityView> capabilities) {

    public DeviceProfileView {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(displayName, "displayName");
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean supports(String capabilityId) {
        return indexOf(capabilityId) >= 0;
    }

    /**
     * {@code capabilityId} 对应的能力描述。
     *
     * @throws IllegalArgumentException 该机型没有此能力；能力 id 来自目录，缺失是上游缺陷，
     *                                  不返回 null 让调用方在渲染时炸得更晚
     */
    public DeviceCapabilityView capability(String capabilityId) {
        int index = indexOf(capabilityId);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "capability " + capabilityId + " is not declared by model " + modelId);
        }
        return capabilities.get(index);
    }

    private int indexOf(String capabilityId) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        for (int index = 0; index < capabilities.size(); index++) {
            if (capabilities.get(index).id().equals(capabilityId)) {
                return index;
            }
        }
        return -1;
    }
}
