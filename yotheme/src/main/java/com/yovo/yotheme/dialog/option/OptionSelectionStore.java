package com.yovo.yotheme.dialog.option;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;
import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项选择状态存储，与 UI 解耦
 */
public final class OptionSelectionStore {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(@NonNull OptionSelectionStore store);
    }

    @NonNull
    private final List<OptionItemSpec> items;
    @NonNull
    private final OptionSelectionMode selectionMode;
    @NonNull
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    @NonNull
    private final List<OnSelectionChangedListener> listeners = new ArrayList<>();

    public OptionSelectionStore(@NonNull List<OptionItemSpec> items,
                                @NonNull OptionSelectionMode selectionMode) {
        this.items = new ArrayList<>(items);
        this.selectionMode = selectionMode;
    }

    private boolean isMultiSelect() {
        return selectionMode == OptionSelectionMode.MULTI_CONFIRM;
    }

    @NonNull
    public Set<String> getSelectedIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(selectedIds));
    }

    @NonNull
    public List<OptionItemSpec> getSelectedItems() {
        List<OptionItemSpec> result = new ArrayList<>();
        for (OptionItemSpec item : items) {
            if (isSelected(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    public boolean isSelected(@NonNull String id) {
        return selectedIds.contains(id);
    }

    public void replaceSelection(@NonNull Collection<String> ids) {
        if (!isMultiSelect() && ids.size() > 1) {
            throw new IllegalArgumentException("Single selection accepts at most one id");
        }
        selectedIds.clear();
        for (String id : ids) {
            if (findItemById(id) != null) {
                selectedIds.add(id);
            }
        }
        notifyChanged();
    }

    public void select(@NonNull String id) {
        OptionItemSpec item = findItemById(id);
        if (item == null || !item.isEnabled()) {
            return;
        }
        if (isMultiSelect()) {
            if (selectedIds.add(id)) {
                notifyChanged();
            }
            return;
        }
        if (selectedIds.size() == 1 && selectedIds.contains(id)) {
            return;
        }
        selectedIds.clear();
        selectedIds.add(id);
        notifyChanged();
    }

    public void toggle(@NonNull String id) {
        if (!isMultiSelect()) {
            select(id);
            return;
        }
        OptionItemSpec item = findItemById(id);
        if (item == null || !item.isEnabled()) {
            return;
        }
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        notifyChanged();
    }

    public void toggleSelectAll() {
        if (isAllSelected()) {
            selectedIds.clear();
        } else {
            for (OptionItemSpec item : items) {
                if (item.isEnabled()) {
                    selectedIds.add(item.getId());
                }
            }
        }
        notifyChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyChanged();
    }

    public boolean isAllSelected() {
        if (!isMultiSelect() || items.isEmpty()) {
            return false;
        }
        int enabledCount = 0;
        for (OptionItemSpec item : items) {
            if (item.isEnabled()) {
                enabledCount++;
                if (!selectedIds.contains(item.getId())) {
                    return false;
                }
            }
        }
        return enabledCount > 0;
    }

    private OptionItemSpec findItemById(@NonNull String id) {
        for (OptionItemSpec item : items) {
            if (id.equals(item.getId())) {
                return item;
            }
        }
        return null;
    }

    public void addListener(@NonNull OnSelectionChangedListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(@NonNull OnSelectionChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (OnSelectionChangedListener listener : new ArrayList<>(listeners)) {
            listener.onSelectionChanged(this);
        }
    }
}

