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
 * <p>顶部一张身份卡回答"我在用什么"，点它就是换平台/换模型的入口；模式是分段控件，
 * 一步切换、立刻生效；回答风格只剩系统提示词一项，点开弹窗写。</p>
 *
 * <p>温度、top_p、最大输出、上下文条数不在这页：它们由 APP 在发请求时统一给定
 * （{@link com.ggec.earbuds.domain.ai.model.AiGenerationDefaults}）。
 * 这些参数调对了收益很小、调错了没人看得出原因，摆在这里只会让人以为必须调。</p>
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
        column.addView(styleCard, cardParams(0));

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

        // 一枚首字母方块：这一页三张卡同宽同圆角，没有它就没有主角。
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
        // 值贴右，和这一页其他行的取值保持同一条竖线。
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.leftMargin = theme.dp(AiTheme.SPACE_M);
        row.addView(promptValue, valueParams);
        row.setOnClickListener(view -> editSystemPrompt());
        return row;
    }

    // ---- 渲染 ----

    private void render(@NonNull AiSettingsUiState state) {
        if (!state.loaded()) {
            return;
        }
        rendered = true;
        current = state;
        loadingHint.setVisibility(View.GONE);

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

        promptValue.setText(state.style().promptPreview().resolve(this));
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
        YoToast.show(this, getString(event.message()));
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
                .setText(current.style().systemPrompt())
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
