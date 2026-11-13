package com.ggec.earbuds.data.ai.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;

import org.junit.Test;

import java.util.Set;

/** 磁盘上的模型条目只带 id 与类别；领域属性不参与序列化。 */
public class StoredModelTest {

    @Test
    public void onlyIdAndCategoryArePersisted() {
        AiModel listed = new AiModel("gpt-4o-realtime", "GPT-4o Realtime", 128_000, 4_096,
                Set.of(), false, AiModelCategory.REALTIME);

        StoredModel stored = StoredModel.of(listed);

        assertEquals("gpt-4o-realtime", stored.id());
        assertEquals(AiModelCategory.REALTIME.id(), stored.categoryId());
    }

    @Test
    public void aModelWithoutCategoryStoresNoCategory() {
        StoredModel stored = StoredModel.of(AiModel.manual("my-model"));

        assertNull(stored.categoryId());
        assertNull("手工条目没有类别，读回时丢弃而不是猜类别", stored.toDomain());
    }

    @Test
    public void roundTripRestoresTheDomainValue() {
        AiModel restored = new StoredModel("qwen3.8-omni-flash-realtime",
                AiModelCategory.REALTIME.id()).toDomain();

        assertEquals("qwen3.8-omni-flash-realtime", restored.id());
        assertEquals("id 同时作为名称，界面上不出现空标题", "qwen3.8-omni-flash-realtime",
                restored.name());
        assertEquals(AiModelCategory.REALTIME, restored.category());
        assertEquals(Set.of(), restored.features());
    }

    @Test
    public void unknownCategoryAndBlankIdAreDropped() {
        assertNull(new StoredModel("m", "not-a-category").toDomain());
        assertNull(new StoredModel("", AiModelCategory.REALTIME.id()).toDomain());
    }
}
