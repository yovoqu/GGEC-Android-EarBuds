package com.ggec.earbuds.ui.ai.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.common.YoPageActivity;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.containers.YoPage;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * AI 设置总览：当前模型、各平台入口、对话参数、数据清理。
 * 每一组是一张独立 {@link AiSectionList}（标题/说明在列表外），行 Ripple 走独立列表几何。
 */
public class AiSettingsActivity extends YoPageActivity {

    private static final int[] MAX_TOKEN_CHOICES = {0, 1024, 2048, 4096, 8192, 16384};
    private static final int[] CONTEXT_CHOICES = {0, 4, 10, 20, 40, 80};
    private static final float DEFAULT_TEMPERATURE = 0.7f;

    private final YoComponents components = new YoComponents();

    private AiSettingsViewModel viewModel;
    private AiSectionList modelSection;
    private AiSectionList platformSection;
    private AiSectionList chatSection;
    private AiSectionList dataSection;
    @Nullable
    private AiSettingsUiState state;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, AiGraph.get(this).factory()).get(AiSettingsViewModel.class);

        modelSection = section(getString(R.string.ai_settings_section_model));
        platformSection = section(getString(R.string.ai_settings_section_platforms));
        chatSection = section(getString(R.string.ai_settings_section_chat));
        dataSection = section(getString(R.string.ai_settings_section_data));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        modelSection.attach(body);
        platformSection.attach(body);
        chatSection.attach(body);
        dataSection.attach(body);
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.addView(body, new NestedScrollView.LayoutParams(
                NestedScrollView.LayoutParams.MATCH_PARENT, NestedScrollView.LayoutParams.WRAP_CONTENT));

        installPage(YoPage.create(this)
                .setTitle(getString(R.string.ai_settings_title))
                .setTitleMode(YoPage.TitleMode.MINI)
                .setLevel(YoPage.Level.SECONDARY)
                .setOnBackListener(this::finish)
                .setContent(scroll));

        viewModel.uiState().observe(this, this::render);
        viewModel.events().observe(this, event -> {
            switch (event.type()) {
                case TOAST -> YoToast.show(this, getString(event.message()));
                case OPEN_PLATFORM -> {
                    if (event.argument() != null) {
                        AiPlatformActivity.start(this, event.argument());
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        components.close();
        super.onDestroy();
    }

    // ---- 渲染 ----

    private void render(@NonNull AiSettingsUiState next) {
        state = next;
        modelSection.clear();
        platformSection.clear();
        chatSection.clear();
        dataSection.clear();

        AiSettingsUiState.ActiveModel active = next.activeModel();
        if (active != null) {
            modelSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_cube),
                    ListTextSpec.of(active.modelName(),
                            getString(R.string.ai_settings_model_detail, active.platformName(),
                                    getString(AiMessages.protocol(active.protocol())))),
                    ListSuffixSpec.arrow(),
                    () -> AiPlatformActivity.start(this, active.platformId()));
        } else {
            modelSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_cube),
                    ListTextSpec.of(getString(R.string.ai_settings_no_model),
                            getString(R.string.ai_settings_no_model_hint)),
                    ListSuffixSpec.none(), null);
        }

        for (AiSettingsUiState.PlatformRow platform : next.platforms()) {
            String target = platform.platformId() != null ? platform.platformId() : platform.templateId();
            ListSuffixSpec suffix = platform.active()
                    ? ListSuffixSpec.badgeAndArrow(getString(R.string.ai_platform_active_badge))
                    : ListSuffixSpec.arrow();
            platformSection.add(ListPrefixSpec.none(), ListTextSpec.of(platform.name(), platformStatus(platform)),
                    suffix, () -> AiPlatformActivity.start(this, target));
        }
        platformSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_plus),
                ListTextSpec.of(getString(R.string.ai_platform_add_custom),
                        getString(R.string.ai_platform_add_custom_summary)),
                ListSuffixSpec.arrow(),
                () -> AiAddPlatformDialog.show(this, components, viewModel.customTemplates(),
                        viewModel::addCustomPlatform));
        platformSection.setNote(getString(R.string.ai_settings_privacy));

        AiSettingsUiState.Preferences preferences = next.preferences();
        chatSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_text),
                ListTextSpec.of(getString(R.string.ai_pref_system_prompt),
                        getString(R.string.ai_pref_system_prompt_hint), promptSummary(preferences)),
                ListSuffixSpec.arrow(), this::editPrompt);
        chatSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_thermo),
                ListTextSpec.of(getString(R.string.ai_pref_temperature),
                        getString(R.string.ai_pref_temperature_summary),
                        preferences.temperature() == null ? getString(R.string.ai_default_value)
                                : String.format(Locale.ROOT, "%.1f", preferences.temperature())),
                ListSuffixSpec.arrow(), this::editTemperature);
        chatSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_stack),
                ListTextSpec.of(getString(R.string.ai_pref_max_tokens), null,
                        preferences.maxOutputTokens() == null ? getString(R.string.ai_default_value)
                                : getString(R.string.ai_value_tokens, preferences.maxOutputTokens())),
                ListSuffixSpec.arrow(), () -> chooseMaxTokens(chatSection.listView()));
        chatSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_chat),
                ListTextSpec.of(getString(R.string.ai_pref_context),
                        getString(R.string.ai_pref_context_summary),
                        preferences.contextMessages() == 0 ? getString(R.string.ai_value_all)
                                : getString(R.string.ai_value_messages, preferences.contextMessages())),
                ListSuffixSpec.arrow(), this::chooseContext);
        chatSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_bulb),
                ListTextSpec.of(getString(R.string.ai_pref_reasoning),
                        getString(R.string.ai_pref_reasoning_summary)),
                ListSuffixSpec.switch_(preferences.showReasoning(), viewModel::setShowReasoning), null);

        dataSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_delete),
                ListTextSpec.destructive(getString(R.string.ai_pref_clear_history)),
                ListSuffixSpec.none(),
                () -> AiDialogs.confirm(this, getString(R.string.ai_history_clear_title),
                        getString(R.string.ai_history_clear_message), getString(R.string.ai_delete), true,
                        viewModel::clearHistory));
        dataSection.setNote(getString(R.string.ai_pref_clear_history_summary));
    }

    private String platformStatus(AiSettingsUiState.PlatformRow platform) {
        if (!platform.configured()) {
            return getString(R.string.ai_platform_status_unset);
        }
        return platform.modelCount() == 0
                ? getString(R.string.ai_platform_status_no_models)
                : getString(R.string.ai_platform_status_models, platform.modelCount());
    }

    private String promptSummary(AiSettingsUiState.Preferences preferences) {
        if (preferences.systemPromptIsDefault()) {
            return getString(R.string.ai_default_value);
        }
        String prompt = preferences.systemPrompt().strip();
        if (prompt.isEmpty()) {
            return getString(R.string.ai_pref_system_prompt_empty);
        }
        return prompt.length() > 10 ? prompt.substring(0, 10) + "…" : prompt;
    }

    // ---- 交互 ----

    private void editPrompt() {
        String current = state == null ? "" : state.preferences().systemPrompt();
        AiDialogs.editText(this, getString(R.string.ai_pref_system_prompt),
                getString(R.string.ai_pref_system_prompt_hint), current,
                AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH, getString(R.string.ai_pref_system_prompt_restore),
                viewModel::restoreSystemPrompt, viewModel::setSystemPrompt);
    }

    private void editTemperature() {
        Double current = state == null ? null : state.preferences().temperature();
        AiDialogs.slider(this, components, getString(R.string.ai_pref_temperature),
                getString(R.string.ai_pref_temperature_summary), 0f, 2f, 0.1f,
                current == null ? null : current.floatValue(), DEFAULT_TEMPERATURE,
                getString(R.string.ai_default_value), value -> viewModel.setTemperature(value),
                () -> viewModel.setTemperature(null));
    }

    private void chooseMaxTokens(@NonNull View anchor) {
        Integer current = state == null ? null : state.preferences().maxOutputTokens();
        String[] labels = new String[MAX_TOKEN_CHOICES.length];
        int selected = 0;
        for (int i = 0; i < MAX_TOKEN_CHOICES.length; i++) {
            labels[i] = MAX_TOKEN_CHOICES[i] == 0
                    ? getString(R.string.ai_default_value)
                    : getString(R.string.ai_value_tokens, MAX_TOKEN_CHOICES[i]);
            if (current != null && current == MAX_TOKEN_CHOICES[i]) {
                selected = i;
            }
        }
        showChoices(anchor, labels, selected, index ->
                viewModel.setMaxOutputTokens(MAX_TOKEN_CHOICES[index] == 0 ? null : MAX_TOKEN_CHOICES[index]));
    }

    private void chooseContext() {
        int current = state == null ? AiChatPreferences.DEFAULT_CONTEXT_MESSAGES
                : state.preferences().contextMessages();
        String[] labels = new String[CONTEXT_CHOICES.length];
        int selected = -1;
        for (int i = 0; i < CONTEXT_CHOICES.length; i++) {
            labels[i] = CONTEXT_CHOICES[i] == 0
                    ? getString(R.string.ai_value_all)
                    : getString(R.string.ai_value_messages, CONTEXT_CHOICES[i]);
            if (current == CONTEXT_CHOICES[i]) {
                selected = i;
            }
        }
        showChoices(chatSection.listView(), labels, selected, index ->
                viewModel.setContextMessages(CONTEXT_CHOICES[index]));
    }

    /**
     * 每次选择新建一个菜单：先构建、选中当前项，再挂上关闭即销毁，
     * 同时登记到页面释放集合，Activity 先销毁时也不会漏。
     */
    private void showChoices(@NonNull View anchor, @NonNull String[] labels, int selected,
                             @NonNull IntConsumer onPick) {
        YoMenu.Builder builder = YoMenu.create(this)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_LEFT)
                .setSelectedMark(YoMenu.SelectedMark.CHECK)
                .setOnItemClickListener(onPick::accept);
        for (int i = 0; i < labels.length; i++) {
            builder.addItem(i, labels[i]);
        }
        YoMenu menu = builder.build();
        if (selected >= 0) {
            menu.setSelectedItemId(selected);
        }
        menu.setOnDismissListener(menu::destroy);
        components.own(menu::destroy);
        menu.show();
    }

    @NonNull
    private AiSectionList section(@NonNull CharSequence title) {
        AiSectionList section = AiSectionList.create(this, title);
        components.own(section::destroy);
        return section;
    }
}
