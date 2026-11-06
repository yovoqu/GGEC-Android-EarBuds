package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoFact;
import com.yovo.lib_ai.models.spec.YoModality;
import com.yovo.lib_ai.models.spec.YoModelCapability;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoSupport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 模型能力注册表：把"一个 id 的规格"聚成一个可查询的整体，供界面展示与路由选型。
 *
 * <p>数据来自三层：按 id 推断（{@link YoModelInference}）→ 平台上报（模型列表解码时写入的
 * {@link YoModelSpec}）→ 用户覆盖（{@link YoModelCatalog}）。每项能力都保留 {@link YoFact}
 * 三态，{@link YoSupport#UNKNOWN} 不等于不支持。</p>
 *
 * <p>未登记过的 id 不抛异常：按 id 推断出一份规格再叠加用户覆盖，因此查询总能得到答复
 * （多半是推断或未知）。{@link #has(String)} 用来区分"来自列表"和"临时问一句"。</p>
 *
 * <p>不可变；{@link #withCatalog} / {@link #plus} 返回新表。</p>
 */
public final class YoCapabilityRegistry {

    private final Map<String, YoModelSpec> specs;
    private final YoModelCatalog catalog;

    private YoCapabilityRegistry(@NonNull Map<String, YoModelSpec> specs, @NonNull YoModelCatalog catalog) {
        this.specs = specs;
        this.catalog = catalog;
    }

    /** 空表：任何 id 都只能按名字推断。 */
    @NonNull
    public static YoCapabilityRegistry empty() {
        return new YoCapabilityRegistry(Map.of(), YoModelCatalog.standard());
    }

    /** 从平台模型列表项构建。 */
    @NonNull
    public static YoCapabilityRegistry fromInfos(@Nullable Collection<YoModelInfo> infos) {
        LinkedHashMap<String, YoModelSpec> map = new LinkedHashMap<>();
        if (infos != null) {
            for (YoModelInfo info : infos) {
                if (info == null) {
                    continue;
                }
                map.putIfAbsent(key(info.id()), info.spec());
            }
        }
        return new YoCapabilityRegistry(map, YoModelCatalog.standard());
    }

    /** 从已解析的规格构建。 */
    @NonNull
    public static YoCapabilityRegistry fromSpecs(@Nullable Collection<YoModelSpec> specs) {
        LinkedHashMap<String, YoModelSpec> map = new LinkedHashMap<>();
        if (specs != null) {
            for (YoModelSpec spec : specs) {
                if (spec != null) {
                    map.putIfAbsent(key(spec.id()), spec);
                }
            }
        }
        return new YoCapabilityRegistry(map, YoModelCatalog.standard());
    }

    /** 叠加用户覆盖，返回新表；旧表不受影响。 */
    @NonNull
    public YoCapabilityRegistry withCatalog(@NonNull YoModelCatalog catalog) {
        return new YoCapabilityRegistry(this.specs, catalog);
    }

    /** 追加或替换一个规格。 */
    @NonNull
    public YoCapabilityRegistry plus(@NonNull YoModelSpec spec) {
        LinkedHashMap<String, YoModelSpec> map = new LinkedHashMap<>(specs);
        map.put(key(spec.id()), spec);
        return new YoCapabilityRegistry(map, catalog);
    }

    /** 登记过的模型规格，按登记顺序，已叠加用户覆盖。 */
    @NonNull
    public List<YoModelSpec> models() {
        List<YoModelSpec> result = new ArrayList<>(specs.size());
        for (YoModelSpec spec : specs.values()) {
            result.add(applyOverride(spec));
        }
        return result;
    }

    /** 这个 id 是否来自模型列表（而不是仅凭名字推断）。 */
    public boolean has(@Nullable String modelId) {
        return modelId != null && specs.containsKey(key(modelId));
    }

    /**
     * 取某个 id 的规格：登记过用登记的，没登记过按名字推断；两者都会再叠加用户覆盖。
     */
    @NonNull
    public YoModelSpec spec(@Nullable String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("model id is required");
        }
        YoModelSpec base = specs.get(key(modelId));
        if (base == null) {
            base = YoModelInference.infer(modelId);
        }
        return applyOverride(base);
    }

    @NonNull
    public YoFact capability(@Nullable String modelId, @NonNull YoModelCapability capability) {
        return spec(modelId).capability(capability);
    }

    @NonNull
    public YoFact input(@Nullable String modelId, @NonNull YoModality modality) {
        return spec(modelId).input(modality);
    }

    @NonNull
    public YoFact output(@Nullable String modelId, @NonNull YoModality modality) {
        return spec(modelId).output(modality);
    }

    @NonNull
    public YoModelCategory category(@Nullable String modelId) {
        return spec(modelId).category();
    }

    /**
     * 登记里满足要求的模型，按登记顺序。{@link YoModelFit#REJECTED} 的剔除；
     * {@link YoModelFit#INFERRED} 保留（调用方可用 {@link YoModelFit} 自行降级）。
     */
    @NonNull
    public List<YoModelSpec> matching(@NonNull YoModelRequirement requirement) {
        List<YoModelSpec> result = new ArrayList<>();
        for (YoModelSpec spec : models()) {
            if (requirement.fit(spec).accepted()) {
                result.add(spec);
            }
        }
        return result;
    }

    /**
     * 挑一个最靠谱的模型：先确认过的，再推断的；都没有返回 {@code null}。
     */
    @Nullable
    public YoModelSpec best(@NonNull YoModelRequirement requirement) {
        YoModelSpec inferred = null;
        for (YoModelSpec spec : models()) {
            YoModelFit fit = requirement.fit(spec);
            if (fit == YoModelFit.CONFIRMED) {
                return spec;
            }
            if (fit == YoModelFit.INFERRED && inferred == null) {
                inferred = spec;
            }
        }
        return inferred;
    }

    @NonNull
    private YoModelSpec applyOverride(@NonNull YoModelSpec base) {
        YoModelOverride override = catalog.override(base.id());
        return override == null ? base : override.applyTo(base);
    }

    @NonNull
    private static String key(@NonNull String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
