package com.ggec.earbuds.ui.ai.chat;

import android.graphics.PorterDuff;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
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
 * 底部输入区：一个描边的圆角输入框，左下角是当前模型，右下角是发送。
 */
public final class AiComposerView extends LinearLayout {

    public interface Listener {
        void onSend(@NonNull String text);

        void onStop();

        void onModelClick();

        void onDisabledClick();

        void onDraftChanged(@NonNull String draft);
    }

    private static final int MAX_LINES = 6;
    private static final int LOOK_IDLE = 0;
    private static final int LOOK_SEND = 1;
    private static final int LOOK_STOP = 2;

    private final AiTheme theme;
    private final EditText input;
    private final TextView chipText;
    private final ImageView sendButton;

    @Nullable
    private Listener listener;
    private boolean enabled;
    private boolean generating;
    private boolean applyingDraft;
    private int sendLook = -1;
    private int hintRes;

    public AiComposerView(@NonNull AiTheme theme) {
        super(theme.context);
        this.theme = theme;
        setOrientation(VERTICAL);
        setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_S));

        LinearLayout field = new LinearLayout(theme.context);
        field.setOrientation(VERTICAL);
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
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setLineSpacing(theme.dpf(4), 1f);
        input.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_XS));
        YoFonts.applyRegular(input);
        input.setOnClickListener(view -> {
            if (!enabled && listener != null) {
                listener.onDisabledClick();
            }
        });
        field.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottom = new LinearLayout(theme.context);
        bottom.setOrientation(HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S));

        LinearLayout chip = new LinearLayout(theme.context);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS),
                theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_XS));
        chip.setBackground(theme.pressable(AiTheme.SPACE_S));
        chipText = theme.text(AiTheme.TEXT_CAPTION, theme.text3, AiTheme.Weight.MEDIUM);
        chipText.setSingleLine(true);
        chipText.setEllipsize(TextUtils.TruncateAt.END);
        chipText.setMaxWidth(theme.dp(230));
        chip.addView(chipText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ImageView chevron = theme.icon(R.drawable.ic_ai_chevron_down, theme.text3);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(theme.dp(14), theme.dp(14));
        chevronParams.setMarginStart(theme.dp(AiTheme.SPACE_XS));
        chip.addView(chevron, chevronParams);
        chip.setContentDescription(theme.context.getString(R.string.ai_chip_choose_model));
        chip.setOnClickListener(view -> {
            if (listener != null) {
                listener.onModelClick();
            }
        });
        bottom.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(36)));
        bottom.addView(new View(theme.context), new LinearLayout.LayoutParams(0, 1, 1f));

        sendButton = new ImageView(theme.context);
        sendButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = theme.dp(9);
        sendButton.setPadding(pad, pad, pad, pad);
        bottom.addView(sendButton, new LinearLayout.LayoutParams(theme.dp(36), theme.dp(36)));
        field.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        refreshSendButton();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

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
        }
        String chip = modelChip != null ? modelChip : theme.context.getString(R.string.ai_chip_choose_model);
        if (!chip.contentEquals(chipText.getText())) {
            chipText.setText(chip);
        }
        if (wasEnabled && !enabled) {
            input.clearFocus();
        }
        refreshSendButton();
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

    private void refreshSendButton() {
        boolean hasText = !input.getText().toString().isBlank();
        int look = generating ? LOOK_STOP : enabled && hasText ? LOOK_SEND : LOOK_IDLE;
        sendButton.setEnabled(look != LOOK_IDLE);
        if (look == sendLook) {
            return;
        }
        sendLook = look;
        if (look == LOOK_IDLE) {
            sendButton.setImageResource(R.drawable.ic_ai_send);
            sendButton.setColorFilter(theme.text3, PorterDuff.Mode.SRC_IN);
            sendButton.setBackground(null);
            sendButton.setContentDescription(theme.context.getString(R.string.ai_send));
            return;
        }
        boolean stop = look == LOOK_STOP;
        sendButton.setImageResource(stop ? R.drawable.ic_ai_stop : R.drawable.ic_ai_send);
        sendButton.setColorFilter(theme.onBrand, PorterDuff.Mode.SRC_IN);
        sendButton.setBackground(theme.pressable(theme.circle(theme.brand), 18));
        sendButton.setContentDescription(theme.context.getString(stop ? R.string.ai_stop : R.string.ai_send));
    }
}
