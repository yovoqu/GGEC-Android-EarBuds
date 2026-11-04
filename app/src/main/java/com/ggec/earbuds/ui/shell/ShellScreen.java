package com.ggec.earbuds.ui.shell;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.shell.ShellDestination;
import com.ggec.earbuds.ui.ai.chat.AiChatPage;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.ui.device.home.DeviceHomePage;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.home.HomeToggle;
import com.ggec.earbuds.ui.device.scan.ScanActivity;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsActivity;
import com.yohu.youi.api.navigation.YoTabs;

/** 应用外壳视图：三个页签、设备页、AI 页和占位页。只装配与展示，不持有业务状态。 */
final class ShellScreen {

    interface Listener {
        void onSelect(@NonNull String key);
        void onAIHistory();
    }

    private final AppCompatActivity activity;
    private final ShellViewModel viewModel;
    private final ViewModelProvider.Factory aiFactory;
    private final ViewModelProvider.Factory deviceHomeFactory;
    private final Listener listener;
    private final YoComponents components = new YoComponents();
    private final YoTabs tabs;
    private AiChatPage aiPage;
    private DeviceHomePage devicePage;

    ShellScreen(@NonNull AppCompatActivity activity,
                @NonNull ShellViewModel viewModel,
                @NonNull ViewModelProvider.Factory aiFactory,
                @NonNull ViewModelProvider.Factory deviceHomeFactory,
                @NonNull Listener listener) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.aiFactory = aiFactory;
        this.deviceHomeFactory = deviceHomeFactory;
        this.listener = listener;
        tabs = createTabs(viewModel.current());
        components.own(tabs::destroy);
        if (aiPage != null) {
            components.own(aiPage::destroy);
        }
        if (devicePage != null) {
            components.own(devicePage::destroy);
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
            DeviceHomeViewModel home = new ViewModelProvider(activity, deviceHomeFactory)
                    .get(DeviceHomeViewModel.class);
            devicePage = new DeviceHomePage(activity, new DevicePageListener(home));
            home.uiState().observe(activity, state -> {
                if (state != null && devicePage != null) {
                    devicePage.render(state);
                }
            });
            home.start();
            return devicePage.view();
        }
        return ShellPageView.create(activity, title);
    }

    private final class DevicePageListener implements DeviceHomePage.Listener {

        private final DeviceHomeViewModel home;

        private DevicePageListener(DeviceHomeViewModel home) {
            this.home = home;
        }

        @Override
        public void onOpenScan() {
            activity.startActivity(ScanActivity.intent(activity));
        }

        @Override
        public void onOpenEntry(@NonNull DevicePage page) {
            activity.startActivity(DeviceSettingsActivity.intent(activity, page));
        }

        @Override
        public void onToggle(@NonNull HomeToggle key, boolean checked) {
            home.setToggle(key, checked);
        }

        @Override
        public void onSelectModel() {
            java.util.List<String> models = home.modelIds();
            if (models.isEmpty()) {
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.device_select_model)
                    .setItems(models.toArray(new String[0]),
                            (dialog, which) -> home.selectModel(models.get(which)))
                    .show();
        }
    }

}
