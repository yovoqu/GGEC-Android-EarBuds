package com.yovo.yotheme.dialog.preset;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionListener;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.option.HeaderLeadingSpec;
import com.yovo.yotheme.dialog.api.option.HeaderTrailingSpec;
import com.yovo.yotheme.dialog.api.content.OptionListContent;
import com.yovo.yotheme.dialog.api.callback.SelectionConfirmCallback;
import com.yovo.yotheme.dialog.api.style.OptionRowStyle;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.api.option.OptionListSpec;
import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 确认选择弹窗 Preset：单选确认 / 多选确认
 */
public final class SelectionConfirmPreset {

    private final OptionSelectionMode selectionMode;

    @Nullable
    private String title;
    @Nullable
    private String dialogSubtitle;
    @NonNull
    private final List<OptionItemSpec> items = new ArrayList<>();
    @NonNull
    private final List<String> initialSelection = new ArrayList<>();
    @Nullable
    private HeaderLeadingSpec headerLeading;
    @Nullable
    private HeaderTrailingSpec headerTrailing;
    @Nullable
    private SelectionConfirmCallback onConfirm;
    @Nullable
    private String positiveText;
    @Nullable
    private DialogActionListener positiveListener;
    @Nullable
    private String negativeText;
    @Nullable
    private DialogActionListener negativeListener;
    @Nullable
    private OptionRowStyle rowStyle;
    @NonNull
    private DialogActionRole positiveRole = DialogActionRole.PRIMARY;
    private boolean positiveAutoDismiss = true;

    private SelectionConfirmPreset(@NonNull OptionSelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }

    @NonNull
    public static SelectionConfirmPreset single() {
        return new SelectionConfirmPreset(OptionSelectionMode.SINGLE_CONFIRM);
    }

    @NonNull
    public static SelectionConfirmPreset multi() {
        return new SelectionConfirmPreset(OptionSelectionMode.MULTI_CONFIRM);
    }

    @NonNull
    public SelectionConfirmPreset title(@Nullable String title) {
        this.title = title;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset dialogSubtitle(@Nullable String dialogSubtitle) {
        this.dialogSubtitle = dialogSubtitle;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset items(@NonNull List<OptionItemSpec> items) {
        this.items.clear();
        this.items.addAll(items);
        return this;
    }

    @NonNull
    public SelectionConfirmPreset initialSelection(@NonNull Collection<String> ids) {
        if (selectionMode == OptionSelectionMode.SINGLE_CONFIRM && ids.size() > 1) {
            throw new IllegalArgumentException("Single selection accepts at most one id");
        }
        this.initialSelection.clear();
        this.initialSelection.addAll(ids);
        return this;
    }

    @NonNull
    public SelectionConfirmPreset headerLeading(@Nullable HeaderLeadingSpec headerLeading) {
        this.headerLeading = headerLeading;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset headerTrailing(@Nullable HeaderTrailingSpec headerTrailing) {
        this.headerTrailing = headerTrailing;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset onConfirm(@Nullable SelectionConfirmCallback callback) {
        this.onConfirm = callback;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset positive(@Nullable String text) {
        this.positiveText = text;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset positive(@Nullable String text,
                                           @Nullable DialogActionListener listener) {
        this.positiveText = text;
        this.positiveListener = listener;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset negative(@Nullable String text,
                                           @Nullable DialogActionListener listener) {
        this.negativeText = text;
        this.negativeListener = listener;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset rowStyle(@Nullable OptionRowStyle rowStyle) {
        this.rowStyle = rowStyle;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset positiveDanger() {
        this.positiveRole = DialogActionRole.DANGER;
        return this;
    }

    @NonNull
    public SelectionConfirmPreset positiveAutoDismiss(boolean autoDismiss) {
        this.positiveAutoDismiss = autoDismiss;
        return this;
    }

    @NonNull
    public YoDialogSpec build() {
        OptionListSpec.Builder listBuilder = OptionListSpec.builder()
                .items(items)
                .selectionMode(selectionMode);
        if (rowStyle != null) {
            listBuilder.rowStyle(rowStyle);
        }
        OptionListSpec optionList = listBuilder.build();

        YoDialogSpec.Builder builder = YoDialogSpec.builder()
                .title(title)
                .subtitle(dialogSubtitle)
                .contentSpec(new OptionListContent(
                        optionList,
                        initialSelection,
                        null,
                        onConfirm))
                .headerLeading(headerLeading)
                .headerTrailing(headerTrailing != null
                        ? headerTrailing
                        : (selectionMode == OptionSelectionMode.MULTI_CONFIRM
                                ? HeaderTrailingSpec.selectAll()
                                : null));

        String positive = positiveText != null
                ? positiveText
                : DialogConstants.DEFAULT_POSITIVE_BUTTON_TEXT.getString();
        builder.addAction(DialogAction.builder(DialogAction.ID_POSITIVE, positive)
                .role(positiveRole)
                .enabled(!initialSelection.isEmpty())
                .autoDismiss(positiveAutoDismiss)
                .listener(positiveListener)
                .build());

        String negative = negativeText != null
                ? negativeText
                : DialogConstants.DEFAULT_NEGATIVE_BUTTON_TEXT.getString();
        builder.addAction(DialogAction.builder(DialogAction.ID_NEGATIVE, negative)
                .role(DialogActionRole.SECONDARY)
                .listener(negativeListener)
                .build());

        return builder.build();
    }
}



