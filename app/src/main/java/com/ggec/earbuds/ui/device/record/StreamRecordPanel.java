package com.ggec.earbuds.ui.device.record;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.PlaybackOutput;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.record.RecordState;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.navigation.YoMenu;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 随录随传视图面板：展示录音主看板（阶段 / 计时 / 波形 / 操作按钮）与手机已存录音列表。
 *
 * <p>本类只把 {@link RecordUiState} 画出来：阶段是枚举、时长由 {@link DeviceFormats#duration(long)}
 * 单点格式化、提示文案按阶段取资源；不判断「能不能录」，只用 {@link RecordUiState#canRecord} 的结果。</p>
 */
public final class StreamRecordPanel {

    public interface Listener {
        void onStartStreamRecord();
        void onStopStreamRecord();
        void onTogglePlay(@NonNull SavedRecordItem item);
        void onDeleteLocalFile(@NonNull SavedRecordItem item);
        void onSelectPlaybackOutput(@NonNull String outputId);
    }

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm";

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;
    private final YoComponents components = new YoComponents();

    private final LinearLayout root;
    private final TextView timerText;
    private final WaveformView waveformView;
    private final TextView statusHintText;
    private final YoButton recordActionButton;

    private final TextView localFilesHeader;
    private final LinearLayout filesContainer;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN, Locale.getDefault());

    public StreamRecordPanel(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                             @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // 1. 录音主看板卡片
        LinearLayout boardCard = new LinearLayout(activity);
        boardCard.setOrientation(LinearLayout.VERTICAL);
        boardCard.setGravity(Gravity.CENTER_HORIZONTAL);
        boardCard.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_XL),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_XL));
        boardCard.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                theme.cardStroke, 1f));

        // 计时器大字号展示
        timerText = theme.text(36f, theme.text1, DeviceTheme.Weight.BOLD);
        timerText.setText(R.string.earbuds_record_stream_timer_default);
        timerText.setGravity(Gravity.CENTER_HORIZONTAL);
        boardCard.addView(timerText, wrapContent());

        // 动态音频波形
        waveformView = new WaveformView(activity, theme);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(48f));
        waveParams.topMargin = theme.dp(DeviceTheme.SPACE_M);
        waveParams.bottomMargin = theme.dp(DeviceTheme.SPACE_M);
        boardCard.addView(waveformView, waveParams);

        // 状态说明提示
        statusHintText = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
        statusHintText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusHintText.setText(R.string.earbuds_record_stream_idle_hint);
        boardCard.addView(statusHintText, wrapContent());

        // 操作主按键
        recordActionButton = YoButton.create(activity)
                .setSize(YoButton.Size.MEDIUM)
                .build();
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                theme.dp(160f), ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = theme.dp(DeviceTheme.SPACE_L);
        boardCard.addView(recordActionButton.getView(), btnParams);

        LinearLayout.LayoutParams boardParams = matchWrap();
        boardParams.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        boardParams.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        boardParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        root.addView(boardCard, boardParams);

        // 2. 本地已存文件分区标题
        localFilesHeader = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text2, DeviceTheme.Weight.MEDIUM);
        localFilesHeader.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XL),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));
        root.addView(localFilesHeader, matchWrap());

        // 本地文件卡片列表容器
        filesContainer = new LinearLayout(activity);
        filesContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams filesParams = matchWrap();
        filesParams.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        filesParams.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        filesParams.bottomMargin = theme.dp(DeviceTheme.SPACE_XL);
        root.addView(filesContainer, filesParams);
    }

    @NonNull
    public View view() {
        return root;
    }

    /** 释放面板自持的 YoUI 组件（播放源切换菜单）。 */
    public void destroy() {
        components.close();
    }

    public void render(@NonNull RecordUiState state) {
        renderBoard(state);
        renderLocalFiles(state);
    }

    private void renderBoard(@NonNull RecordUiState state) {
        boolean recording = state.streamPhase() == RecordState.StreamPhase.RECORDING;
        waveformView.setRecording(recording);
        waveformView.setAmplitude(state.streamAmplitude());

        timerText.setText(recording
                ? DeviceFormats.duration(state.streamDurationMs())
                : activity.getString(R.string.earbuds_record_stream_timer_default));
        statusHintText.setText(hintRes(state));

        boolean active = state.streamActive();
        recordActionButton.setText(activity.getString(active
                ? R.string.earbuds_record_stream_btn_stop
                : R.string.earbuds_record_stream_btn_start));
        recordActionButton.setEnabled(state.canRecord(RecordMode.STREAM));
        if (active) {
            recordActionButton.setOnClickListener(v -> listener.onStopStreamRecord());
            return;
        }
        recordActionButton.setOnClickListener(v -> listener.onStartStreamRecord());
    }

    private void renderLocalFiles(@NonNull RecordUiState state) {
        List<SavedRecordItem> files = state.localFiles();
        localFilesHeader.setText(activity.getString(R.string.earbuds_record_files_title_count,
                activity.getString(R.string.earbuds_record_local_files_title), files.size()));
        filesContainer.removeAllViews();

        if (files.isEmpty()) {
            TextView emptyText = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
            emptyText.setText(R.string.earbuds_record_file_empty);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, theme.dp(DeviceTheme.SPACE_XL), 0, theme.dp(DeviceTheme.SPACE_XL));
            filesContainer.addView(emptyText, matchWrap());
            return;
        }

        for (SavedRecordItem file : files) {
            filesContainer.addView(buildFileItemView(file, state));
        }
    }

    @StringRes
    private int hintRes(@NonNull RecordUiState state) {
        if (!state.canRecord(RecordMode.STREAM)) {
            return R.string.earbuds_record_hint_unavailable;
        }
        return switch (state.streamPhase()) {
            case IDLE -> R.string.earbuds_record_stream_idle_hint;
            case STARTING, WAITING_DATA -> R.string.earbuds_record_stream_waiting_hint;
            case RECORDING -> R.string.earbuds_record_stream_recording_hint;
            case STOPPING -> R.string.earbuds_record_stream_stopping_hint;
        };
    }

    private View buildFileItemView(@NonNull SavedRecordItem item, @NonNull RecordUiState state) {
        boolean isPlaying = state.playing(item.filePath());

        // 播放时卡片纵向展开：顶行（播放键 / 信息 / 播放源 / 删除）+ 第二行进度条。
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                isPlaying ? theme.brand : theme.cardStroke, isPlaying ? 1.5f : 1f));

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(headerRow, matchWrap());

        // 左侧圆形底座播放/停止按键
        LinearLayout playCircle = new LinearLayout(activity);
        playCircle.setGravity(Gravity.CENTER);
        playCircle.setBackground(theme.rounded(isPlaying ? theme.brandSoft : theme.fill, 20f));
        int circleSize = theme.dp(40f);
        headerRow.addView(playCircle, new LinearLayout.LayoutParams(circleSize, circleSize));

        ImageView playIcon = new ImageView(activity);
        playIcon.setImageResource(isPlaying ? R.drawable.ic_record_pause_rect : R.drawable.ic_record_play_arrow);
        playIcon.setColorFilter(isPlaying ? theme.brand : theme.text1, PorterDuff.Mode.SRC_IN);
        int iconSize = theme.dp(20f);
        playCircle.addView(playIcon, new LinearLayout.LayoutParams(iconSize, iconSize));
        playCircle.setOnClickListener(v -> listener.onTogglePlay(item));

        // 中间文件信息详情
        LinearLayout infoLayout = new LinearLayout(activity);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = theme.dp(DeviceTheme.SPACE_M);
        infoParams.rightMargin = theme.dp(DeviceTheme.SPACE_S);
        headerRow.addView(infoLayout, infoParams);

        TextView nameText = theme.text(DeviceTheme.TEXT_BODY, isPlaying ? theme.brand : theme.text1,
                DeviceTheme.Weight.MEDIUM);
        nameText.setText(item.fileName());
        nameText.setSingleLine(true);
        infoLayout.addView(nameText, wrapContent());

        TextView metaText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        metaText.setText(String.join(DeviceFormats.META_SEPARATOR,
                DeviceFormats.duration(item.durationMs()),
                DeviceFormats.size(item.sizeBytes()),
                dateFormat.format(new Date(item.lastModifiedTime()))));
        LinearLayout.LayoutParams metaParams = wrapContent();
        metaParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        infoLayout.addView(metaText, metaParams);

        // 右侧播放源图标位：只在播放时出现，显示当前正在工作的物理播放器，点按切换。
        if (isPlaying) {
            PlaybackOutput output = state.playbackOutput().orElse(null);
            ImageView outputSlot = new ImageView(activity);
            outputSlot.setImageResource(outputIconRes(output));
            outputSlot.setColorFilter(theme.brand, PorterDuff.Mode.SRC_IN);
            outputSlot.setPadding(theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_S),
                    theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_S));
            outputSlot.setBackground(theme.roundedStroke(theme.brandSoft, 18f, theme.brand, 1f));
            outputSlot.setContentDescription(activity.getString(
                    R.string.earbuds_record_playback_switch_output, outputLabel(output)));
            outputSlot.setOnClickListener(v -> showOutputChooser(state));
            LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                    theme.dp(36f), theme.dp(36f));
            slotParams.setMarginEnd(theme.dp(DeviceTheme.SPACE_S));
            headerRow.addView(outputSlot, slotParams);
        }

        // 右侧极简删除按键
        ImageView deleteBtn = new ImageView(activity);
        deleteBtn.setImageResource(R.drawable.ic_record_trash);
        deleteBtn.setColorFilter(theme.text3, PorterDuff.Mode.SRC_IN);
        deleteBtn.setPadding(theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS));
        deleteBtn.setOnClickListener(v -> showDeleteConfirm(item));
        headerRow.addView(deleteBtn, new LinearLayout.LayoutParams(theme.dp(36f), theme.dp(36f)));

        if (isPlaying) {
            card.addView(buildPlaybackProgressRow(state), progressRowParams());
        }

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = theme.dp(DeviceTheme.SPACE_S);
        card.setLayoutParams(cardParams);
        return card;
    }

    /** 播放进度行：已播时间 · 进度条 · 总时长。总时长未知时进度条走不确定态。 */
    private View buildPlaybackProgressRow(@NonNull RecordUiState state) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView positionText = theme.text(DeviceTheme.TEXT_CAPTION, theme.brand, DeviceTheme.Weight.MEDIUM);
        positionText.setText(DeviceFormats.clock(state.playbackPositionMs()));
        row.addView(positionText, wrapContent());

        boolean durationKnown = state.playbackDurationMs() > 0L;
        ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setIndeterminate(!durationKnown);
        if (durationKnown) {
            bar.setProgress(state.playbackPercent() * 10);
        }
        bar.setProgressTintList(ColorStateList.valueOf(theme.brand));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(theme.cardStroke));
        bar.setMinimumHeight(theme.dp(4f));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                0, theme.dp(4f), 1f);
        barParams.leftMargin = theme.dp(DeviceTheme.SPACE_S);
        barParams.rightMargin = theme.dp(DeviceTheme.SPACE_S);
        row.addView(bar, barParams);

        TextView durationText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        durationText.setText(durationKnown
                ? DeviceFormats.clock(state.playbackDurationMs())
                : DeviceFormats.UNKNOWN);
        row.addView(durationText, wrapContent());
        return row;
    }

    private LinearLayout.LayoutParams progressRowParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = theme.dp(DeviceTheme.SPACE_S);
        return params;
    }

    private void showOutputChooser(@NonNull RecordUiState state) {
        List<PlaybackOutput> outputs = state.playbackOutputs();
        if (outputs.isEmpty()) {
            return;
        }
        String currentId = state.playbackOutput().map(PlaybackOutput::id).orElse("");
        YoMenu.Builder builder = YoMenu.create(activity)
                .setAnchorView(activity.findViewById(android.R.id.content));
        for (int index = 0; index < outputs.size(); index++) {
            PlaybackOutput output = outputs.get(index);
            String mark = output.id().equals(currentId)
                    ? activity.getString(R.string.earbuds_record_playback_output_current_mark)
                    : "";
            builder.addItem(index, outputLabel(output) + mark);
        }
        YoMenu menu = builder
                .setOnItemClickListener(id -> {
                    if (id < 0 || id >= outputs.size()) {
                        return;
                    }
                    PlaybackOutput selected = outputs.get(id);
                    if (!selected.id().equals(currentId)) {
                        listener.onSelectPlaybackOutput(selected.id());
                    }
                })
                .build();
        components.own(menu::destroy);
        menu.show();
    }

    @StringRes
    private int outputIconRes(@Nullable PlaybackOutput output) {
        if (output == null) {
            return R.drawable.ic_record_output_other;
        }
        return switch (output.kind()) {
            case EARBUDS -> R.drawable.ic_record_output_earbuds;
            case PHONE_SPEAKER -> R.drawable.ic_record_output_speaker;
            case EARPIECE -> R.drawable.ic_record_output_earpiece;
            case WIRED -> R.drawable.ic_record_output_wired;
            case USB -> R.drawable.ic_record_output_usb;
            case OTHER -> R.drawable.ic_record_output_other;
        };
    }

    @NonNull
    private String outputLabel(@Nullable PlaybackOutput output) {
        if (output == null) {
            return activity.getString(R.string.earbuds_record_playback_output_unknown);
        }
        return switch (output.kind()) {
            case EARBUDS -> activity.getString(R.string.earbuds_record_playback_output_earbuds);
            case PHONE_SPEAKER -> activity.getString(R.string.earbuds_record_playback_output_speaker);
            case EARPIECE -> activity.getString(R.string.earbuds_record_playback_output_earpiece);
            case WIRED -> activity.getString(R.string.earbuds_record_playback_output_wired);
            case USB -> activity.getString(R.string.earbuds_record_playback_output_usb);
            case OTHER -> activity.getString(R.string.earbuds_record_playback_output_other);
        };
    }

    private void showDeleteConfirm(@NonNull SavedRecordItem item) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.earbuds_record_delete_confirm_title))
                .setMessage(activity.getString(R.string.earbuds_record_delete_confirm_msg))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_dialog_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.device_dialog_confirm),
                                () -> listener.onDeleteLocalFile(item))
                )
                .show();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
