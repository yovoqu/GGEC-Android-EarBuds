package com.ggec.earbuds.wiring;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.shell.ShellCatalogSource;
import com.ggec.earbuds.data.shell.ShellRepositoryImpl;
import com.ggec.earbuds.data.shell.ShellTabMapper;
import com.ggec.earbuds.domain.shell.LoadShellTabs;
import com.ggec.earbuds.domain.shell.ShellRepository;
import com.ggec.earbuds.domain.shell.ShellSelectionPolicy;
import com.ggec.earbuds.ui.shell.ShellUiMapper;
import com.ggec.earbuds.ui.shell.ShellViewModel;

public final class ShellGraph {

    private final ShellRepository repository = new ShellRepositoryImpl(
            new ShellCatalogSource(), new ShellTabMapper());
    private final LoadShellTabs loadShellTabs = new LoadShellTabs(repository);
    private final ShellSelectionPolicy selection = new ShellSelectionPolicy();
    private final ShellUiMapper uiMapper = new ShellUiMapper();

    @NonNull
    public ViewModelProvider.Factory factory() {
        return new Factory(loadShellTabs, selection, uiMapper);
    }

    private static final class Factory implements ViewModelProvider.Factory {

        private final LoadShellTabs loadShellTabs;
        private final ShellSelectionPolicy selection;
        private final ShellUiMapper uiMapper;

        private Factory(LoadShellTabs loadShellTabs,
                        ShellSelectionPolicy selection,
                        ShellUiMapper uiMapper) {
            this.loadShellTabs = loadShellTabs;
            this.selection = selection;
            this.uiMapper = uiMapper;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
            if (!ShellViewModel.class.equals(modelClass)) {
                throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
            }
            SavedStateHandle savedState = SavedStateHandleSupport.createSavedStateHandle(extras);
            return modelClass.cast(new ShellViewModel(savedState, loadShellTabs, selection, uiMapper));
        }
    }
}
