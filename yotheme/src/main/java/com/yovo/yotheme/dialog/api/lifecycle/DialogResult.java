package com.yovo.yotheme.dialog.api.lifecycle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框关闭时发出的结果
 */
public final class DialogResult {

    public enum Reason {
        ACTION,
        OUTSIDE,
        BACK,
        PROGRAMMATIC,
        LIFECYCLE
    }

    @NonNull
    private final Reason reason;

    @Nullable
    private final String actionId;

    @NonNull
    private final List<String> selectedIds;

    public DialogResult(@NonNull Reason reason,
                        @Nullable String actionId,
                        @NonNull List<String> selectedIds) {
        this.reason = reason;
        this.actionId = actionId;
        this.selectedIds = Collections.unmodifiableList(new ArrayList<>(selectedIds));
    }

    @NonNull
    public Reason getReason() {
        return reason;
    }

    @Nullable
    public String getActionId() {
        return actionId;
    }

    @NonNull
    public List<String> getSelectedIds() {
        return selectedIds;
    }
}

