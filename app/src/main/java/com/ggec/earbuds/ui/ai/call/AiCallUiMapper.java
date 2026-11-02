package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;

/** 领域快照 → 通话页状态。纯映射，不持有会话、不读资源、不碰视图。 */
public final class AiCallUiMapper {

    @NonNull
    public AiCallUiState map(@NonNull AiVoiceSnapshot snapshot) {
        return AiCallUiState.fromSnapshot(snapshot);
    }
}
