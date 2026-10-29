package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台详情页（View 层）。
 *
 * <p>这一层只做一件事：把 {@link AiPlatformUiState} 摆到屏幕上。
 * 它不判断"通不通""哪个快""能不能点"——那些都在
 * {@link AiPlatformUiMapper} 与 {@link AiPlatformViewModel} 里。</p>
 */
public final class AiPlatformActivity extends androidx.appcompat.app.AppCompatActivity {

    private static final String EXTRA_PLATFORM_ID = "platformId";
    private static final String EXTRA_TEMPLATE_ID = "templateId";
    private static final String EXTRA_MODE = "mode";
    private static final int MENU_TEST = 31;
    private static final int MENU_SELECT = 32;
    private static final int MENU_DELETE = 33;
    /** 添加菜单里的"手动填一个模型 id"，与拉取并列。 */
    private static final int MENU_MANUAL = 35;

    private AiTheme theme;
    private AiPlatformViewModel viewModel;
    private LinearLayout column;
    private TextView titleView;
    private TextView badgeView;
    private boolean createdOnce;
    /** 这一趟是在配文本模型还是实时模型；决定列表过滤与空态文案。 */
    private AiInteractionMode mode = AiInteractionMode.defaultMode();

    /** 搜索框只建一次：重建页面时重新挂上去，输入的内容与焦点都还在。 */
    @Nullable
    private YoTextInput searchField;
    /** 每次重建都要销毁上一批列表，否则 YoList 的主题订阅会一直累积。 */
    private final List<YoList> lists = new ArrayList<>();

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String platformId,
                                @NonNull AiInteractionMode mode) {
        return new Intent(context, AiPlatformActivity.class)
                .putExtra(EXTRA_PLATFORM_ID, platformId)
                .putExtra(EXTRA_MODE, mode.name());
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String templateId,
                                      @NonNull AiInteractionMode mode) {
        return new Intent(context, AiPlatformActivity.class)
                .putExtra(EXTRA_TEMPLATE_ID, templateId)
                .putExtra(EXTRA_MODE, mode.name());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = AiTheme.of(this);
        viewModel = new ViewModelProvider(this, AiGraph.get(this).factory())
                .get(AiPlatformViewModel.class);
        setContentView(buildRoot());

        mode = AiInteractionMode.fromName(getIntent().getStringExtra(EXTRA_MODE));
        String platformId = getIntent().getStringExtra(EXTRA_PLATFORM_ID);
        String templateId = getIntent().getStringExtra(EXTRA_TEMPLATE_ID);
        if (platformId != null) {
            viewModel.open(platformId, mode);
        } else if (templateId != null) {
            viewModel.create(templateId, mode);
        }
        viewModel.state().observe(this, this::render);
        viewModel.events().observe(this, this::showEvent);
        createdOnce = savedInstanceState != null;
    }

    @NonNull
    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(theme.page);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        root.addView(outer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        outer.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(AiTheme.HEADER_HEIGHT)));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_XL));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 页头必须让开状态栏：不处理 inset 时标题会压到时钟上（第一次上机就是这样）。
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            scroll.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
        return root;
    }

    @NonNull
    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);

        ImageView back = theme.icon(R.drawable.ic_ai_settings_back, theme.text1);
        back.setContentDescription(getString(R.string.ai_cancel));
        back.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S));
        back.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        titleView = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = theme.dp(AiTheme.SPACE_S);
        header.addView(titleView, titleParams);

        badgeView = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        badgeView.setPadding(theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_S), 0);
        badgeView.setBackground(theme.rounded(theme.fill, AiTheme.RADIUS_ROW));
        badgeView.setVisibility(View.GONE);
        header.addView(badgeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private void render(@NonNull AiPlatformUiState state) {
        if (!state.loaded()) {
            return;
        }
        titleView.setText(state.title());
        boolean hasBadge = state.badge() != null;
        badgeView.setVisibility(hasBadge ? View.VISIBLE : View.GONE);
        if (hasBadge) {
            badgeView.setText(state.badge());
        }
        // 搜索框在重建前可能正被输入：记下焦点，挂回去之后还给用户，键盘不会掉。
        boolean searchFocused = searchField != null && searchField.getView().hasFocus();
        List<YoList> previous = new ArrayList<>(lists);
        lists.clear();
        column.removeAllViews();
        addConnection(state.connection());
        addModels(state.models());
        addAdvanced(state.advanced(), state.advancedExpanded());
        if (state.canDelete()) {
            addDanger();
        }
        for (YoList list : previous) {
            list.destroy();
        }
        if (searchFocused && searchField != null) {
            searchField.getView().requestFocus();
        }
    }

    private void addConnection(@NonNull AiPlatformUiState.Connection connection) {
        List<Runnable> actions = new ArrayList<>();
        YoList list = newList(actions);

        actions.add(() -> {
            if (connection.blockedReason() == null) {
                viewModel.testConnection();
            }
        });
        String value = connection.blockedReason() != null
                ? connection.blockedReason()
                : connection.result() == null ? getString(R.string.ai_settings_connection_start) : connection.result();
        list.addItem(theme.listPrefix(R.drawable.ic_ai_bolt, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_connection_test), value),
                ListSuffixSpec.none());

        actions.add(viewModel::toggleKeyRevealed);
        list.addItem(theme.listPrefix(R.drawable.ic_ai_key, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_field_key), connection.key().display()),
                ListSuffixSpec.custom(theme.icon(R.drawable.ic_ai_settings_reveal, theme.text3)));
        addSection(R.string.ai_settings_connection_section, list);
    }

    private void addModels(@NonNull AiPlatformUiState.Models models) {
        List<Runnable> actions = new ArrayList<>();
        YoList list = newList(actions);

        for (AiPlatformUiState.Row row : models.rows()) {
            actions.add(() -> viewModel.selectModel(row.id()));
            String facts = String.join(" · ", row.facts());
            if (row.state() == AiPlatformUiState.RowState.TESTING) {
                facts = getString(R.string.ai_settings_speed_testing);
            }
            ListTextSpec text = facts.isEmpty()
                    ? ListTextSpec.of(row.name())
                    : ListTextSpec.of(row.name(), facts);
            list.addItem(theme.listPrefix(
                            row.selected() ? R.drawable.ic_ai_check : R.drawable.ic_ai_chat,
                            row.selected() ? theme.brand : theme.text2),
                    text, ListSuffixSpec.custom(moreButton(row.id())));
        }

        if (models.count() == 0) {
            // 空态不是"点一下就拉取"：拉取和手填是两种意图，先给菜单（规格 §4.3）。
            actions.add(this::showAddMenu);
            list.addItem(theme.listPrefix(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(getString(R.string.ai_settings_models_empty),
                            getString(mode == AiInteractionMode.VOICE
                                    ? R.string.ai_settings_models_empty_hint_voice
                                    : R.string.ai_settings_models_empty_hint)),
                    ListSuffixSpec.arrow());
        } else if (models.rows().isEmpty()) {
            // 平台有模型，只是这一次搜索没命中：不能写成"还没有模型"，那是另一件事。
            actions.add(NO_OP);
            list.addItem(ListTextSpec.of(getString(R.string.ai_settings_models_no_match)));
            actions.add(this::showAddMenu);
            list.addItem(theme.listPrefix(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(getString(R.string.ai_settings_add_model_entry)),
                    ListSuffixSpec.arrow());
        } else {
            actions.add(this::showAddMenu);
            list.addItem(theme.listPrefix(R.drawable.ic_ai_plus, theme.text2),
                    ListTextSpec.of(getString(R.string.ai_settings_add_model_entry)),
                    ListSuffixSpec.arrow());
        }

        String title = getString(R.string.ai_settings_models_section) + " · " + models.count();
        addModelsSection(title, models, list, () -> {
            if (models.speedRunning()) {
                viewModel.cancelSpeed();
            } else {
                viewModel.runAllSpeed();
            }
        });
    }

    /**
     * 模型区块：标题行（含「全部测速」）→ 搜索框（> 8 个模型才出现）→ 「最快」结论 → 列表。
     *
     * <p>结论放在标题下面单独一行，是让它和「全部测速」各占一处：两者都是 12sp，
     * 挤在同一行时谁都不好看，而且测速结束后结论一变就会推动按钮左右挪。</p>
     */
    private void addModelsSection(@NonNull CharSequence title, @NonNull AiPlatformUiState.Models models,
                                  @NonNull YoList list, @NonNull Runnable onAction) {
        column.addView(sectionHeader(title, models.actionLabel(), onAction),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (models.searchable()) {
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            searchParams.leftMargin = theme.dp(AiTheme.PAGE_INSET);
            searchParams.rightMargin = theme.dp(AiTheme.PAGE_INSET);
            searchParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
            column.addView(searchView(), searchParams);
        }

        if (models.summary() != null) {
            TextView summary = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
            summary.setText(models.summary());
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            summaryParams.leftMargin = theme.dp(AiTheme.PAGE_INSET);
            summaryParams.rightMargin = theme.dp(AiTheme.PAGE_INSET);
            summaryParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
            column.addView(summary, summaryParams);
        }

        column.addView(list.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 搜索框只建一次；每次重建重新挂上来，内容与焦点都留着。 */
    @NonNull
    private View searchView() {
        if (searchField == null) {
            searchField = YoTextInput.create(this)
                    .setPlaceholder(getString(R.string.ai_settings_models_search_hint))
                    .setSize(YoTextInput.Size.SMALL)
                    .setOnValueChangeListener(value -> viewModel.setSearchQuery(value.toString()))
                    .build();
        }
        return searchField.getView();
    }

    private void showAddMenu() {
        YoMenu menu = YoMenu.create(this)
                .setAnchorView(column)
                .addItem(MENU_SELECT, getString(R.string.ai_settings_models_fetch))
                .addItem(MENU_MANUAL, getString(R.string.ai_settings_add_model))
                .build();
        menu.setOnItemClickListener(item -> {
            if (item == MENU_SELECT) {
                viewModel.fetchModels();
            } else if (item == MENU_MANUAL) {
                showManualAdd();
            }
        });
        menu.show();
    }

    /**
     * 手动填一个模型 id。
     *
     * <p>为什么这条一定要在：拉取接口并不是每个平台都实现（自定义服务地址常常没有 /models），
     * 手填是唯一能用的兜底；空态那句"手动填一个模型 id"也必须有地方兑现。</p>
     */
    private void showManualAdd() {
        YoTextInput field = YoTextInput.create(this)
                .setPlaceholder(getString(mode == AiInteractionMode.VOICE
                        ? R.string.ai_settings_add_model_hint_voice
                        : R.string.ai_settings_add_model_hint))
                .setMaxLength(128)
                .setShowErrorEnabled(true)
                .build();
        YoDialog.create(this)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(getString(R.string.ai_settings_add_model))
                .setActions(
                        YoDialog.Action.negative(getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(getString(R.string.ai_settings_create), () ->
                                viewModel.addManualModel(String.valueOf(field.getText()))))
                .show();
    }

    @NonNull
    private View moreButton(@NonNull String modelId) {
        ImageView button = theme.icon(R.drawable.ic_ai_more, theme.text3);
        int padding = theme.dp(AiTheme.SPACE_M);
        button.setPadding(padding, padding, padding, padding);
        button.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        button.setOnClickListener(anchor -> {
            YoMenu menu = YoMenu.create(this)
                    .addItem(MENU_TEST, getString(R.string.ai_settings_model_menu_test))
                    .setAnchorView(anchor)
                    .setOnItemClickListener(id -> {
                        if (id == MENU_TEST) {
                            viewModel.testModel(modelId);
                        } else if (id == MENU_DELETE) {
                            viewModel.deleteModel(modelId);
                        }
                    })
                    .build();
            menu.addDestructiveItem(MENU_DELETE, getString(R.string.ai_delete));
            menu.show();
        });
        return button;
    }

    private void addAdvanced(@NonNull AiPlatformUiState.Advanced advanced, boolean expanded) {
        List<Runnable> actions = new ArrayList<>();
        YoList list = newList(actions);
        if (!expanded) {
            actions.add(viewModel::toggleAdvanced);
            String protocol = getString(advanced.protocolRes());
            list.addItem(theme.listPrefix(R.drawable.ic_ai_swap, theme.text2),
                    ListTextSpec.of(getString(R.string.ai_settings_advanced_section),
                            protocol + " · " + advanced.baseUrl()),
                    ListSuffixSpec.custom(theme.icon(R.drawable.ic_ai_chevron_down, theme.text3)));
            addSection(R.string.ai_settings_advanced_section, list);
            return;
        }
        actions.add(NO_OP);
        list.addItem(theme.listPrefix(R.drawable.ic_ai_text, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_field_name), advanced.name()),
                ListSuffixSpec.none());
        actions.add(NO_OP);
        list.addItem(theme.listPrefix(R.drawable.ic_ai_globe, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_field_base_url), advanced.baseUrl()),
                ListSuffixSpec.none());
        actions.add(NO_OP);
        list.addItem(theme.listPrefix(R.drawable.ic_ai_swap, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_field_protocol),
                        getString(advanced.protocolRes())),
                ListSuffixSpec.none());
        addSection(R.string.ai_settings_advanced_section, list);
    }

    private void addDanger() {
        TextView delete = theme.text(AiTheme.TEXT_BODY, theme.alert, AiTheme.Weight.MEDIUM);
        delete.setText(R.string.ai_settings_delete_platform);
        delete.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        delete.setMinimumHeight(theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M));
        delete.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        delete.setOnClickListener(view -> confirmDelete());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(AiTheme.SPACE_XL);
        params.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        params.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        column.addView(delete, params);
    }

    private void confirmDelete() {
        AiDialogs.confirm(this, getString(R.string.ai_settings_delete_title),
                getString(R.string.ai_settings_delete_message),
                getString(R.string.ai_settings_delete_platform), true, viewModel::deletePlatform);
    }

    private static final Runnable NO_OP = () -> {
    };

    @NonNull
    private YoList newList(@NonNull List<Runnable> actions) {
        YoList list = YoList.create(this)
                .setItemVerticalInset(AiTheme.SPACE_M)
                .setOnItemClickListener(position -> {
                    if (position >= 0 && position < actions.size()) {
                        actions.get(position).run();
                    }
                })
                .build();
        // 页面每次重建都会换一批列表，旧的必须销毁：YoList 的主题订阅是静态的。
        lists.add(list);
        return list;
    }

    private void addSection(@StringRes int titleRes, @NonNull YoList list) {
        column.addView(sectionHeader(getString(titleRes), null, null),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(list.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 区块标题行：左标题（12sp 三级色）+ 右动作（品牌色）。动作可以没有。 */
    @NonNull
    private View sectionHeader(@NonNull CharSequence title, @Nullable CharSequence action,
                               @Nullable Runnable onAction) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_XL),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S));

        TextView label = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        label.setText(title);
        header.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (action != null) {
            TextView actionView = theme.text(AiTheme.TEXT_CAPTION, theme.brand, AiTheme.Weight.MEDIUM);
            actionView.setText(action);
            actionView.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_XS),
                    theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_XS));
            actionView.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
            actionView.setOnClickListener(view -> {
                if (onAction != null) {
                    onAction.run();
                }
            });
            header.addView(actionView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        return header;
    }

    private void showEvent(@NonNull AiPlatformEvent event) {
        YoToast.show(this, getString(event.message()));
        if (event.closed()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        for (YoList list : lists) {
            list.destroy();
        }
        lists.clear();
        if (searchField != null) {
            searchField.destroy();
            searchField = null;
        }
        super.onDestroy();
    }
}