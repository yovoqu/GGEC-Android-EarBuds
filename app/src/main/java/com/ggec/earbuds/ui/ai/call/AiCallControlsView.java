package com.ggec.earbuds.ui.ai.call;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.yohu.youi.api.feedback.YoToast;

/** 通话页控制排：相机占位、静音、扬声器与挂断。 */
final class AiCallControlsView extends LinearLayout {

    interface Listener {
        void onMuted(boolean muted);
        void onSpeaker(boolean speaker);
        void onHangUp();
    }

    private static final int NO_STROKE = 0;
    private static final float CONTROL_SIZE = 56f;

    private final AiTheme theme;
    private final Listener listener;
    private final ImageView camera;
    private final ImageView microphone;
    private final ImageView speaker;
    private final ImageView hangUp;
    private boolean controlsLive;
    private boolean ended;
    private boolean muted;
    private boolean speakerOn = true;

    AiCallControlsView(@NonNull AiTheme theme, @NonNull Listener listener) {
        super(theme.context);
        this.theme = theme;
        this.listener = listener;
        setOrientation(VERTICAL);
        setPadding(theme.dp(AiTheme.SPACE_L), 0, theme.dp(AiTheme.SPACE_L),
                theme.dp(AiTheme.SPACE_XL));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        camera = roundButton(R.drawable.ic_ai_call_camera, 24f,
                theme.fill, theme.text2, theme.cardStroke, R.string.ai_call_camera);
        camera.setOnClickListener(view -> YoToast.show(getContext(),
                getContext().getString(R.string.ai_call_camera_unavailable)));
        microphone = roundButton(R.drawable.ic_ai_call_mic, 24f,
                theme.brandSoft, theme.brand, NO_STROKE, R.string.ai_call_mute);
        microphone.setOnClickListener(view -> listener.onMuted(!muted));
        speaker = roundButton(R.drawable.ic_ai_call_speaker, 24f,
                theme.brandSoft, theme.brand, NO_STROKE,
                R.string.ai_call_speaker_to_earpiece);
        speaker.setOnClickListener(view -> listener.onSpeaker(!speakerOn));
        hangUp = roundButton(R.drawable.ic_ai_call_hangup, 24f,
                theme.alert, theme.onBrand, NO_STROKE, R.string.ai_call_hang_up);
        hangUp.setOnClickListener(view -> listener.onHangUp());

        addControl(row, camera, 0f);
        addControl(row, microphone, AiTheme.SPACE_M);
        addControl(row, speaker, AiTheme.SPACE_M);
        addControl(row, hangUp, AiTheme.SPACE_L);
        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void render(@NonNull AiCallUiState state) {
        setControlsLive(state.live());
        applyEndedLook(state.ended());
        muted = state.muted();
        speakerOn = state.speakerOn();
        if (!ended) {
            applyMicLook();
            applySpeakerLook();
        }
    }

    void setBottomInset(int px) {
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), px);
    }

    private void setControlsLive(boolean live) {
        if (controlsLive == live) {
            return;
        }
        controlsLive = live;
        if (!ended) {
            applyMicLook();
            applySpeakerLook();
        }
    }

    private void applyEndedLook(boolean next) {
        if (ended == next) {
            return;
        }
        ended = next;
        hangUp.setBackground(theme.pressable(circleBackground(
                ended ? theme.fill : theme.alert, NO_STROKE), CONTROL_SIZE / 2f));
        hangUp.setColorFilter(ended ? theme.text2 : theme.onBrand, PorterDuff.Mode.SRC_IN);
        hangUp.setContentDescription(getContext().getString(ended
                ? R.string.ai_call_close : R.string.ai_call_hang_up));
        camera.setEnabled(!ended);
        camera.setAlpha(ended ? 0.4f : 1f);
        if (ended) {
            dim(microphone);
            dim(speaker);
        } else {
            applyMicLook();
            applySpeakerLook();
        }
    }

    private void applyMicLook() {
        microphone.setImageResource(muted
                ? R.drawable.ic_ai_call_mic_off : R.drawable.ic_ai_call_mic);
        microphone.setColorFilter(muted ? theme.text2 : theme.brand, PorterDuff.Mode.SRC_IN);
        microphone.setBackground(theme.pressable(circleBackground(
                muted ? theme.fill : theme.brandSoft, muted ? theme.cardStroke : NO_STROKE),
                CONTROL_SIZE / 2f));
        microphone.setContentDescription(getContext().getString(muted
                ? R.string.ai_call_unmute : R.string.ai_call_mute));
        microphone.setEnabled(controlsLive);
        microphone.setAlpha(controlsLive ? 1f : 0.4f);
    }

    private void applySpeakerLook() {
        speaker.setImageResource(speakerOn
                ? R.drawable.ic_ai_call_speaker : R.drawable.ic_ai_call_speaker_off);
        speaker.setColorFilter(speakerOn ? theme.brand : theme.text2, PorterDuff.Mode.SRC_IN);
        speaker.setBackground(theme.pressable(circleBackground(
                speakerOn ? theme.brandSoft : theme.fill,
                speakerOn ? NO_STROKE : theme.cardStroke), CONTROL_SIZE / 2f));
        speaker.setContentDescription(getContext().getString(speakerOn
                ? R.string.ai_call_speaker_to_earpiece : R.string.ai_call_speaker_to_speaker));
        speaker.setEnabled(controlsLive);
        speaker.setAlpha(controlsLive ? 1f : 0.4f);
    }

    private void dim(@NonNull ImageView button) {
        button.setEnabled(false);
        button.setAlpha(0.4f);
        button.setBackground(theme.pressable(circleBackground(theme.fill, NO_STROKE),
                CONTROL_SIZE / 2f));
    }

    @NonNull
    private ImageView roundButton(@DrawableRes int icon, float iconDp,
                                  @ColorInt int fill, @ColorInt int tint,
                                  @ColorInt int stroke, @StringRes int description) {
        ImageView button = theme.icon(icon, tint);
        int padding = theme.dp((CONTROL_SIZE - iconDp) / 2f);
        button.setPadding(padding, padding, padding, padding);
        button.setBackground(theme.pressable(circleBackground(fill, stroke), CONTROL_SIZE / 2f));
        button.setContentDescription(getContext().getString(description));
        attachPressFeedback(button);
        return button;
    }

    @NonNull
    private Drawable circleBackground(@ColorInt int fill, @ColorInt int stroke) {
        return stroke == NO_STROKE
                ? theme.circle(fill)
                : theme.roundedStroke(fill, CONTROL_SIZE / 2f, stroke, 1);
    }

    private void attachPressFeedback(@NonNull View view) {
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> target.animate()
                        .scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate()
                        .scaleX(1f).scaleY(1f).setDuration(160).start();
                default -> {
                }
            }
            return false;
        });
    }

    private void addControl(@NonNull LinearLayout row, @NonNull View button, float gapDp) {
        LayoutParams params = new LayoutParams(
                theme.dp(CONTROL_SIZE), theme.dp(CONTROL_SIZE));
        params.setMarginStart(theme.dp(gapDp));
        row.addView(button, params);
    }
}
