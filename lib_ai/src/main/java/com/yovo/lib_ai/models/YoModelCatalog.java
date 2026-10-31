package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.models.spec.YoModelSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 把三层来源按优先级合成一个答复：<b>推断 → 平台上报 → 用户覆盖</b>，后者逐项盖过前者。
 *
 * <p>两个入口对应两种手里有什么的情形：
 * <ul>
 *   <li>{@link #resolve(YoModelInfo)}：拉过平台列表，规格里已经有平台上报的内容；</li>
 *   <li>{@link #describe(String)}：只有一个 id（用户手填的模型名、或者上次存下来的 id），
 *       没有平台上报，只能推断。</li>
 * </ul>
 * 两者都会再叠加用户覆盖，连"一个能力都不勾"也算数。</p>
 *
 * <p>不可变：{@link #withOverride} 返回新的目录，旧目录不受影响——
 * 设置页在改动过程中随时可以回滚，也不用担心某个 ViewModel 手里攥着半更新的状态。</p>
 */
public final class YoModelCatalog {

    private static final YoModelCatalog STANDARD = new YoModelCatalog(Map.of());

    private final Map<String, YoModelOverride> overrides;

    public YoModelCatalog() {
        this(Map.of());
    }

    private YoModelCatalog(@NonNull Map<String, YoModelOverride> overrides) {
        this.overrides = Map.copyOf(overrides);
    }

    /**
     * 没有任何用户覆盖的目录。
     */
    @NonNull
    public static YoModelCatalog standard() {
        return STANDARD;
    }

    /**
     * 只凭 id 查一个模型的规格：推断，再叠加用户覆盖。
     *
     * @throws IllegalArgumentException id 为空
     */
    @NonNull
    public YoModelSpec describe(@Nullable String id) {
        String requested = normalize(id);
        return withOverride(YoModelInference.infer(requested));
    }

    /**
     * 拿到了平台列表里的一项：它的规格已含平台上报，再叠加用户覆盖。
     */
    @NonNull
    public YoModelSpec resolve(@NonNull YoModelInfo info) {
        return withOverride(info.spec());
    }

    /**
     * 按输入顺序批量按 id 查，跳过空白项。
     */
    @NonNull
    public List<YoModelSpec> describeAll(@Nullable Collection<String> ids) {
        List<YoModelSpec> specs = new ArrayList<>();
        if (ids == null) {
            return specs;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            specs.add(describe(id));
        }
        return specs;
    }

    /**
     * 按输入顺序批量解析平台列表。
     */
    @NonNull
    public List<YoModelSpec> resolveAll(@Nullable Collection<YoModelInfo> infos) {
        List<YoModelSpec> specs = new ArrayList<>();
        if (infos == null) {
            return specs;
        }
        for (YoModelInfo info : infos) {
            specs.add(resolve(info));
        }
        return specs;
    }

    /**
     * 记下用户对某个模型的表态。返回新目录，该模型的相关说法此后永久压过推断和平台上报。
     * 匹配按 id 原文（忽略大小写），故意不做家族前缀匹配：
     * 用户确认的是那个具体模型，不该被将来某个同家族新版本继承。
     */
    @NonNull
    public YoModelCatalog withOverride(@Nullable String id, @NonNull YoModelOverride override) {
        String requested = normalize(id);
        Map<String, YoModelOverride> merged = new LinkedHashMap<>(overrides);
        merged.put(key(requested), Objects.requireNonNull(override, "override"));
        return new YoModelCatalog(merged);
    }

    /**
     * 撤掉某个模型的用户覆盖，回到推断与平台上报。
     */
    @NonNull
    public YoModelCatalog withoutOverride(@Nullable String id) {
        String requested = normalize(id);
        String key = key(requested);
        if (!overrides.containsKey(key)) {
            return this;
        }
        Map<String, YoModelOverride> merged = new LinkedHashMap<>(overrides);
        merged.remove(key);
        return new YoModelCatalog(merged);
    }

    /**
     * 用户对这个模型的表态；没动过返回 {@code null}（用来区分"没动过"和"勾了一个都不支持"）。
     */
    @Nullable
    public YoModelOverride override(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return overrides.get(key(id.trim()));
    }

    /**
     * 有没有用户覆盖。设置页据此显示"已手动指定"。
     */
    public boolean hasOverride(@Nullable String id) {
        return override(id) != null;
    }

    @NonNull
    private YoModelSpec withOverride(@NonNull YoModelSpec base) {
        YoModelOverride override = overrides.get(key(base.id()));
        return override == null ? base : override.applyTo(base);
    }

    @NonNull
    private static String normalize(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("model id is required");
        }
        return id.trim();
    }

    @NonNull
    private static String key(@NonNull String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
