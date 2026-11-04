package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.model.Capability;
import com.ggec.earbuds.domain.device.model.CapabilitySet;
import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.DeviceProfile;
import com.ggec.earbuds.domain.device.model.DeviceVariant;
import com.ggec.earbuds.domain.device.model.ProfileParams;
import com.ggec.earbuds.domain.device.port.DeviceProfileCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 项目名称 / 变种 / 能力清单的静态目录。
 *
 * <p>这里只描述 APP 需要知道的两件事：项目名称下有几种变种、每种变种支持哪些能力。
 * 变种背后的 FeatureCode / market / 扫描方式一概不在此处，由 SDK 负责。</p>
 */
public final class DeviceProfileCatalogImpl implements DeviceProfileCatalog {

    private static final Capability[] HS01_CAPS = {
            Capability.CONNECTION, Capability.BATTERY, Capability.SOUND_QUALITY,
            Capability.EQ_MODE, Capability.TOUCH_LONG, Capability.TOUCH_SHORT,
            Capability.TOUCH_TAP, Capability.WEAR_DETECT, Capability.VOICE_WAKEUP,
            Capability.GAME_MODE, Capability.FALL_ALERT, Capability.FIND_EAR,
            Capability.VOLUME_REMIND, Capability.DEVICE_INFO, Capability.RENAME,
            Capability.CLEAR_PAIRING, Capability.RECORD_LOCAL, Capability.RECORD_STREAM,
            Capability.RECORD_LIST, Capability.RECORD_TRANSFER, Capability.AI_ASSISTANT,
            Capability.OTA, Capability.OFFLINE_LOG, Capability.CLASSIC_BT
    };

    private static final Capability[] HS02_EXTRA = {
            Capability.DUAL_CONNECTION, Capability.CUSTOM_EQ, Capability.TOUCH_DOUBLE,
            Capability.SERVICE_STATUS, Capability.BEGINNER_MODE, Capability.INTERPRET
    };

    private static final Capability[] WY01_CAPS = {
            Capability.CONNECTION, Capability.BATTERY, Capability.DEVICE_INFO
    };

    private final List<DeviceModel> models;
    private final Map<String, List<DeviceVariant>> variantsByModel;
    private final Map<String, DeviceProfile> profilesByKey;

    public DeviceProfileCatalogImpl() {
        List<DeviceModel> modelList = new ArrayList<>();
        Map<String, List<DeviceVariant>> variantMap = new LinkedHashMap<>();
        Map<String, DeviceProfile> profileMap = new LinkedHashMap<>();

        DeviceModel hs01 = DeviceModel.of("HS", "01");
        DeviceModel hs02 = DeviceModel.of("HS", "02");
        DeviceModel wy01 = DeviceModel.of("WY", "01");
        modelList.add(hs01);
        modelList.add(hs02);
        modelList.add(wy01);

        CapabilitySet hs01Set = CapabilitySet.of(HS01_CAPS);
        CapabilitySet hs02Set = merge(HS01_CAPS, HS02_EXTRA);
        CapabilitySet wy01Set = CapabilitySet.of(WY01_CAPS);

        List<DeviceVariant> hs01Variants = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            DeviceVariant variant = DeviceVariant.of(hs01.id(), "v" + index);
            hs01Variants.add(variant);
            profileMap.put(key(hs01.id(), variant.id()),
                    new DeviceProfile(hs01, variant, hs01Set, ProfileParams.base()));
        }
        variantMap.put(hs01.id(), Collections.unmodifiableList(hs01Variants));

        DeviceVariant hs02Variant = DeviceVariant.of(hs02.id(), "v1");
        variantMap.put(hs02.id(), List.of(hs02Variant));
        profileMap.put(key(hs02.id(), hs02Variant.id()),
                new DeviceProfile(hs02, hs02Variant, hs02Set, ProfileParams.extended()));

        // WY01 只有占位能力，变种数待定。
        variantMap.put(wy01.id(), Collections.emptyList());
        DeviceVariant wy01Placeholder = DeviceVariant.of(wy01.id(), "default");
        profileMap.put(key(wy01.id(), wy01Placeholder.id()),
                new DeviceProfile(wy01, wy01Placeholder, wy01Set, ProfileParams.base()));

        this.models = Collections.unmodifiableList(modelList);
        this.variantsByModel = Collections.unmodifiableMap(variantMap);
        this.profilesByKey = Collections.unmodifiableMap(profileMap);
    }

    private static CapabilitySet merge(Capability[] base, Capability[] extra) {
        Capability[] all = new Capability[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return CapabilitySet.of(all);
    }

    private static String key(String modelId, String variantId) {
        return modelId + "#" + (variantId == null ? "" : variantId);
    }

    @Override
    public List<DeviceModel> models() {
        return models;
    }

    @Override
    public Optional<DeviceModel> model(String modelId) {
        for (DeviceModel model : models) {
            if (model.id().equals(modelId)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<DeviceVariant> variants(String modelId) {
        List<DeviceVariant> variants = variantsByModel.get(modelId);
        return variants == null ? Collections.emptyList() : variants;
    }

    @Override
    public Optional<DeviceProfile> profile(String modelId, String variantId) {
        return Optional.ofNullable(profilesByKey.get(key(modelId, variantId)));
    }

    @Override
    public DeviceProfile defaultProfile(String modelId) {
        List<DeviceVariant> variants = variants(modelId);
        if (!variants.isEmpty()) {
            DeviceProfile profile = profilesByKey.get(key(modelId, variants.get(0).id()));
            if (profile != null) {
                return profile;
            }
        }
        DeviceProfile placeholder = profilesByKey.get(key(modelId, "default"));
        if (placeholder != null) {
            return placeholder;
        }
        throw new IllegalArgumentException("Unknown model: " + modelId);
    }
}
