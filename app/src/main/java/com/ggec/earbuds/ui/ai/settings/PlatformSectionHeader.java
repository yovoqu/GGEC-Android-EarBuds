package com.ggec.earbuds.ui.ai.settings;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;

/** 平台页区块标题。只负责标题与可选动作的排版，不包含任何业务判断。 */
final class PlatformSectionHeader {

    private PlatformSectionHeader() {
    }

    @NonNull
    static View titleOnly(@NonNull AiTheme theme, @NonNull android.content.Context context,
                          @NonNull CharSequence title) {
        return build(theme, context, title, null, null);
    }

    @NonNull
    static View withAction(@NonNull AiTheme theme, @NonNull android.content.Context context,
                           @NonNull TextView title,
                           @NonNull TextView action, @NonNull Runnable onAction) {
        LinearLayout header = build(theme, context, null, null, null);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        action.setOnClickListener(view -> onAction.run());
        header.addView(action, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return header;
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
    static TextView title(@NonNull AiTheme theme) {
        return theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
    }

    @NonNull
    private static LinearLayout build(@NonNull AiTheme theme, @NonNull android.content.Context context,
                                      @Nullable CharSequence title,
                                      @Nullable CharSequence action, @Nullable Runnable onAction) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(AiTheme.LIST_EDGE), theme.dp(AiTheme.SPACE_XL),
                theme.dp(AiTheme.LIST_EDGE), theme.dp(AiTheme.SPACE_S));
        if (title != null) {
            TextView label = PlatformSectionHeader.title(theme);
            label.setText(title);
            header.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (action != null) {
            TextView actionView = PlatformSectionHeader.action(theme);
            actionView.setText(action);
            actionView.setOnClickListener(view -> {
                if (onAction != null) {
                    onAction.run();
                }
            });
            header.addView(actionView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return header;
    }
}
