package com.ggec.earbuds.ui.ai.chat;

import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiKeyboard;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.theme.YoFonts;

/**
 * 底部输入区：上面一行是当前模型，下面是一个会随行数长高的胶囊。
 *
 * <p>模型状态只在这里出现一次（页头不再重复），字号和颜色压到它该有的分量：
 * 说明「这句话发给谁」，不抢输入框。</p>
 */
public final class AiComposerView extends LinearLayout {

    public interface Listener {
        void onSend(@NonNull String text);

        void onStop();

        void onDraftChanged(@NonNull String draft);
    }

    private static final int MAX_LINES = 5;
    private static final int LOOK_IDLE = 0;
    private static final int LOOK_SEND = 1;
    private static final int LOOK_STOP = 2;

    private final AiTheme theme;
    private final LinearLayout field;
    private final EditText input;
    private final TextView meta;
    private final ImageView sendButton;

    @Nullable
    private Listener listener;
    private boolean enabled;
    private boolean generating;
    private boolean applyingDraft;
    private int sendLook = -1;
    private int hintRes;
    private boolean fieldDisabled;

    public AiComposerView(@NonNull AiTheme theme) {
        super(theme.context);
        this.theme = theme;
        setOrientation(VERTICAL);
        setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(10), theme.dp(AiTheme.PAGE_INSET), theme.dp(10));

        meta = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.bottomMargin = theme.dp(AiTheme.SPACE_S);
        addView(meta, metaParams);

        field = new LinearLayout(theme.context);
        field.setOrientation(HORIZONTAL);
        field.setGravity(Gravity.BOTTOM);
        field.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_FIELD, theme.cardStroke, 1));
        addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(theme.context);
        input.setBackground(null);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
        input.setTextColor(theme.text1);
        input.setHintTextColor(theme.hint);
        input.setHint(R.string.ai_composer_hint);
        input.setMaxLines(MAX_LINES);
        input.setMinLines(1);
        input.setIncludeFontPadding(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setLineSpacing(theme.dpf(3), 1f);
        input.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(11), theme.dp(AiTheme.SPACE_S), theme.dp(11));
        YoFonts.applyRegular(input);
        field.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        sendButton = new ImageView(theme.context);
        sendButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = theme.dp(9);
        sendButton.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(theme.dp(36), theme.dp(36));
        sendParams.setMarginEnd(theme.dp(6));
        sendParams.bottomMargin = theme.dp(6);
        field.addView(sendButton, sendParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                refreshSendButton();
                if (!applyingDraft && listener != null) {
                    listener.onDraftChanged(text.toString());
                }
            }
        });
        sendButton.setOnClickListener(view -> onSendClick());
        setState(false, false, null);
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /**
     * @param modelChip 平台 · 模型；为空表示还没有可用模型，这行改说「未接入平台」
     */
    public void setState(boolean enabled, boolean generating, @Nullable String modelChip) {
        boolean wasEnabled = this.enabled;
        this.enabled = enabled;
        this.generating = generating;
        input.setFocusable(enabled);
        input.setFocusableInTouchMode(enabled);
        int nextHint = enabled ? R.string.ai_composer_hint : R.string.ai_composer_hint_setup;
        if (nextHint != hintRes) {
            hintRes = nextHint;
            input.setHint(nextHint);
            // 不可用时提示语是这一屏唯一说明原因的地方，用正文次级色；可用时才退回占位色的淡。
            input.setHintTextColor(enabled ? theme.hint : theme.text2);
        }
        String text = modelChip != null && !modelChip.isEmpty()
                ? modelChip : theme.context.getString(R.string.ai_model_none);
        if (!text.contentEquals(meta.getText())) {
            meta.setText(text);
        }
        // 有模型时不写这一行：每条回答上方已经标了是哪个模型，输入条再写一遍是同一件事说两次。
        // 没有模型时才写，因为那是"为什么发不出去"的唯一说明。
        boolean hasModel = modelChip != null && !modelChip.isEmpty();
        setVisibleState(meta, !hasModel);
        if (wasEnabled && !enabled) {
            input.clearFocus();
        }
        applyFieldLook(!enabled);
        refreshSendButton();
    }

    /** 不可用时换成浅填充底色：一眼看出这不是能打字的地方，而不只是提示语变了。 */
    private void applyFieldLook(boolean disabled) {
        if (disabled == fieldDisabled) {
            return;
        }
        fieldDisabled = disabled;
        field.setBackground(theme.roundedStroke(disabled ? theme.fill : theme.card,
                AiTheme.RADIUS_FIELD, theme.cardStroke, 1));
    }

    public void setDraft(@NonNull String draft) {
        if (draft.contentEquals(input.getText())) {
            return;
        }
        applyingDraft = true;
        try {
            input.setText(draft);
            input.setSelection(input.length());
        } finally {
            applyingDraft = false;
        }
    }

    public void hideKeyboard() {
        AiKeyboard.hide(input);
    }

    private void onSendClick() {
        if (listener == null) {
            return;
        }
        if (generating) {
            listener.onStop();
            return;
        }
        String text = input.getText().toString().strip();
        if (!enabled || text.isEmpty()) {
            return;
        }
        input.setText("");
        listener.onSend(text);
    }

    private void setVisibleState(@NonNull android.view.View view, boolean visible) {
        int target = visible ? VISIBLE : GONE;
        if (view.getVisibility() != target) {
            view.setVisibility(target);
        }
    }

    private void refreshSendButton() {        boolean hasText = !input.getText().toString().isBlank();
        int look = generating ? LOOK_STOP : enabled && hasText ? LOOK_SEND : LOOK_IDLE;
        sendButton.setEnabled(look != LOOK_IDLE);
        if (look == sendLook) {
            return;
        }
        sendLook = look;
        if (look == LOOK_IDLE) {
            sendButton.setImageResource(R.drawable.ic_ai_send);
            sendButton.setColorFilter(enabled ? theme.text2 : theme.text3, PorterDuff.Mode.SRC_IN);
            // 空输入时压到明显不可用：0.7 看上去仍然能点，点下去却没反应。
            sendButton.setAlpha(enabled ? 0.4f : 0.28f);
            sendButton.setBackground(null);
            sendButton.setContentDescription(theme.context.getString(R.string.ai_send));
            return;
        }
        boolean stop = look == LOOK_STOP;
        sendButton.setImageResource(stop ? R.drawable.ic_ai_stop : R.drawable.ic_ai_send);
        sendButton.setColorFilter(theme.onBrand, PorterDuff.Mode.SRC_IN);
        sendButton.setAlpha(1f);
        sendButton.setBackground(theme.pressable(theme.circle(theme.brand), 18));
        sendButton.setContentDescription(theme.context.getString(stop ? R.string.ai_stop : R.string.ai_send));
    }
}
