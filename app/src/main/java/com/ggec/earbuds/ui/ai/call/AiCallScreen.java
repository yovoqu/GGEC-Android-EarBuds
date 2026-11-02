package com.ggec.earbuds.ui.ai.call;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.platform.YoSystemBar;

/** 通话页的组合视图：页头、舞台、控制排。只渲染 UiState 并转发用户动作。 */
final class AiCallScreen {

    interface Listener {
        void onClose();
        void onInterrupt();
        void onAction(@NonNull AiCallUiState.Action action);
        void onMuted(boolean muted);
        void onSpeaker(boolean speaker);
        void onHangUp();
    }

    private final AiTheme theme;
    private final LinearLayout root;
    private final AiCallStageView stage;
    private final AiCallControlsView controls;

    AiCallScreen(@NonNull AiTheme theme, @NonNull Listener listener) {
        this.theme = theme;
        root = new LinearLayout(theme.context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);
        root.addView(new AiCallHeaderView(theme, listener::onClose), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stage = new AiCallStageView(theme, new AiCallStageView.Listener() {
            @Override
            public void onInterrupt() {
                listener.onInterrupt();
            }

            @Override
            public void onAction(@NonNull AiCallUiState.Action action) {
                listener.onAction(action);
            }
        });
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        controls = new AiCallControlsView(theme, new AiCallControlsView.Listener() {
            @Override
            public void onMuted(boolean muted) {
                listener.onMuted(muted);
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
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            controls.setBottomInset(bars.bottom + theme.dp(AiTheme.SPACE_XL));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @NonNull
    View view() {
        return root;
    }

    void render(@NonNull AiCallUiState state) {
        stage.render(state);
        controls.render(state);
    }

}
