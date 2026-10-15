package com.yovo.yotheme.dialog.preset;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.behavior.DialogBehavior;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionListener;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.style.DialogStyle;
import com.yovo.yotheme.dialog.api.callback.ImmediateSelectionCallback;
import com.yovo.yotheme.dialog.api.content.OptionListContent;
import com.yovo.yotheme.dialog.api.style.OptionRowStyle;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.api.option.OptionListSpec;
import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 立即单选弹窗 Preset：点选即提交并关闭
 */
public final class ImmediateSelectionPreset {

    @Nullable
    private String title;
    @NonNull
    private final List<OptionItemSpec> items = new ArrayList<>();
    @Nullable
    private ImmediateSelectionCallback onSelect;
    @Nullable
    private String selectedId;
    @Nullable
    private Integer horizontalScreenMarginDp;
    @Nullable
    private String negativeText;
    @Nullable
    private DialogActionListener negativeListener;
    @Nullable
    private OptionRowStyle rowStyle;

    @NonNull
    public static ImmediateSelectionPreset create() {
        return new ImmediateSelectionPreset();
    }

    @NonNull
    public ImmediateSelectionPreset title(@Nullable String title) {
        this.title = title;
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset items(@NonNull List<OptionItemSpec> items) {
        this.items.clear();
        this.items.addAll(items);
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset onSelect(@Nullable ImmediateSelectionCallback callback) {
        this.onSelect = callback;
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset selectedId(@Nullable String selectedId) {
        this.selectedId = selectedId;
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset horizontalScreenMarginDp(int marginDp) {
        this.horizontalScreenMarginDp = marginDp;
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset negative(@NonNull String text, @Nullable DialogActionListener listener) {
        this.negativeText = text;
        this.negativeListener = listener;
        return this;
    }

    @NonNull
    public ImmediateSelectionPreset rowStyle(@Nullable OptionRowStyle rowStyle) {
        this.rowStyle = rowStyle;
        return this;
    }

    @NonNull
    public YoDialogSpec build() {
        OptionListSpec.Builder listBuilder = OptionListSpec.builder()
                .items(items)
                .selectionMode(OptionSelectionMode.SINGLE_IMMEDIATE);
        if (rowStyle != null) {
            listBuilder.rowStyle(rowStyle);
        }
        OptionListSpec optionList = listBuilder.build();

        List<String> initialSelected = new ArrayList<>();
        if (selectedId != null) {
            initialSelected.add(selectedId);
        }

        YoDialogSpec.Builder builder = YoDialogSpec.builder()
                .title(title)
                .contentSpec(new OptionListContent(
                        optionList,
                        initialSelected,
                        onSelect,
                        null))
                .behavior(DialogBehavior.builder()
                        .cancelOnTouchOutside(true)
                        .cancelableByBack(true)
                        .build())
                .addAction(DialogAction.builder(
                                DialogAction.ID_NEGATIVE,
                                negativeText != null
                                        ? negativeText
                                        : DialogConstants.DEFAULT_NEGATIVE_BUTTON_TEXT.getString())
                        .role(DialogActionRole.SECONDARY)
                        .listener(negativeListener)
                        .build());

        if (horizontalScreenMarginDp != null) {
            builder.style(DialogStyle.builder()
                    .horizontalScreenMarginDp(horizontalScreenMarginDp)
                    .build());
        }
        return builder.build();
    }
}



