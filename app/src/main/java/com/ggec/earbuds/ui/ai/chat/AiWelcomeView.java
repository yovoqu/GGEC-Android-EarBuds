package com.ggec.earbuds.ui.ai.chat;

import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.containers.YoCard;
import com.yohu.youi.api.controls.YoButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 还没有消息时的对话页：一句说明加几个可以直接点的建议，或者一张引导卡片。
 * 组件都用 YoUI 的默认样式，只排位置，不另外定制外观。
 */
public final class AiWelcomeView extends ScrollView {

    public interface Listener {
        void onSuggestion(@NonNull String text);

        void onOpenSettings();

        void onPickModel();
    }

    private final AiTheme theme;
    private final LinearLayout column;
    private final TextView heading;
    private final TextView message;
    private final GridLayout suggestions;
    private final List<YoButton> suggestionButtons = new ArrayList<>();
    private final View setupCard;
    private final LinearLayout setupContent;
    private final YoCard setupHost;
    private final TextView setupBody;
    private final TextView note;

    @Nullable
    private final Listener listener;
    @Nullable
    private YoButton setupButton;
    @Nullable
    private AiChatUiState.Welcome mode;

    public AiWelcomeView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        column = new LinearLayout(theme.context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_XL),
                theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_XL));
        addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        heading = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        column.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        message = theme.text(14, theme.text2);
        message.setLineSpacing(theme.dpf(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = theme.dp(AiTheme.SPACE_S);
        column.addView(message, messageParams);

        suggestions = new GridLayout(theme.context);
        suggestions.setColumnCount(2);
        suggestions.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        suggestions.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        column.addView(suggestions, gridParams);

        setupContent = new LinearLayout(theme.context);
        setupContent.setOrientation(LinearLayout.VERTICAL);
        setupHost = YoCard.create(theme.context).setContentView(setupContent).build();
        setupCard = setupHost.getView();
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        column.addView(setupCard, cardParams);

        setupBody = theme.text(14, theme.text2);
        setupBody.setLineSpacing(theme.dpf(4), 1f);

        note = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = theme.dp(AiTheme.SPACE_M);
        column.addView(note, noteParams);
    }

    public void setMode(@NonNull AiChatUiState.Welcome next) {
        if (next == mode) {
            return;
        }
        mode = next;
        for (YoButton button : suggestionButtons) {
            button.destroy();
        }
        suggestionButtons.clear();
        suggestions.removeAllViews();
        switch (next) {
            case SUGGESTIONS -> {
                suggestions.setVisibility(VISIBLE);
                setupCard.setVisibility(GONE);
                note.setVisibility(GONE);
                heading.setText(R.string.ai_welcome_title);
                message.setText(R.string.ai_welcome_subtitle);
                message.setVisibility(VISIBLE);
                addSuggestion(R.drawable.ic_ai_headphones, R.string.ai_suggest_connect);
                addSuggestion(R.drawable.ic_ai_battery, R.string.ai_suggest_battery);
                addSuggestion(R.drawable.ic_ai_sound, R.string.ai_suggest_anc);
                addSuggestion(R.drawable.ic_ai_text, R.string.ai_suggest_manual);
            }
            case NEEDS_PLATFORM -> {
                showSetup(R.string.ai_setup_title, R.string.ai_setup_message, R.string.ai_setup_action,
                        () -> {
                            if (listener != null) {
                                listener.onOpenSettings();
                            }
                        });
                note.setText(R.string.ai_privacy_hint);
                note.setVisibility(VISIBLE);
            }
            case NEEDS_MODEL -> {
                showSetup(R.string.ai_pick_model_title, R.string.ai_pick_model_message,
                        R.string.ai_pick_model_action, () -> {
                            if (listener != null) {
                                listener.onPickModel();
                            }
                        });
                note.setVisibility(GONE);
            }
            default -> {
            }
        }
    }

    private void showSetup(@StringRes int title, @StringRes int body, @StringRes int action,
                           @NonNull Runnable onAction) {
        suggestions.setVisibility(GONE);
        setupCard.setVisibility(VISIBLE);
        message.setVisibility(GONE);
        heading.setText(title);
        setupBody.setText(body);
        setupContent.removeAllViews();
        setupContent.addView(setupBody, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (setupButton != null) {
            setupButton.destroy();
        }
        setupButton = YoButton.create(theme.context)
                .setText(theme.context.getString(action))
                .setOnClickListener(view -> onAction.run())
                .build();
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = theme.dp(AiTheme.SPACE_L);
        setupContent.addView(setupButton.getView(), buttonParams);
    }

    private void addSuggestion(@DrawableRes int icon, @StringRes int text) {
        YoButton chip = YoButton.create(theme.context)
                .setText(theme.context.getString(text))
                .setIcon(icon)
                .setStyle(YoButton.Style.OUTLINED)
                .setSize(YoButton.Size.SMALL)
                .setOnClickListener(view -> {
                    if (listener != null) {
                        listener.onSuggestion(theme.context.getString(text));
                    }
                })
                .build();
        suggestionButtons.add(chip);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1, 1f));
        params.width = 0;
        params.setMargins(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(4));
        suggestions.addView(chip.getView(), params);
    }

    void destroy() {
        for (YoButton button : suggestionButtons) {
            button.destroy();
        }
        suggestionButtons.clear();
        if (setupButton != null) {
            setupButton.destroy();
        }
        setupHost.destroy();
    }
}
