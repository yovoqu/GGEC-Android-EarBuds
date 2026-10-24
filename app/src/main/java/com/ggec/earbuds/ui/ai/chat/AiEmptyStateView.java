package com.ggec.earbuds.ui.ai.chat;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

import java.util.List;

/**
 * 还没有消息时的内容区：一句提问、一句说明，下面是四句可以直接发出去的问法。
 *
 * <p>四句问法用整行（56dp）而不是胶囊网格，因为这几句都是十来个中文字，两列放不下不折行的宽度；
 * 整行也正好和系统列表的语言一致。平台或模型没就绪时换成一则状态说明，不放按钮——
 * 这一版没有配置入口，放了按钮也没有去处。</p>
 *
 * <p>只有这一个视图自己画行，不用主题组件，所以不需要配对的 destroy。</p>
 */
public final class AiEmptyStateView extends ScrollView {

    public interface Listener {
        void onSuggestion(@NonNull String text);
    }

    private static final long ENTRANCE_DURATION_MS = 240L;
    private static final long ENTRANCE_STEP_MS = 60L;
    private static final float ENTRANCE_OFFSET_DP = 10f;

    private final AiTheme theme;
    private final Listener listener;

    private final LinearLayout column;
    private final TextView heading;
    private final TextView message;
    private final LinearLayout card;
    private final TextView note;

    private AiChatUiState.Welcome mode;
    private boolean entered;
    private boolean voiceReady;

    public AiEmptyStateView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);

        column = new LinearLayout(theme.context);
        column.setOrientation(LinearLayout.VERTICAL);
        // 三种空状态共用同一个起点：标题永远落在这个高度上，换状态时画面不会整块上下跳。
        column.setPadding(0, theme.dp(AiTheme.EMPTY_TOP), 0, theme.dp(AiTheme.SPACE_XL));
        addView(column, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout block = new LinearLayout(theme.context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(theme.dp(AiTheme.PAGE_INSET), 0, theme.dp(AiTheme.PAGE_INSET), 0);
        column.addView(block, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        heading = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        heading.setLineSpacing(theme.dpf(5), 1f);
        block.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        message = theme.text(14, theme.text2);
        message.setLineSpacing(theme.dpf(5), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = theme.dp(AiTheme.SPACE_S);
        block.addView(message, messageParams);

        card = new LinearLayout(theme.context);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        block.addView(card, cardParams);

        note = theme.text(AiTheme.TEXT_CAPTION, theme.text2);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = theme.dp(AiTheme.SPACE_M);
        block.addView(note, noteParams);
    }

    /** 换一种空状态。构建好之后才第一次显示时才播放一次入场，重渲染不重播。 */
    void setMode(@NonNull AiChatUiState.Welcome next) {
        setMode(next, false);
    }

    /**
     * @param voiceReady 有可用的语音模型时，就绪态把"说"写进引导里；
     *                   没有就只说打字的路径，不承诺做不到的事。
     */
    void setMode(@NonNull AiChatUiState.Welcome next, boolean voiceReady) {
        if (next == mode && voiceReady == this.voiceReady) {
            return;
        }
        mode = next;
        this.voiceReady = voiceReady;
        rebuild(next);
        if (!entered) {
            entered = true;
            playEntrance();
        }
    }

    private void rebuild(@NonNull AiChatUiState.Welcome next) {
        card.removeAllViews();
        switch (next) {
            case SUGGESTIONS -> {
                // 就绪态不放假问题：以前这里写死四条示例提问，用户以为那是产品内容。
                // 现在只留一句真话——问什么由用户自己决定，能不能说取决于有没有语音模型。
                heading.setText(R.string.ai_welcome_title);
                // 没有语音模型时不写第二行：上一版是「直接问它耳机的问题。」，
                // 和标题「耳机有什么问题？」说的是同一件事，纯属重复。
                if (voiceReady) {
                    message.setText(R.string.ai_welcome_subtitle_voice);
                    message.setVisibility(VISIBLE);
                } else {
                    message.setVisibility(GONE);
                }
                note.setVisibility(GONE);
                card.setVisibility(GONE);
            }
            case NEEDS_PLATFORM -> {
                showStatus(R.string.ai_setup_title, R.string.ai_setup_message, R.string.ai_privacy_hint);
            }
            case NEEDS_MODEL -> {
                showStatus(R.string.ai_pick_model_title, R.string.ai_pick_model_message, 0);
            }
            default -> {
            }
        }
    }

    private void showStatus(@StringRes int title, @StringRes int body, @StringRes int footnote) {
        heading.setText(title);
        message.setText(body);
        message.setVisibility(VISIBLE);
        card.setVisibility(GONE);
        if (footnote == 0) {
            note.setVisibility(GONE);
        } else {
            note.setText(footnote);
            note.setVisibility(VISIBLE);
        }
    }

    /** 首次进入：标题先到，说明和建议顺次跟上，总共不超过 420ms。 */
    private void playEntrance() {
        int step = 0;
        for (View view : List.of(heading, message, card, note)) {
            if (view.getVisibility() != VISIBLE) {
                continue;
            }
            view.setAlpha(0f);
            view.setTranslationY(theme.dpf(ENTRANCE_OFFSET_DP));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(ENTRANCE_DURATION_MS)
                    .setStartDelay(step * ENTRANCE_STEP_MS)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
                    .start();
            step++;
        }
    }
}
