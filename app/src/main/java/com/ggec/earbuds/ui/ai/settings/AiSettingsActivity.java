package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/**
 * AI 设置：按"要做的决定"分组，能一步改完的都不进第二层。
 *
 * <p>这一版换的是结构，不是皮肤。上一版是一张目录：每个参数占一行，点开弹窗、输入、保存，
 * 模式和平台靠勾选表达——最常改的两件事（换模式、调温度）都要四次点击，
 * 而"我现在用哪个平台哪个模型"散在列表里根本看不出来。</p>
 *
 * <p>现在：顶部一张身份卡回答"我在用什么"，点它就是换平台/换模型的入口；
 * 模式是分段控件，一步切换、立刻生效；四个参数是内联滑杆，拖动即生效、右侧实时显示值，
 * 点数值可退回平台默认；只有提示词需要打字，才开弹窗。用量保持三行 k-v。</p>
 */
public final class AiSettingsActivity extends AppCompatActivity {

    /** 菜单里"添加平台"占的 id，平台行用 0..n-1。 */
    private static final int MENU_ADD_PLATFORM = -1;

    private AiTheme theme;
    private AiSettingsViewModel viewModel;
    private NestedScrollView scroll;
    private LinearLayout column;

    private LinearLayout identityCard;
    private TextView identityAvatar;
    private TextView identityName;
    private TextView identityModel;
    private TextView identityAction;
    private AiSegmentedControl modes;
    private TextView promptValue;
    private androidx.appcompat.widget.SwitchCompat globalSwitch;
    private boolean syncingGlobal;
    private TextView scopeValue;
    private TextView scopeHint;
    private LinearLayout applyRow;
    private TextView applyHint;
    private AiSliderRow temperature;
    private AiSliderRow topP;
    private AiSliderRow maxOutput;
    private AiSliderRow context;
    private LinearLayout usageCard;
    private TextView loadingHint;

    @Nullable
    private AiSettingsUiState current;
    private boolean rendered;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, AiSettingsActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        theme = AiTheme.of(this);
        viewModel = new ViewModelProvider(this, AiGraph.get(this).factory())
                .get(AiSettingsViewModel.class);
        setContentView(buildRoot());

        viewModel.uiState().observe(this, state -> {
            if (state != null) {
                render(state);
            }
        });
        viewModel.events().observe(this, this::showEvent);
        viewModel.start();
    }

    @NonNull
    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        AiSettingsHeader header = new AiSettingsHeader(this, theme,
                R.string.ai_settings_title, this::finish);
        root.addView(header.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            scroll.setPadding(0, 0, 0, bars.bottom + theme.dp(AiTheme.SPACE_XL));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        buildSections();
        return root;
    }

    /**
     * 全局参数开关。关着时下面的滑杆仍显示用户调过的值，但界面写明"未启用"——
     * 直接把控件藏起来会让人以为自己没设过。
     */
    private View globalParamsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_S));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView label = theme.text(AiTheme.TEXT_BODY, theme.text1);
        label.setText(R.string.ai_settings_global_params);
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        globalSwitch = new androidx.appcompat.widget.SwitchCompat(this);
        // SwitchCompat 的默认强调色是主题色（这台设备上是紫），与设计里的品牌蓝不一致，直接指定。
        globalSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(theme.brand));
        globalSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(theme.brandSoft));
        globalSwitch.setShowText(false);
        globalSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!syncingGlobal) {
                viewModel.saveGlobalParamsEnabled(checked);
            }
        });
        line.addView(globalSwitch, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(line);

        TextView hint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        hint.setText(R.string.ai_settings_global_params_hint);
        row.addView(hint);
        return row;
    }

    /**
     * 作用范围：只说事实——"哪些模型不再跟随这套值"。
     *
     * <p>这一版没有可写的范围字段（也不打算加半成品），所以这里不是三选一控件，
     * 而是从参数覆盖表反推出来的一句话；想改变它就用下面那行"应用到全部模型"。</p>
     */
    private View scopeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S));

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = theme.text(AiTheme.TEXT_BODY, theme.text1);
        label.setText(R.string.ai_settings_scope_title);
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        scopeValue = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        scopeValue.setGravity(Gravity.END);
        line.addView(scopeValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(line);

        scopeHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        scopeHint.setPadding(0, theme.dp(2), 0, 0);
        row.addView(scopeHint);
        return row;
    }

    /** 把当前这套值写进每个模型。写完后它们各自固定，不再跟随全局——副行里说清这一点。 */
    private View applyAllRow() {
        applyRow = new LinearLayout(this);
        applyRow.setOrientation(LinearLayout.VERTICAL);
        applyRow.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S));
        applyRow.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        applyRow.setOnClickListener(view -> viewModel.applyParamsToAllModels());

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = theme.text(AiTheme.TEXT_BODY, theme.brand, AiTheme.Weight.MEDIUM);
        label.setText(R.string.ai_settings_apply_all);
        line.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        applyRow.addView(line);

        applyHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        applyHint.setPadding(0, theme.dp(2), 0, 0);
        applyRow.addView(applyHint);
        return applyRow;
    }

    private void buildSections() {
        column.addView(identityView(), cardParams(AiTheme.SPACE_L));

        column.addView(sectionTitle(R.string.ai_settings_section_mode), titleParams());
        LinearLayout modeCard = card();
        modes = new AiSegmentedControl(theme);
        modes.setListener(second -> viewModel.selectMode(second ? AiMode.OMNI : AiMode.CASCADED));
        modeCard.addView(modes, rowParams());
        column.addView(modeCard, cardParams(0));

        column.addView(sectionTitle(R.string.ai_settings_section_style), titleParams());
        LinearLayout styleCard = card();
        styleCard.addView(promptRow(), rowParams());
        styleCard.addView(globalParamsRow(), rowParams());
        styleCard.addView(scopeRow(), rowParams());
        styleCard.addView(applyAllRow(), rowParams());
        temperature = AiSliderRow.decimal(theme, R.string.ai_settings_param_temperature, 0f, 2f, 20);
        temperature.setListener(value -> viewModel.saveTemperature(value));
        styleCard.addView(temperature, rowParams());
        topP = AiSliderRow.decimal(theme, R.string.ai_settings_param_top_p, 0f, 1f, 10);
        topP.setListener(value -> viewModel.saveTopP(value));
        styleCard.addView(topP, rowParams());
        maxOutput = AiSliderRow.integer(theme, R.string.ai_settings_param_max_output, 256, 8192, 256);
        maxOutput.setListener(value -> viewModel.saveMaxOutputTokens(
                value == null ? null : (int) Math.round(value)));
        styleCard.addView(maxOutput, rowParams());
        context = AiSliderRow.integer(theme, R.string.ai_settings_param_context, 2, 40, 1);
        context.setListener(value -> viewModel.saveContextMessages(
                value == null ? AiChatPreferences.DEFAULT_CONTEXT_MESSAGES : (int) Math.round(value)));
        styleCard.addView(context, rowParams());
        column.addView(styleCard, cardParams(0));

        column.addView(sectionTitle(R.string.ai_settings_section_usage), titleParams());
        usageCard = card();
        column.addView(usageCard, cardParams(0));

        loadingHint = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        loadingHint.setText(R.string.ai_settings_loading);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        hintParams.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        column.addView(loadingHint, hintParams);
    }

    // ---- 构件 ----

    @NonNull
    private View identityView() {
        identityCard = new LinearLayout(this);
        identityCard.setOrientation(LinearLayout.VERTICAL);
        identityCard.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_SHEET,
                theme.cardStroke, 1));
        identityCard.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_L),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_L));
        identityCard.setOnClickListener(view -> openPlatformMenu());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // 一枚首字母方块：这一页四张卡同宽同圆角，没有它就没有主角。
        // 它不是一个装饰图标，而是"我在用哪一家"的标识。
        identityAvatar = theme.text(AiTheme.TEXT_HEADLINE - 4, theme.brand, AiTheme.Weight.MEDIUM);
        identityAvatar.setGravity(Gravity.CENTER);
        identityAvatar.setBackground(theme.rounded(theme.brandSoft, AiTheme.RADIUS_ROW));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET));
        avatarParams.rightMargin = theme.dp(AiTheme.SPACE_M);
        row.addView(identityAvatar, avatarParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        identityName = theme.text(AiTheme.TEXT_HEADLINE - 4, theme.text1, AiTheme.Weight.MEDIUM);
        identityName.setSingleLine(true);
        identityName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identityModel = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        identityModel.setSingleLine(true);
        identityModel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams modelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modelParams.topMargin = theme.dp(2);
        texts.addView(identityName);
        texts.addView(identityModel, modelParams);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        identityAction = theme.text(AiTheme.TEXT_SUBHEAD, theme.brand, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.leftMargin = theme.dp(AiTheme.SPACE_M);
        row.addView(identityAction, actionParams);

        identityCard.addView(row);
        return identityCard;
    }

    private TextView sectionTitle(@StringRes int res) {
        TextView title = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        title.setText(res);
        return title;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_SHEET, theme.cardStroke, 1));
        return card;
    }

    @NonNull
    private LinearLayout.LayoutParams cardParams(float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        params.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        params.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        return params;
    }

    @NonNull
    private LinearLayout.LayoutParams titleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(AiTheme.SPACE_L);
        params.bottomMargin = theme.dp(AiTheme.SPACE_S);
        params.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        params.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        return params;
    }

    @NonNull
    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull
    private View promptRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M));
        row.setBackground(theme.pressable(AiTheme.RADIUS_ROW));

        TextView label = theme.text(AiTheme.TEXT_BODY, theme.text1);
        label.setText(R.string.ai_settings_param_prompt);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        promptValue = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        promptValue.setSingleLine(true);
        promptValue.setGravity(Gravity.END);
        promptValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // 值固定贴右，和下面四条滑杆的数值同一条竖线。
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.leftMargin = theme.dp(AiTheme.SPACE_M);
        row.addView(promptValue, valueParams);
        row.setOnClickListener(view -> editSystemPrompt());
        return row;
    }

    // ---- 渲染 ----

    /**
     * 开关只影响"是否生效"，不隐藏控件：用户调过的值还在，关掉时界面上说明它没被使用。
     */
    private void applyGlobalParams(boolean enabled) {
        if (globalSwitch != null && globalSwitch.isChecked() != enabled) {
            // 程序化同步不能回写成用户操作，否则会再触发一次保存。
            syncingGlobal = true;
            globalSwitch.setChecked(enabled);
            syncingGlobal = false;
        }
        for (AiSliderRow row : new AiSliderRow[]{temperature, topP, maxOutput}) {
            if (row != null) {
                row.setAlpha(enabled ? 1f : 0.45f);
            }
        }
    }

    private void render(@NonNull AiSettingsUiState state) {
        if (!state.loaded()) {
            return;
        }
        rendered = true;
        current = state;
        loadingHint.setVisibility(View.GONE);
        applyGlobalParams(state.params().globalParamsEnabled());
        bindScope(state.params());

        AiSettingsUiState.PlatformRow active = null;
        for (AiSettingsUiState.PlatformRow row : state.platforms()) {
            if (row.current()) {
                active = row;
            }
        }
        if (active == null) {
            identityAvatar.setText("");
            identityAvatar.setVisibility(View.INVISIBLE);
            identityName.setText(R.string.ai_settings_identity_empty);
            identityModel.setText(R.string.ai_settings_identity_empty_hint);
            identityAction.setText(R.string.ai_settings_add_platform);
        } else {
            identityAvatar.setVisibility(View.VISIBLE);
            identityAvatar.setText(firstLetter(active.name()));
            identityName.setText(active.name());
            // 副标题放"现在用的是哪个模型"，而不是模型数量：
            // 用户在设置页要看的是"我在跟谁说话"，数量是他进平台详情之后才关心的事。
            String model = state.activeModel();
            identityModel.setText(model == null || model.isBlank()
                    ? active.subtitle().resolve(this) : model);
            identityAction.setText(R.string.ai_settings_platform_change);
        }

        AiSettingsUiState.ModeRow omni = modeRow(state, AiMode.OMNI);
        AiSettingsUiState.ModeRow cascaded = modeRow(state, AiMode.CASCADED);
        if (omni != null && cascaded != null) {
            modes.bind(label(cascaded), label(omni), omni.selected(), omni.enabled(),
                    omni.subtitle().resolve(this));
        }

        AiSettingsUiState.Params params = state.params();
        promptValue.setText(params.promptPreview().resolve(this));
        CharSequence defaultValue = getString(R.string.ai_settings_value_platform_default);
        temperature.bind(params.temperature(), defaultValue);
        topP.bind(params.topP(), defaultValue);
        maxOutput.bind(params.maxOutputTokens() == null ? null : params.maxOutputTokens().doubleValue(),
                defaultValue);
        context.bind((double) params.contextMessages(), params.contextText().resolve(this));

        bindUsage(state.usage());
    }

    /** 作用范围只说事实；能不能批量应用由 {@code canApply} 决定，与这里的文案分开。 */
    private void bindScope(@NonNull AiSettingsUiState.Params params) {
        AiSettingsUiState.Scope scope = params.scope();
        switch (scope.kind()) {
            case DISABLED -> {
                scopeValue.setText(R.string.ai_settings_scope_disabled);
                scopeHint.setText(R.string.ai_settings_scope_hint_disabled);
            }
            case ALL -> {
                scopeValue.setText(R.string.ai_settings_scope_all);
                scopeHint.setText(R.string.ai_settings_scope_hint_all);
            }
            case ONE_MODEL -> {
                scopeValue.setText(getString(R.string.ai_settings_scope_one,
                        scope.subject() == null ? "" : scope.subject()));
                scopeHint.setText(R.string.ai_settings_scope_hint_one);
            }
            case PLATFORM -> {
                scopeValue.setText(R.string.ai_settings_scope_platform);
                scopeHint.setText(scope.subject() == null
                        ? getString(R.string.ai_settings_scope_hint_platform_plain,
                                String.valueOf(scope.pinned()))
                        : getString(R.string.ai_settings_scope_hint_platform,
                                scope.subject(), String.valueOf(scope.pinned())));
            }
            case PARTIAL -> {
                String count = String.valueOf(scope.pinned());
                scopeValue.setText(getString(R.string.ai_settings_scope_partial, count));
                scopeHint.setText(scope.subject() == null
                        ? getString(R.string.ai_settings_scope_hint_partial, count)
                        : getString(R.string.ai_settings_scope_hint_partial_named,
                                scope.subject(), count));
            }
        }
        applyRow.setVisibility(params.canApply() ? View.VISIBLE : View.GONE);
        if (params.canApply()) {
            applyHint.setText(getString(R.string.ai_settings_apply_all_hint,
                    String.valueOf(params.totalModels())));
        }
    }

    private void bindUsage(@NonNull AiSettingsUiState.Usage usage) {
        usageCard.removeAllViews();
        String dash = getString(R.string.ai_settings_usage_dash);
        String unknown = getString(R.string.ai_settings_usage_cost_unknown);
        usageCard.addView(usageRow(getString(R.string.ai_settings_usage_requests),
                usage.requests() == null ? dash : usage.requests().resolve(this), true));
        usageCard.addView(usageRow(getString(R.string.ai_settings_usage_tokens),
                usage.tokens() == null ? dash : usage.tokens().resolve(this), false));
        // 费用单独一格：一条都没上报时写「未上报」，0.00 会被读成"免费"。
        usageCard.addView(usageRow(getString(R.string.ai_settings_usage_cost),
                usage.cost() == null ? unknown : usage.cost().resolve(this), false));
    }

    @NonNull
    private View usageRow(@NonNull CharSequence label, @NonNull CharSequence value, boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S));

        TextView name = theme.text(AiTheme.TEXT_BODY, theme.text1);
        name.setText(label);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView amount = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        amount.setText(value);
        row.addView(amount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (first) {
            return row;
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(theme.divider);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
        wrap.addView(row);
        return wrap;
    }

    /** 平台名首字：中文取第一个字，拉丁取首字母大写。空名返回空串。 */
    @NonNull
    private static String firstLetter(@NonNull String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        String first = trimmed.substring(0, end);
        return first.toUpperCase(java.util.Locale.ROOT);
    }

    @NonNull
    private CharSequence label(@Nullable AiSettingsUiState.ModeRow row) {
        return row == null ? "" : getString(row.title());
    }

    @Nullable
    private static AiSettingsUiState.ModeRow modeRow(@NonNull AiSettingsUiState state,
                                                     @NonNull AiMode mode) {
        for (AiSettingsUiState.ModeRow row : state.modes()) {
            if (row.mode() == mode) {
                return row;
            }
        }
        return null;
    }

    // ---- 交互 ----

    private void showEvent(@NonNull AiSettingsEvent event) {
        if (!rendered) {
            return;
        }
        String text = event.argument() == null
                ? getString(event.message())
                : getString(event.message(), event.argument());
        YoToast.show(this, text);
    }

    /** 换平台/换模型的入口就在身份卡上：用户问"我在用什么"的地方，也是他想换的地方。 */
    private void openPlatformMenu() {
        if (current == null) {
            return;
        }
        List<AiSettingsUiState.PlatformRow> platforms = current.platforms();
        YoMenu.Builder builder = YoMenu.create(this).setAnchorView(identityCard);
        for (int i = 0; i < platforms.size(); i++) {
            AiSettingsUiState.PlatformRow row = platforms.get(i);
            builder.addItem(i, row.current()
                    ? row.name() + " · " + getString(R.string.ai_settings_platform_current)
                    : row.name());
        }
        builder.addItem(MENU_ADD_PLATFORM, getString(R.string.ai_settings_add_platform));
        builder.setOnItemClickListener(id -> {
            if (id == MENU_ADD_PLATFORM) {
                showAddPlatform();
            } else if (id >= 0 && id < platforms.size()) {
                startActivity(AiPlatformActivity.intent(this, platforms.get(id).platformId()));
            }
        });
        builder.build().show();
    }

    private void showAddPlatform() {
        List<AiPlatformTemplate> templates = viewModel.templates();
        if (templates.isEmpty()) {
            YoToast.show(this, getString(R.string.ai_settings_add_platform_empty));
            return;
        }
        AiAddPlatformDialog.show(this, templates,
                template -> startActivity(AiPlatformActivity.createIntent(this, template.id())));
    }

    private void editSystemPrompt() {
        if (current == null) {
            return;
        }
        YoTextInput field = YoTextInput.create(this)
                .setText(current.params().systemPrompt())
                .setPlaceholder(getString(R.string.ai_settings_prompt_dialog_hint))
                .setMaxLength(AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .setMultiline(true, 4, 8)
                .build();
        YoDialog.create(this)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(getString(R.string.ai_settings_prompt_dialog_title))
                .setActions(
                        YoDialog.Action.negative(getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(getString(R.string.ai_settings_save), () ->
                                viewModel.saveSystemPrompt(String.valueOf(field.getText()))))
                .show();
    }
}
