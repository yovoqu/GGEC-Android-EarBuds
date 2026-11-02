package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 语音设置各区块的标题行：12sp 三级灰标题 + 可选右侧动作，与卡片外沿对齐。
 *
 * <p>语速与平台详情页一致，但独立成语音设置自己的构件——这一页的标题要能带"数量"与
 * "拉取"这种整块动作，不能借平台页那个只看标题的实现。</p>
 */
final class VoiceSectionHeader {

    private VoiceSectionHeader() {
    }

    @NonNull
    static View titleOnly(@NonNull AiTheme theme, @NonNull Context context,
                          @NonNull CharSequence title) {
        LinearLayout header = base(theme, context);
        TextView label = title(theme);
        label.setText(title);
        header.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    @NonNull
    static View withAction(@NonNull AiTheme theme, @NonNull Context context,
                           @NonNull TextView title,
                           @NonNull TextView action, @NonNull Runnable onAction) {
        LinearLayout header = base(theme, context);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        action.setOnClickListener(view -> onAction.run());
        header.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
    }

    @NonNull
    static TextView title(@NonNull AiTheme theme) {
        return theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
    }

    @NonNull
    static TextView action(@NonNull AiTheme theme) {
        TextView action = theme.text(AiTheme.TEXT_CAPTION, theme.brand, AiTheme.Weight.MEDIUM);
        action.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_XS),
                theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_XS));
        action.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        return action;
    }

    @NonNull
    private static LinearLayout base(@NonNull AiTheme theme, @NonNull Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(AiTheme.LIST_EDGE), theme.dp(AiTheme.SPACE_XL),
                theme.dp(AiTheme.LIST_EDGE), theme.dp(AiTheme.SPACE_S));
        return header;
    }
}
