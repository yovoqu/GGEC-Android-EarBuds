package com.yovo.yotheme.dialog.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.option.OptionSelectionStore;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 与对话框实例关联的可变运行时状态
 */
public final class DialogStateStore {

    public interface Listener {
        void onStateChanged();
    }

    @NonNull
    private final List<Listener> listeners = new ArrayList<>();

    @NonNull
    private final Map<String, Boolean> actionEnabledMap = new LinkedHashMap<>();

    @Nullable
    private OptionSelectionStore optionSelectionStore;
    @Nullable
    private OptionSelectionStore.OnSelectionChangedListener optionSelectionListener;

    public void attachOptionSelectionStore(@NonNull OptionSelectionStore store) {
        if (optionSelectionStore != null && optionSelectionListener != null) {
            optionSelectionStore.removeListener(optionSelectionListener);
        }
        this.optionSelectionStore = store;
        this.optionSelectionListener = selectionStore -> {
            updateSelectionAction(selectionStore);
            notifyStateChanged();
        };
        store.addListener(optionSelectionListener);
        updateSelectionAction(store);
    }

    public void syncFromSpec(@NonNull YoDialogSpec spec) {
        actionEnabledMap.clear();
        for (DialogAction action : spec.getActions()) {
            actionEnabledMap.put(action.getId(), action.isEnabled());
        }
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    @NonNull
    public Set<String> getSelectedIds() {
        if (optionSelectionStore == null) {
            return Collections.emptySet();
        }
        return optionSelectionStore.getSelectedIds();
    }

    public boolean isActionEnabled(@NonNull String actionId) {
        Boolean enabled = actionEnabledMap.get(actionId);
        return enabled == null || enabled;
    }

    public void setActionEnabled(@NonNull String actionId, boolean enabled) {
        actionEnabledMap.put(actionId, enabled);
        notifyStateChanged();
    }

    @NonNull
    public Map<String, Boolean> getActionEnabledMap() {
        return Collections.unmodifiableMap(actionEnabledMap);
    }

    public boolean isAllSelected() {
        if (optionSelectionStore != null) {
            return optionSelectionStore.isAllSelected();
        }
        return false;
    }

    public void toggleSelectAll() {
        if (optionSelectionStore == null) {
            return;
        }
        optionSelectionStore.toggleSelectAll();
    }

    private void updateSelectionAction(@NonNull OptionSelectionStore store) {
        if (actionEnabledMap.containsKey(DialogAction.ID_POSITIVE)) {
            actionEnabledMap.put(DialogAction.ID_POSITIVE, !store.getSelectedIds().isEmpty());
        }
    }

    public void notifyStateChanged() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged();
        }
    }
}
