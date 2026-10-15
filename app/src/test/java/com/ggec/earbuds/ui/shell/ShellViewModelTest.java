package com.ggec.earbuds.ui.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;

import com.ggec.earbuds.domain.shell.LoadShellTabs;
import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.domain.shell.ShellSelectionPolicy;
import com.ggec.earbuds.domain.shell.ShellTab;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ShellViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    @Test
    public void startsOnFirstCatalogTab() {
        SavedStateHandle savedState = new SavedStateHandle();
        ShellViewModel viewModel = viewModel(savedState);

        assertEquals("device", viewModel.current().selectedKey());
        assertEquals("device", viewModel.uiState().getValue().selectedKey());
        assertEquals("device", savedState.get(ShellViewModel.SELECTED_KEY));
    }

    @Test
    public void selectPublishesAiAndStoresKey() {
        SavedStateHandle savedState = new SavedStateHandle();
        ShellViewModel viewModel = viewModel(savedState);

        viewModel.select("ai");

        assertEquals("ai", viewModel.current().selectedKey());
        assertEquals("ai", viewModel.uiState().getValue().selectedKey());
        assertEquals("ai", savedState.get(ShellViewModel.SELECTED_KEY));
    }

    @Test
    public void selectSameKeyKeepsState() {
        ShellViewModel viewModel = viewModel(new SavedStateHandle());
        ShellUiState before = viewModel.current();

        viewModel.select(before.selectedKey());

        assertSame(before, viewModel.current());
    }

    @Test
    public void restoresSavedSelection() {
        SavedStateHandle savedState = new SavedStateHandle(Map.of(ShellViewModel.SELECTED_KEY, "ai"));

        ShellViewModel viewModel = viewModel(savedState);

        assertEquals(ShellDestination.AI.key(), viewModel.current().selectedKey());
    }

    @Test
    public void selectRejectsUnknownKeyWithoutChangingSelection() {
        ShellViewModel viewModel = viewModel(new SavedStateHandle());
        String selected = viewModel.current().selectedKey();

        try {
            viewModel.select("sound");
            fail("unknown key must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(selected, viewModel.current().selectedKey());
        }
    }

    private static ShellViewModel viewModel(SavedStateHandle savedState) {
        List<ShellTab> tabs = List.of(
                new ShellTab(ShellDestination.DEVICE),
                new ShellTab(ShellDestination.AI),
                new ShellTab(ShellDestination.MINE));
        return new ShellViewModel(savedState, new LoadShellTabs(() -> tabs),
                new ShellSelectionPolicy(), new ShellUiMapper());
    }

}
