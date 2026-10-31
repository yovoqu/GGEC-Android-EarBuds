package com.ggec.earbuds.ui.shell;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.ui.ai.chat.AiChatPage;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AiGraph;
import com.ggec.earbuds.wiring.ShellGraph;
import com.yohu.youi.api.navigation.YoTabs;

/**
 * 应用外壳：设备 / AI / 我的三个页签，底部是悬浮标签栏。
 *
 * <p>窗口级 edge-to-edge 由 {@link YoUi#edgeToEdge} 处理。AI 页自己避开状态栏，
 * 占位页由 {@link ShellPageView} 补顶部。</p>
 */
public class MainActivity extends AppCompatActivity {

    private final YoComponents components = new YoComponents();

    private ShellViewModel shellViewModel;
    private YoTabs tabs;
    private AiChatPage aiPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);

        shellViewModel = new ViewModelProvider(this, ShellGraph.factory()).get(ShellViewModel.class);
        tabs = createTabs(shellViewModel.current());
        // YoComponents 逆序释放：先登记 tabs、再登记 aiPage，销毁时 aiPage 先于 tabs。
        components.own(tabs::destroy);
        if (aiPage != null) {
            components.own(aiPage::destroy);
        }

        setContentView(tabs.getView());
        shellViewModel.uiState().observe(this, this::showSelectedTab);
    }

    @Override
    protected void onDestroy() {
        components.close();
        super.onDestroy();
    }

    @NonNull
    private YoTabs createTabs(@NonNull ShellUiState state) {
        // 不设置 BarStyle：库默认就是 FLOATING（居中胶囊），显式写反而像做过定制。
        YoTabs.Builder builder = YoTabs.create(this)
                .setSelectedId(state.selectedTabId());
        for (ShellTabUi tab : state.tabs()) {
            CharSequence title = getString(tab.titleRes());
            builder.addTab(tab.tabId(), title, tab.iconRes(), tab.selectedIconRes(), createPage(tab, title));
        }
        YoTabs created = builder.build();
        created.setOnChangeListener((index, id) ->
                shellViewModel.select(shellViewModel.current().keyForTab(id)));
        if (aiPage != null) {
            created.setOnContentInsetListener(aiPage::setBottomInset);
            aiPage.setBottomInset(created.getContentInsetPx());
        }
        return created;
    }

    /** AI 页是 {@link AiChatPage}；其它页签是共用的占位页。 */
    @NonNull
    private View createPage(@NonNull ShellTabUi tab, @NonNull CharSequence title) {
        if (ShellDestination.AI.key().equals(tab.key())) {
            AiChatViewModel viewModel = new ViewModelProvider(this, AiGraph.get(this).factory())
                    .get(AiChatViewModel.class);
            aiPage = new AiChatPage(this, viewModel);
            return aiPage.view();
        }
        return ShellPageView.create(this, title);
    }

    private void showSelectedTab(@NonNull ShellUiState state) {
        if (tabs.getSelectedId() != state.selectedTabId()) {
            tabs.setSelectedId(state.selectedTabId());
        }
    }
}
