package com.ggec.earbuds.ui.shell;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.ui.ai.chat.AiChatPage;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.navigation.YoTabs;

/** 应用外壳视图：三个页签、AI 页和占位页。只装配与展示，不持有业务状态。 */
final class ShellScreen {

    interface Listener {
        void onSelect(@NonNull String key);
        void onAIHistory();
    }

    private final AppCompatActivity activity;
    private final ShellViewModel viewModel;
    private final ViewModelProvider.Factory aiFactory;
    private final Listener listener;
    private final YoComponents components = new YoComponents();
    private final YoTabs tabs;
    private AiChatPage aiPage;

    ShellScreen(@NonNull AppCompatActivity activity,
                @NonNull ShellViewModel viewModel,
                @NonNull ViewModelProvider.Factory aiFactory,
                @NonNull Listener listener) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.aiFactory = aiFactory;
        this.listener = listener;
        tabs = createTabs(viewModel.current());
        components.own(tabs::destroy);
        if (aiPage != null) {
            components.own(aiPage::destroy);
        }
    }

    @NonNull
    View view() {
        return tabs.getView();
    }

    void render(@NonNull ShellUiState state) {
        if (tabs.getSelectedId() != state.selectedTabId()) {
            tabs.setSelectedId(state.selectedTabId());
        }
    }

    void destroy() {
        components.close();
    }

    @NonNull
    private YoTabs createTabs(@NonNull ShellUiState state) {
        YoTabs.Builder builder = YoTabs.create(activity)
                .setSelectedId(state.selectedTabId());
        for (ShellTabUi tab : state.tabs()) {
            CharSequence title = activity.getString(tab.titleRes());
            builder.addTab(tab.tabId(), title, tab.iconRes(), tab.selectedIconRes(),
                    createPage(tab, title));
        }
        YoTabs created = builder.build();
        created.setOnChangeListener((index, id) ->
                listener.onSelect(viewModel.current().keyForTab(id)));
        if (aiPage != null) {
            created.setOnContentInsetListener(aiPage::setBottomInset);
            aiPage.setBottomInset(created.getContentInsetPx());
        }
        return created;
    }

    @NonNull
    private View createPage(@NonNull ShellTabUi tab, @NonNull CharSequence title) {
        if (ShellDestination.AI.key().equals(tab.key())) {
            AiChatViewModel chat = new ViewModelProvider(activity, aiFactory)
                    .get(AiChatViewModel.class);
            aiPage = new AiChatPage(activity, chat, listener::onAIHistory);
            return aiPage.view();
        }
        return ShellPageView.create(activity, title);
    }
}
