package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 能力的第 3 级来源：把"内置表 → 推断 → 用户覆盖"三层按优先级合并成一个答复。
 *
 * <p>不可变：{@link #withUserCapabilities(String, Set)} 返回新的目录，旧目录不受影响——
 * 设置页在改动过程中随时可以回滚，也不用担心某个 ViewModel 手里攥着半更新的状态。
 *
 * <p>用户覆盖这轮不接界面，只留方法；接界面时把 {@link #userCapabilities(String)} 读出来勾选即可。
 */
public final class YoModelCatalog {

    private static final YoModelCatalog STANDARD = new YoModelCatalog(Map.of());

    private final Map<String, Set<YoModelCapability>> userOverrides;

    public YoModelCatalog() {
        this(Map.of());
    }

    private YoModelCatalog(@NonNull Map<String, Set<YoModelCapability>> userOverrides) {
        this.userOverrides = Map.copyOf(userOverrides);
    }

    /**
     * 只有内置表 + 推断的目录。
     */
    @NonNull
    public static YoModelCatalog standard() {
        return STANDARD;
    }

    /**
     * 查一个模型的能力。两层合并，后者覆盖前者：
     * <ol>
     *   <li>{@link YoModelInference} 名称推断 → {@link YoCapabilitySource#INFERRED}；</li>
     *   <li>用户覆盖 → {@link YoCapabilitySource#USER}，连"一个能力都不勾"也算数。</li>
     * </ol>
     *
     * @throws IllegalArgumentException id 为空
     */
    @NonNull
    public YoModelDescriptor describe(@Nullable String id) {
        String requested = normalize(id);
        YoModelDescriptor base = YoModelInference.infer(requested);
        Set<YoModelCapability> override = userOverrides.get(key(requested));
        if (override == null) {
            return base;
        }
        return base.withCapabilities(override, YoCapabilitySource.USER);
    }

    /**
     * 按输入顺序批量查。平台模型列表动辄上百条，逐条 {@link #describe(String)} 会重复查表；
     * 这里没有额外缓存，因为查表本身是 O(家族数)，缓存的失效成本更高。
     */
    @NonNull
    public List<YoModelDescriptor> describeAll(@Nullable Collection<String> ids) {
        List<YoModelDescriptor> descriptors = new ArrayList<>();
        if (ids == null) {
            return descriptors;
        }
        for (String id : ids) {
            if (id == null || id.isBlank()) {
                continue;
            }
            descriptors.add(describe(id));
        }
        return descriptors;
    }

    /**
     * 给某个模型写一份用户勾选的能力。返回新目录，{@code source} 变成 {@link YoCapabilitySource#USER}，
     * 从此永久压过内置表和推断。
     *
     * @param capabilities 允许为空集合，表示用户明确说"这些都不支持"
     */
    @NonNull
    public YoModelCatalog withUserCapabilities(@Nullable String id,
                                               @Nullable Set<YoModelCapability> capabilities) {
        String requested = normalize(id);
        Map<String, Set<YoModelCapability>> merged = new LinkedHashMap<>(userOverrides);
        merged.put(key(requested), capabilities == null ? Set.of() : Set.copyOf(capabilities));
        return new YoModelCatalog(merged);
    }

    /**
     * 去掉某个模型的用户覆盖，回到按 id 推断。
     */
    @NonNull
    public YoModelCatalog withoutUserCapabilities(@Nullable String id) {
        String requested = normalize(id);
        String key = key(requested);
        if (!userOverrides.containsKey(key)) {
            return this;
        }
        Map<String, Set<YoModelCapability>> merged = new LinkedHashMap<>(userOverrides);
        merged.remove(key);
        return new YoModelCatalog(merged);
    }

    /**
     * 用户给这个模型勾过的能力；没勾过返回 {@code null}（用来区分"没动过"和"勾了一个都不支持"）。
     */
    @Nullable
    public Set<YoModelCapability> userCapabilities(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return userOverrides.get(key(id.trim()));
    }

    /**
     * 有没有用户覆盖。设置页据此显示"已手动指定"。
     */
    public boolean hasUserCapabilities(@Nullable String id) {
        return userCapabilities(id) != null;
    }

    @NonNull
    private static String normalize(@Nullable String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("model id is required");
        }
        return id.trim();
    }

    /**
     * 用户覆盖按 id 原文（忽略大小写）匹配。故意不做家族前缀匹配：
     * 用户勾的是那个具体模型，不该被将来某个同家族新版本继承。
     */
    @NonNull
    private static String key(@NonNull String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
