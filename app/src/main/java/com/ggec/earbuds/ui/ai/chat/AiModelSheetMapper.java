package com.ggec.earbuds.ui.ai.chat;

import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import java.util.ArrayList;
import java.util.List;

public final class AiModelSheetMapper {

    public AiModelSheetUi map(AiSettings settings) {
        AiSelection selection = settings.selection();
        List<AiModelSheetUi.Group> groups = new ArrayList<>();
        for (AiPlatformConfig platform : settings.platforms()) {
            if (!platform.configured() || platform.models().isEmpty()) {
                continue;
            }
            List<AiModelSheetUi.Row> rows = new ArrayList<>();
            for (AiModel model : platform.models()) {
                AiSpeedResult speed = platform.speedResults().get(model.id());
                boolean selected = selection != null
                        && selection.platformId().equals(platform.id())
                        && selection.modelId().equals(model.id());
                rows.add(new AiModelSheetUi.Row(
                        model.id(),
                        model.name(),
                        model.contextWindow() == null ? 0 : model.contextWindow(),
                        speed != null && speed.ok() ? speed.firstTokenMillis() : -1L,
                        selected));
            }
            groups.add(new AiModelSheetUi.Group(platform.id(), platform.name(), rows));
        }
        return new AiModelSheetUi(groups);
    }
}
