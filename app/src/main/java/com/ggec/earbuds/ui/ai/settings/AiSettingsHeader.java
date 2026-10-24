package com.ggec.earbuds.ui.ai.settings;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
 */
final class AiSettingsHeader {

    private final LinearLayout root;
    private final TextView title;

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
}
