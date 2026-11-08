package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.widget.AiHeaderActionButton;

/**
 * 通话页顶部导航栏 (AiCallHeaderView)：
 * 全面基于 {@link AiTheme} 语义化调色系统与对称美学架构构建。
 *
 * <p>布局架构与空间互斥规范：
 * <ul>
 *   <li><b>绝对对称锚点</b>：左侧 40dp 黄金圆形返回键，右侧 40dp 黄金圆形字幕键，左右宽度严格 1:1 对等；</li>
 *   <li><b>中央灵动复合胶囊 (Dynamic Composite Capsule)</b>：
 *       将【通话状态 · 时长】与【全双工/半双工模式切换】完美融合于单一中央胶囊中，
 *       彻底消除旧版多个悬浮按钮在水平方向的挤压与碰撞；</li>
 *   <li><b>全屏级防溢出壁垒</b>：中央胶囊被严格约束在左右操作键之间，并启用文本尾部省略，在任意小屏上绝对零重叠。</li>
 * </ul>
 * </p>
 */
public final class AiCallHeaderView extends RelativeLayout {

    public interface Listener {
        void onClose();
        void onToggleSubtitles();
        void onToggleDuplex();
    }

    private static final float STATUS_DOT_SIZE = 7f;
    private static final float DUPLEX_ICON_SIZE = 15f;
    private static final float BUTTON_SIZE_DP = 40f;

    private final AiTheme theme;
    private final Listener listener;

    private final AiHeaderActionButton closeButton;
    private final AiHeaderActionButton subtitlesButton;

    private final View statusDot;
    private final TextView statusText;
    private final TextView durationText;

    private final LinearLayout duplexButton;
    private final ImageView duplexIcon;
    private final TextView duplexText;

    public AiCallHeaderView(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        super(context);
        this.theme = theme;
        this.listener = listener;

        int pad = theme.dp(AiTheme.PAGE_INSET);
        setPadding(pad, theme.dp(AiTheme.SPACE_S), pad, theme.dp(AiTheme.SPACE_S));

        int btnSize = theme.dp(BUTTON_SIZE_DP);

        // 1. 左侧：40dp 统一圆形返回/关闭按键
        closeButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.NEUTRAL)
                .icon(R.drawable.ic_ai_back)
                .contentDesc(R.string.ai_call_close)
                .onClick(v -> listener.onClose());
        closeButton.setId(View.generateViewId());
        LayoutParams closeParams = new LayoutParams(btnSize, btnSize);
        closeParams.addRule(ALIGN_PARENT_START);
        closeParams.addRule(CENTER_VERTICAL);
        addView(closeButton, closeParams);

        // 2. 右侧：40dp 统一圆形双态字幕开关按键
        subtitlesButton = new AiHeaderActionButton(context, theme, AiHeaderActionButton.Style.TOGGLE)
                .icon(R.drawable.ic_ai_call_subtitles)
                .contentDesc(R.string.ai_call_subtitles_toggle)
                .onClick(v -> listener.onToggleSubtitles());
        subtitlesButton.setId(View.generateViewId());
        LayoutParams subParams = new LayoutParams(btnSize, btnSize);
        subParams.addRule(ALIGN_PARENT_END);
        subParams.addRule(CENTER_VERTICAL);
        addView(subtitlesButton, subParams);

        // 3. 中央：灵动复合状态与双工胶囊 (状态 · 时长 | 双工切换)
        LinearLayout centerPill = new LinearLayout(context);
        centerPill.setId(View.generateViewId());
        centerPill.setOrientation(LinearLayout.HORIZONTAL);
        centerPill.setGravity(Gravity.CENTER_VERTICAL);

        // 胶囊背景：卡片底色 + 细微轮廓描边，圆角半径严格等于高度一半 (20dp)，高度与左右按钮完全对齐 (40dp)
        GradientDrawable pillBg = theme.roundedStroke(theme.card, BUTTON_SIZE_DP / 2f, theme.cardStroke, 1f);
        centerPill.setBackground(pillBg);
        centerPill.setPadding(theme.dp(12f), 0, theme.dp(10f), 0);

        // 3.1 呼吸状态指示灯
        statusDot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(theme.brand);
        statusDot.setBackground(dotBg);
        int dotPx = theme.dp(STATUS_DOT_SIZE);
        centerPill.addView(statusDot, new LinearLayout.LayoutParams(dotPx, dotPx));

        // 3.2 状态文字：自适应字号提升为 TEXT_SUBHEAD (13.5sp)，中等字重
        statusText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text1, AiTheme.Weight.MEDIUM);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setText(R.string.ai_call_phase_connecting);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.leftMargin = theme.dp(6f);
        statusParams.rightMargin = theme.dp(4f);
        centerPill.addView(statusText, statusParams);

        // 3.3 分隔圆点
        TextView dotSeparator = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        dotSeparator.setText("·");
        centerPill.addView(dotSeparator, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 3.4 实时通话时长：字号自适应提升为 TEXT_SUBHEAD (13.5sp)
        durationText = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        durationText.setSingleLine(true);
        durationText.setText("00:00");
        LinearLayout.LayoutParams durParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durParams.leftMargin = theme.dp(4f);
        centerPill.addView(durationText, durParams);

        // 3.5 竖向精致微细分隔线
        View divider = new View(context);
        divider.setBackgroundColor(theme.cardStroke);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                theme.dp(1f), theme.dp(16f));
        divParams.leftMargin = theme.dp(10f);
        divParams.rightMargin = theme.dp(8f);
        centerPill.addView(divider, divParams);

        // 3.6 双工模式切换按键 (内置于胶囊右翼，支持果冻按压反馈)
        duplexButton = new LinearLayout(context);
        duplexButton.setOrientation(LinearLayout.HORIZONTAL);
        duplexButton.setGravity(Gravity.CENTER);
        duplexButton.setClickable(true);
        duplexButton.setFocusable(true);
        duplexButton.setPadding(theme.dp(6f), theme.dp(4f), theme.dp(6f), theme.dp(4f));

        GradientDrawable duplexRippleMask = new GradientDrawable();
        duplexRippleMask.setCornerRadius(theme.dp(12f));
        duplexRippleMask.setColor(0xFFFFFFFF);
        duplexButton.setBackground(new RippleDrawable(
                ColorStateList.valueOf(theme.brandSoft), null, duplexRippleMask));
        duplexButton.setOnClickListener(v -> listener.onToggleDuplex());

        duplexIcon = new ImageView(context);
        duplexIcon.setImageResource(R.drawable.ic_ai_swap);
        int iconPx = theme.dp(DUPLEX_ICON_SIZE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconPx, iconPx);
        duplexButton.addView(duplexIcon, iconParams);

        duplexText = theme.text(12.5f, theme.brand, AiTheme.Weight.MEDIUM);
        duplexText.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = theme.dp(4f);
        duplexButton.addView(duplexText, textParams);

        centerPill.addView(duplexButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 居中对齐，高度严格对齐左右按键 40dp (btnSize)，消除落差
        LayoutParams centerParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, btnSize);
        centerParams.addRule(CENTER_IN_PARENT);
        addView(centerPill, centerParams);
    }

    public void render(@NonNull AiCallUiState state) {
        statusText.setText(state.title());
        durationText.setText(state.formattedDuration());

        // 呼吸指示灯随通话相位切换主题语义色
        int dotColor = switch (state.phase()) {
            case LISTENING -> theme.isDark() ? 0xFF00E676 : 0xFF10B981;
            case SPEAKING -> theme.brand;
            case THINKING -> theme.isDark() ? 0xFFA855F7 : 0xFF7C3AED;
            case CONNECTING -> theme.brand;
            case OFFLINE -> theme.text3;
        };
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(dotColor);
        statusDot.setBackground(dotDrawable);

        // 渲染双工模式文字与强调色彩
        boolean isFull = state.duplex() == VoiceDuplex.FULL_DUPLEX;
        int activeColor = theme.brand;
        int inactiveColor = theme.text2;
        int targetColor = isFull ? activeColor : inactiveColor;
        duplexText.setText(isFull ? R.string.ai_call_duplex_full : R.string.ai_call_duplex_half);
        duplexText.setTextColor(targetColor);
        duplexIcon.setColorFilter(targetColor);
        duplexButton.setContentDescription(getContext().getString(
                R.string.ai_call_duplex_toggle_desc,
                getContext().getString(isFull ? R.string.ai_call_duplex_full : R.string.ai_call_duplex_half)));

        subtitlesButton.setActive(state.captionsVisible());
    }
}
