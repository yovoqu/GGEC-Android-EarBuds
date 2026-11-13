package com.ggec.earbuds.ui.ai.chat;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiDialogs;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.call.AiCallActivity;
import com.ggec.earbuds.ui.ai.settings.AiSettingsActivity;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.feedback.YoToast;

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

    private final AppCompatActivity activity;
    private final AiChatViewModel viewModel;
    private final AiTheme theme;
    private final YoComponents components = new YoComponents();

    private final LinearLayout root;
    private final AiChatHeaderView header;
    private final AiChatContentView content;
    private final AiComposerView composer;
    private final LinearLayout composerBlock;
    private final AiChatInsets insets;
    private final AiChatMenus menus;
    private final Runnable onHistory;

    public AiChatPage(@NonNull AppCompatActivity activity, @NonNull AiChatViewModel viewModel,
                      @NonNull Runnable onHistory) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.onHistory = onHistory;
        this.theme = AiTheme.of(activity);

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        menus = new AiChatMenus(activity, components, new AiChatMenus.Listener() {
            @Override
            public void onNewConversation() {
                viewModel.newConversation();
            }

            @Override
            public void onSettings() {
                activity.startActivity(AiSettingsActivity.intent(activity));
            }

            @Override
            public void onCopy(@NonNull String text) {
                onCopy(text);
            }

            @Override
            public void onRegenerate() {
                onRegenerate();
            }

            @Override
            public void onDelete(@NonNull String messageId) {
                onDelete(messageId);
            }
        });
        header = new AiChatHeaderView(activity, theme, new AiChatHeaderView.Listener() {
            @Override
            public void onHistory() {
                onHistory.run();
            }

            @Override
            public void onCall() {
                openCall();
            }

            @Override
            public void onMore() {
                menus.showPageMenu(header.moreAnchor());
            }
        });
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new AiChatContentView(activity, theme, components, this,
                new AiChatContentView.Listener() {
                    @Override
                    public void onSuggestion(@NonNull String text) {
                        viewModel.send(text);
                    }

                    @Override
                    public void onScrollChanged(boolean canScrollUp) {
                        header.setRuleVisible(canScrollUp);
                    }
                });
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

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

        wireInput();
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
                content.jumpToEnd();
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
     * 进通话页。入口是常驻的：能不能拨通由通话页自己判定，
     * 没配好语音模型时它会把失败原因和「去设置」摆出来，而不是让用户在对话页找不到入口。
     */
    private void openCall() {
        activity.startActivity(AiCallActivity.intent(activity));
    }

    private void render(@NonNull AiChatUiState state) {
        content.render(state);
        composer.setState(state.composer());
        composer.setDraft(viewModel.draft());
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
        content.jumpToEnd();
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
        menus.showMessageMenu(anchor, message);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.ai_assistant_name), text));
        }
    }
}
