package com.ggec.earbuds.ui.ai.chat;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.R;
import androidx.annotation.StringRes;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.ui.ai.call.AiCallActivity;
import com.ggec.earbuds.ui.ai.history.AiHistorySheet;
import com.ggec.earbuds.ui.ai.settings.AiSettingsActivity;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.List;

/**
 * AI 标签页：页内标题行、消息列表（或空状态）、底部输入区。
 *
 * <p>这一页不再用 {@code YoPage}：标签栏本身就是窗口级容器，再套一页标题栏是库明确禁止的
 * 窗口壳嵌套，也会让顶部留白和底部避让各补两遍。标题行、顶部留白和底部避让都由这一页自己管
 * （见 {@link AiChatInsets}）。</p>
 *
 * <p>页面不保存对话状态，只把 {@link AiChatViewModel} 的界面状态画出来、把用户操作转交给它。</p>
 */
public final class AiChatPage implements AiMessageActions {

    private static final int MENU_NEW_CHAT = 1;
    private static final int MENU_SETTINGS = 2;

    private static final int ACTION_COPY = 1;
    private static final int ACTION_REGENERATE = 2;
    private static final int ACTION_DELETE = 3;

    private final AppCompatActivity activity;
    private final AiChatViewModel viewModel;
    private final AiTheme theme;
    private final YoComponents components = new YoComponents();

    private final LinearLayout root;

    private final ImageView call;
    private final ImageView more;
    private final ImageView history;
    private final View headerRule;
    private final RecyclerView messages;
    private final AiMessageAdapter adapter;
    private final AiEmptyStateView emptyState;
    private final TextView storageBanner;
    private final YoButton toBottom;
    private final AiComposerView composer;
    private final LinearLayout composerBlock;
    private final AiChatInsets insets;

    private boolean jumpToEnd = true;
    private String firstMessageId = "";
    private int shownCount;

    public AiChatPage(@NonNull AppCompatActivity activity, @NonNull AiChatViewModel viewModel) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.theme = AiTheme.of(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        // ---- 标题行：左边历史，页名，右边通话 / 设置 / 新对话 ----
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(theme.dp(AiTheme.HEADER_HEIGHT));
        header.setPadding(theme.dp(AiTheme.SPACE_XS), theme.dp(AiTheme.SPACE_XS), theme.dp(10), 0);

        // 历史放在标题左边：它是"我在哪条对话里"的入口，位置本身就该在标题之前。
        history = new ImageView(activity);
        history.setImageResource(R.drawable.ic_ai_history);
        history.setColorFilter(theme.text2, PorterDuff.Mode.SRC_IN);
        history.setScaleType(ImageView.ScaleType.FIT_CENTER);
        history.setPadding(theme.dp(10), theme.dp(10), theme.dp(10), theme.dp(10));
        history.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        history.setContentDescription(activity.getString(R.string.ai_history_entry));
        history.setOnClickListener(view -> openHistory());
        header.addView(history, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_title);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_XS));
        header.addView(title, titleParams);

        // 通话入口放在新对话左边：它是这一页唯一"换一种交互方式"的动作，不该被当成次要按钮。
        call = new ImageView(activity);
        call.setImageResource(R.drawable.ic_ai_call_entry);
        call.setColorFilter(theme.brand, PorterDuff.Mode.SRC_IN);
        call.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPad = theme.dp(10);
        call.setPadding(iconPad, iconPad, iconPad, iconPad);
        call.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        call.setContentDescription(activity.getString(R.string.ai_call_entry));
        call.setOnClickListener(view -> openCall());
        header.addView(call, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        // 页头只留两个主操作 + 一个菜单。新对话和设置都是低频动作：
        // 新对话每开一条才用一次，设置配完就不再进来；把它们摊在页头会和"通话"抢注意力
        // （ChatGPT / Claude / Gemini 的页头也都是"一个主操作 + 一个 ⋯"）。
        more = new ImageView(activity);
        more.setImageResource(R.drawable.ic_ai_more);
        more.setColorFilter(theme.text2, PorterDuff.Mode.SRC_IN);
        more.setScaleType(ImageView.ScaleType.FIT_CENTER);
        more.setPadding(iconPad, iconPad, iconPad, iconPad);
        more.setBackground(theme.pressable(AiTheme.TOUCH_TARGET / 2f));
        more.setContentDescription(activity.getString(R.string.ai_more_entry));
        more.setOnClickListener(view -> showMoreMenu());
        header.addView(more, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 消息一旦滚起来，标题和内容之间要有一条线；停在顶部时没有。
        headerRule = new View(activity);
        headerRule.setBackgroundColor(theme.divider);
        headerRule.setAlpha(0f);
        root.addView(headerRule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));

        // ---- 内容区：消息列表和空状态叠在同一个位置，同一时刻只显示一个 ----
        FrameLayout content = new FrameLayout(activity);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        messages = new RecyclerView(activity);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager);
        adapter = new AiMessageAdapter(theme, this);
        messages.setAdapter(adapter);
        messages.setClipToPadding(false);
        messages.setPadding(0, theme.dp(10), 0, theme.dp(AiTheme.SPACE_S));
        messages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(160);
        messages.setItemAnimator(animator);
        content.addView(messages, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        emptyState = new AiEmptyStateView(theme, text -> {
            jumpToEnd = true;
            viewModel.send(text);
        });
        content.addView(emptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        storageBanner = theme.text(12.5f, theme.warning, AiTheme.Weight.MEDIUM);
        storageBanner.setText(R.string.ai_storage_failed);
        storageBanner.setGravity(Gravity.CENTER);
        storageBanner.setBackgroundColor(theme.warningSoft);
        storageBanner.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S));
        storageBanner.setVisibility(View.GONE);
        content.addView(storageBanner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        toBottom = YoButton.create(activity)
                .setIcon(R.drawable.ic_ai_arrow_down)
                .setShape(YoButton.Shape.CIRCULAR)
                .setSize(YoButton.Size.SMALL)
                .build();
        toBottom.getView().setContentDescription(activity.getString(R.string.ai_scroll_to_bottom));
        toBottom.getView().setAlpha(0f);
        toBottom.getView().setVisibility(View.GONE);
        FrameLayout.LayoutParams toBottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        toBottomParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        content.addView(toBottom.getView(), toBottomParams);

        // ---- 输入区 ----
        composer = new AiComposerView(theme);
        composerBlock = new LinearLayout(activity);
        composerBlock.setOrientation(LinearLayout.VERTICAL);
        composerBlock.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(composerBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        insets = new AiChatInsets(theme, composerBlock);
        insets.install(root);

        components.own(toBottom::destroy);

        wireInput();
        wireScrolling();
        observe();
    }

    /** 交给标签容器放进去的根视图。 */
    @NonNull
    public View view() {
        return root;
    }

    /** 悬浮标签栏占据的底部高度；输入区要让开它，不然会被盖住。 */
    public void setBottomInset(int px) {
        insets.setTabsInset(px);
    }

    public void destroy() {
        components.close();
    }

    // ---- 装配 ----

    private void wireInput() {
        composer.setDraft(viewModel.draft());
        composer.setListener(new AiComposerView.Listener() {
            @Override
            public void onSend(@NonNull String text) {
                jumpToEnd = true;
                viewModel.send(text);
            }

            @Override
            public void onStop() {
                viewModel.stop();
            }

            @Override
            public void onDraftChanged(@NonNull String draft) {
                viewModel.setDraft(draft);
            }
        });
    }

    private void wireScrolling() {
        messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateToBottom();
            }
        });
        toBottom.setOnClickListener(view -> scrollToEnd());
    }

    private void observe() {
        viewModel.uiState().observe(activity, this::render);
        viewModel.events().observe(activity, event -> {
            String text = event.argument() == null
                    ? activity.getString(event.message())
                    : activity.getString(event.message(), event.argument());
            YoToast.show(activity, text);
        });
    }

    // ---- 渲染 ----

    /**
     * 进通话页。平台 id 为空的兜底在这里就返回：按钮只在有可用模型时可见，
     * 走到这行说明状态刚刚被清掉，不值得为它弹一句错误。
     */
    private void openCall() {
        AiSelection selection = viewModel.voiceSelection();
        if (selection == null) {
            return;
        }
        activity.startActivity(AiCallActivity.intent(activity, selection));
    }

    /**
     * 页头菜单：新对话与设置。
     *
     * <p>为什么用菜单而不是两个图标：这两个动作一次会话里各用一次，摊在页头会跟"通话"
     * 抢同一个视觉权重；而"通话"是这一页唯一的差异化入口。菜单还解决了一个老问题——
     * 空对话时"新对话"要藏起来（没有对象可清），藏在菜单里就不用为它做可见性特例。</p>
     */
    private void showMoreMenu() {
        YoMenu.create(activity)
                .addItem(MENU_NEW_CHAT, getString(R.string.ai_menu_new_chat),
                        R.drawable.ic_ai_new_chat)
                .addItem(MENU_SETTINGS, getString(R.string.ai_settings_entry),
                        R.drawable.ic_ai_settings)
                .setAnchorView(more)
                .setOnItemClickListener(id -> {
                    if (id == MENU_NEW_CHAT) {
                        viewModel.newConversation();
                    } else if (id == MENU_SETTINGS) {
                        activity.startActivity(AiSettingsActivity.intent(activity));
                    }
                })
                .build()
                .show();
    }

    private String getString(@StringRes int res) {
        return activity.getString(res);
    }

    /**
     * 打开历史面板。列表、改名、删除、清空都由面板自己转交 {@code AiChatSession}，
     * 页面不再重复调一遍会话方法——那样同一次删除会走两遍。
     */
    private void openHistory() {        AiGraph graph = AiGraph.get(activity);
        AiHistorySheet.show(activity, graph.chatSession(), graph.listConversations(), null);
    }

    private void render(@NonNull AiChatUiState state) {
        List<AiMessageUi> list = state.messages();
        boolean showEmpty = state.welcome() != AiChatUiState.Welcome.NONE;
        emptyState.setMode(state.welcome(),
                viewModel.voiceSelection() != null);
        setVisible(emptyState, showEmpty);
        setVisible(messages, !showEmpty);
        setVisible(storageBanner, state.storageFailed());
        // 通话要能真的拨出去才有意义：文本没就绪、或语音模式还没配好实时模型时都不摆入口，
        // 而不是点进去看一句失败，或点了完全没反应。
        setVisible(call, state.composer().enabled() && viewModel.voiceSelection() != null);
        composer.setState(state.composer().enabled(), state.composer().generating(), state.composer().modelChip());

        String firstId = list.isEmpty() ? "" : list.get(0).id();
        boolean conversationChanged = !firstId.equals(firstMessageId) || list.size() < shownCount;
        boolean appended = list.size() > shownCount;
        firstMessageId = firstId;
        shownCount = list.size();
        boolean follow = jumpToEnd || conversationChanged || (appended && isNearEnd());
        jumpToEnd = false;
        adapter.submitList(list, () -> {
            if (follow && !list.isEmpty()) {
                messages.scrollToPosition(list.size() - 1);
            }
            updateToBottom();
        });
        composer.setDraft(viewModel.draft());
    }

    /**
     * 只改可见性，不改布局参数：返回箭头那种叠在内容上的东西用 GONE，
     * 标题行里的按钮用 INVISIBLE，免得一有对话标题就往右跳。
     */
    private void setVisible(@NonNull View view, boolean visible) {
        int target = visible ? View.VISIBLE
                : (view == call ? View.INVISIBLE : View.GONE);
        if (view.getVisibility() == target) {
            return;
        }
        if (visible) {
            view.animate().cancel();
            view.setVisibility(View.VISIBLE);
            if (view.getAlpha() < 1f) {
                view.animate().alpha(1f).setDuration(140).start();
            }
        } else if (target == View.INVISIBLE) {
            view.setVisibility(View.INVISIBLE);
        } else {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
        }
    }

    private boolean isNearEnd() {
        return !messages.canScrollVertically(1);
    }

    private void updateToBottom() {
        boolean show = messages.canScrollVertically(1) && adapter.getItemCount() > 0;
        View button = toBottom.getView();
        if (show && button.getVisibility() != View.VISIBLE) {
            button.setVisibility(View.VISIBLE);
            button.animate().alpha(1f).setDuration(140).start();
        } else if (!show && button.getVisibility() == View.VISIBLE) {
            button.animate().alpha(0f).setDuration(120)
                    .withEndAction(() -> button.setVisibility(View.GONE)).start();
        }
        float rule = messages.canScrollVertically(-1) ? 1f : 0f;
        if (headerRule.getAlpha() != rule) {
            headerRule.animate().alpha(rule).setDuration(160).start();
        }
    }

    private void scrollToEnd() {
        if (adapter.getItemCount() > 0) {
            messages.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // ---- 消息操作 ----

    @Override
    public void onCopy(@NonNull String text) {
        copyToClipboard(text);
        YoToast.show(activity, activity.getString(R.string.ai_copied));
    }

    @Override
    public void onCopyCode(@NonNull String code) {
        copyToClipboard(code);
    }

    @Override
    public void onRegenerate() {
        viewModel.regenerate();
    }

    @Override
    public void onContinue() {
        jumpToEnd = true;
        viewModel.send(activity.getString(R.string.ai_continue_prompt));
    }

    /** 删掉一条回复无法找回，旁边的图标又很容易误触，所以先确认。 */
    @Override
    public void onDelete(@NonNull String messageId) {
        AiDialogs.confirm(activity, activity.getString(R.string.ai_message_delete_title),
                activity.getString(R.string.ai_history_delete_message), activity.getString(R.string.ai_delete), true,
                () -> viewModel.deleteMessage(messageId));
    }

    @Override
    public void onToggleReasoning(@NonNull String messageId) {
        viewModel.toggleReasoning(messageId);
    }

    @Override
    public void onLink(@NonNull String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // 没有能打开链接的应用，什么都不做。
        }
    }

    @Override
    public void onLongPress(@NonNull View anchor, @NonNull AiMessageUi message) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(ACTION_COPY, activity.getString(R.string.ai_copy), R.drawable.ic_ai_copy)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_LEFT)
                .setOnItemClickListener(id -> {
                    if (id == ACTION_COPY) {
                        onCopy(message.text());
                    } else if (id == ACTION_REGENERATE) {
                        onRegenerate();
                    } else if (id == ACTION_DELETE) {
                        onDelete(message.id());
                    }
                })
                .build();
        if (message.canRegenerate()) {
            menu.addItem(ACTION_REGENERATE, activity.getString(R.string.ai_action_regenerate), R.drawable.ic_ai_refresh);
        }
        if (message.canDelete()) {
            menu.addDestructiveItem(ACTION_DELETE, activity.getString(R.string.ai_delete));
        }
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.ai_assistant_name), text));
        }
    }
}
