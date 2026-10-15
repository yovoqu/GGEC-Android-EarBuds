package com.yovo.yotheme.dialog.api.content;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.callback.ImmediateSelectionCallback;
import com.yovo.yotheme.dialog.api.callback.SelectionConfirmCallback;
import com.yovo.yotheme.dialog.api.option.OptionListSpec;
import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 带选择回调的选项列表主体内容
 */
public final class OptionListContent extends DialogContentSpec {

    @NonNull
    private final OptionListSpec optionList;
    @NonNull
    private final List<String> initialSelectedIds;
    @Nullable
    private final ImmediateSelectionCallback immediateSelectionCallback;
    @Nullable
    private final SelectionConfirmCallback selectionConfirmCallback;

    public OptionListContent(@NonNull OptionListSpec optionList) {
        this(optionList, Collections.emptyList(), null, null);
    }

    public OptionListContent(@NonNull OptionListSpec optionList,
                             @NonNull List<String> initialSelectedIds,
                             @Nullable ImmediateSelectionCallback immediateSelectionCallback,
                             @Nullable SelectionConfirmCallback selectionConfirmCallback) {
        this.optionList = optionList;
        this.initialSelectedIds = Collections.unmodifiableList(new ArrayList<>(initialSelectedIds));
        this.immediateSelectionCallback = immediateSelectionCallback;
        this.selectionConfirmCallback = selectionConfirmCallback;
    }

    @NonNull
    public OptionListSpec getOptionList() {
        return optionList;
    }

    @NonNull
    public OptionSelectionMode getSelectionMode() {
        return optionList.getSelectionMode();
    }

    @NonNull
    public List<String> getInitialSelectedIds() {
        return initialSelectedIds;
    }

    @Nullable
    public ImmediateSelectionCallback getImmediateSelectionCallback() {
        return immediateSelectionCallback;
    }

    @Nullable
    public SelectionConfirmCallback getSelectionConfirmCallback() {
        return selectionConfirmCallback;
    }
}



