package com.ggec.earbuds.ui.ai.call;

import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/** 通话页页头：关闭、页名和当前交互形态。只负责展示。 */
final class AiCallHeaderView extends FrameLayout {

    private static final float CLOSE_SIZE = 40f;
    private static final float HEADER_HEIGHT = 64f;

    AiCallHeaderView(@NonNull AiTheme theme, @NonNull Runnable onClose) {
        super(theme.context);
        setMinimumHeight(theme.dp(HEADER_HEIGHT));
        setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.PAGE_INSET), 0);

        ImageView close = theme.iconButton(R.drawable.ic_ai_call_close, theme.text2,
                CLOSE_SIZE, 10f, getContext().getString(R.string.ai_call_close));
        close.setOnClickListener(view -> onClose.run());
        addView(close, new LayoutParams(
                theme.dp(CLOSE_SIZE), theme.dp(CLOSE_SIZE),
                Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_call_title);
        titles.addView(title, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView mode = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        mode.setText(R.string.ai_call_mode_realtime);
        mode.setSingleLine(true);
        mode.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        modeParams.topMargin = theme.dp(AiTheme.SPACE_XS);
        titles.addView(mode, modeParams);

        LayoutParams titlesParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        titlesParams.leftMargin = theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M);
        titlesParams.rightMargin = theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M);
        addView(titles, titlesParams);
    }
}
