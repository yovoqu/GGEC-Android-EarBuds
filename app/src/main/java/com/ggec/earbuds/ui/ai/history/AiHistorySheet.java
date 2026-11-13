package com.ggec.earbuds.ui.ai.history;

import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.feedback.YoToast;

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
 * 由它再转给应用级的 {@link AiChatSession}。面板只负责问一句和画出来。</p>
 */
public final class AiHistorySheet {

    private static final float FOOTER_HEIGHT = 48f;

    /** 用调用方给的 ViewModel 拉起面板；ViewModel 的生命周期由调用方负责。 */
    @NonNull
    public static AiHistorySheet show(@NonNull AppCompatActivity activity,
                                      @NonNull AiHistoryViewModel viewModel) {
        return new AiHistorySheet(activity, viewModel).present();
    }

    private final AppCompatActivity activity;
    private final AiHistoryViewModel viewModel;
    private final AiTheme theme;
    private final YoComponents components = new YoComponents();

    private final View content;
    /** 面板头以下、页脚以上的那一块：四态都叠在这里，同一时刻只显示一种。 */
    private final AiHistoryStateView stateView;
    private final AiHistoryDialogs dialogs;
    private final TextView clearRow;

    private YoSheet sheet;
    private boolean released;

    private AiHistorySheet(@NonNull AppCompatActivity activity,
                           @NonNull AiHistoryViewModel viewModel) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.theme = AiTheme.of(activity);

        FrameLayout root = new FrameLayout(activity);
        // 面板在 Sheet 里，不再套窗口壳：这一层只管"头 / 体 / 脚"三段。
        // 底部安全区（导航条/手势）由 YoSheet 按 WindowInsets 统一注入（SheetContentInsets +
        // SheetPageHost padding），页面不得再补，否则双份内边距；这里只留 8dp 视觉余量。
        root.setPadding(0, theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_S));

        FrameLayout body = new FrameLayout(activity);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        bodyParams.topMargin = theme.dp(AiTheme.HEADER_HEIGHT);
        bodyParams.bottomMargin = theme.dp(FOOTER_HEIGHT);
        root.addView(body, bodyParams);

        stateView = new AiHistoryStateView(activity, theme, components,
                new AiHistoryStateView.Listener() {
                    @Override
                    public void onRetry() {
                        viewModel.refresh();
                    }

                    @Override
                    public void onStartNew() {
                        startNewChat();
                    }

                    @Override
                    public void onOpen(@NonNull AiHistoryItemUi item) {
                        open(item);
                    }

                    @Override
                    public void onRename(@NonNull AiHistoryItemUi item) {
                        askRename(item);
                    }

                    @Override
                    public void onDelete(@NonNull AiHistoryItemUi item) {
                        confirmDelete(item);
                    }
                });
        body.addView(stateView.view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialogs = new AiHistoryDialogs(activity);

        clearRow = buildClearRow();
        FrameLayout.LayoutParams clearParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(FOOTER_HEIGHT));
        clearParams.gravity = Gravity.BOTTOM;
        root.addView(clearRow, clearParams);
        clearRow.setVisibility(View.GONE);

        root.addView(new AiHistoryHeaderView(activity, theme, this::startNewChat, this::dismiss).view(),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        theme.dp(AiTheme.HEADER_HEIGHT), Gravity.TOP));
        this.content = root;
    }

    /** 收起面板。改名/删除后面板留在原地，只有"选中一条""新对话"和标题行的 × 会走到这里。 */
    public void dismiss() {
        if (sheet != null) {
            sheet.dismiss();
        }
    }

    private void startNewChat() {
        viewModel.newChat();
        dismiss();
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
        viewModel.uiState().observe(activity, ignored -> render());
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
        AiHistoryUiState state = viewModel.uiState().getValue();
        if (state == null) {
            state = AiHistoryUiState.initial();
        }
        clearRow.setVisibility(state.content() ? View.VISIBLE : View.GONE);
        stateView.bind(state);
    }
    // ---- 交互 ----

    private void open(@NonNull AiHistoryItemUi item) {
        viewModel.open(item.id());
        dismiss();
    }

    private void askRename(@NonNull AiHistoryItemUi item) {
        // 没起过名的对话把输入框留空：让用户从头写，而不是先删掉"未命名对话"这几个字。
        String initial = item.title().isRaw() ? item.title().raw() : "";
        AiHistoryRenameDialog.show(activity, initial, typed -> {
            viewModel.rename(item.id(), typed);
        });
    }

    private void confirmDelete(@NonNull AiHistoryItemUi item) {
        dialogs.confirmDelete(() -> {
            viewModel.delete(item.id());
        });
    }

    private void confirmClearAll() {
        dialogs.confirmClearAll(() -> {
            viewModel.clearAll();
        });
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
        stateView.release();
        // 面板关了就不再收状态：ViewModel 可能被调用方留着继续用，没必要让它再把结果画到已收起的树上。
        viewModel.uiState().removeObservers(activity);
        viewModel.events().removeObservers(activity);
        components.close();
        viewModel.close();
        if (sheet != null) {
            sheet.destroy();
            sheet = null;
        }
    }
}
