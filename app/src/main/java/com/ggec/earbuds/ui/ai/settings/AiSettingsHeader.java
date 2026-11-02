package com.ggec.earbuds.ui.ai.settings;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 设置类页面共用的页头：返回箭头 + 页名 20sp + 底部 1px 分隔线。
 *
 * <p>与对话页的标题行同一套尺寸（56dp 高、20sp 页名、页面内边距 20），差别只在于这里多一个返回箭头：
 * 设置页是二级页面，没有返回就没有别的出口。不套 {@code YoPage}／{@code YoTabs}，
 * 因为这两个都是窗口级容器，页面里再嵌一层是库明确禁止的。</p>
 *
 * <p>页头右侧预留一个状态位（保存中 / 保存失败）。它固定在 56dp 行里，显隐不改变页头高度，
 * 因此"改一个值"不会把下面的内容推上推下——这是设置页稳定性的第一道闸。</p>
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
        row.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS), theme.dp(AiTheme.SPACE_L), 0);

        ImageView back = theme.iconButton(R.drawable.ic_ai_settings_back, theme.text2,
                AiTheme.TOUCH_TARGET, 10f, activity.getString(R.string.ai_settings_back));
        back.setOnClickListener(view -> onBack.run());
        row.addView(back, new LinearLayout.LayoutParams(
                theme.dp(AiTheme.TOUCH_TARGET), theme.dp(AiTheme.TOUCH_TARGET)));

        title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(titleRes);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(theme.dp(AiTheme.SPACE_XS));
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
