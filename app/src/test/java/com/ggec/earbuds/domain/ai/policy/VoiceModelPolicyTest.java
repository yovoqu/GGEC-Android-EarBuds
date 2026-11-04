package com.ggec.earbuds.domain.ai.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;

import org.junit.Test;

import java.util.List;

/** 语音候选的筛选规则：只留实时类别，顺序不变。 */
public class VoiceModelPolicyTest {

    @Test
    public void keepsOnlyRealtimeModelsInOriginalOrder() {
        List<AiModel> raw = List.of(
                model("gpt-4o", AiModelCategory.TEXT),
                model("gpt-4o-realtime-preview", AiModelCategory.REALTIME),
                model("text-embedding-3-small", AiModelCategory.EMBEDDING),
                model("qwen-audio-3.1-realtime-flash", AiModelCategory.REALTIME));

        List<AiModel> usable = new VoiceModelPolicy().realtimeOnly(raw);

        assertEquals(List.of("gpt-4o-realtime-preview", "qwen-audio-3.1-realtime-flash"),
                usable.stream().map(AiModel::id).toList());
        assertTrue(usable.stream().allMatch(model -> model.category() == AiModelCategory.REALTIME));
    }

    private static AiModel model(String id, AiModelCategory category) {
        return new AiModel(id, id, null, null, java.util.Set.of(), false, category);
    }
}
