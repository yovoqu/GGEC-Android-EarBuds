package com.ggec.earbuds.ui.ai.settings;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.policy.AiSpeedRanking;
import com.ggec.earbuds.ui.ai.common.AiFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 把设置、临时状态合成平台详情页的界面状态。这里决定列表怎么过滤和排序、
 * 每一行显示什么速度状态，以及"使用最快"是否出现；页面只负责画。
 */
public final class AiPlatformUiMapper {

    /** 模型多到这个数量才显示搜索框，少了反而碍事。 */
    static final int SEARCH_THRESHOLD = 8;

    private final AiSpeedRanking ranking = new AiSpeedRanking();

    public AiPlatformUiState map(
            AiSettings settings, String platformId, List<AiPlatformTemplate> templates, AiPlatformLocal local) {
        Optional<AiPlatformConfig> stored = settings.platform(platformId);
        AiPlatformTemplate template = templateOf(stored, platformId, templates);
        if (template == null || (stored.isEmpty() && template.custom())) {
            return AiPlatformUiState.missing(platformId);
        }
        AiPlatformConfig platform = stored.orElseGet(() -> AiPlatformConfig.create(
                template.id(), template.id(), template.name(), template.defaultProtocol()));
        boolean active = settings.activeModel()
                .filter(model -> model.platform().id().equals(platform.id()))
                .isPresent();
        return new AiPlatformUiState(
                true,
                false,
                platform.id(),
                platform.name(),
                template.custom(),
                platform.configured(),
                active,
                platform.configured() ? AiFormat.maskKey(platform.apiKey()) : "",
                platform.baseUrl() != null ? platform.baseUrl() : template.defaultBaseUrl(),
                platform.baseUrl() == null,
                platform.protocol(),
                template.protocols(),
                template.consoleUrl(),
                models(platform, settings.selection(), local));
    }

    /** 过滤并排序之后的模型 id，测速就按这个顺序取前若干个。 */
    public List<String> visibleModelIds(AiSettings settings, String platformId, AiPlatformLocal local) {
        return settings.platform(platformId)
                .map(platform -> ordered(platform, local).stream().map(AiModel::id).toList())
                .orElse(List.of());
    }

    public Optional<String> fastestModelId(AiSettings settings, String platformId) {
        return settings.platform(platformId)
                .flatMap(platform -> ranking.fastest(platform.speedResults().values()))
                .map(AiSpeedResult::modelId);
    }

    private AiPlatformUiState.Models models(AiPlatformConfig platform, AiSelection selection, AiPlatformLocal local) {
        List<AiPlatformUiState.ModelRow> rows = new ArrayList<>();
        for (AiModel model : ordered(platform, local)) {
            rows.add(new AiPlatformUiState.ModelRow(
                    model.id(),
                    model.name(),
                    model.contextWindow() == null ? 0 : model.contextWindow(),
                    model.features().contains(AiModelFeature.REASONING),
                    model.features().contains(AiModelFeature.VISION),
                    model.manual(),
                    isSelected(selection, platform, model.id()),
                    speedOf(model, platform, local.run())));
        }
        int total = platform.models().size();
        boolean anySpeed = false;
        for (AiSpeedResult result : platform.speedResults().values()) {
            anySpeed |= result.ok();
        }
        return new AiPlatformUiState.Models(
                rows,
                total,
                total >= SEARCH_THRESHOLD || !local.query().isEmpty(),
                anySpeed,
                local.fetch(),
                platform.modelsFetchedAtMillis(),
                new AiPlatformUiState.SpeedRun(local.run().active(), local.run().done(), local.run().total()),
                local.run().active() ? null : fastest(platform, selection));
    }

    private AiPlatformUiState.Fastest fastest(AiPlatformConfig platform, AiSelection selection) {
        Optional<AiSpeedResult> best = ranking.fastest(platform.speedResults().values());
        if (best.isEmpty()) {
            return null;
        }
        AiSpeedResult result = best.get();
        Optional<AiModel> model = platform.model(result.modelId());
        if (model.isEmpty() || isSelected(selection, platform, result.modelId())) {
            return null;
        }
        return new AiPlatformUiState.Fastest(
                result.modelId(), model.get().name(), result.firstTokenMillis(), result.totalMillis());
    }

    private List<AiModel> ordered(AiPlatformConfig platform, AiPlatformLocal local) {
        String query = local.query().strip().toLowerCase(Locale.ROOT);
        List<AiModel> filtered = new ArrayList<>();
        for (AiModel model : platform.models()) {
            if (query.isEmpty()
                    || model.id().toLowerCase(Locale.ROOT).contains(query)
                    || model.name().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(model);
            }
        }
        if (!local.sortBySpeed()) {
            return filtered;
        }
        List<AiSpeedResult> measured = new ArrayList<>();
        Map<String, AiModel> measuredModels = new HashMap<>();
        List<AiModel> untested = new ArrayList<>();
        List<AiModel> failed = new ArrayList<>();
        for (AiModel model : filtered) {
            AiSpeedResult result = platform.speedResults().get(model.id());
            if (result == null) {
                untested.add(model);
            } else if (result.ok()) {
                measured.add(result);
                measuredModels.put(model.id(), model);
            } else {
                failed.add(model);
            }
        }
        List<AiModel> sorted = new ArrayList<>(filtered.size());
        for (AiSpeedResult result : ranking.rank(measured)) {
            sorted.add(measuredModels.get(result.modelId()));
        }
        sorted.addAll(untested);
        sorted.addAll(failed);
        return sorted;
    }

    private static AiPlatformUiState.Speed speedOf(AiModel model, AiPlatformConfig platform, AiPlatformLocal.Run run) {
        if (run.queued().contains(model.id())) {
            return AiPlatformUiState.Speed.QUEUED;
        }
        if (run.running().contains(model.id())) {
            return AiPlatformUiState.Speed.RUNNING;
        }
        AiSpeedResult result = platform.speedResults().get(model.id());
        if (result == null) {
            return AiPlatformUiState.Speed.NONE;
        }
        return switch (result.status()) {
            case OK -> new AiPlatformUiState.Speed(
                    AiPlatformUiState.Speed.State.OK, result.firstTokenMillis(), null);
            case FAILED -> new AiPlatformUiState.Speed(
                    AiPlatformUiState.Speed.State.FAILED, -1L, result.failureKind());
            case TIMEOUT -> new AiPlatformUiState.Speed(
                    AiPlatformUiState.Speed.State.TIMEOUT, -1L, result.failureKind());
        };
    }

    private static boolean isSelected(AiSelection selection, AiPlatformConfig platform, String modelId) {
        return selection != null
                && selection.platformId().equals(platform.id())
                && selection.modelId().equals(modelId);
    }

    private static AiPlatformTemplate templateOf(
            Optional<AiPlatformConfig> stored, String platformId, List<AiPlatformTemplate> templates) {
        String templateId = stored.map(AiPlatformConfig::templateId).orElse(platformId);
        for (AiPlatformTemplate template : templates) {
            if (template.id().equals(templateId)) {
                return template;
            }
        }
        return null;
    }
}
