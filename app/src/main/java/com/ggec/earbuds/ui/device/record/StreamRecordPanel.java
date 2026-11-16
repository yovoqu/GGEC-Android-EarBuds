package com.ggec.earbuds.ui.device.record;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoDialog;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 随录随传视图面板：展示录音主看板（计时、波形、操作按钮）与手机已存录音列表。
 */
public final class StreamRecordPanel {

    public interface Listener {
        void onStartStreamRecord();
        void onStopStreamRecord();
        void onTogglePlay(@NonNull SavedRecordItem item);
        void onDeleteLocalFile(@NonNull SavedRecordItem item);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final TextView timerText;
    private final WaveformView waveformView;
    private final TextView statusHintText;
    private final YoButton recordActionButton;

    private final TextView localFilesHeader;
    private final LinearLayout filesContainer;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

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

    public void render(@NonNull RecordUiState state) {
        boolean recording = state.isStreamRecording();
        boolean dataReceiving = state.isStreamDataReceiving();

        waveformView.setRecording(recording && dataReceiving);
        waveformView.setAmplitude(state.streamAmplitude());

        if (recording) {
            recordActionButton.setText(activity.getString(R.string.earbuds_record_stream_btn_stop));
            recordActionButton.setOnClickListener(v -> listener.onStopStreamRecord());

            if (dataReceiving) {
                timerText.setText(formatDuration(state.streamDurationMs()));
                statusHintText.setText(R.string.earbuds_record_stream_recording_hint);
            } else {
                timerText.setText(R.string.earbuds_record_stream_timer_default);
                statusHintText.setText(R.string.earbuds_record_stream_waiting_hint);
            }
        } else {
            timerText.setText(R.string.earbuds_record_stream_timer_default);
            statusHintText.setText(R.string.earbuds_record_stream_idle_hint);
            recordActionButton.setText(activity.getString(R.string.earbuds_record_stream_btn_start));
            recordActionButton.setOnClickListener(v -> listener.onStartStreamRecord());
        }

        // 更新本地已存文件列表
        List<SavedRecordItem> files = state.localFiles();
        localFilesHeader.setText(activity.getString(R.string.earbuds_record_local_files_title) + " (" + files.size() + ")");
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
            filesContainer.addView(buildFileItemView(file, state.playingFilePath()), matchWrap());
        }
    }

    private View buildFileItemView(@NonNull SavedRecordItem item, String playingPath) {
        boolean isPlaying = playingPath != null && playingPath.equals(item.filePath());

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                isPlaying ? theme.brand : theme.cardStroke, isPlaying ? 1.5f : 1f));

        // 左侧圆形底座播放/停止按键
        LinearLayout playCircle = new LinearLayout(activity);
        playCircle.setGravity(Gravity.CENTER);
        playCircle.setBackground(theme.rounded(isPlaying ? theme.brandSoft : theme.fill, 20f));
        int circleSize = theme.dp(40f);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(circleSize, circleSize);
        card.addView(playCircle, circleParams);

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
        card.addView(infoLayout, infoParams);

        // 顶行：文件名
        TextView nameText = theme.text(DeviceTheme.TEXT_BODY, isPlaying ? theme.brand : theme.text1,
                DeviceTheme.Weight.MEDIUM);
        nameText.setText(item.fileName());
        nameText.setSingleLine(true);
        infoLayout.addView(nameText, wrapContent());

        // 底行：格式指标
        TextView metaText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        String durationStr = formatDuration(item.durationMs());
        String sizeStr = DeviceFormats.size(item.sizeBytes());
        String dateStr = dateFormat.format(new Date(item.lastModifiedTime()));
        metaText.setText(durationStr + "  ·  " + sizeStr + "  ·  " + dateStr);
        LinearLayout.LayoutParams metaParams = wrapContent();
        metaParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        infoLayout.addView(metaText, metaParams);

        // 右侧极简删除按键
        ImageView deleteBtn = new ImageView(activity);
        deleteBtn.setImageResource(R.drawable.ic_record_trash);
        deleteBtn.setColorFilter(theme.text3, PorterDuff.Mode.SRC_IN);
        deleteBtn.setPadding(theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS));
        deleteBtn.setOnClickListener(v -> showDeleteConfirm(item));
        card.addView(deleteBtn, new LinearLayout.LayoutParams(theme.dp(36f), theme.dp(36f)));

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = theme.dp(DeviceTheme.SPACE_S);
        return card;
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

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", 0, m, s);
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
