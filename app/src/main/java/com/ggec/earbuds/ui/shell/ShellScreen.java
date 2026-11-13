package com.ggec.earbuds.ui.shell;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.ui.ai.chat.AiChatPage;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceFailureText;
import com.ggec.earbuds.ui.device.roster.DeviceRosterDialogs;
import com.ggec.earbuds.ui.device.roster.DeviceRosterEvent;
import com.ggec.earbuds.ui.device.home.DeviceHomePage;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.scan.ScanActivity;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsActivity;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoTabs;

/**
 * 应用外壳视图：设备 / AI / 我的三个页签。
 *
 * <p>只装配与展示，不持有业务状态：设备页签的状态在
 * {@link DeviceRosterViewModel}，AI 页的状态在 {@link AiChatViewModel}。</p>
 */
final class ShellScreen {

    interface Listener {
        void onSelect(@NonNull String key);

        void onAIHistory();
    }

    private final AppCompatActivity activity;
    private final ShellViewModel viewModel;
    private final ViewModelProvider.Factory aiFactory;
    private final ViewModelProvider.Factory homeFactory;
    private final Listener listener;
    private final YoComponents components = new YoComponents();
    private final YoTabs tabs;

    private AiChatPage aiPage;
    private DeviceHomePage devicePage;
    private DeviceHomeViewModel homeViewModel;

    ShellScreen(@NonNull AppCompatActivity activity,
                @NonNull ShellViewModel viewModel,
                @NonNull ViewModelProvider.Factory aiFactory,
                @NonNull ViewModelProvider.Factory homeFactory,
                @NonNull Listener listener) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.aiFactory = aiFactory;
        this.homeFactory = homeFactory;
        this.listener = listener;
        tabs = createTabs(viewModel.current());
        components.own(tabs::destroy);
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
                .setSelectedId(state.selectedTabId())
                .setScrollable(false);
        for (ShellTabUi tab : state.tabs()) {
            CharSequence title = activity.getString(tab.titleRes());
            builder.addTab(tab.tabId(), title, tab.iconRes(), tab.selectedIconRes(),
                    createPage(tab, title));
        }
        YoTabs created = builder.build();
        created.setOnChangeListener((index, id) ->
                listener.onSelect(viewModel.current().keyForTab(id)));
        created.setOnContentInsetListener(this::applyBottomInset);
        applyBottomInset(created.getContentInsetPx());
        return created;
    }

    private void applyBottomInset(int px) {
        if (aiPage != null) {
            aiPage.setBottomInset(px);
        }
        if (devicePage != null) {
            devicePage.setBottomInset(px);
        }
    }

    @NonNull
    private View createPage(@NonNull ShellTabUi tab, @NonNull CharSequence title) {
        if (ShellDestination.AI.key().equals(tab.key())) {
            AiChatViewModel chat = new ViewModelProvider(activity, aiFactory)
                    .get(AiChatViewModel.class);
            aiPage = new AiChatPage(activity, chat, listener::onAIHistory);
            return aiPage.view();
        }
        if (ShellDestination.DEVICE.key().equals(tab.key())) {
            homeViewModel = new ViewModelProvider(activity, homeFactory)
                    .get(DeviceHomeViewModel.class);
            devicePage = new DeviceHomePage(activity, new DeviceTabListener(homeViewModel));
            homeViewModel.uiState().observe(activity, state -> {
                if (state != null && devicePage != null) {
                    devicePage.render(state);
                }
            });
            homeViewModel.events().observe(activity, this::onRosterEvent);
            homeViewModel.start();
            return devicePage.view();
        }
        return ShellPageView.create(activity, title);
    }

    private void onRosterEvent(@NonNull DeviceRosterEvent event) {
        switch (event) {
            case DeviceRosterEvent.OpenFailed failed ->
                    YoToast.showWarning(activity,
                            activity.getString(DeviceFailureText.message(failed.failure())));
            case DeviceRosterEvent.OpenSucceeded ignored -> {
                // 连接成功后停在设备页：能力入口随台账刷新出现，不替用户跳进某个能力页。
            }
            case DeviceRosterEvent.StoreFailed ignored ->
                    YoToast.showWarning(activity, activity.getString(R.string.device_error_store));
            case DeviceRosterEvent.DeviceMissing ignored ->
                    YoToast.showWarning(activity, activity.getString(R.string.device_error_not_found));
        }
    }

    private final class DeviceTabListener implements DeviceHomePage.Listener {

        private final DeviceHomeViewModel home;

        private DeviceTabListener(@NonNull DeviceHomeViewModel home) {
            this.home = home;
        }

        @Override
        public void onOpenScan() {
            activity.startActivity(ScanActivity.intent(activity));
        }

        @Override
        public void onSelectDevice(@NonNull String key) {
            home.select(key);
        }

        @Override
        public void onOpenEntry(@NonNull DeviceCapabilityGroup group) {
            activity.startActivity(DeviceSettingsActivity.intent(activity, group));
        }

    }
}
