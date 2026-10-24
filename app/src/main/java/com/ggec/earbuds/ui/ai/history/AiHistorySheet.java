package com.ggec.earbuds.ui.ai.history;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;
import com.yohu.youi.api.platform.YoSystemBar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AI 会话历史面板：{@link YoSheet}（large）里的一列会话，管新建 / 切换 / 重命名 / 删除 / 清空。
 *
 * <p>结构照页面规格 §2：面板头（页名 + 新建 + 关闭）／四态之一／底部「清空全部」。
 * 四态是加载「正在读取对话…」、空「还没有对话 + 开始新对话」、内容、读取失败「没能读取对话记录 + 重试」，
 * 四种互斥，不会同时出现两块。</p>
 *
 * <p><b>为什么行是自绘的，没有用 {@code YoList} 的四段式</b>：规格 §2.2 要求时刻挂在<b>标题行右端</b>、
 * 摘要与标题各占一行且任何长度都不折行。列表组件的副标题允许两行、尾部是按整行垂直居中的，
 * 一旦标题变长时刻就会掉到第二行中间——组件规格 §2.2 的验收项恰好卡在这一条上。
 * 所以这里只借用 Sheet、Button、Dialog、Menu 这些组件，行本身按规格画，卡、分隔线、水波纹都在这一处。</p>
 *
 * <p>面板不判断"要不要真的删掉"：改名、删除、清空全部、切换都转交给 {@link AiHistoryViewModel}，
 * 由它再转给应用级的 {@link AiChatSession}。面板只负责问一句（{@link YoDialog}）和画出来。</p>
 */
public final class AiHistorySheet {

    /**
     * 宿主可以监听的动作。全部有默认实现，只关心"关掉了"的调用方直接传 {@code null} 即可。
     *
     * <p>这些回调只是<b>通知</b>：动作已经由面板经 ViewModel 提交给会话了，宿主不要再调一次
     * {@code AiChatSession}，否则同一件事会执行两遍。</p>
     */
    public interface Listener {

        default void onNewChat() {
        }

        default void onOpen(@NonNull String conversationId) {
        }

        default void onRename(@NonNull String conversationId) {
        }

        default void onDelete(@NonNull String conversationId) {
        }

        default void onClearAll() {
        }

        /** 面板已经收起并释放完毕。 */
        default void onClosed() {
        }
    }

    private static final int ACTION_RENAME = 1;
    private static final int ACTION_DELETE = 2;

    private static final float GROUP_TITLE_SIZE = AiTheme.TEXT_CAPTION;
    private static final float FOOTER_HEIGHT = 48f;
    private static final float ICON_SIZE = 24f;
    private static final float ROW_INSET = AiTheme.SPACE_L;
    private static final float TEXT_GAP = AiTheme.SPACE_M;
    private static final float DUAL_LINE_HEIGHT = 72f;

    /**
     * 读列表是磁盘操作，一条守护线程足够：面板一次只开一个，串行也正合适。
     * 需要自己管线程的调用方走带 {@code ViewModel} 的那个入口。
     */
    private static final Executor IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ai-history");
        thread.setDaemon(true);
        return thread;
    });

    /** 从对话页拉起面板。内部自己建 {@link AiHistoryViewModel}，面板关闭时一并释放。 */
    @NonNull
    public static AiHistorySheet show(@NonNull AppCompatActivity activity,
                                      @NonNull AiChatSession session,
                                      @NonNull ListConversations conversations,
                                      @Nullable Listener listener) {
        AiHistoryViewModel viewModel = new AiHistoryViewModel(session, conversations, IO,
                System::currentTimeMillis, new AiHistoryUiMapper());
        return new AiHistorySheet(activity, viewModel, listener, true).present();
    }

    /** 用调用方给的 ViewModel 拉起面板；ViewModel 的生命周期由调用方负责。 */
    @NonNull
    public static AiHistorySheet show(@NonNull AppCompatActivity activity,
                                      @NonNull AiHistoryViewModel viewModel,
                                      @Nullable Listener listener) {
        return new AiHistorySheet(activity, viewModel, listener, false).present();
    }

    private final AppCompatActivity activity;
    private final AiHistoryViewModel viewModel;
    private final boolean ownsViewModel;
    private final Listener listener;
    private final AiTheme theme;
    private final YoComponents components = new YoComponents();

    private final View content;
    /** 面板头以下、页脚以上的那一块：四态都叠在这里，同一时刻只显示一种。 */
    private final FrameLayout body;
    private final NestedScrollView scroll;
    private final LinearLayout column;
    private final TextView loading;
    private final LinearLayout emptyState;
    private final LinearLayout failedState;
    private final TextView clearRow;

    private YoSheet sheet;
    private List<String> signature = List.of();
    private boolean released;

    private AiHistorySheet(@NonNull AppCompatActivity activity,
                           @NonNull AiHistoryViewModel viewModel,
                           @Nullable Listener listener,
                           boolean ownsViewModel) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.ownsViewModel = ownsViewModel;
        this.listener = listener == null ? new Listener() {
        } : listener;
        this.theme = AiTheme.of(activity);

        FrameLayout root = new FrameLayout(activity);
        // 面板在 Sheet 里，不再套窗口壳：这一层只管"头 / 体 / 脚"三段。
        // 底部先按"20dp 页边距 + 24dp 手势条 + 4dp 余量"给足，inset 到了再按真实值收放（见 applyBottomInset）。
        root.setPadding(0, theme.dp(AiTheme.SPACE_S), 0,
                theme.dp(AiTheme.PAGE_INSET + AiTheme.SPACE_XL + AiTheme.SPACE_XS));
        applyBottomInset(root);

        body = new FrameLayout(activity);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        bodyParams.topMargin = theme.dp(AiTheme.HEADER_HEIGHT);
        bodyParams.bottomMargin = theme.dp(FOOTER_HEIGHT);
        root.addView(body, bodyParams);

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setPadding(0, 0, 0, theme.dp(AiTheme.SPACE_S));
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setVisibility(View.GONE);

        loading = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        loading.setText(R.string.ai_history_loading);
        loading.setGravity(Gravity.CENTER);
        body.addView(loading, centered());
        loading.setVisibility(View.GONE);

        emptyState = buildState(R.string.ai_history_empty_title, R.string.ai_history_empty_action,
                view -> startNewChat());
        body.addView(emptyState, centered());
        emptyState.setVisibility(View.GONE);

        failedState = buildState(R.string.ai_history_load_failed_title, R.string.ai_history_load_failed_action,
                view -> viewModel.refresh());
        body.addView(failedState, centered());
        failedState.setVisibility(View.GONE);

        clearRow = buildClearRow();
        FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(FOOTER_HEIGHT));
        clearParams.gravity = Gravity.BOTTOM;
        root.addView(clearRow, clearParams);
        clearRow.setVisibility(View.GONE);

        root.addView(buildHeader(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(AiTheme.HEADER_HEIGHT), Gravity.TOP));
        this.content = root;
    }

    /** 收起面板。改名/删除后面板留在原地，只有"选中一条""新对话"和标题行的 × 会走到这里。 */
    public void dismiss() {
        if (sheet != null) {
            sheet.dismiss();
        }
    }

    /**
     * 面板底部要让开系统手势条与板子的圆角。
     *
     * <p>Sheet 的呈现树由库自持：它把内容铺到窗口底，而白色圆角板只画到安全区上沿，
     * 于是"贴底"的元素会被板子的下边缘切掉——「清空全部」原先只露出半行红字。
     * 留白由三块相加：20dp 页边距（规格 §0）＋ 手势条高度（inset 报得小时按 24dp 兜底）
     * ＋ 4dp 圆角裁剪的余量。</p>
     */
    private void applyBottomInset(@NonNull View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int bar = YoSystemBar.systemBarsAndCutout(insets).bottom;
            int bottom = theme.dp(AiTheme.PAGE_INSET)
                    + Math.max(bar, theme.dp(AiTheme.SPACE_XL))
                    + theme.dp(AiTheme.SPACE_XS);
            if (view.getPaddingBottom() != bottom) {
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(),
                        view.getPaddingRight(), bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // ---- 面板头 ----

    /**
     * 页名 + 新建 + 关闭。
     *
     * <p>三个元素的左／右边界与页面规格 §0 的 20dp 页边距对齐：标题左 20，关闭右 10（图标自身的
     * 24dp 视觉再加 8dp 内边距，落点仍落在 20dp 的视觉线上）。新建用铅笔而不是「＋」——
     * 这一动作是"再起一段对话"，铅笔与"写"的语义一致，也和行尾的重命名图标同族。</p>
     */
    @NonNull
    private View buildHeader() {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(10), 0);

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_history_panel_title);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(iconButton(R.drawable.ic_ai_edit, theme.text2,
                R.string.ai_history_new_chat, view -> startNewChat()));
        header.addView(iconButton(R.drawable.ic_ai_call_close, theme.text2,
                R.string.ai_history_close, view -> dismiss()));
        return header;
    }

    /** 40dp 触控区、24dp 图形、圆形水波纹：与对话页标题行的按钮同一档尺寸。 */
    @NonNull
    private ImageView iconButton(int icon, int tint, @StringRes int description,
                                 @NonNull View.OnClickListener onClick) {
        ImageView button = theme.icon(icon, tint);
        int pad = theme.dp(8);
        button.setPadding(pad, pad, pad, pad);
        button.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        button.setContentDescription(activity.getString(description));
        button.setOnClickListener(onClick);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));
        return button;
    }

    private void startNewChat() {
        viewModel.newChat();
        listener.onNewChat();
        dismiss();
    }

    // ---- 空态与失败态 ----

    /** 一句真话 + 一个动作，居中；不放插画（规格 §7）。 */
    @NonNull
    private LinearLayout buildState(@StringRes int message, @StringRes int action,
                                    @NonNull View.OnClickListener onClick) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER_HORIZONTAL);
        block.setPadding(theme.dp(AiTheme.SPACE_XL), 0, theme.dp(AiTheme.SPACE_XL), 0);

        TextView hint = theme.text(AiTheme.TEXT_BODY, theme.text2);
        hint.setText(message);
        hint.setGravity(Gravity.CENTER);
        block.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        YoButton button = YoButton.create(activity)
                .setText(activity.getString(action))
                .setStyle(YoButton.Style.TEXT)
                .setSize(YoButton.Size.SMALL)
                .setOnClickListener(onClick)
                .build();
        components.own(button::destroy);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = theme.dp(AiTheme.SPACE_S);
        block.addView(button.getView(), buttonParams);
        return block;
    }

    @NonNull
    private FrameLayout.LayoutParams centered() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
    }

    // ---- 页脚：清空全部 ----

    /**
     * 「清空全部」在列表卡之外、面板底部、左对齐 20dp、13sp 破坏色（规格 §2.4）。
     *
     * <p>它<b>不</b>进列表：和会话同一张卡、同一个图标列时，它看起来像"第二条会话"，
     * 而误触的代价是全部记录。这也与平台页的「删除平台」同一条规则。</p>
     */
    @NonNull
    private TextView buildClearRow() {
        TextView row = theme.text(AiTheme.TEXT_SUBHEAD, theme.alert, AiTheme.Weight.MEDIUM);
        row.setText(R.string.ai_history_clear_all);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
        row.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        row.setOnClickListener(view -> confirmClearAll());
        return row;
    }

    // ---- 展示 ----

    @NonNull
    private AiHistorySheet present() {
        sheet = YoSheet.create(activity)
                .setContent(content)
                .setHeight(YoSheet.Height.large())
                .setScrollSizeMode(YoSheet.ScrollSizeMode.FOLLOW_DETENT)
                // 库自带的关闭钮浮在面板右上角，与标题行里那颗 × 重复。规格只要一个，
                // 保留标题行内与铅笔同组的那颗，把库的那颗收起来（关闭动作在那边）。
                .setCloseActionVisible(false)
                .setDismissOnBack(true)
                .setDismissOnDrag(true)
                .setDismissOnOutsideTap(true)
                .setListener(new YoSheet.Listener() {
                    @Override
                    public void onDismissed(@NonNull YoSheet.DismissReason reason) {
                        release();
                    }
                })
                .build();
        viewModel.items().observe(activity, ignored -> render());
        viewModel.loaded().observe(activity, ignored -> render());
        viewModel.loadFailed().observe(activity, ignored -> render());
        viewModel.events().observe(activity, this::showEvent);
        viewModel.start();
        sheet.show();
        return this;
    }

    /** 四态互斥，同一时刻只亮一块（规格 §2.3）。 */
    private void render() {
        if (released) {
            return;
        }
        boolean loaded = Boolean.TRUE.equals(viewModel.loaded().getValue());
        boolean failed = Boolean.TRUE.equals(viewModel.loadFailed().getValue());
        List<AiHistoryItemUi> items = viewModel.items().getValue();
        if (items == null) {
            items = List.of();
        }
        boolean hasRows = loaded && !items.isEmpty();

        loading.setVisibility(loaded ? View.GONE : View.VISIBLE);
        scroll.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        clearRow.setVisibility(hasRows ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(loaded && !hasRows && !failed ? View.VISIBLE : View.GONE);
        failedState.setVisibility(loaded && !hasRows && failed ? View.VISIBLE : View.GONE);
        if (hasRows) {
            rebuild(items);
        }
    }

    /**
     * 内容没变就不重建：会话写盘会让 {@code historyRevision} 连着跳，重建会把正在滚动的列表拉回顶部。
     */
    private void rebuild(@NonNull List<AiHistoryItemUi> items) {
        List<String> next = signature(items);
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        column.removeAllViews();

        AiHistoryItemUi.Group group = null;
        LinearLayout card = null;
        for (AiHistoryItemUi item : items) {
            if (item.group() != group) {
                boolean first = group == null;
                group = item.group();
                column.addView(groupTitle(group), groupParams(first));
                card = newCard();
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
                column.addView(card, cardParams);
            }
            if (card.getChildCount() > 0) {
                card.addView(divider(), dividerParams());
            }
            card.addView(row(item), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        scroll.scrollTo(0, 0);
    }

    @NonNull
    private List<String> signature(@NonNull List<AiHistoryItemUi> items) {
        List<String> keys = new ArrayList<>(items.size());
        for (AiHistoryItemUi item : items) {
            keys.add(item.id() + '\u0000' + item.title() + '\u0000' + item.preview()
                    + '\u0000' + item.time() + '\u0000' + item.current() + '\u0000' + item.group());
        }
        return List.copyOf(keys);
    }

    /** 分组标题：12sp 三级色，左对齐 20dp（规格 §2.2 分组）。 */
    @NonNull
    private TextView groupTitle(@NonNull AiHistoryItemUi.Group group) {
        TextView title = theme.text(GROUP_TITLE_SIZE, theme.text3, AiTheme.Weight.MEDIUM);
        title.setText(groupLabel(group));
        title.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
        return title;
    }

    @StringRes
    private static int groupLabel(@NonNull AiHistoryItemUi.Group group) {
        switch (group) {
            case TODAY:
                return R.string.ai_history_group_today;
            case YESTERDAY:
                return R.string.ai_history_group_yesterday;
            default:
                return R.string.ai_history_group_earlier;
        }
    }

    @NonNull
    private LinearLayout.LayoutParams groupParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(first ? AiTheme.SPACE_XS : AiTheme.SPACE_L);
        params.bottomMargin = theme.dp(AiTheme.SPACE_S);
        return params;
    }

    /** 一张分组卡：卡底色 + 1px 描边 + 16dp 圆角，并按这个圆角裁剪行内的水波纹。 */
    @NonNull
    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_SHEET, theme.cardStroke, 1));
        card.setClipToOutline(true);
        return card;
    }

    /** 分隔线只出现在同一张卡的相邻行之间，并从标题左边缘开始（规格 §0）。 */
    @NonNull
    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(theme.divider);
        return line;
    }

    @NonNull
    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1)));
        params.setMarginStart(theme.dp(ROW_INSET + ICON_SIZE + TEXT_GAP));
        return params;
    }

    /**
     * 一行：勾/会话图标 · 标题（单行）· 时刻（标题行右端）· ⋯，有摘要时下面再一行。
     *
     * <p>行高 72dp／56dp 用 {@code minimumHeight} 而不是固定高度：中文与系统字号放大时
     * 文字不该被切掉，宁可这一行比规格高一两 dp。</p>
     */
    @NonNull
    private View row(@NonNull AiHistoryItemUi item) {
        boolean hasPreview = item.preview() != null;

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(hasPreview ? DUAL_LINE_HEIGHT : AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(ROW_INSET), 0, theme.dp(AiTheme.SPACE_XS), 0);
        row.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        row.setOnClickListener(view -> open(item));

        // brand 色的勾＝"这段正在被查看"；其余行给会话图标，勾位不空着，列表左右才对得齐。
        ImageView mark = theme.icon(item.current() ? R.drawable.ic_ai_check : R.drawable.ic_ai_chat,
                item.current() ? theme.brand : theme.text3);
        row.addView(mark, new LinearLayout.LayoutParams(
                theme.dp(ICON_SIZE), theme.dp(ICON_SIZE)));

        LinearLayout lines = new LinearLayout(activity);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linesParams.setMarginStart(theme.dp(TEXT_GAP));
        row.addView(lines, linesParams);

        lines.addView(titleLine(item), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (hasPreview) {
            TextView preview = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
            preview.setText(item.preview().resolve(activity));
            singleLine(preview);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            previewParams.topMargin = theme.dp(2);
            lines.addView(preview, previewParams);
        }

        row.addView(moreButton(item));
        return row;
    }

    /** 标题行：标题吃掉剩下的宽度，时刻贴在它右端；两者都单行。 */
    @NonNull
    private View titleLine(@NonNull AiHistoryItemUi item) {
        LinearLayout line = new LinearLayout(activity);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        CharSequence title = item.title().resolve(activity);
        if (item.current()) {
            SpannableString bold = new SpannableString(title);
            bold.setSpan(new StyleSpan(Typeface.BOLD), 0, bold.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            title = bold;
        }
        TextView titleView = theme.text(AiTheme.TEXT_BODY, theme.text1);
        titleView.setText(title);
        singleLine(titleView);
        line.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView time = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        time.setText(item.time().resolve(activity));
        singleLine(time);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMarginStart(theme.dp(AiTheme.SPACE_S));
        line.addView(time, timeParams);
        return line;
    }

    /** 任何长度都不折行：单行 + 尾部省略，是规格 §2.2 的验收项。 */
    private static void singleLine(@NonNull TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
    }

    @NonNull
    private ImageView moreButton(@NonNull AiHistoryItemUi item) {
        ImageView more = theme.icon(R.drawable.ic_ai_more, theme.text3);
        int pad = theme.dp(8);
        more.setPadding(pad, pad, pad, pad);
        more.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        more.setContentDescription(activity.getString(R.string.ai_history_row_more));
        more.setOnClickListener(view -> showRowMenu(view, item));
        more.setLayoutParams(new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));
        return more;
    }

    // ---- 交互 ----

    private void open(@NonNull AiHistoryItemUi item) {
        viewModel.open(item.id());
        listener.onOpen(item.id());
        dismiss();
    }

    private void showRowMenu(@NonNull View anchor, @NonNull AiHistoryItemUi item) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(ACTION_RENAME, activity.getString(R.string.ai_history_rename), R.drawable.ic_ai_edit)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_RIGHT)
                .setOnItemClickListener(id -> {
                    if (id == ACTION_RENAME) {
                        askRename(item);
                    } else if (id == ACTION_DELETE) {
                        confirmDelete(item);
                    }
                })
                .build();
        menu.addDestructiveItem(ACTION_DELETE, activity.getString(R.string.ai_delete));
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }

    private void askRename(@NonNull AiHistoryItemUi item) {
        // 没起过名的对话把输入框留空：让用户从头写，而不是先删掉"未命名对话"这几个字。
        String initial = item.title().isRaw() ? item.title().raw() : "";
        AiHistoryRenameDialog.show(activity, initial, typed -> {
            viewModel.rename(item.id(), typed);
            listener.onRename(item.id());
        });
    }

    private void confirmDelete(@NonNull AiHistoryItemUi item) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.ai_history_delete_title))
                .setMessage(activity.getString(R.string.ai_history_delete_message))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.ai_delete), () -> {
                            viewModel.delete(item.id());
                            listener.onDelete(item.id());
                        }).alert())
                .show();
    }

    private void confirmClearAll() {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.ai_history_clear_title))
                .setMessage(activity.getString(R.string.ai_history_clear_message))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.ai_history_clear_all), () -> {
                            viewModel.clearAll();
                            listener.onClearAll();
                        }).alert())
                .show();
    }

    private void showEvent(@NonNull AiHistoryEvent event) {
        String text = event.argument() == null
                ? activity.getString(event.message())
                : activity.getString(event.message(), event.argument());
        YoToast.show(activity, text);
    }

    // ---- 释放 ----

    private void release() {
        if (released) {
            return;
        }
        released = true;
        column.removeAllViews();
        // 面板关了就不再收状态：ViewModel 可能被调用方留着继续用，没必要让它再把结果画到已收起的树上。
        viewModel.items().removeObservers(activity);
        viewModel.loaded().removeObservers(activity);
        viewModel.loadFailed().removeObservers(activity);
        viewModel.events().removeObservers(activity);
        components.close();
        if (ownsViewModel) {
            viewModel.destroy();
        }
        if (sheet != null) {
            sheet.destroy();
            sheet = null;
        }
        listener.onClosed();
    }
}
