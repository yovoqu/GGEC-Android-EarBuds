package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSegmentButton;

import java.util.List;

/**
 * 通话设置区：在 {@link YoList} 行内展示「交流模式」及右侧分段按钮（全双工 / 半双工）。
 *
 * <p>严格 MVVM 纯视图层：不接触任何领域枚举或业务文案分支，分段选项顺序、文案资源与当前选中项
 * 均由 {@code AiSettingsUiMapper} 映射进 {@link AiSettingsUiState.Voice}，
 * 本视图只负责渲染并通过 {@link Listener} 上送选中项的 {@code id}。</p>
 */
final class VoiceDuplexCard {

    interface Listener {
        void onSelectDefaultDuplex(@NonNull String duplexId);
    }

    private final Context context;
    private final YoComponents components;
    private final Listener listener;
    private final LinearLayout view;
    private final YoList list;
    @Nullable
    private YoSegmentButton segment;
    @Nullable
    private List<AiSettingsUiState.DuplexOption> currentOptions;
    private boolean binding;

    VoiceDuplexCard(@NonNull Context context, @NonNull AiTheme theme,
                    @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.components = components;
        this.listener = listener;

        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(VoiceSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_voice_call_section)), VoiceCard.matchWrap());

        list = YoList.create(context).build();
        components.own(list::destroy);
        view.addView(list.getView(), VoiceCard.matchWrap());
        view.setVisibility(View.GONE);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        List<AiSettingsUiState.DuplexOption> options = voice.duplexOptions();
        currentOptions = options;
        if (options.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setVisibility(View.VISIBLE);
        int targetIndex = selectedIndex(options);
        if (segment == null) {
            segment = buildSegment(options, targetIndex);
            components.own(segment::destroy);
            list.addItem(
                    ListPrefixSpec.none(),
                    ListTextSpec.of(context.getString(R.string.ai_settings_voice_communication_mode)),
                    ListSuffixSpec.custom(segment.getView()));
            return;
        }
        syncSelection(targetIndex);
    }

    @NonNull
    private YoSegmentButton buildSegment(@NonNull List<AiSettingsUiState.DuplexOption> options,
                                         int selectedIndex) {
        YoSegmentButton.Builder builder = YoSegmentButton.create(context)
                .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                .setSize(YoSegmentButton.Size.SMALL)
                .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE);
        for (int i = 0; i < options.size(); i++) {
            builder.addSegment(i, context.getString(options.get(i).labelRes()));
        }
        return builder.setSelectedIndex(selectedIndex)
                .setOnSelectionChangeListener(this::dispatchSelection)
                .build();
    }

    private void syncSelection(int targetIndex) {
        if (segment == null || segment.getSelectedIndex() == targetIndex) {
            return;
        }
        binding = true;
        try {
            segment.setSelectedIndex(targetIndex);
        } finally {
            binding = false;
        }
    }

    private void dispatchSelection(int index) {
        if (binding) {
            return;
        }
        List<AiSettingsUiState.DuplexOption> options = currentOptions;
        if (options != null && index >= 0 && index < options.size()) {
            listener.onSelectDefaultDuplex(options.get(index).id());
        }
    }

    private static int selectedIndex(@NonNull List<AiSettingsUiState.DuplexOption> options) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).current()) {
                return i;
            }
        }
        return 0;
    }
}
