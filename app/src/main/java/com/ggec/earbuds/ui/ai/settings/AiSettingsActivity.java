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
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.List;

/**
 * AI 设置：顶部先选"接哪一类模型"，下面的内容跟着换。
 *
 * <p>分段按钮放在页面最上方，是因为它决定这一页其余部分的含义：文本模式配的是
 * DeepSeek 这类对话模型，语音交流模式配的是实时全/多模态模型。两种模式各自记着自己的
 * 平台与模型，切换只是换视图，不会把对方的选择顶掉。</p>
 *
 * <p>温度、top_p、最大输出、上下文条数不在这页：它们由 APP 在发请求时统一给定
 * （{@link com.ggec.earbuds.domain.ai.model.AiGenerationDefaults}）。
 * 这些参数调对了收益很小、调错了没人看得出原因，摆在这里只会让人以为必须调。</p>
 */
public final class AiSettingsActivity extends AppCompatActivity {

    /** 菜单里"添加平台"占的 id，平台行用 0..n-1。 */
    private static final int MENU_ADD_PLATFORM = -1;
    /** 分段按钮的段 id；两段正好等于下标，读选中态时不必再换算。 */
    private static final int SEGMENT_TEXT = 0;
    private static final int SEGMENT_VOICE = 1;

    private AiTheme theme;
    private AiSettingsViewModel viewModel;
    private NestedScrollView scroll;
    private LinearLayout column;

    private YoSegmentButton modes;
    private TextView modeHint;
    private LinearLayout identityCard;
    private TextView identityAvatar;
    private TextView identityName;
    private TextView identityModel;
    private TextView identityAction;
    private TextView styleTitle;
    private LinearLayout styleCard;
    private TextView voiceTitle;
    private LinearLayout voiceCard;
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
        // 分段按钮是这一页的第一个元素：先选接入形态，再谈配置。控件直接用 Youi Kit 的。
        modes = YoSegmentButton.create(this)
                .addSegment(SEGMENT_TEXT, getString(R.string.ai_settings_mode_text))
                .addSegment(SEGMENT_VOICE, getString(R.string.ai_settings_mode_voice))
                .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                .setSize(YoSegmentButton.Size.MEDIUM)
                .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE)
                .setSelectedIndex(SEGMENT_TEXT)
                .setOnSelectionChangeListener(index -> viewModel.selectMode(
                        index == SEGMENT_VOICE ? AiInteractionMode.VOICE : AiInteractionMode.TEXT))
                .build();
        column.addView(modes.getView(), cardParams(AiTheme.SPACE_L));

        // 分段下方那行说明：模式接什么模型。它不属于分段控件，单独一行更轻。
        modeHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        modeHint.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams modeHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeHintParams.topMargin = theme.dp(AiTheme.SPACE_S);
        modeHintParams.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        modeHintParams.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        column.addView(modeHint, modeHintParams);

        column.addView(identityView(), cardParams(AiTheme.SPACE_L));

        styleTitle = sectionTitle(R.string.ai_settings_section_style);
        column.addView(styleTitle, titleParams());
        styleCard = card();
        styleCard.addView(promptRow(), rowParams());
        column.addView(styleCard, cardParams(0));

        voiceTitle = sectionTitle(R.string.ai_settings_section_voice);
        column.addView(voiceTitle, titleParams());
        voiceCard = voiceCard();
        column.addView(voiceCard, cardParams(0));

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

    /**
     * 语音模式唯一的分区：说清这条链路是什么。
     *
     * <p>不摆转写模型、音色、静音阈值：这些都由实时模型在服务端处理，
     * 摆出来只会让人以为 APP 还在做 ASR → LLM → TTS。</p>
     */
    @NonNull
    private LinearLayout voiceCard() {
        LinearLayout card = card();
        card.addView(voiceLinkRow(), rowParams());

        TextView note = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        note.setText(R.string.ai_settings_voice_note);
        note.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.leftMargin = theme.dp(AiTheme.SPACE_L);
        noteParams.rightMargin = theme.dp(AiTheme.SPACE_L);
        noteParams.topMargin = theme.dp(AiTheme.SPACE_S);
        noteParams.bottomMargin = theme.dp(AiTheme.SPACE_L);
        card.addView(note, noteParams);
        return card;
    }

    @NonNull
    private View voiceLinkRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M));

        TextView label = theme.text(AiTheme.TEXT_BODY, theme.text1);
        label.setText(R.string.ai_settings_voice_link);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        value.setText(R.string.ai_settings_voice_link_value);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
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

        boolean voice = state.mode() == AiInteractionMode.VOICE;
        int selected = voice ? SEGMENT_VOICE : SEGMENT_TEXT;
        if (modes.getSelectedIndex() != selected) {
            modes.setSelectedIndex(selected);
        }
        modeHint.setText(state.modeHint().resolve(this));

        // 只留下当前模式的分区：另一模式的配置不是"不可用"，而是不属于这一屏。
        setVisible(styleTitle, !voice);
        setVisible(styleCard, !voice);
        setVisible(voiceTitle, voice);
        setVisible(voiceCard, voice);

        AiSettingsUiState.Identity identity = state.identity();
        if (identity.empty()) {
            identityAvatar.setText("");
            // 空态没有平台可标识：把方块收掉，标题才能从卡片左缘开始，而不是留一个空洞。
            identityAvatar.setVisibility(View.GONE);
            identityName.setText(identity.emptyTitle());
            identityModel.setText(identity.emptyHint());
            identityAction.setText(R.string.ai_settings_add_platform);
        } else {
            identityAvatar.setVisibility(View.VISIBLE);
            identityAvatar.setText(firstLetter(identity.platformName()));
            identityName.setText(identity.platformName());
            // 副标题放"现在用的是哪个模型"，而不是模型数量：
            // 用户在设置页要看的是"我在跟谁说话"，数量是他进平台详情之后才关心的事。
            identityModel.setText(identity.modelName());
            identityAction.setText(R.string.ai_settings_platform_change);
        }

        promptValue.setText(state.style().promptPreview().resolve(this));
    }

    @Override
    protected void onDestroy() {
        if (modes != null) {
            modes.destroy();
        }
        super.onDestroy();
    }

    private static void setVisible(@Nullable View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 平台名首字：中文取第一个字，拉丁取首字母大写。空名返回空串。 */
    @NonNull
    private static String firstLetter(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        String first = trimmed.substring(0, end);
        return first.toUpperCase(java.util.Locale.ROOT);
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
                startActivity(AiPlatformActivity.intent(this, platforms.get(id).platformId(),
                        current.mode()));
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
        AiInteractionMode mode = current == null ? AiInteractionMode.defaultMode() : current.mode();
        AiAddPlatformDialog.show(this, templates,
                template -> startActivity(AiPlatformActivity.createIntent(this, template.id(), mode)));
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
