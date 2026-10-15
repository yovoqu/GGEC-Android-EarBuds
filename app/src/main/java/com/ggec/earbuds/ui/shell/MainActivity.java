package com.ggec.earbuds.ui.shell;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.wiring.ShellGraph;
import com.yohu.youi.api.navigation.YoTabs;
import com.yohu.youi.api.platform.YoSystemBar;
import com.yohu.youi.api.theme.YoColors;

public class MainActivity extends AppCompatActivity {

    private YoTabs tabs;
    private ShellViewModel shellViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoSystemBar.prepareTransparentSystemBars(this);
        shellViewModel = new ViewModelProvider(this, ShellGraph.factory()).get(ShellViewModel.class);
        tabs = createTabs(shellViewModel.current());
        setContentView(tabs.getView());
        shellViewModel.uiState().observe(this, this::showSelectedTab);
    }

    @Override
    protected void onDestroy() {
        if (tabs != null) {
            tabs.destroy();
        }
        super.onDestroy();
    }

    @NonNull
    private YoTabs createTabs(@NonNull ShellUiState state) {
        YoColors colors = YoColors.of(this);
        int pageColor = colors.palette().snowGray(1);
        int titleColor = colors.textPrimary();
        YoTabs.Builder builder = YoTabs.create(this)
                .setBarStyle(YoTabs.BarStyle.FLOATING)
                .setSelectedId(state.selectedTabId());
        for (ShellTabUi tab : state.tabs()) {
            CharSequence title = getString(tab.titleRes());
            builder.addTab(tab.tabId(), title, tab.iconRes(), tab.selectedIconRes(),
                    ShellPageView.create(this, title, pageColor, titleColor));
        }
        YoTabs created = builder.build();
        created.setOnChangeListener((index, id) ->
                shellViewModel.select(shellViewModel.current().keyForTab(id)));
        return created;
    }

    private void showSelectedTab(@NonNull ShellUiState state) {
        if (tabs.getSelectedId() != state.selectedTabId()) {
            tabs.setSelectedId(state.selectedTabId());
        }
    }
}
