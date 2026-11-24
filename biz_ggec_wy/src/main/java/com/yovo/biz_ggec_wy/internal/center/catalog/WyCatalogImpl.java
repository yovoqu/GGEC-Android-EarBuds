package com.yovo.biz_ggec_wy.internal.center.catalog;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.api.WyDevice;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * WY 设备能力目录：声明产品线、产品型号及其支持的能力与呈现形式。
 */
public final class WyCatalogImpl {

    public static final String PROJECT_WY = "WY";
    public static final String PROJECT_WY_NAME = "WY 系列";

    public static final String MODEL_WY01 = "WY01";
    public static final String MODEL_WY01_NAME = "WY01";

    private static final Set<WyDevice.Capability> WY01_CAPABILITIES = Collections.unmodifiableSet(EnumSet.of(
            WyDevice.Capability.CONNECTION,
            WyDevice.Capability.RECORD_STREAM,
            WyDevice.Capability.AI_CHAT,
            WyDevice.Capability.DEVICE_INFO,
            WyDevice.Capability.BATTERY
    ));

    private final Map<String, WyDevice.Product> products = new LinkedHashMap<>();
    private final Map<WyDevice.Capability, WyDevice.Feature> features = new LinkedHashMap<>();

    public WyCatalogImpl() {
        products.put(MODEL_WY01, new WyDevice.Product(
                MODEL_WY01,
                MODEL_WY01_NAME,
                PROJECT_WY,
                WyDevice.Type.EARBUDS,
                WY01_CAPABILITIES
        ));

        features.put(WyDevice.Capability.CONNECTION, new WyDevice.Feature(
                WyDevice.Capability.CONNECTION,
                "经典蓝牙SPP连接",
                WyDevice.Group.CONTROL,
                WyDevice.Form.GATE,
                List.of(),
                Optional.empty()
        ));

        features.put(WyDevice.Capability.RECORD_STREAM, new WyDevice.Feature(
                WyDevice.Capability.RECORD_STREAM,
                "随录随传",
                WyDevice.Group.RECORD,
                WyDevice.Form.ACTION,
                List.of(),
                Optional.empty()
        ));

        features.put(WyDevice.Capability.AI_CHAT, new WyDevice.Feature(
                WyDevice.Capability.AI_CHAT,
                "AI 对话",
                WyDevice.Group.AI,
                WyDevice.Form.GATE,
                List.of(),
                Optional.empty()
        ));

        features.put(WyDevice.Capability.DEVICE_INFO, new WyDevice.Feature(
                WyDevice.Capability.DEVICE_INFO,
                "设备信息",
                WyDevice.Group.ABOUT,
                WyDevice.Form.GATE,
                List.of(),
                Optional.empty()
        ));

        features.put(WyDevice.Capability.BATTERY, new WyDevice.Feature(
                WyDevice.Capability.BATTERY,
                "耳机电量",
                WyDevice.Group.ABOUT,
                WyDevice.Form.GATE,
                List.of(),
                Optional.empty()
        ));
    }

    @NonNull
    public List<WyDevice.Project> projects() {
        return List.of(new WyDevice.Project(PROJECT_WY, PROJECT_WY_NAME));
    }

    @NonNull
    public List<WyDevice.Product> models() {
        return List.copyOf(products.values());
    }

    @NonNull
    public WyDevice.Product product(@NonNull String modelId) {
        Objects.requireNonNull(modelId, "modelId");
        WyDevice.Product p = products.get(modelId.toUpperCase());
        if (p == null) {
            return new WyDevice.Product(modelId, modelId, PROJECT_WY, WyDevice.Type.EARBUDS, Set.of());
        }
        return p;
    }

    public boolean supports(@NonNull String modelId, @NonNull WyDevice.Capability capability) {
        return product(modelId).capabilities().contains(capability);
    }

    @NonNull
    public WyDevice.Feature feature(@NonNull String modelId, @NonNull WyDevice.Capability capability) {
        WyDevice.Feature f = features.get(capability);
        if (f != null) {
            return f;
        }
        return new WyDevice.Feature(
                capability,
                capability.name(),
                WyDevice.Group.CONTROL,
                WyDevice.Form.GATE,
                List.of(),
                Optional.empty()
        );
    }
}
