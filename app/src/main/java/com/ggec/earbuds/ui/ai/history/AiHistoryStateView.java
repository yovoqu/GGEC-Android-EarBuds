package com.ggec.earbuds.ui.ai.history;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoButton;

import java.util.List;

/** 历史面板的互斥四态：加载、内容、空、失败。 */
final class AiHistoryStateView {

    interface Listener {
        void onRetry();
        void onStartNew();
        void onOpen(@NonNull AiHistoryItemUi item);
        void onRename(@NonNull AiHistoryItemUi item);
        void onDelete(@NonNull AiHistoryItemUi item);
    }

    private final AppCompatActivity activity;
    private final AiTheme theme;
    private final Listener listener;
    private final FrameLayout view;
    private final NestedScrollView scroll;
    private final AiHistoryListContent list;
    private final TextView loading;
    private final LinearLayout empty;
    private final LinearLayout failed;

    AiHistoryStateView(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                       @NonNull YoComponents components, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        view = new FrameLayout(activity);

        scroll = new NestedScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setPadding(0, 0, 0, theme.dp(AiTheme.SPACE_S));
        list = new AiHistoryListContent(activity, theme, components,
                new AiHistoryListContent.Listener() {
                    @Override
                    public void onOpen(@NonNull AiHistoryItemUi item) {
                        listener.onOpen(item);
                    }

                    @Override
                    public void onRename(@NonNull AiHistoryItemUi item) {
                        listener.onRename(item);
                    }

                    @Override
                    public void onDelete(@NonNull AiHistoryItemUi item) {
                        listener.onDelete(item);
                    }
                });
        scroll.addView(list.view(), new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.addView(scroll, matchParent());
        scroll.setVisibility(View.GONE);

        loading = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        loading.setText(R.string.ai_history_loading);
        loading.setGravity(Gravity.CENTER);
        view.addView(loading, centered());
        loading.setVisibility(View.GONE);

        empty = state(R.string.ai_history_empty_title, R.string.ai_history_empty_action,
                ignored -> listener.onStartNew(), components);
        view.addView(empty, centered());
        empty.setVisibility(View.GONE);

        failed = state(R.string.ai_history_load_failed_title, R.string.ai_history_load_failed_action,
                ignored -> listener.onRetry(), components);
        view.addView(failed, centered());
        failed.setVisibility(View.GONE);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiHistoryUiState state) {
        loading.setVisibility(state.loading() ? View.VISIBLE : View.GONE);
        scroll.setVisibility(state.content() ? View.VISIBLE : View.GONE);
        empty.setVisibility(state.empty() ? View.VISIBLE : View.GONE);
        failed.setVisibility(state.failed() && !state.content() ? View.VISIBLE : View.GONE);
        if (state.content()) {
            list.render(state.items());
        }
    }

    void release() {
        list.release();
    }

    @NonNull
    private LinearLayout state(@StringRes int message, @StringRes int action,
                               @NonNull View.OnClickListener onClick,
                               @NonNull YoComponents components) {
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(AiTheme.SPACE_S);
        block.addView(button.getView(), params);
        return block;
    }

    @NonNull
    private static FrameLayout.LayoutParams centered() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
    }

    @NonNull
    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
