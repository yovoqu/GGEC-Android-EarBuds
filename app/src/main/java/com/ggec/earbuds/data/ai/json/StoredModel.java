package com.ggec.earbuds.data.ai.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;

import java.util.Set;

/**
 * 持久化里的一个实时模型条目：只保留「重新打开应用还需要知道的东西」。
 *
 * <p>领域里的 {@link AiModel} 还带名称、上下文档、能力集合等运行时属性；那些是每次拉取列表时
 * 由厂商返回的，落盘反而会让旧数据冒充新数据。所以磁盘上用这份最小记录，读写都在本包内完成，
 * 领域模型不参与序列化。</p>
 *
 * @param id         模型 id，空串视为无效
 * @param categoryId 类别 id；读回时无法识别则整条丢弃（不猜类别）
 */
record StoredModel(@NonNull String id, @Nullable String categoryId) {

    StoredModel {
        id = id == null ? "" : id;
    }

    @NonNull
    static StoredModel of(@NonNull AiModel model) {
        AiModelCategory category = model.category();
        return new StoredModel(model.id(), category == null ? null : category.id());
    }

    /** 还原成领域值；id 为空或类别无法识别时返回 null，由调用方跳过这一条。 */
    @Nullable
    AiModel toDomain() {
        AiModelCategory category = AiModelCategory.fromId(categoryId);
        if (id.isEmpty() || category == null) {
            return null;
        }
        return new AiModel(id, id, null, null, Set.of(), false, category);
    }
}
