package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.api.content.OptionListContent;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.core.DialogStateStore;
import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;
import com.yovo.yotheme.dialog.option.OptionSelectionStore;

import java.util.ArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表内容的渲染层入口
 */
public final class OptionListRenderer {

    private OptionListRenderer() {}

    @NonNull
    public static View render(@NonNull Context context,
                              @NonNull OptionListContent content,
                              @NonNull YoDialogSpec spec,
                              @NonNull DialogStateStore stateStore,
                              @NonNull DialogActionCallback callback) {
        OptionSelectionStore selectionStore = new OptionSelectionStore(
                content.getOptionList().getItems(),
                content.getSelectionMode());
        if (!content.getInitialSelectedIds().isEmpty()) {
            selectionStore.replaceSelection(content.getInitialSelectedIds());
        }

        int dialogCornerRadiusDp = spec.getStyle().getCornerRadiusDp();
        com.yovo.yotheme.dialog.option.OptionListRenderer renderer =
                com.yovo.yotheme.dialog.option.OptionListRenderer.create(
                        context,
                        content.getOptionList(),
                        selectionStore,
                        dialogCornerRadiusDp,
                        (index, item) -> {
                            if (content.getImmediateSelectionCallback() != null) {
                                content.getImmediateSelectionCallback().onSelect(index, item);
                            }
                            if (content.getSelectionMode() == OptionSelectionMode.SINGLE_IMMEDIATE) {
                                callback.requestDismiss(new DialogResult(
                                        DialogResult.Reason.ACTION,
                                        null,
                                        new ArrayList<>(stateStore.getSelectedIds())));
                            }
                        });

        stateStore.attachOptionSelectionStore(selectionStore);
        return renderer.getView();
    }
}


