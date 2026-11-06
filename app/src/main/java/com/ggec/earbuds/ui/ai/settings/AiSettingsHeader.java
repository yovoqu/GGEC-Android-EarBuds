package com.ggec.earbuds.ui.ai.settings;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/**
 * 设置类页面共用的页头：返回按键 + 页名 20sp + 底部 1px 分隔线。
 * <p>统一采用 {@link AiHeaderActionButton} 圆形拟物微质感按键体系，自适应深浅色模式。</p>
 */
final class AiSettingsHeader {

    private final LinearLayout root;
    private final TextView title;
    private final TextView status;

    AiSettingsHeader(@NonNull AppCompatActivity activity, @NonNull AiTheme theme, @StringRes int titleRes,
                     @NonNull Runnable onBack) {
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(AiTheme.HEADER_HEIGHT));
        row.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_XS), theme.dp(AiTheme.PAGE_INSET), 0);

        AiHeaderActionButton back = new AiHeaderActionButton(activity, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.ai_settings_back)
                .onClick(view -> onBack.run());
        row.addView(back, AiHeaderActionButton.createLinearLayoutParams(theme));

        title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(titleRes);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_M));
        row.addView(title, titleParams);

        status = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.END);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMarginStart(theme.dp(AiTheme.SPACE_S));
        row.addView(status, statusParams);

        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rule = new View(activity);
        rule.setBackgroundColor(theme.divider);
        root.addView(rule, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
    }

    @NonNull
    View view() {
        return root;
    }

    void setTitle(@NonNull CharSequence text) {
        if (!text.equals(title.getText())) {
            title.setText(text);
        }
    }

    /**
     * 页头右侧状态：传 null 或空串即清空。文本高度小于 56dp 行高，显隐不改变页头尺寸。
     */
    void setStatus(@Nullable CharSequence text, @ColorInt int color) {
        if (text == null || text.length() == 0) {
            if (status.getVisibility() != View.GONE) {
                status.setText("");
                status.setVisibility(View.GONE);
            }
            return;
        }
        status.setTextColor(color);
        status.setText(text);
        if (status.getVisibility() != View.VISIBLE) {
            status.setVisibility(View.VISIBLE);
        }
    }
}
