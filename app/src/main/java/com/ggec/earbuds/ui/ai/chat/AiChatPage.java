package com.ggec.earbuds.ui.ai.chat;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoPage;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.List;

/**
 * AI 标签页：标题栏、消息列表（或欢迎引导）、底部输入区。
 * 页面不保存对话状态，只把 {@link AiChatViewModel} 的界面状态画出来、把用户操作转交给它。
 *
 * <p>AI 设置页已经整个删掉：模型标签、引导卡片和禁用态点击都直接进平台页，
 * 这一页负责聊天，平台页负责密钥、模型和测速。</p>
 */
public final class AiChatPage implements AiMessageActions {

    private static final int MENU_NEW_CHAT = 1;
    private static final int ACTION_COPY = 1;
    private static final int ACTION_REGENERATE = 2;
    private static final int ACTION_DELETE = 3;

    private final AppCompatActivity activity;
    private final AiChatViewModel viewModel;
    private final AiTheme theme;
    private final YoComponents components = new YoComponents();
    private final YoPage page;
    private final RecyclerView messages;
    private final AiMessageAdapter adapter;
    private final AiWelcomeView welcome;
    private final TextView storageBanner;
    private final YoButton toBottom;
    private final AiComposerView composer;
    private final AiChatBottomInset bottomInset;

    private boolean jumpToEnd = true;
    private String firstMessageId = "";
    private int shownCount;

    public AiChatPage(@NonNull AppCompatActivity activity, @NonNull AiChatViewModel viewModel) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.theme = AiTheme.of(activity);

        messages = new RecyclerView(activity);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager);
        adapter = new AiMessageAdapter(theme, this);
        messages.setAdapter(adapter);
        messages.setClipToPadding(false);
        messages.setPadding(0, theme.dp(4), 0, theme.dp(8));
        messages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setSupportsChangeAnimations(false);
        animator.setAddDuration(160);
        messages.setItemAnimator(animator);

        welcome = new AiWelcomeView(theme, new AiWelcomeView.Listener() {
            @Override
            public void onSuggestion(@NonNull String text) {
                jumpToEnd = true;
                viewModel.send(text);
            }
        });
        storageBanner = theme.text(12.5f, theme.warning, AiTheme.Weight.MEDIUM);
        storageBanner.setText(R.string.ai_storage_failed);
        storageBanner.setGravity(Gravity.CENTER);
        storageBanner.setBackgroundColor(theme.warningSoft);
        storageBanner.setPadding(theme.dp(16), theme.dp(8), theme.dp(16), theme.dp(8));
        storageBanner.setVisibility(View.GONE);

        toBottom = YoButton.create(activity)
                .setIcon(R.drawable.ic_ai_arrow_down)
                .setShape(YoButton.Shape.CIRCULAR)
                .setSize(YoButton.Size.SMALL)
                .build();
        toBottom.getView().setContentDescription(activity.getString(R.string.ai_scroll_to_bottom));
        toBottom.getView().setAlpha(0f);
        toBottom.getView().setVisibility(View.GONE);

        FrameLayout listArea = new FrameLayout(activity);
        listArea.addView(messages, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listArea.addView(welcome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listArea.addView(storageBanner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        FrameLayout.LayoutParams toBottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        toBottomParams.bottomMargin = theme.dp(8);
        listArea.addView(toBottom.getView(), toBottomParams);

        composer = new AiComposerView(theme);
        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(theme.page);
        column.addView(listArea, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page = YoPage.create(activity)
                .setTitle(activity.getString(R.string.ai_title))
                .setSubtitle(activity.getString(R.string.ai_subtitle_no_model))
                .setTitleMode(YoPage.TitleMode.MINI)
                .setLevel(YoPage.Level.PRIMARY)
                .setMenuMaxCount(1)
                .setMenuItems(List.of(
                        new YoPage.MenuItem(MENU_NEW_CHAT, activity.getString(R.string.ai_menu_new_chat),
                                R.drawable.ic_ai_new_chat, viewModel::newConversation)))
                .setContent(column)
                .build();

        bottomInset = new AiChatBottomInset(theme, bottomBar);
        bottomInset.install(column);
        components.own(page::destroy);
        components.own(toBottom::destroy);
        components.own(welcome::destroy);

        wireInput();
        wireScrolling();
        observe();
    }

    /** 交给标签容器放进去的根视图。 */
    @NonNull
    public View view() {
        return page.getView();
    }

    /** 悬浮标签栏占据的底部高度；输入区要让开它，不然会被盖住。 */
    public void setBottomInset(int px) {
        bottomInset.setTabsInset(px);
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

    private void render(@NonNull AiChatUiState state) {
        page.setSubtitle(state.subtitle().isEmpty()
                ? activity.getString(R.string.ai_subtitle_no_model) : state.subtitle());
        boolean showWelcome = state.welcome() != AiChatUiState.Welcome.NONE;
        welcome.setMode(state.welcome());
        welcome.setVisibility(showWelcome ? View.VISIBLE : View.GONE);
        messages.setVisibility(showWelcome ? View.INVISIBLE : View.VISIBLE);
        storageBanner.setVisibility(state.storageFailed() ? View.VISIBLE : View.GONE);
        composer.setState(state.composer().enabled(), state.composer().generating(), state.composer().modelChip());

        List<AiMessageUi> list = state.messages();
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
