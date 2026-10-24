package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 平台详情页的映射。纯函数，不碰 Android 与存储。
 *
 * <p>页面只该关心"怎么摆"，"显示什么"是业务规则。规则集中在这里，
 * 就能用 JVM 单测跑遍每个分支，不必开模拟器。</p>
 */
public final class AiPlatformUiMapper {

    /** 超过这个数量才给搜索框：8 个以内一屏扫得完。 */
    static final int SEARCH_THRESHOLD = 8;

    @NonNull
    public AiPlatformUiState map(@NonNull AiSettings settings,
                                 @Nullable AiPlatformConfig platform,
                                 @Nullable AiPlatformTemplate template,
                                 @NonNull Set<String> testing,
                                 boolean speedRunning,
                                 boolean advancedExpanded,
                                 @NonNull String query) {
        if (platform == null) {
            return AiPlatformUiState.empty();
        }
        AiSelection selection = settings.selection();
        boolean selectedHere = selection != null && platform.id().equals(selection.platformId());
        String selectedModelId = selectedHere ? selection.modelId() : null;

        return new AiPlatformUiState(
                true,
                title(platform, template),
                badge(template),
                connection(platform, selectedModelId),
                models(platform, selectedModelId, testing, speedRunning, query),
                advanced(platform, template, settings, selectedModelId),
                advancedExpanded,
                true);
    }

    @NonNull
    static String title(@NonNull AiPlatformConfig platform, @Nullable AiPlatformTemplate template) {
        if (template != null && !template.custom() && !template.name().isBlank()) {
            return template.name();
        }
        return platform.name();
    }

    @Nullable
    static String badge(@Nullable AiPlatformTemplate template) {
        return template != null && !template.custom() ? "官方" : null;
    }

    @NonNull
    static AiPlatformUiState.Connection connection(@NonNull AiPlatformConfig platform,
                                                   @Nullable String selectedModelId) {
        boolean filled = !platform.apiKey().isBlank();
        String keyText = filled
                ? com.ggec.earbuds.ui.ai.common.AiFormat.maskKey(platform.apiKey())
                : "未填写";
        if (!filled) {
            return new AiPlatformUiState.Connection(
                    new AiPlatformUiState.Key(false, keyText), null, false, "先填 API Key");
        }
        if (selectedModelId == null) {
            return new AiPlatformUiState.Connection(
                    new AiPlatformUiState.Key(true, keyText), null, false, "先选一个模型");
        }
        AiSpeedResult result = platform.speedResults().get(selectedModelId);
        String text = result == null ? null : switch (result.status()) {
            case OK -> "连接正常 · 首字 "
                    + com.ggec.earbuds.ui.ai.common.AiFormat.duration(result.firstTokenMillis());
            case TIMEOUT -> "连接超时";
            case FAILED -> "连接失败";
        };
        return new AiPlatformUiState.Connection(
                new AiPlatformUiState.Key(true, keyText), text, false, null);
    }

    @NonNull
    static AiPlatformUiState.Models models(@NonNull AiPlatformConfig platform,
                                           @Nullable String selectedModelId,
                                           @NonNull Set<String> testing,
                                           boolean speedRunning,
                                           @NonNull String query) {
        List<AiPlatformUiState.Row> all = new ArrayList<>(platform.models().size());
        for (AiModel model : platform.models()) {
            AiSpeedResult speed = platform.speedResults().get(model.id());
            all.add(new AiPlatformUiState.Row(
                    model.id(), model.name(), model.id().equals(selectedModelId),
                    facts(model, speed), stateOf(model.id(), speed, testing),
                    speed != null && speed.ok() ? speed.firstTokenMillis() : -1L));
        }
        boolean searchable = all.size() > SEARCH_THRESHOLD;
        String applied = searchable ? query.strip() : "";
        return new AiPlatformUiState.Models(all.size(), filter(all, applied),
                speedRunning ? "停止" : "全部测速", speedRunning,
                // 结论要覆盖平台上的全部模型，不能只在筛选结果里比谁快。
                summary(all), searchable, applied);
    }

    /**
     * 按名称或 id 过滤。纯函数：页面把搜索框的值交给它，不需要联网也不需要重新映射整个状态。
     *
     * <p>大小写不敏感，并且允许空白词返回全量——空搜索框必须等于"没筛"，
     * 否则清空搜索框会得到空列表。</p>
     */
    @NonNull
    static List<AiPlatformUiState.Row> filter(@NonNull List<AiPlatformUiState.Row> rows,
                                              @NonNull String query) {
        String needle = query.strip().toLowerCase(java.util.Locale.ROOT);
        if (needle.isEmpty()) {
            return List.copyOf(rows);
        }
        List<AiPlatformUiState.Row> matched = new ArrayList<>(rows.size());
        for (AiPlatformUiState.Row row : rows) {
            if (row.id().toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || row.name().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                matched.add(row);
            }
        }
        return matched;
    }

    /** 事实片段：首字 → 上下文 → （手动添加且没有别的可说时）手动。 */
    @NonNull
    static List<String> facts(@NonNull AiModel model, @Nullable AiSpeedResult speed) {
        List<String> facts = new ArrayList<>(2);
        if (speed != null && speed.ok()) {
            facts.add("首字 "
                    + com.ggec.earbuds.ui.ai.common.AiFormat.duration(speed.firstTokenMillis()));
        }
        if (model.contextWindow() != null) {
            facts.add(com.ggec.earbuds.ui.ai.common.AiFormat.tokens(model.contextWindow())
                    + " 上下文");
        }
        if (facts.isEmpty() && model.manual()) {
            facts.add("手动添加");
        }
        return facts;
    }

    @NonNull
    static AiPlatformUiState.RowState stateOf(@NonNull String modelId, @Nullable AiSpeedResult speed,
                                              @NonNull Set<String> testing) {
        if (testing.contains(modelId)) {
            return AiPlatformUiState.RowState.TESTING;
        }
        if (speed == null) {
            return AiPlatformUiState.RowState.IDLE;
        }
        return speed.ok() ? AiPlatformUiState.RowState.OK : AiPlatformUiState.RowState.FAILED;
    }

    /** 测速结束后区块头的结论：只报最快的一个——平均值会掩盖差异。 */
    @Nullable
    static String summary(@NonNull List<AiPlatformUiState.Row> rows) {
        String fastest = null;
        long best = Long.MAX_VALUE;
        for (AiPlatformUiState.Row row : rows) {
            if (row.state() != AiPlatformUiState.RowState.OK || row.firstTokenMillis() < 0) {
                continue;
            }
            if (row.firstTokenMillis() < best) {
                best = row.firstTokenMillis();
                fastest = row.name();
            }
        }
        return fastest == null ? null : "最快：" + fastest;
    }

    /**
     * 参数的来源必须写出来：用户改了全局却没生效时，这一行是唯一能解释原因的地方。
     */
    @NonNull
    static String params(@NonNull AiPlatformConfig platform, @NonNull AiSettings settings,
                         @Nullable String selectedModelId) {
        if (selectedModelId == null) {
            return "先选一个模型";
        }
        com.ggec.earbuds.domain.ai.model.AiModel model = platform.model(selectedModelId).orElse(null);
        if (model == null) {
            return "";
        }
        com.ggec.earbuds.domain.ai.policy.AiParamsPolicy.Resolved resolved =
                com.ggec.earbuds.domain.ai.policy.AiParamsPolicy.resolve(
                        model, platform, settings.preferences());
        String source = switch (resolved.source()) {
            case MODEL -> "此模型";
            case GLOBAL -> "全局";
            case DEFAULT -> null;
        };
        String temperature = resolved.temperature() == null
                ? "平台默认" : trim(resolved.temperature()) + (source == null ? "" : "（" + source + "）");
        String topP = resolved.topP() == null
                ? "平台默认" : trim(resolved.topP()) + (source == null ? "" : "（" + source + "）");
        return "温度 " + temperature + " · top_p " + topP;
    }

    /**
     * 参数值最多两位小数，且去掉无意义的零。
     *
     * <p>存储里是 float，宽成 double 会带出 1.2999999523162842 这种尾巴——
     * 直接把 double 打出来，用户会以为参数被改坏了。</p>
     */
    @NonNull
    static String trim(double value) {
        java.math.BigDecimal rounded = java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return rounded.toPlainString();
    }

    @NonNull
    static AiPlatformUiState.Advanced advanced(@NonNull AiPlatformConfig platform,
                                               @Nullable AiPlatformTemplate template,
                                               @NonNull AiSettings settings,
                                               @Nullable String selectedModelId) {
        String url = platform.baseUrl() == null || platform.baseUrl().isBlank()
                ? (template == null ? "未设置" : template.defaultBaseUrl())
                : platform.baseUrl();
        return new AiPlatformUiState.Advanced(platform.name(),
                template != null && template.custom(), url,
                com.ggec.earbuds.ui.ai.common.AiMessages.protocol(platform.protocol()),
                template != null && template.protocols().size() > 1,
                params(platform, settings, selectedModelId));
    }
}