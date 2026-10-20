package com.ggec.earbuds.ui.ai.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.ui.ai.common.AiFormat;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.ai.common.AiTexts;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.common.YoPageActivity;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.containers.YoPage;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.List;

/**
 * 单个平台的详情页：连接设置、拉取模型、测速并选模型。
 * 每一组是一张独立 {@link AiSectionList}（标题/说明在列表外），行 Ripple 走独立列表几何。
 * 搜索用 {@link YoTextInput}。
 */
public class AiPlatformActivity extends YoPageActivity {

    public static void start(@NonNull Context context, @NonNull String platformId) {
        Intent intent = new Intent(context, AiPlatformActivity.class);
        intent.putExtra(AiGraph.ARG_PLATFORM_ID, platformId);
        context.startActivity(intent);
    }

    private final YoComponents components = new YoComponents();

    private AiPlatformViewModel viewModel;
    private YoTextInput search;
    private AiSectionList connectionSection;
    private AiSectionList modelSection;
    private AiSectionList dangerSection;
    @Nullable
    private AiPlatformUiState state;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, AiGraph.get(this).factory()).get(AiPlatformViewModel.class);

        search = YoTextInput.create(this)
                .setPlaceholder(getString(R.string.ai_models_search_hint))
                .setLeadingIcon(R.drawable.ic_ai_search)
                .setShowCancel(true)
                .setOnValueChangeListener(value -> viewModel.setQuery(value.toString()))
                .build();
        components.own(search::destroy);

        connectionSection = section(getString(R.string.ai_platform_section_connection));
        modelSection = section(getString(R.string.ai_platform_section_models));
        dangerSection = section(getString(R.string.ai_platform_section_danger));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        connectionSection.attach(body);
        modelSection.attach(body);
        dangerSection.attach(body);

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.addView(body, new NestedScrollView.LayoutParams(
                NestedScrollView.LayoutParams.MATCH_PARENT, NestedScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(search.getView(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        installPage(YoPage.create(this)
                .setTitle("")
                .setTitleMode(YoPage.TitleMode.MINI)
                .setLevel(YoPage.Level.SECONDARY)
                .setOnBackListener(this::finish)
                .setContent(content));

        viewModel.uiState().observe(this, this::render);
        viewModel.events().observe(this, event -> {
            switch (event.type()) {
                case TOAST -> YoToast.show(this, event.argument() == null
                        ? getString(event.message())
                        : getString(event.message(), event.argument()));
                case FINISH -> finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        components.close();
        super.onDestroy();
    }

    // ---- 渲染 ----

    private void render(@NonNull AiPlatformUiState next) {
        state = next;
        if (next.missing()) {
            YoToast.show(this, getString(R.string.ai_platform_missing));
            finish();
            return;
        }
        if (!next.loaded()) {
            return;
        }
        page().setTitle(next.name());
        page().setSubtitle(headerStatus(next));
        search.getView().setVisibility(next.models().searchable() ? View.VISIBLE : View.GONE);
        connectionSection.clear();
        modelSection.clear();
        dangerSection.clear();
        fill(next);
    }

    private void fill(@NonNull AiPlatformUiState s) {
        connectionSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_key),
                ListTextSpec.of(getString(R.string.ai_platform_key),
                        s.configured() ? s.maskedKey() : getString(R.string.ai_platform_key_unset)),
                ListSuffixSpec.arrow(), this::editKey);

        if (s.configured()) {
            connectionSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_globe),
                    ListTextSpec.of(getString(R.string.ai_platform_base_url),
                            s.defaultBaseUrl() ? getString(R.string.ai_platform_base_url_default) : s.baseUrl()),
                    ListSuffixSpec.arrow(), () -> editBaseUrl(s));
            if (s.protocols().size() > 1) {
                connectionSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_swap),
                        ListTextSpec.of(getString(R.string.ai_platform_protocol),
                                getString(AiMessages.protocol(s.protocol()))),
                        ListSuffixSpec.arrow(), () -> chooseProtocol(s, connectionSection.listView()));
            }
            if (s.custom()) {
                connectionSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_edit),
                        ListTextSpec.of(getString(R.string.ai_platform_name), s.name()),
                        ListSuffixSpec.arrow(), this::editName);
            }
        }
        if (s.consoleUrl() != null) {
            connectionSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_external),
                    ListTextSpec.of(getString(R.string.ai_platform_get_key),
                            getString(R.string.ai_platform_get_key_summary)),
                    ListSuffixSpec.arrow(), () -> openConsole(s));
        }

        AiPlatformUiState.Models models = s.models();
        modelSection.setTitle(models.totalCount() > 0
                ? getString(R.string.ai_models_count, models.totalCount())
                : getString(R.string.ai_platform_section_models));
        if (!s.configured()) {
            modelSection.setNote(getString(R.string.ai_fetch_need_key));
        } else {
            addModelsRows(s, models);
        }

        boolean removable = s.configured() || s.custom();
        dangerSection.setShown(removable);
        if (removable) {
            dangerSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_delete),
                    ListTextSpec.destructive(getString(R.string.ai_platform_remove)),
                    ListSuffixSpec.none(), () -> AiDialogs.confirm(this,
                            getString(R.string.ai_platform_remove_title, s.name()),
                            getString(R.string.ai_platform_remove_message), getString(R.string.ai_remove),
                            true, viewModel::removePlatform));
        }
    }

    private void addModelsRows(@NonNull AiPlatformUiState s, @NonNull AiPlatformUiState.Models models) {
        boolean fetching = models.fetch().status() == AiPlatformUiState.Fetch.Status.RUNNING;
        boolean testing = models.speed().active();

        modelSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_download),
                ListTextSpec.of(getString(fetching ? R.string.ai_fetch_running : R.string.ai_fetch_models),
                        fetchNote(models)),
                ListSuffixSpec.none(), fetching ? null : viewModel::fetchModels);

        modelSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_bolt),
                ListTextSpec.of(getString(testing ? R.string.ai_speed_stop : R.string.ai_speed_start),
                        speedNote(models)),
                ListSuffixSpec.none(), () -> {
                    if (models.speed().active()) {
                        viewModel.stopSpeedTest();
                    } else {
                        viewModel.startSpeedTest();
                    }
                });

        StringBuilder note = new StringBuilder();
        if (models.fetch().status() == AiPlatformUiState.Fetch.Status.FAILED
                && models.fetch().failureKind() != null) {
            note.append(getString(R.string.ai_fetch_failed_reason,
                    getString(AiMessages.failure(models.fetch().failureKind()))));
        }
        if (models.rows().isEmpty()) {
            if (note.length() > 0) {
                note.append('\n');
            }
            note.append(getString(models.totalCount() == 0
                    ? R.string.ai_models_empty : R.string.ai_models_no_match));
        }

        for (AiPlatformUiState.ModelRow model : models.rows()) {
            ListPrefixSpec prefix = model.selected()
                    ? ListPrefixSpec.icon(R.drawable.ic_ai_check) : ListPrefixSpec.none();
            modelSection.add(prefix, ListTextSpec.of(model.name(), modelSubtitle(s, model)),
                    ListSuffixSpec.arrow(),
                    () -> viewModel.selectModel(model.id()));
        }
        modelSection.add(ListPrefixSpec.icon(R.drawable.ic_ai_plus), ListTextSpec.of(getString(R.string.ai_model_add)),
                ListSuffixSpec.none(), () -> AiDialogs.input(this,
                        getString(R.string.ai_model_add_title), getString(R.string.ai_model_add_hint), "",
                        YoTextInput.InputType.TEXT, 200, viewModel::addModel));
        if (note.length() > 0) {
            note.append('\n');
        }
        note.append(getString(R.string.ai_speed_hint));
        modelSection.setNote(note);
    }

    private String fetchNote(AiPlatformUiState.Models models) {
        if (models.fetch().status() == AiPlatformUiState.Fetch.Status.RUNNING) {
            return getString(R.string.ai_fetch_running);
        }
        if (models.fetchedAtMillis() > 0 && models.totalCount() > 0) {
            return getString(R.string.ai_fetch_done_time, models.totalCount(),
                    AiTexts.relativeTime(this, System.currentTimeMillis(), models.fetchedAtMillis()));
        }
        return null;
    }

    private String speedNote(AiPlatformUiState.Models models) {
        if (models.speed().active()) {
            return getString(R.string.ai_speed_running, models.speed().done(), models.speed().total());
        }
        AiPlatformUiState.Fastest fastest = models.fastest();
        return fastest == null ? null
                : getString(R.string.ai_speed_summary_fastest, fastest.modelName());
    }

    private String modelSubtitle(AiPlatformUiState s, AiPlatformUiState.ModelRow model) {
        String window = AiTexts.contextWindow(this, model.contextWindow());
        return model.speed().state() == AiPlatformUiState.Speed.State.OK
                ? window + " · " + AiFormat.duration(model.speed().firstTokenMillis())
                : window;
    }

    private String headerStatus(AiPlatformUiState s) {
        if (!s.configured()) {
            return getString(R.string.ai_platform_status_unset);
        }
        return s.models().totalCount() == 0
                ? getString(R.string.ai_platform_status_no_models)
                : getString(R.string.ai_platform_status_models, s.models().totalCount());
    }

    // ---- 交互 ----

    private void editKey() {
        AiDialogs.input(this, getString(R.string.ai_key_dialog_title), getString(R.string.ai_key_dialog_hint),
                "", YoTextInput.InputType.PASSWORD, 512, viewModel::saveKey);
    }

    private void editName() {
        AiPlatformUiState current = state;
        AiDialogs.input(this, getString(R.string.ai_name_dialog_title),
                getString(R.string.ai_custom_name_hint), current == null ? "" : current.name(),
                YoTextInput.InputType.TEXT, 40, viewModel::saveName);
    }

    private void editBaseUrl(AiPlatformUiState current) {
        String initial = current.defaultBaseUrl() ? "" : current.baseUrl();
        if (current.custom()) {
            AiDialogs.input(this, getString(R.string.ai_url_dialog_title), getString(R.string.ai_url_dialog_hint),
                    initial, YoTextInput.InputType.TEXT, 300, viewModel::saveBaseUrl);
        } else {
            AiDialogs.inputWithReset(this, getString(R.string.ai_url_dialog_title),
                    current.baseUrl(), initial, getString(R.string.ai_url_restore_default), viewModel::saveBaseUrl);
        }
    }

    /**
     * 每次选择新建一个菜单：先构建、选中当前项，再挂上关闭即销毁，
     * 同时登记到页面释放集合，Activity 先销毁时也不会漏。
     */
    private void chooseProtocol(AiPlatformUiState current, @NonNull View anchor) {
        List<AiProtocol> protocols = current.protocols();
        YoMenu.Builder builder = YoMenu.create(this)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_LEFT)
                .setSelectedMark(YoMenu.SelectedMark.CHECK)
                .setOnItemClickListener(index -> {
                    AiValidationError error = viewModel.setProtocol(protocols.get(index));
                    if (error != null) {
                        YoToast.show(this, getString(AiMessages.validation(error)));
                    }
                });
        int selected = 0;
        for (int i = 0; i < protocols.size(); i++) {
            builder.addItem(i, getString(AiMessages.protocol(protocols.get(i))));
            if (protocols.get(i) == current.protocol()) {
                selected = i;
            }
        }
        YoMenu menu = builder.build();
        menu.setSelectedItemId(selected);
        menu.setOnDismissListener(menu::destroy);
        components.own(menu::destroy);
        menu.show();
    }

    private void openConsole(AiPlatformUiState current) {
        String url = current.consoleUrl();
        if (url == null) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (ActivityNotFoundException ignored) {
            // 没有浏览器可用，什么都不做。
        }
    }

    @NonNull
    private AiSectionList section(@NonNull CharSequence title) {
        AiSectionList section = AiSectionList.create(this, title);
        components.own(section::destroy);
        return section;
    }
}
