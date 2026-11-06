package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话异常中断恢复面板 (AiCallErrorView)：
 * 全面基于 {@link AiTheme} 语义化调色系统构建，严禁硬编码，自适应深浅色模式。
 */
public final class AiCallErrorView extends LinearLayout {

    public interface Listener {
        void onErrorAction(@NonNull AiCallUiState.Action action);
    }

    private final AiTheme theme;
    private final TextView reasonText;
    private final TextView actionButton;
    private final Listener listener;

    private AiCallUiState.Action currentAction = AiCallUiState.Action.NONE;

    public AiCallErrorView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;

        int padH = theme.dp(AiTheme.PAGE_INSET);
        int padV = theme.dp(AiTheme.SPACE_L);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setPadding(padH, padV, padH, padV);

        // 报警恢复卡片底座
        GradientDrawable bg = theme.roundedStroke(theme.card, AiTheme.RADIUS_FIELD, theme.alertSoft, 1.5f);
        setBackground(bg);

        // 警告图标
        ImageView icon = theme.icon(R.drawable.ic_ai_warning, theme.alert);
        int iconSize = theme.dp(32f);
        addView(icon, new LayoutParams(iconSize, iconSize));

        // 错误原因描述
        reasonText = theme.text(AiTheme.TEXT_BODY, theme.text2);
        reasonText.setGravity(Gravity.CENTER);
        LayoutParams reasonParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = theme.dp(AiTheme.SPACE_M);
        reasonParams.bottomMargin = theme.dp(AiTheme.SPACE_L);
        addView(reasonText, reasonParams);

        // 操作恢复按钮
        actionButton = theme.text(AiTheme.TEXT_BODY, Color.WHITE, AiTheme.Weight.MEDIUM);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(theme.dp(AiTheme.SPACE_XL), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_XL), theme.dp(AiTheme.SPACE_M));

        GradientDrawable btnBg = theme.rounded(theme.alert, 20f);
        RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), btnBg, null);
        actionButton.setBackground(ripple);
        actionButton.setOnClickListener(v -> {
            if (currentAction != AiCallUiState.Action.NONE) {
                listener.onErrorAction(currentAction);
            }
        });

        LayoutParams btnParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addView(actionButton, btnParams);

        setVisibility(GONE);
    }

    public void render(@Nullable @StringRes Integer reasonRes, @NonNull AiCallUiState.Action action) {
        this.currentAction = action;
        if (action == AiCallUiState.Action.NONE && reasonRes == null) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);
        if (reasonRes != null) {
            reasonText.setText(reasonRes);
        } else {
            reasonText.setText(R.string.ai_failure_network);
        }

        if (action == AiCallUiState.Action.RETRY) {
            actionButton.setText(R.string.ai_call_retry);
        } else if (action == AiCallUiState.Action.SETTINGS) {
            actionButton.setText(R.string.ai_call_failed_action);
        } else {
            actionButton.setText(R.string.ai_call_retry);
        }
    }
}
