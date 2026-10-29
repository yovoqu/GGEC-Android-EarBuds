package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.yovo.lib_ai.models.YoModelCapability;
import com.yovo.lib_ai.models.YoModelCatalog;
import com.yovo.lib_ai.models.YoModelFeature;
import com.yovo.lib_ai.models.YoModelInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台的模型列表混着向量、图片、语音模型。设置页只要两类：能对话的，和能直接接实时语音的。
 *
 * <p>模型清单只有两个来源：平台 {@code /models} 的真实返回，或用户手填的 id。
 * 这里不补任何"厂商默认模型"——平台没返回的模型，APP 不该替它出现。</p>
 *
 * <p>排序：发布时间新的在前，没有发布时间的排在最后，再按名字。</p>
 */
final class LibAiModels {

    private static final YoModelCatalog CATALOG = YoModelCatalog.standard();

    private LibAiModels() {
    }

    static List<AiModel> models(List<YoModelInfo> infos) {
        List<YoModelInfo> selectable = new ArrayList<>();
        for (YoModelInfo info : infos) {
            if (info.kind().chatCapable() || realtime(info)) {
                selectable.add(info);
            }
        }
        selectable.sort(Comparator.comparingLong(LibAiModels::recency).reversed()
                .thenComparing(info -> info.displayName().toLowerCase(Locale.ROOT)));
        List<AiModel> models = new ArrayList<>();
        for (YoModelInfo info : selectable) {
            models.add(toModel(info));
        }
        return models;
    }

    /**
     * 实时模型在平台的用途分类里算 AUDIO，不是对话模型；只按 {@code chatCapable} 筛会把它丢掉，
     * 语音模式就永远选不到东西。能力表认它，这里就留下。
     */
    private static boolean realtime(YoModelInfo info) {
        return CATALOG.describe(info.id()).supports(YoModelCapability.REALTIME_AUDIO)
                || AiModel.looksRealtime(info.id());
    }

    private static long recency(YoModelInfo info) {
        return info.createdAtMillis() > 0 ? info.createdAtMillis() : Long.MIN_VALUE;
    }

    private static AiModel toModel(YoModelInfo info) {
        // 能力以平台自己报的为准；平台没报的，只按 id 推断"能不能接实时语音"这一项。
        Set<AiModelFeature> features = providerFeatures(info);
        if (CATALOG.describe(info.id()).supports(YoModelCapability.REALTIME_AUDIO)
                || AiModel.looksRealtime(info.id())) {
            features = withRealtime(features);
        }
        return new AiModel(info.id(), info.displayName(), info.contextWindow(), info.maxOutputTokens(),
                features, false);
    }

    private static Set<AiModelFeature> providerFeatures(YoModelInfo info) {
        Set<AiModelFeature> features = EnumSet.noneOf(AiModelFeature.class);
        for (YoModelFeature feature : info.features()) {
            features.add(feature == YoModelFeature.VISION ? AiModelFeature.VISION : AiModelFeature.REASONING);
        }
        return features;
    }

    private static Set<AiModelFeature> withRealtime(Set<AiModelFeature> features) {
        Set<AiModelFeature> next = EnumSet.noneOf(AiModelFeature.class);
        next.addAll(features);
        next.add(AiModelFeature.REALTIME_AUDIO);
        return next;
    }
}
