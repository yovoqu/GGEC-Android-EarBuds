package com.ggec.earbuds.ui.ai.chat;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoButton;

import java.util.List;

/**
 * 对话内容区：消息列表、空态、存储失败条和回到底部按钮。
 * 只负责展示与滚动表现，不调用会话、不读设置。
 */
final class AiChatContentView extends FrameLayout {

    interface Listener {
        void onSuggestion(@NonNull String text);
        void onScrollChanged(boolean canScrollUp);
    }

    private final AppCompatActivity activity;
    private final AiTheme theme;
    private final RecyclerView messages;
    private final AiMessageAdapter adapter;
    private final AiEmptyStateView emptyState;
    private final TextView storageBanner;
    private final YoButton toBottom;

    private boolean jumpToEnd = true;
    private String firstMessageId = "";
    private int shownCount;

    AiChatContentView(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                      @NonNull YoComponents components, @NonNull AiMessageActions actions,
                      @NonNull Listener listener) {
        super(activity);
        this.activity = activity;
        this.theme = theme;

        messages = new RecyclerView(activity);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setStackFromEnd(true);
        messages.setLayoutManager(layoutManager);
        adapter = new AiMessageAdapter(theme, actions);
        messages.setAdapter(adapter);
        messages.setClipToPadding(false);
        messages.setPadding(0, theme.dp(10), 0, theme.dp(AiTheme.SPACE_S));
        messages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        messages.setItemAnimator(null);
        messages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateToBottom();
                listener.onScrollChanged(messages.canScrollVertically(-1));
            }
        });
        addView(messages, matchParent());

        emptyState = new AiEmptyStateView(theme, text -> {
            jumpToEnd = true;
            listener.onSuggestion(text);
        });
        addView(emptyState, matchParent());

        storageBanner = theme.text(12.5f, theme.warning, AiTheme.Weight.MEDIUM);
        storageBanner.setText(R.string.ai_storage_failed);
        storageBanner.setGravity(Gravity.CENTER);
        storageBanner.setBackgroundColor(theme.warningSoft);
        storageBanner.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S));
        storageBanner.setVisibility(View.GONE);
        addView(storageBanner, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        toBottom = YoButton.create(activity)
                .setIcon(R.drawable.ic_ai_arrow_down)
                .setShape(YoButton.Shape.CIRCULAR)
                .setSize(YoButton.Size.SMALL)
                .build();
        toBottom.getView().setContentDescription(activity.getString(R.string.ai_scroll_to_bottom));
        toBottom.getView().setAlpha(0f);
        toBottom.getView().setVisibility(View.GONE);
        LayoutParams toBottomParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        toBottomParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        addView(toBottom.getView(), toBottomParams);
        toBottom.setOnClickListener(view -> scrollToEnd());
        components.own(toBottom::destroy);
    }

    void render(@NonNull AiChatUiState state) {
        List<AiMessageUi> list = state.messages();
        boolean showEmpty = state.welcome() != AiChatUiState.Welcome.NONE;
        emptyState.setMode(state.welcome(), state.voiceReady());
        setVisible(emptyState, showEmpty);
        setVisible(messages, !showEmpty);
        setVisible(storageBanner, state.storageFailed());

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
    }

    void jumpToEnd() {
        jumpToEnd = true;
    }

    void scrollToEnd() {
        if (adapter.getItemCount() > 0) {
            messages.scrollToPosition(adapter.getItemCount() - 1);
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
    }

    private void setVisible(@NonNull View view, boolean visible) {
        int target = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == target) {
            return;
        }
        if (visible) {
            view.animate().cancel();
            view.setVisibility(View.VISIBLE);
            if (view.getAlpha() < 1f) {
                view.animate().alpha(1f).setDuration(140).start();
            }
        } else {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setVisibility(View.GONE);
        }
    }

    @NonNull
    private static LayoutParams matchParent() {
        return new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
