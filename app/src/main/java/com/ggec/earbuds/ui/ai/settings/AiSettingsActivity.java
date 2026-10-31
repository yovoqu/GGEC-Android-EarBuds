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
import com.yohu.youi.api.callback.OnItemClickListener;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.controls.YoSelect;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 设置：顶部先选"接哪一类模型"，下面的内容跟着换。
 *
 * <p>分段按钮放在页面最上方，是因为它决定这一页其余部分的含义：文本模式配的是
 * DeepSeek 这类对话模型，语音交流模式配的是实时全/多模态模型。两种模式各自记着自己的
 * 平台与模型，切换只是换视图，不会把对方的选择顶掉。</p>
 *
 * <p>卡片一律用 YoUI 的 {@link YoList}，不自己拼行：行高、行内边距、分隔线、按压与
 * 圆角都由库按 HDS 列表规范给出（单行 48dp / 双行 64dp、行内边距 16dp、卡片内边距 4dp），
 * 与平台详情页同一套几何。页面只负责决定每一行是三段式里的哪一段。</p>
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
    /** 语音 Key 的长度上限：只防手滑粘进一整段报文，不做格式校验。 */
    private static final int MAX_VOICE_KEY_LENGTH = 256;
    /** 语音卡里的行序：模型在下拉里选，Key 在对话框里填。 */
    private static final int ROW_VOICE_MODEL = 0;
    private static final int ROW_VOICE_KEY = 1;

    private AiTheme theme;
    private AiSettingsViewModel viewModel;
    private NestedScrollView scroll;
    private LinearLayout column;
    /** 卡片区：文本/语音两种形态往里放不同的列表，所以整块由 render 重画。 */
    private LinearLayout rows;

    private YoSegmentButton modes;
    private TextView modeHint;
    private TextView loadingHint;

    /** 菜单锚点：点在哪张卡上，菜单就从哪张卡旁边出来。 */
    @Nullable
    private View identityCard;

    /** 页面上现役的列表。重建前必须先销毁：YoList 的主题订阅是静态的。 */
    private final List<YoList> lists = new ArrayList<>();
    /** 行内嵌的下拉控件同样持有主题订阅，跟着列表一起销毁。 */
    private final List<YoSelect> selects = new ArrayList<>();

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
        column.addView(modes.getView(), insetParams(AiTheme.SPACE_L));

        // 分段下方那行说明：模式接什么模型。它不属于分段控件，单独一行更轻。
        modeHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        modeHint.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams modeHintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modeHintParams.topMargin = theme.dp(AiTheme.SPACE_S);
        modeHintParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        modeHintParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(modeHint, modeHintParams);

        // 卡片区留一个容器：切换模式时整块换内容，不必再逐张卡调可见性。
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        column.addView(rows, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadingHint = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        loadingHint.setText(R.string.ai_settings_loading);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        hintParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(loadingHint, hintParams);
    }

    // ---- 列表 ----

    /**
     * 建一张独立列表卡片。行点击按位置分派：每张卡只有一到两行，位置就是语义。
     */
    @NonNull
    private YoList newList(@NonNull OnItemClickListener onRowClick) {
        YoList list = YoList.create(this)
                .setOnItemClickListener(onRowClick)
                .build();
        lists.add(list);
        return list;
    }

    /**
     * 把列表挂进卡片区。左右不留 margin：16dp 屏幕边距由 YoList 自己画，
     * 分区标题用 {@link AiTheme#LIST_EDGE} 跟卡片外沿对齐。
     */
    private void addCard(@NonNull YoList list, float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        rows.addView(list.getView(), params);
    }

    /**
     * 文本模式的身份卡：一行说清"我在用什么"，点它换平台。
     *
     * <p>空态不画首字母方块：没有平台可标识，方块只是一个空洞；标题因此从卡片左缘开始。</p>
     */
    private void addIdentityRow(@NonNull AiSettingsUiState.Identity identity) {
        boolean empty = identity.empty();
        ListPrefixSpec prefix = empty
                ? ListPrefixSpec.none()
                : ListPrefixSpec.custom(identityBadge(firstLetter(identity.platformName())));
        // 有平台：标题是平台名，副标题是"我在跟谁说话"的那个模型。
        ListTextSpec text = empty
                ? ListTextSpec.of(getString(identity.emptyTitle()), getString(identity.emptyHint()))
                : ListTextSpec.of(identity.platformName(), identity.modelName());
        // 有平台时把动作写出来（换一家）；空态副标题已经说了"添加平台"，箭头就够了。
        ListSuffixSpec suffix = empty
                ? ListSuffixSpec.arrow()
                : ListSuffixSpec.textAndArrow(getString(R.string.ai_settings_platform_change));

        YoList list = newList(position -> openPlatformMenu());
        list.addItem(prefix, text, suffix);
        identityCard = list.getView();
        addCard(list, AiTheme.SPACE_L);
    }

    /**
     * 语音模式的模型卡：选哪家预设、这家的 Key 填没填。
     *
     * <p>它不是文本身份卡的变体：文本卡背后是"平台 + 模型列表"，这里背后是 APP 预设的
     * 固定几项（{@code AiVoiceModel}），所以没有平台名、没有模型数量，也没有"添加平台"。</p>
     */
    private void addVoiceRows(@NonNull AiSettingsUiState.Voice voice) {
        final YoSelect select = newVoiceSelect(voice);
        YoList list = newList(position -> {
            // 行本身也接一下：点在标题上，和点右侧下拉按钮是同一件事。
            if (position == ROW_VOICE_MODEL) {
                select.toggle();
            } else if (position == ROW_VOICE_KEY) {
                editVoiceKey();
            }
        });
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_chat, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_voice_model)),
                ListSuffixSpec.custom(select.getView()));
        // 没填 Key 时用品牌色：这一行是页面上唯一"还差一步"的地方，值得一个强调。
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_key, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_voice_key)),
                voice.configured()
                        ? ListSuffixSpec.text(getString(R.string.ai_settings_voice_key_set))
                        : ListSuffixSpec.text(getString(R.string.ai_settings_voice_key_empty), theme.brand));
        addCard(list, AiTheme.SPACE_L);
    }

    /**
     * 语音模型下拉：按钮上是当前模型，点开是预设的几家。
     *
     * <p>用库的 {@link YoSelect} 而不是页面自己弹菜单：触发器的文字、向下箭头、按压态、展开时箭头
     * 翻转，以及菜单的对齐与避让、选中项底色，都由库按 HarmonyOS Select 的规范画，页面不再算弹层位置。</p>
     *
     * <p>尺寸取 SMALL（28dp、14sp）：和行内其他取值同一档，放进 48dp 的行里不撑高。
     * 底色显式给一层浅填充——库默认的 triggerBackground 在这个主题下与卡片几乎同色
     * （253 vs 255），看不出是个按钮。菜单右对齐到按钮右缘：按钮贴着卡片右边，左对齐会伸出屏幕。</p>
     */
    @NonNull
    private YoSelect newVoiceSelect(@NonNull AiSettingsUiState.Voice voice) {
        YoSelect.Builder builder = YoSelect.create(this)
                .setControlSize(YoSelect.ControlSize.SMALL)
                .setBackgroundColor(theme.fill)
                .setValue(voice.modelName())
                .setMenuAlign(YoSelect.MenuAlignType.END)
                .setOnSelectListener((index, text, itemId) -> {
                    // itemId 就是选项在预设表里的下标：模型 id 是字符串，只在建选项时映射一次。
                    if (itemId >= 0 && itemId < voice.options().size()) {
                        viewModel.selectVoiceModel(voice.options().get(itemId).id());
                    }
                });
        for (int i = 0; i < voice.options().size(); i++) {
            AiSettingsUiState.VoiceOption option = voice.options().get(i);
            builder.addOption(i, option.name());
            if (option.current()) {
                builder.setSelected(i);
            }
        }
        YoSelect select = builder.build();
        selects.add(select);
        return select;
    }

    /**
     * 回答风格：只有系统提示词。
     *
     * <p>预览放在副标题而不是右侧取值：提示词是一句话，右侧尾槽只有一行且不换行，
     * 长句会把标题挤没；副标题最多两行并省略，长什么样都读得下去。</p>
     */
    private void addPromptRow(@NonNull AiSettingsUiState.Style style) {
        YoList list = newList(position -> editSystemPrompt());
        list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_text, theme.text2),
                ListTextSpec.of(getString(R.string.ai_settings_param_prompt),
                        style.promptPreview().resolve(this)),
                ListSuffixSpec.arrow());
        addCard(list, 0);
    }

    /** 分区标题：12sp 三级色，只在卡片上方出现一次，与平台页同一套间距。 */
    private void addSectionTitle(@StringRes int titleRes) {
        TextView title = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        title.setText(titleRes);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(AiTheme.SPACE_XL);
        params.bottomMargin = theme.dp(AiTheme.SPACE_S);
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        rows.addView(title, params);
    }

    /**
     * 首字母方块：这一页没有平台 logo，用首字代替，尺寸取列表头像档（40dp），
     * 挂在 {@link ListPrefixSpec#custom} 上——它是"我在用哪一家"的标识，不是装饰图标。
     */
    @NonNull
    private View identityBadge(@NonNull String letter) {
        TextView badge = theme.text(AiTheme.TEXT_HEADLINE - 4, theme.brand, AiTheme.Weight.MEDIUM);
        badge.setText(letter);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(theme.rounded(theme.brandSoft, AiTheme.RADIUS_ROW));
        // 前缀槽会把 LayoutParams 换成 WRAP_CONTENT，尺寸只能靠最小宽高撑出来。
        badge.setMinWidth(theme.dp(AiTheme.TOUCH_TARGET));
        badge.setMinHeight(theme.dp(AiTheme.TOUCH_TARGET));
        return badge;
    }

    @NonNull
    private LinearLayout.LayoutParams insetParams(float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        return params;
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

        // 只画当前模式的分区：文本画身份卡与回答风格，语音只画语音模型卡。
        clearRows();
        if (voice) {
            addVoiceRows(state.voice());
        } else {
            addIdentityRow(state.identity());
            addSectionTitle(R.string.ai_settings_section_style);
            addPromptRow(state.style());
        }
    }

    /** 拆掉上一轮的卡片。列表与行内下拉都必须先 destroy 再丢引用，否则主题监听会一直挂着。 */
    private void clearRows() {
        rows.removeAllViews();
        identityCard = null;
        for (YoList list : lists) {
            list.destroy();
        }
        lists.clear();
        for (YoSelect select : selects) {
            select.destroy();
        }
        selects.clear();
    }

    @Override
    protected void onDestroy() {
        clearRows();
        if (modes != null) {
            modes.destroy();
        }
        super.onDestroy();
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
        if (current == null || identityCard == null) {
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

    /**
     * 语音模型的 Key。对话框只写不读：存过的 Key 不回显，想换就粘一份新的。
     * 已经存过 Key 时多给一个「清除」——输入框为空时「保存」是禁用的，
     * 没有这个出口，用户就换不掉一个填错的 Key。
     */
    private void editVoiceKey() {
        if (current == null) {
            return;
        }
        AiSettingsUiState.Voice voice = current.voice();
        YoTextInput field = YoTextInput.create(this)
                .setPlaceholder(getString(R.string.ai_settings_voice_key_dialog_hint))
                .setMaxLength(MAX_VOICE_KEY_LENGTH)
                .build();
        YoDialog.Action cancel = YoDialog.Action.negative(getString(R.string.ai_cancel), null);
        YoDialog.Action clear = YoDialog.Action.neutral(
                getString(R.string.ai_settings_voice_key_clear),
                () -> viewModel.saveVoiceApiKey(""));
        YoDialog.Action save = YoDialog.Action.positive(getString(R.string.ai_settings_save),
                () -> viewModel.saveVoiceApiKey(String.valueOf(field.getText())));
        YoDialog.Builder dialog = YoDialog.create(this)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setButtonDirection(YoDialog.ButtonDirection.HORIZONTAL)
                .setTitle(getString(R.string.ai_settings_voice_key_dialog_title, voice.modelName()));
        if (voice.configured()) {
            dialog.setActions(cancel, clear, save);
        } else {
            dialog.setActions(cancel, save);
        }
        dialog.show();
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
