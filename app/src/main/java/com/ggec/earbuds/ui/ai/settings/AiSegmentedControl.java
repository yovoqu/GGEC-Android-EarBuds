package com.ggec.earbuds.ui.ai.settings;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 二选一的分段控件。
 *
 * <p>为什么不用"两行列表 + 勾"：模式是互斥的两个状态，不是两条并列的选项。
 * 分段控件把"现在选的是哪个"和"可以换成哪个"放在同一个控件里，一眼看完；
 * 列表行还需要一行勾去说明选中，占的却是两倍高度。</p>
 *
 * <p>不可用时不做成"点不动的灰块"：段本身压暗，下面那行原因说的是"缺什么才能选"，
 * 而不是"暂不可用"。</p>
 */
final class AiSegmentedControl extends LinearLayout {

    private final AiTheme theme;
    private final TextView[] segments = new TextView[2];
    private final TextView reason;

    @Nullable
    private Listener listener;
    private boolean secondEnabled = true;
    private boolean secondSelected;

    interface Listener {
        void onSelect(boolean second);
    }

    AiSegmentedControl(@NonNull AiTheme theme) {
        super(theme.context);
        this.theme = theme;
        setOrientation(VERTICAL);

        LinearLayout track = new LinearLayout(theme.context);
        track.setOrientation(HORIZONTAL);
        track.setBackground(theme.rounded(theme.fill, AiTheme.RADIUS_FIELD));
        track.setPadding(theme.dp(4), theme.dp(4), theme.dp(4), theme.dp(4));

        for (int i = 0; i < 2; i++) {
            final boolean second = i == 1;
            TextView segment = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2, AiTheme.Weight.MEDIUM);
            segment.setGravity(Gravity.CENTER);
            segment.setPadding(0, theme.dp(10), 0, theme.dp(10));
            segment.setOnClickListener(view -> {
                if (second && !secondEnabled) {
                    return;
                }
                if (listener != null) {
                    listener.onSelect(second);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            track.addView(segment, params);
            segments[i] = segment;
        }
        addView(track, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        reason = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        reason.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        reasonParams.topMargin = theme.dp(AiTheme.SPACE_S);
        addView(reason, reasonParams);
    }

    void setListener(@Nullable Listener next) {
        listener = next;
    }

    /**
     * @param secondEnabled 没有可用实时模型时第二段不可选
     * @param reasonText    不可选时必须给出原因；可选时传当前模式的一句说明
     */
    void bind(@NonNull CharSequence first, @NonNull CharSequence second, boolean secondSelected,
              boolean secondEnabled, @Nullable CharSequence reasonText) {
        boolean changed = this.secondSelected != secondSelected || this.secondEnabled != secondEnabled;
        this.secondSelected = secondSelected;
        this.secondEnabled = secondEnabled;
        segments[0].setText(first);
        segments[1].setText(second);
        if (changed) {
            applyLook(segments[0], !secondSelected, true);
            applyLook(segments[1], secondSelected, secondEnabled);
        }
        reason.setText(reasonText == null ? "" : reasonText);
        reason.setVisibility(reasonText == null || reasonText.length() == 0 ? GONE : VISIBLE);
    }

    private void applyLook(@NonNull TextView segment, boolean selected, boolean enabled) {
        if (selected) {
            segment.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_FIELD - 4,
                    theme.cardStroke, 1));
            segment.setTextColor(theme.text1);
            segment.setAlpha(1f);
        } else {
            segment.setBackground(null);
            // 不可用也要读得清：用 text2 加 0.75 透明，约 3.4:1；压到 text3 再加透明就只剩 2:1 了。
            segment.setTextColor(enabled ? theme.text2 : theme.text2);
            segment.setAlpha(enabled ? 1f : 0.75f);
        }
        segment.setEnabled(enabled);
    }
}
