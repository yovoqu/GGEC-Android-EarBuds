package com.ggec.earbuds.ui.ai.call;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 通话页舞台：光晕、状态、失败原因和唯一出口。 */
final class AiCallStageView extends LinearLayout {

    interface Listener {
        void onInterrupt();
        void onAction(@NonNull AiCallUiState.Action action);
    }

    private static final float ORB_SIZE = 240f;
    private static final float ACTION_HEIGHT = 48f;
    private static final float ACTION_MIN_WIDTH = 120f;

    private final AiTheme theme;
    private final Listener listener;
    private final VoiceOrbView orb;
    private final TextView statusTitle;
    private final TextView statusReason;
    private final TextView actionButton;
    private AiCallUiState.Action action = AiCallUiState.Action.NONE;
    private boolean interruptibleShown;

    AiCallStageView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);

        orb = new VoiceOrbView(theme.context, theme);
        orb.setOnClickListener(view -> listener.onInterrupt());
        orb.setClickable(false);
        addView(orb, new LayoutParams(theme.dp(ORB_SIZE), theme.dp(ORB_SIZE)));

        statusTitle = theme.text(AiTheme.TEXT_BODY, theme.text1);
        statusTitle.setText(R.string.ai_call_phase_connecting);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setMaxLines(2);
        statusTitle.setLineSpacing(theme.dpf(3), 1f);
        addView(statusTitle, fullWidth(theme.dp(AiTheme.SPACE_XL)));

        statusReason = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        statusReason.setGravity(Gravity.CENTER);
        statusReason.setMaxLines(2);
        statusReason.setLineSpacing(theme.dpf(2), 1f);
        statusReason.setVisibility(GONE);
        addView(statusReason, fullWidth(theme.dp(AiTheme.SPACE_S)));

        actionButton = theme.text(AiTheme.TEXT_BODY, theme.onBrand, AiTheme.Weight.MEDIUM);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setMinWidth(theme.dp(ACTION_MIN_WIDTH));
        actionButton.setPadding(theme.dp(AiTheme.SPACE_XL), 0,
                theme.dp(AiTheme.SPACE_XL), 0);
        actionButton.setBackground(theme.pressable(
                theme.rounded(theme.brand, AiTheme.RADIUS_FIELD), AiTheme.RADIUS_FIELD));
        actionButton.setVisibility(GONE);
        actionButton.setOnClickListener(view -> listener.onAction(action));
        LayoutParams actionParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(ACTION_HEIGHT));
        actionParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        addView(actionButton, actionParams);
    }

    void render(@NonNull AiCallUiState state) {
        orb.setPhase(phaseOf(state.phase()));
        orb.setLevel(state.level());
        showStatus(getContext().getString(state.title()),
                state.reason() == null ? null : getContext().getString(state.reason()));
        showAction(state.action());
        if (state.interruptible() != interruptibleShown) {
            interruptibleShown = state.interruptible();
            orb.setClickable(interruptibleShown);
            orb.setContentDescription(interruptibleShown
                    ? getContext().getString(R.string.ai_call_interrupt) : null);
        }
    }

    private void showStatus(@NonNull CharSequence title, @Nullable CharSequence reason) {
        if (!TextUtils.equals(statusTitle.getText(), title)) {
            statusTitle.animate().cancel();
            statusTitle.animate().alpha(0f).setDuration(90).withEndAction(() -> {
                statusTitle.setText(title);
                statusTitle.animate().alpha(1f).setDuration(160).start();
            }).start();
        }
        if (reason == null) {
            statusReason.setVisibility(GONE);
            return;
        }
        statusReason.setText(reason);
        statusReason.setVisibility(VISIBLE);
    }

    private void showAction(@NonNull AiCallUiState.Action next) {
        if (action == next) {
            return;
        }
        action = next;
        if (next == AiCallUiState.Action.NONE) {
            actionButton.setVisibility(GONE);
            return;
        }
        actionButton.setText(next == AiCallUiState.Action.RETRY
                ? R.string.ai_call_retry : R.string.ai_call_failed_action);
        actionButton.setVisibility(VISIBLE);
    }

    @NonNull
    private LayoutParams fullWidth(int topMarginPx) {
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        params.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        params.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        return params;
    }

    @NonNull
    private static VoiceOrbView.Phase phaseOf(@NonNull AiCallUiState.Phase state) {
        return switch (state) {
            case CONNECTING -> VoiceOrbView.Phase.CONNECTING;
            case LISTENING -> VoiceOrbView.Phase.LISTENING;
            case THINKING -> VoiceOrbView.Phase.THINKING;
            case SPEAKING -> VoiceOrbView.Phase.SPEAKING;
            case MUTED -> VoiceOrbView.Phase.MUTED;
            case OFFLINE -> VoiceOrbView.Phase.OFFLINE;
        };
    }
}
