package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.spec.YoModelSpec;
import com.yovo.lib_ai.models.spec.YoModality;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 平台的模型列表混着向量、图片、语音模型。APP 只留能走对话协议的：
 * 平台页是文本模式的配置页。ChatGPT 的实时模型由语音页另拉一份，
 * 经 {@code YoChatGptRealtime} 筛选，不混进这里的文本计数。
 *
 * <p>模型清单只有两个来源：平台 {@code /models} 的真实返回，或用户手填的 id。
 * 这里不补任何"厂商默认模型"——平台没返回的模型，APP 不该替它出现。</p>
 *
 * <p>类别、模态、限额、推理全部来自 SDK 的 {@link YoModelSpec}：APP 不再按 id 猜能力，
 * 只把规格翻译成界面需要的四个字段。排序：发布时间新的在前，没有发布时间的排在最后，再按名字。</p>
 */
final class LibAiModels {

    private LibAiModels() {
    }

    static List<AiModel> models(List<YoModelInfo> infos) {
        List<YoModelInfo> selectable = new ArrayList<>();
        for (YoModelInfo info : infos) {
            if (info.spec().category().conversational()) {
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
                spec.limits().maxOutputTokens(), features, false);
    }
}
