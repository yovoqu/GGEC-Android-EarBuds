package com.ggec.earbuds.ui.ai.call;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 通话页全景布局容器 (AiCallScreen)：
 * 全面基于 {@link ConstraintLayout} 刚性约束拓扑与 {@link AiTheme} 语义化设计构建。
 *
 * <p>布局架构与空间互斥原则：
 * <ul>
 *   <li><b>顶栏避让</b>：精准避让系统状态栏与摄像头挖孔刘海屏（Cutout），首帧即对齐；</li>
 *   <li><b>控制舱底座</b>：底部悬浮于全面屏导航栏安全区上方，严密响应 WindowInsets；</li>
 *   <li><b>流式字幕防重叠刚性壁垒</b>：字幕卡片底部通过 `bottomToTopOf = ai_call_controls`
 *       锚定于控制舱顶部，并保持 16dp 刚性安全间隙，彻底杜绝物理重叠；</li>
 *   <li><b>弹性中央流体舞台</b>：流体球在顶栏与字幕（或控制舱）之间维持黄金比例偏心（bias=0.45），
 *       无字幕时舒展居中，有字幕时自然承托于上方，空间动态自适应。</li>
 * </ul>
 * </p>
 */
public final class AiCallScreen {

    public interface Listener {
        void onClose();
        void onToggleSubtitles();
        void onToggleDuplex();
        void onMicMuted(boolean micMuted);
        void onSpeaker(boolean speaker);
        void onHangUp();
        void onInterrupt();
        void onAction(@NonNull AiCallUiState.Action action);
    }

    private static final float VISUALIZER_SIZE = 210f;

    private final AiTheme theme;
    private final ConstraintLayout root;
    private final AiCallHeaderView headerView;
    private final AiFluidVisualizerView visualizerView;
    private final AiCallSubtitleView subtitleView;
    private final AiCallErrorView errorView;
    private final AiCallControlsView controlsView;

    public AiCallScreen(@NonNull AiTheme theme, @NonNull Listener listener) {
        this.theme = theme;
        Context context = theme.context;
        root = new ConstraintLayout(context);
        root.setId(View.generateViewId());
        root.setClipChildren(false);
        root.setClipToPadding(false);

        // 自适应深浅色背景：深色采用黑曜石夜空渐变，浅色采用清爽纯白雪夜渐变
        GradientDrawable pageBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, theme.callPageStops());
        root.setBackground(pageBg);

        // 1. 顶部导航栏 (ai_call_header)
        headerView = new AiCallHeaderView(context, theme, new AiCallHeaderView.Listener() {
            @Override
            public void onClose() {
                listener.onClose();
            }

            @Override
            public void onToggleSubtitles() {
                listener.onToggleSubtitles();
            }

            @Override
            public void onToggleDuplex() {
                listener.onToggleDuplex();
            }
        });
        headerView.setId(View.generateViewId());
        ConstraintLayout.LayoutParams headerParams = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        headerParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        headerParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        // 首帧只放基础间距；状态栏/刘海高度等 insets 派发后由监听器补齐。
        headerParams.topMargin = theme.dp(AiTheme.SPACE_S);
        root.addView(headerView, headerParams);

        // 2. 底部黄金三键悬浮控制舱 (ai_call_controls)
        controlsView = new AiCallControlsView(context, theme, new AiCallControlsView.Listener() {
            @Override
            public void onMicMuted(boolean micMuted) {
                listener.onMicMuted(micMuted);
            }

            @Override
            public void onSpeaker(boolean speaker) {
                listener.onSpeaker(speaker);
            }

            @Override
            public void onHangUp() {
                listener.onHangUp();
            }
        });
        controlsView.setId(View.generateViewId());
        ConstraintLayout.LayoutParams controlsParams = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        controlsParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        controlsParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        root.addView(controlsView, controlsParams);

        // 3. 悬浮流式字幕卡片 (ai_call_subtitle)
        // 关键约束：bottomToTop 锚定 controlsView 顶部，刚性设立 16dp 间隙，永不重叠
        subtitleView = new AiCallSubtitleView(context, theme);
        subtitleView.setId(View.generateViewId());
        ConstraintLayout.LayoutParams subParams = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.constrainedHeight = true;
        subParams.bottomToTop = controlsView.getId();
        subParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        subParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        subParams.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        subParams.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        subParams.bottomMargin = theme.dp(16f);
        root.addView(subtitleView, subParams);

        // 4. 中央全息流体声场引擎 (ai_call_visualizer)
        // 垂直弹簧链：位于 header 与 subtitle 之间，偏心比 0.45 黄金视觉中心
        visualizerView = new AiFluidVisualizerView(context, theme);
        visualizerView.setId(View.generateViewId());
        visualizerView.setOnBargeInListener(listener::onInterrupt);
        int orbPx = theme.dp(VISUALIZER_SIZE);
        ConstraintLayout.LayoutParams orbParams = new ConstraintLayout.LayoutParams(orbPx, orbPx);
        orbParams.topToBottom = headerView.getId();
        orbParams.bottomToTop = subtitleView.getId();
        orbParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        orbParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        orbParams.verticalBias = 0.40f;
        orbParams.topMargin = theme.dp(8f);
        orbParams.bottomMargin = theme.dp(8f);
        root.addView(visualizerView, orbParams);

        // 5. 异常中断恢复卡片 (ai_call_error)
        errorView = new AiCallErrorView(context, theme, listener::onAction);
        errorView.setId(View.generateViewId());
        ConstraintLayout.LayoutParams errParams = new ConstraintLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT);
        errParams.topToBottom = headerView.getId();
        errParams.bottomToTop = controlsView.getId();
        errParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        errParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        errParams.leftMargin = theme.dp(AiTheme.PAGE_INSET * 1.5f);
        errParams.rightMargin = theme.dp(AiTheme.PAGE_INSET * 1.5f);
        root.addView(errorView, errParams);

        // 严谨适配全面屏 Edge-to-Edge WindowInsets（状态栏、刘海屏、导航条）
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets statusInsets = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout());
            Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());

            int top = statusInsets.top;
            ConstraintLayout.LayoutParams hp = (ConstraintLayout.LayoutParams) headerView.getLayoutParams();
            if (hp != null) {
                hp.topMargin = top + theme.dp(AiTheme.SPACE_S);
                headerView.setLayoutParams(hp);
            }

            controlsView.setBottomInset(navInsets.bottom);
            return insets;
        });
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull AiCallUiState state) {
        headerView.render(state);
        controlsView.render(state);
        errorView.render(state.reason(), state.action());

        AiFluidVisualizerView.VisualPhase vPhase = switch (state.phase()) {
            case CONNECTING -> AiFluidVisualizerView.VisualPhase.CONNECTING;
            case LISTENING -> AiFluidVisualizerView.VisualPhase.LISTENING;
            case THINKING -> AiFluidVisualizerView.VisualPhase.THINKING;
            case SPEAKING -> AiFluidVisualizerView.VisualPhase.SPEAKING;
            case OFFLINE -> AiFluidVisualizerView.VisualPhase.OFFLINE;
        };
        visualizerView.setPhase(vPhase);
        visualizerView.setAudioLevel(state.meter());

        if (state.action() != AiCallUiState.Action.NONE) {
            visualizerView.setVisibility(View.INVISIBLE);
            subtitleView.setVisibility(View.GONE);
        } else {
            visualizerView.setVisibility(View.VISIBLE);
            subtitleView.renderItems(state.subtitleItems(), state.captionsVisible());
        }
    }

}
