package com.ggec.earbuds.ui.ai.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.ai.settings.AiSettingsActivity;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AiGraph;
import com.yohu.youi.api.feedback.YoToast;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 语音通话页：全屏单页，从上到下只有四段——页头、光晕主角、状态文案、控制排。
 *
 * <p>这一页不画标题栏、不套 YoUI 的窗口壳（标签栏在外面），系统栏留白由本页自己吃：
 * 一个 View 只挂一个 inset 监听，避免两层各补一遍。竖屏由清单锁死。</p>
 *
 * <p>它只负责画：状态机在语音会话里，页面拿到的每一帧都是 {@link AiVoiceSnapshot} 的投影。
 * 语音交流只有实时一条链路，页面里没有任何按模式分支的代码。</p>
 *
 * <p>失败时只给一个出口，另一个**隐藏而不是并排摆着**：能靠改配置解决的（没模型、鉴权、
 * 接口不存在）去设置，能靠再试一次的（网络、超时、服务端、限流）才给重试。
 * 两个都给等于让用户猜该点哪个。</p>
 */
public final class AiCallActivity extends AppCompatActivity {

    private static final String EXTRA_PLATFORM_ID = "extra_platform_id";
    private static final String EXTRA_MODEL_ID = "extra_model_id";

    private static final String STATE_SPEAKER = "ai_call_speaker";

    /** 描边参数的哨兵值：0 表示这颗按钮是实心底，不画边。 */
    private static final int NO_STROKE = 0;
    /** 没有出口 / 去设置 / 重试。 */
    private static final int ACTION_NONE = 0;
    private static final int ACTION_SETTINGS = 1;
    private static final int ACTION_RETRY = 2;

    private static final float CLOSE_SIZE = 40f;
    private static final float CONTROL_SIZE = 56f;
    private static final float ORB_SIZE = 240f;
    private static final float HEADER_HEIGHT = 64f;
    private static final float ACTION_HEIGHT = 48f;
    private static final float ACTION_MIN_WIDTH = 120f;

    private final ActivityResultLauncher<String> microphone = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCall();
                } else {
                    showPermissionDenied();
                }
            });

    private AiTheme theme;
    private AiCallViewModel viewModel;
    private AiSelection selection;

    private VoiceOrbView orb;
    private TextView statusTitle;
    private TextView statusReason;
    private TextView actionButton;
    private TextView modeLabel;
    private ImageView camera;
    private ImageView microphoneButton;
    private ImageView speakerButton;
    private ImageView hangUpButton;
    private View controlsBlock;

    private boolean askedForMicrophone;
    private boolean denied;
    private boolean controlsLive;
    private boolean endedLook;
    private boolean finishing;
    private boolean muted;
    private boolean speakerOn = true;
    private boolean interruptibleShown;
    private int actionMode = ACTION_NONE;

    /**
     * 组装拉起这一页的 Intent。
     *
     * <p>{@link AiSelection} 是领域 record，不做 Parcelable：这里摊平成两个 extra，
     * 页面自己就能重建选择，不必从界面层去读设置仓库（那条路在分层检查里是禁止的）。</p>
     */
    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull AiSelection selection) {
        return new Intent(context, AiCallActivity.class)
                .putExtra(EXTRA_PLATFORM_ID, selection.platformId())
                .putExtra(EXTRA_MODEL_ID, selection.modelId());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = AiTheme.of(this);
        selection = readSelection(getIntent());
        if (savedInstanceState != null) {
            speakerOn = savedInstanceState.getBoolean(STATE_SPEAKER, true);
        }

        YoUi.edgeToEdge(this);
        // 通话页一停下来屏幕就暗，等于中途断线；这只影响亮屏，不改变任何业务状态。
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new ViewModelProvider(this, AiGraph.get(this).factory()).get(AiCallViewModel.class);
        setContentView(buildRoot());
        viewModel.snapshot().observe(this, this::render);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasMicrophone()) {
            if (askedForMicrophone) {
                showPermissionDenied();
            } else {
                askedForMicrophone = true;
                microphone.launch(Manifest.permission.RECORD_AUDIO);
            }
            return;
        }
        startCall();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_SPEAKER, speakerOn);
    }

    // ---- 装配 ----

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(buildStage(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        controlsBlock = buildControls();
        root.addView(controlsBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            controlsBlock.setPadding(theme.dp(AiTheme.SPACE_L), 0, theme.dp(AiTheme.SPACE_L),
                    bars.bottom + theme.dp(AiTheme.SPACE_XL));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        return root;
    }

    /** 页头：左边关闭，中间两行（页名 20sp + 当前模式 12sp）。右边故意留空。 */
    private View buildHeader() {
        FrameLayout header = new FrameLayout(this);
        header.setMinimumHeight(theme.dp(HEADER_HEIGHT));
        header.setPadding(theme.dp(AiTheme.PAGE_INSET), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.PAGE_INSET), 0);

        ImageView close = theme.iconButton(R.drawable.ic_ai_call_close, theme.text2,
                CLOSE_SIZE, 10f, getString(R.string.ai_call_close));
        close.setOnClickListener(view -> hangUp());
        header.addView(close, new FrameLayout.LayoutParams(
                theme.dp(CLOSE_SIZE), theme.dp(CLOSE_SIZE), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        title.setText(R.string.ai_call_title);
        titles.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        modeLabel = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        modeLabel.setText(R.string.ai_call_mode_realtime);
        modeLabel.setSingleLine(true);
        modeLabel.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = theme.dp(AiTheme.SPACE_XS);
        titles.addView(modeLabel, labelParams);

        FrameLayout.LayoutParams titlesParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        // 左右各让开一个触控目标再多一小段：标题居中在"按钮之间的空白"里，而不是整个页宽。
        titlesParams.leftMargin = theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M);
        titlesParams.rightMargin = theme.dp(AiTheme.TOUCH_TARGET + AiTheme.SPACE_M);
        header.addView(titles, titlesParams);
        return header;
    }

    /** 中部：光晕是主角；下面一行状态，失败时再加一行原因与唯一的出口。 */
    private View buildStage() {
        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER);

        orb = new VoiceOrbView(this, theme);
        orb.setOnClickListener(view -> viewModel.interrupt());
        // setOnClickListener 会顺带把它变成可点；第一帧之前这一页还没有可打断的东西，先收回来。
        orb.setClickable(false);
        stage.addView(orb, new LinearLayout.LayoutParams(theme.dp(ORB_SIZE), theme.dp(ORB_SIZE)));

        statusTitle = theme.text(AiTheme.TEXT_BODY, theme.text1);
        statusTitle.setText(R.string.ai_call_phase_connecting);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setMaxLines(2);
        statusTitle.setLineSpacing(theme.dpf(3), 1f);
        stage.addView(statusTitle, fullWidth(theme.dp(AiTheme.SPACE_XL)));

        // 原因只在失败时出现：它比结论低一档（13sp / 次级色），读的时候先看到"通话已断开"。
        statusReason = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2);
        statusReason.setGravity(Gravity.CENTER);
        statusReason.setMaxLines(2);
        statusReason.setLineSpacing(theme.dpf(2), 1f);
        statusReason.setVisibility(View.GONE);
        stage.addView(statusReason, fullWidth(theme.dp(AiTheme.SPACE_S)));

        actionButton = theme.text(AiTheme.TEXT_BODY, theme.onBrand, AiTheme.Weight.MEDIUM);
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setText(R.string.ai_call_retry);
        actionButton.setMinWidth(theme.dp(ACTION_MIN_WIDTH));
        actionButton.setPadding(theme.dp(AiTheme.SPACE_XL), 0, theme.dp(AiTheme.SPACE_XL), 0);
        actionButton.setBackground(theme.pressable(
                theme.rounded(theme.brand, AiTheme.RADIUS_FIELD), AiTheme.RADIUS_FIELD));
        actionButton.setVisibility(View.GONE);
        actionButton.setOnClickListener(view -> onAction());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(ACTION_HEIGHT));
        actionParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        actionParams.gravity = Gravity.CENTER_HORIZONTAL;
        stage.addView(actionButton, actionParams);
        return stage;
    }

    /** 四个 56dp 圆钮：拍照（描边，低一档）、麦克风、扬声器、挂断。 */
    private View buildControls() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(theme.dp(AiTheme.SPACE_L), 0, theme.dp(AiTheme.SPACE_L),
                theme.dp(AiTheme.SPACE_XL));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        camera = roundButton(R.drawable.ic_ai_call_camera, 24f,
                theme.fill, theme.text2, theme.cardStroke, R.string.ai_call_camera);
        camera.setOnClickListener(view ->
                YoToast.show(this, getString(R.string.ai_call_camera_unavailable)));

        microphoneButton = roundButton(R.drawable.ic_ai_call_mic, 24f,
                theme.brandSoft, theme.brand, NO_STROKE, R.string.ai_call_mute);
        microphoneButton.setOnClickListener(view -> viewModel.setMuted(!muted));

        speakerButton = roundButton(R.drawable.ic_ai_call_speaker, 24f,
                theme.brandSoft, theme.brand, NO_STROKE, R.string.ai_call_speaker_to_earpiece);
        speakerButton.setOnClickListener(view -> {
            speakerOn = !speakerOn;
            viewModel.setSpeakerphone(speakerOn);
            applySpeakerLook();
        });

        hangUpButton = roundButton(R.drawable.ic_ai_call_hangup, 24f,
                theme.alert, theme.onBrand, NO_STROKE, R.string.ai_call_hang_up);
        hangUpButton.setOnClickListener(view -> hangUp());

        addControl(row, camera, 0f);
        addControl(row, microphoneButton, AiTheme.SPACE_M);
        addControl(row, speakerButton, AiTheme.SPACE_M);
        addControl(row, hangUpButton, AiTheme.SPACE_L);

        block.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyMicLook();
        applySpeakerLook();
        return block;
    }

    // ---- 渲染 ----

    /**
     * 快照是高频的（电平约每秒 25 帧），所以每一处都先比再画：
     * 只有相位、文案、静音态、出口真正变了才动视图，否则光晕的呼吸会和重建背景抢同一帧。
     */
    private void render(@Nullable AiVoiceSnapshot snapshot) {
        if (finishing || denied || snapshot == null) {
            return;
        }
        AiVoiceSnapshot.State state = snapshot.state();

        orb.setPhase(phaseOf(state));
        orb.setLevel(state == AiVoiceSnapshot.State.LISTENING ? snapshot.inputLevel() : 0f);

        boolean failed = state == AiVoiceSnapshot.State.FAILED;
        boolean ended = state == AiVoiceSnapshot.State.ENDED;
        showStatus(getString(titleOf(state)), failed ? reasonOf(snapshot.failure()) : null);
        showAction(failed ? snapshot.failure() : null);

        setControlsLive(isLive(state));
        applyEndedLook(failed || ended);

        boolean nextMuted = state == AiVoiceSnapshot.State.MUTED;
        if (nextMuted != muted) {
            muted = nextMuted;
            if (!endedLook) {
                applyMicLook();
            }
        }

        // 只有"它正在生成"的时候，点光晕才是在说"你停一下"；其余时刻这一页没有可打断的东西。
        boolean interruptible = state == AiVoiceSnapshot.State.THINKING
                || state == AiVoiceSnapshot.State.SPEAKING;
        if (interruptible != interruptibleShown) {
            interruptibleShown = interruptible;
            orb.setClickable(interruptible);
            orb.setContentDescription(interruptible ? getString(R.string.ai_call_interrupt) : null);
        }
    }

    /**
     * 权限被拒：会话根本没起来，页面停在静态灰盘上，并把"为什么没有声音"写在状态行里。
     * 静音与扬声器同时禁用——它们都是发给会话的指令，没有会话就没有意义。
     */
    private void showPermissionDenied() {
        denied = true;
        orb.setPhase(VoiceOrbView.Phase.OFFLINE);
        orb.setClickable(false);
        orb.setContentDescription(null);
        showStatus(getString(R.string.ai_call_no_permission), null);
        showAction(null);
        setControlsLive(false);
    }

    /** 状态行：一行结论；失败时多一行原因（13sp 次级色），其余状态原因整行不占位。 */
    private void showStatus(@NonNull CharSequence title, @Nullable CharSequence reason) {
        if (!TextUtils.equals(statusTitle.getText(), title)) {
            statusTitle.animate().cancel();
            statusTitle.animate().alpha(0f).setDuration(90).withEndAction(() -> {
                statusTitle.setText(title);
                statusTitle.animate().alpha(1f).setDuration(160).start();
            }).start();
        }
        if (reason == null) {
            statusReason.setVisibility(View.GONE);
            return;
        }
        statusReason.setText(reason);
        statusReason.setVisibility(View.VISIBLE);
    }

    /**
     * 失败出口只有一个：可修的给「去设置」，临时的给「重试」。
     * 另一个不是变灰，是根本不出现——并排两个按钮等于把判断成本丢给用户。
     */
    private void showAction(@Nullable AiFailure failure) {
        if (failure == null) {
            setActionMode(ACTION_NONE);
            return;
        }
        setActionMode(failure.kind().retryable() ? ACTION_RETRY : ACTION_SETTINGS);
    }

    private void setActionMode(int mode) {
        if (actionMode == mode) {
            return;
        }
        actionMode = mode;
        if (mode == ACTION_NONE) {
            actionButton.setVisibility(View.GONE);
            return;
        }
        actionButton.setText(mode == ACTION_RETRY ? R.string.ai_call_retry : R.string.ai_call_failed_action);
        actionButton.setVisibility(View.VISIBLE);
    }

    private void onAction() {
        if (actionMode == ACTION_RETRY) {
            retryCall();
        } else if (actionMode == ACTION_SETTINGS) {
            // 去设置是把这一通放弃掉：修好配置后从对话页重新拨，比在死页面上等更清楚。
            finishing = true;
            viewModel.hangUp();
            startActivity(AiSettingsActivity.intent(this));
            finish();
        }
    }

    /**
     * 重试：先挂掉旧会话再起一条新的。
     *
     * <p>不能只调 {@code start}——它会因为"已经有一条会话"直接返回，页面就停在失败态不动；
     * 也不能复用旧会话：失败之后它内部的状态已经不可信。</p>
     */
    private void retryCall() {
        viewModel.hangUp();
        setActionMode(ACTION_NONE);
        viewModel.start(selection);
    }

    private void setControlsLive(boolean live) {
        if (controlsLive == live) {
            return;
        }
        controlsLive = live;
        if (!endedLook) {
            applyMicLook();
            applySpeakerLook();
        }
    }

    /**
     * 失败与结束后，这一页最重的动作不该还是"挂断"（那只是关页面），
     * 所以挂断降级成中性，另外三个钮一起置灰：同屏只留一个还能点的东西。
     */
    private void applyEndedLook(boolean ended) {
        if (endedLook == ended) {
            return;
        }
        endedLook = ended;
        hangUpButton.setBackground(theme.pressable(circleBackground(
                ended ? theme.fill : theme.alert, NO_STROKE), CONTROL_SIZE / 2f));
        hangUpButton.setColorFilter(ended ? theme.text2 : theme.onBrand, PorterDuff.Mode.SRC_IN);
        hangUpButton.setContentDescription(getString(ended
                ? R.string.ai_call_close : R.string.ai_call_hang_up));
        camera.setEnabled(!ended);
        camera.setAlpha(ended ? 0.4f : 1f);

        if (ended) {
            dim(microphoneButton);
            dim(speakerButton);
        } else {
            applyMicLook();
            applySpeakerLook();
        }
    }

    private void dim(@NonNull ImageView button) {
        button.setEnabled(false);
        button.setAlpha(0.4f);
        button.setBackground(theme.pressable(circleBackground(theme.fill, NO_STROKE), CONTROL_SIZE / 2f));
    }

    /**
     * 静音键的状态只认快照：会话确认进 MUTED 之后按钮才变成描边，
     * 否则会出现"按钮说静音了、实际还在传"的假象。
     */
    private void applyMicLook() {
        microphoneButton.setImageResource(muted
                ? R.drawable.ic_ai_call_mic_off : R.drawable.ic_ai_call_mic);
        microphoneButton.setColorFilter(muted ? theme.text2 : theme.brand, PorterDuff.Mode.SRC_IN);
        microphoneButton.setBackground(theme.pressable(circleBackground(
                muted ? theme.fill : theme.brandSoft, muted ? theme.cardStroke : NO_STROKE),
                CONTROL_SIZE / 2f));
        microphoneButton.setContentDescription(getString(muted
                ? R.string.ai_call_unmute : R.string.ai_call_mute));
        microphoneButton.setEnabled(controlsLive);
        microphoneButton.setAlpha(controlsLive ? 1f : 0.4f);
    }

    /** 扬声器开 = 品牌淡底；关 = 描边。 */
    private void applySpeakerLook() {
        speakerButton.setImageResource(speakerOn
                ? R.drawable.ic_ai_call_speaker : R.drawable.ic_ai_call_speaker_off);
        speakerButton.setColorFilter(speakerOn ? theme.brand : theme.text2, PorterDuff.Mode.SRC_IN);
        speakerButton.setBackground(theme.pressable(circleBackground(
                speakerOn ? theme.brandSoft : theme.fill,
                speakerOn ? NO_STROKE : theme.cardStroke), CONTROL_SIZE / 2f));
        speakerButton.setContentDescription(getString(speakerOn
                ? R.string.ai_call_speaker_to_earpiece : R.string.ai_call_speaker_to_speaker));
        speakerButton.setEnabled(controlsLive);
        speakerButton.setAlpha(controlsLive ? 1f : 0.4f);
    }

    // ---- 交互 ----

    private void startCall() {
        denied = false;
        viewModel.start(selection);
        viewModel.setSpeakerphone(speakerOn);
        setControlsLive(true);
    }

    private void hangUp() {
        if (finishing) {
            return;
        }
        finishing = true;
        viewModel.hangUp();
        finish();
    }

    // ---- 小工具 ----

    private boolean hasMicrophone() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isLive(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, LISTENING, THINKING, SPEAKING, MUTED -> true;
            case IDLE, FAILED, ENDED -> false;
        };
    }

    @NonNull
    private static VoiceOrbView.Phase phaseOf(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case CONNECTING, IDLE -> VoiceOrbView.Phase.CONNECTING;
            case LISTENING -> VoiceOrbView.Phase.LISTENING;
            case THINKING -> VoiceOrbView.Phase.THINKING;
            case SPEAKING -> VoiceOrbView.Phase.SPEAKING;
            case MUTED -> VoiceOrbView.Phase.MUTED;
            // 失败与结束都是"链路没了"：同一块灰盘，不做两种死法。
            case FAILED, ENDED -> VoiceOrbView.Phase.OFFLINE;
        };
    }

    @StringRes
    private static int titleOf(@NonNull AiVoiceSnapshot.State state) {
        return switch (state) {
            case IDLE, CONNECTING -> R.string.ai_call_phase_connecting;
            case LISTENING -> R.string.ai_call_phase_listening;
            case THINKING -> R.string.ai_call_phase_thinking;
            case SPEAKING -> R.string.ai_call_phase_speaking;
            case MUTED -> R.string.ai_call_phase_muted;
            case FAILED -> R.string.ai_call_phase_failed;
            case ENDED -> R.string.ai_call_phase_ended;
        };
    }

    /** 失败原因来自领域失败种类；平台原话可能为空，所以只翻种类。 */
    @Nullable
    private String reasonOf(@Nullable AiFailure failure) {
        return failure == null ? null : getString(AiMessages.failure(failure.kind()));
    }

    @NonNull
    private LinearLayout.LayoutParams fullWidth(int topMarginPx) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        params.leftMargin = theme.dp(AiTheme.PAGE_INSET);
        params.rightMargin = theme.dp(AiTheme.PAGE_INSET);
        return params;
    }

    @NonNull
    private ImageView roundButton(@DrawableRes int icon, float iconDp,
                                  @ColorInt int fill, @ColorInt int tint, @ColorInt int stroke,
                                  @StringRes int description) {
        ImageView button = theme.icon(icon, tint);
        int padding = theme.dp((CONTROL_SIZE - iconDp) / 2f);
        button.setPadding(padding, padding, padding, padding);
        button.setBackground(theme.pressable(circleBackground(fill, stroke), CONTROL_SIZE / 2f));
        button.setContentDescription(getString(description));
        attachPressFeedback(button);
        return button;
    }

    /** 描边给"低一档"的按钮用（拍照），实心给当前是通的一侧用；两者半径都是半个边长。 */
    @NonNull
    private Drawable circleBackground(@ColorInt int fill, @ColorInt int stroke) {
        return stroke == NO_STROKE
                ? theme.circle(fill)
                : theme.roundedStroke(fill, CONTROL_SIZE / 2f, stroke, 1);
    }

    /** 按下的缩放：0.96 足够被眼睛捕捉，又不至于让整排按钮跟着晃。水波纹由主题层负责。 */
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                theme.dp(CONTROL_SIZE), theme.dp(CONTROL_SIZE));
        params.setMarginStart(theme.dp(gapDp));
        row.addView(button, params);
    }

    /**
     * 缺字段时给空串而不是 null：让会话按"没有平台/没有模型"失败，
     * 而不是让页面替它挑一个。空串在 planFor 里会得到一条说清缺什么的失败原因。
     */
    @NonNull
    private static AiSelection readSelection(@Nullable Intent intent) {
        String platformId = intent == null ? null : intent.getStringExtra(EXTRA_PLATFORM_ID);
        String modelId = intent == null ? null : intent.getStringExtra(EXTRA_MODEL_ID);
        return new AiSelection(platformId == null ? "" : platformId, modelId == null ? "" : modelId);
    }
}
