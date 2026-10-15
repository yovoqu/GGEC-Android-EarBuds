package com.ggec.earbuds.ui.shell;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.shell.LoadShellTabs;
import com.ggec.earbuds.domain.shell.ShellSelectionPolicy;
import com.ggec.earbuds.domain.shell.ShellTab;

import java.util.List;
import java.util.Objects;

public final class ShellViewModel extends ViewModel {

    public static final String SELECTED_KEY = "shell.selected_key";

    private final List<ShellTab> tabs;
    private final ShellSelectionPolicy selection;
    private final ShellUiMapper uiMapper;
    private final SavedStateHandle savedState;
    private final MutableLiveData<ShellUiState> uiState;
    private ShellUiState state;

    public ShellViewModel(@NonNull SavedStateHandle savedState,
                          @NonNull LoadShellTabs loadShellTabs,
                          @NonNull ShellSelectionPolicy selection,
                          @NonNull ShellUiMapper uiMapper) {
        this.savedState = Objects.requireNonNull(savedState, "savedState");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.uiMapper = Objects.requireNonNull(uiMapper, "uiMapper");
        this.tabs = Objects.requireNonNull(loadShellTabs, "loadShellTabs").execute();
        String savedKey = savedState.get(SELECTED_KEY);
        ShellTab selected = savedKey == null
                ? this.selection.first(tabs)
                : this.selection.select(tabs, savedKey);
        this.state = this.uiMapper.map(tabs, selected);
        this.uiState = new MutableLiveData<>(state);
        this.savedState.set(SELECTED_KEY, selected.key());
    }

    @NonNull
    public LiveData<ShellUiState> uiState() {
        return uiState;
    }

    @NonNull
    public ShellUiState current() {
        return state;
    }

    public void select(@NonNull String key) {
        if (key.equals(state.selectedKey())) {
            return;
        }
        ShellTab selected = selection.select(tabs, key);
        savedState.set(SELECTED_KEY, selected.key());
        state = uiMapper.map(tabs, selected);
        uiState.setValue(state);
    }
}
