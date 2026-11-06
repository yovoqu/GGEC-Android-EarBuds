package com.ggec.earbuds.ui.ai.call.subtitle;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域模型 -> 表现层数据映射器 (Domain to UI Mapper)。
 *
 * <p>纯函数无状态转换，实现领域实体与 UI 模型的强隔离与高内聚，
 * 过滤空文本并完成角色枚举类型投影。
 */
public final class AiSubtitleMapper {

    private AiSubtitleMapper() {}

    @NonNull
    public static List<AiSubtitleItem> fromDomainTurns(@NonNull List<AiVoiceSnapshot.Turn> domainTurns) {
        if (domainTurns.isEmpty()) {
            return List.of();
        }
        List<AiSubtitleItem> items = new ArrayList<>(domainTurns.size());
        for (AiVoiceSnapshot.Turn turn : domainTurns) {
            if (turn.text().isEmpty()) {
                continue;
            }
            items.add(new AiSubtitleItem(
                    turn.id(),
                    turn.user() ? AiSubtitleItem.Speaker.USER : AiSubtitleItem.Speaker.AI,
                    turn.text(),
                    turn.finalTranscript()
            ));
        }
        return List.copyOf(items);
    }
}
