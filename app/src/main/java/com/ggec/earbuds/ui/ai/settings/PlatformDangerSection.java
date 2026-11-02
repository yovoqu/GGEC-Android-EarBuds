package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 平台删除区：只在已有平台时显示。 */
final class PlatformDangerSection {

    private final LinearLayout view;

    PlatformDangerSection(@NonNull Context context, @NonNull AiTheme theme,
                          @NonNull Runnable onDelete) {
        TextView delete = theme.text(AiTheme.TEXT_BODY, theme.alert, AiTheme.Weight.MEDIUM);
        delete.setText(R.string.ai_settings_delete_platform);
        delete.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        delete.setMinimumHeight(theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M));
        delete.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
        delete.setOnClickListener(view -> onDelete.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(AiTheme.SPACE_XL);
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        delete.setLayoutParams(params);

        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(delete);
    }

    @NonNull
    View view() {
        return view;
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
