package com.ggec.earbuds.provider.lib_ai;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.spec.YoModelCategory;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoModality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把 SDK 的 {@link YoModelSpec} 抄成领域模型。
 *
 * <p><b>不筛选</b>：平台返回什么就映射什么，类别原样写入 {@link AiModelCategory}。
 * 哪些模型能用于文本对话、哪些能用于实时语音，由领域策略
 * （{@code AiPlatformConfig.chatModels()} / {@code VoiceModelPolicy}）决定——
 * 网关替用户挑模型会造出“网页里有、列表里没有”的黑洞。</p>
 *
 * <p>排序：发布时间新的在前，没有发布时间的排在最后，再按名字。</p>
 */
final class LibAiModels {

    private LibAiModels() {
    }

    static List<AiModel> all(List<YoModelInfo> infos) {
        List<YoModelInfo> selectable = new ArrayList<>();
        for (YoModelInfo info : infos) {
            if (info != null) {
                selectable.add(info);
            }
        }
        selectable.sort(Comparator.comparingLong(LibAiModels::recency).reversed()
                .thenComparing(info -> info.displayName().toLowerCase(Locale.ROOT)));
        List<AiModel> models = new ArrayList<>(selectable.size());
        for (YoModelInfo info : selectable) {
            models.add(toModel(info));
        }
        return models;
    }

    private static long recency(YoModelInfo info) {
        return info.createdAtMillis() > 0 ? info.createdAtMillis() : Long.MIN_VALUE;
    }

    private static AiModel toModel(YoModelInfo info) {
        YoModelSpec spec = info.spec();
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        if (spec.accepts(YoModality.IMAGE) || spec.accepts(YoModality.VIDEO)) {
            features.add(AiModelFeature.VISION);
        }
        if (spec.reasoning().reasons()) {
            features.add(AiModelFeature.REASONING);
        }
        return new AiModel(info.id(), info.displayName(), spec.limits().contextWindow(),
                spec.limits().maxOutputTokens(), features, false, category(spec.category()));
    }

    private static AiModelCategory category(YoModelCategory category) {
        return switch (category) {
            case TEXT -> AiModelCategory.TEXT;
            case VISION -> AiModelCategory.VISION;
            case OMNI -> AiModelCategory.OMNI;
            case SPEECH_RECOGNITION -> AiModelCategory.SPEECH_RECOGNITION;
            case SPEECH_SYNTHESIS -> AiModelCategory.SPEECH_SYNTHESIS;
            case EMBEDDING -> AiModelCategory.EMBEDDING;
            case REALTIME -> AiModelCategory.REALTIME;
            case IMAGE_GENERATION -> AiModelCategory.IMAGE_GENERATION;
            case VIDEO_GENERATION -> AiModelCategory.VIDEO_GENERATION;
            case RERANK -> AiModelCategory.RERANK;
            case MODERATION -> AiModelCategory.MODERATION;
            case OTHER -> AiModelCategory.OTHER;
        };
    }
}
