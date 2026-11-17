package com.ggec.earbuds.ui.device.record;

import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.record.RecordState;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.List;
import java.util.Optional;

/**
 * 耳机本地录音视图面板：录音看板、刷新入口、同步进度卡片、耳机内文件列表。
 *
 * <p>本类只渲染 {@link RecordUiState}：录音阶段、同步阶段都是状态里的枚举，
 * 不存在「刷新中」这类视图自持标志位；按钮可写性只看 {@link RecordUiState#canRecord}。</p>
 */
public final class LocalRecordPanel {

    public interface Listener {
        void onStartDeviceRecord();
        void onStopDeviceRecord();
        void onRefreshDeviceRecords();
        void onSyncDeviceRecord(@NonNull String fileId);
        void onCancelSync();
        void onDeleteDeviceRecord(@NonNull String fileId);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final Listener listener;

    private final LinearLayout root;
    private final TextView timerText;
    private final TextView statusHintText;
    private final YoButton recordActionButton;

    private final LinearLayout syncProgressCard;
    private final TextView syncTitleText;
    private final TextView syncPercentText;
    private final ProgressBar syncProgressBar;
    private final YoButton syncCancelBtn;

    private final TextView deviceFilesTitleText;
    private final LinearLayout refreshActionLayout;
    private final ImageView refreshIcon;
    private final TextView refreshLabel;
    private final LinearLayout filesContainer;

    public LocalRecordPanel(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                            @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;

        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // 1. 耳机本地录音主控制看板
        LinearLayout boardCard = new LinearLayout(activity);
        boardCard.setOrientation(LinearLayout.VERTICAL);
        boardCard.setGravity(Gravity.CENTER_HORIZONTAL);
        boardCard.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_XL),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_XL));
        boardCard.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                theme.cardStroke, 1f));

        // 录音计时器
        timerText = theme.text(36f, theme.text1, DeviceTheme.Weight.BOLD);
        timerText.setText(R.string.earbuds_record_stream_timer_default);
        timerText.setGravity(Gravity.CENTER_HORIZONTAL);
        boardCard.addView(timerText, wrapContent());

        // 状态说明微文案
        statusHintText = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
        statusHintText.setGravity(Gravity.CENTER_HORIZONTAL);
        statusHintText.setText(R.string.earbuds_record_device_idle_hint);
        LinearLayout.LayoutParams hintParams = wrapContent();
        hintParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        hintParams.bottomMargin = theme.dp(DeviceTheme.SPACE_M);
        boardCard.addView(statusHintText, hintParams);

        // 操作主按键（开始/结束耳机录制）
        recordActionButton = YoButton.create(activity)
                .setSize(YoButton.Size.MEDIUM)
                .build();
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                theme.dp(160f), ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = theme.dp(DeviceTheme.SPACE_M);
        boardCard.addView(recordActionButton.getView(), btnParams);

        LinearLayout.LayoutParams boardParams = matchWrap();
        boardParams.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        boardParams.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        boardParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        root.addView(boardCard, boardParams);

        // 2. 同步进度卡片（传输中显示）
        syncProgressCard = new LinearLayout(activity);
        syncProgressCard.setOrientation(LinearLayout.VERTICAL);
        syncProgressCard.setPadding(theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M));
        syncProgressCard.setBackground(theme.roundedStroke(theme.brandSoft, DeviceTheme.RADIUS_ROW,
                theme.brand, 1f));
        syncProgressCard.setVisibility(View.GONE);

        LinearLayout syncTopRow = new LinearLayout(activity);
        syncTopRow.setOrientation(LinearLayout.HORIZONTAL);
        syncTopRow.setGravity(Gravity.CENTER_VERTICAL);
        syncProgressCard.addView(syncTopRow, matchWrap());

        syncTitleText = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        syncTopRow.addView(syncTitleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        syncCancelBtn = YoButton.create(activity)
                .setSize(YoButton.Size.SMALL)
                .build();
        syncCancelBtn.setText(activity.getString(R.string.earbuds_record_sync_cancel));
        syncCancelBtn.setOnClickListener(v -> listener.onCancelSync());
        syncTopRow.addView(syncCancelBtn.getView(), wrapContent());

        syncProgressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        syncProgressBar.setMax(100);
        LinearLayout.LayoutParams barParams = matchWrap();
        barParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        barParams.bottomMargin = theme.dp(DeviceTheme.SPACE_XS);
        syncProgressCard.addView(syncProgressBar, barParams);

        syncPercentText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text2);
        syncProgressCard.addView(syncPercentText, wrapContent());

        LinearLayout.LayoutParams syncCardParams = matchWrap();
        syncCardParams.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        syncCardParams.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        syncCardParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        root.addView(syncProgressCard, syncCardParams);

        // 3. 耳机存储文件标题栏（左侧标题，右侧刷新按键）
        LinearLayout sectionHeaderRow = new LinearLayout(activity);
        sectionHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        sectionHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        sectionHeaderRow.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XL),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_S));

        deviceFilesTitleText = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text2, DeviceTheme.Weight.MEDIUM);
        sectionHeaderRow.addView(deviceFilesTitleText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧刷新按钮组合（精致微交互）
        refreshActionLayout = new LinearLayout(activity);
        refreshActionLayout.setOrientation(LinearLayout.HORIZONTAL);
        refreshActionLayout.setGravity(Gravity.CENTER_VERTICAL);
        refreshActionLayout.setPadding(theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_XS));
        refreshActionLayout.setBackground(theme.rounded(theme.fill, theme.dp(DeviceTheme.RADIUS_FIELD)));
        refreshActionLayout.setOnClickListener(v -> listener.onRefreshDeviceRecords());

        refreshIcon = new ImageView(activity);
        refreshIcon.setImageResource(R.drawable.ic_record_refresh_arrow);
        refreshIcon.setColorFilter(theme.brand, PorterDuff.Mode.SRC_IN);
        int iconSize = theme.dp(16f);
        refreshActionLayout.addView(refreshIcon, new LinearLayout.LayoutParams(iconSize, iconSize));

        refreshLabel = theme.text(DeviceTheme.TEXT_CAPTION, theme.brand, DeviceTheme.Weight.MEDIUM);
        refreshLabel.setText(R.string.earbuds_record_device_refresh);
        LinearLayout.LayoutParams labelParams = wrapContent();
        labelParams.leftMargin = theme.dp(DeviceTheme.SPACE_XS);
        refreshActionLayout.addView(refreshLabel, labelParams);

        sectionHeaderRow.addView(refreshActionLayout, wrapContent());
        root.addView(sectionHeaderRow, matchWrap());

        // 耳机文件卡片列表容器
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
        renderBoard(state);
        renderSyncCard(state);
        renderDeviceFiles(state);
    }

    private void renderBoard(@NonNull RecordUiState state) {
        RecordState.DeviceRecordPhase phase = state.deviceRecordPhase();
        boolean active = state.deviceRecordActive();
        boolean busy = phase == RecordState.DeviceRecordPhase.STARTING
                || phase == RecordState.DeviceRecordPhase.STOPPING;

        timerText.setText(phase == RecordState.DeviceRecordPhase.ACTIVE
                ? DeviceFormats.duration(state.deviceRecordDurationMs())
                : activity.getString(R.string.earbuds_record_stream_timer_default));
        statusHintText.setText(hintRes(state));
        recordActionButton.setText(activity.getString(active
                ? R.string.earbuds_record_device_stop
                : R.string.earbuds_record_device_start));
        recordActionButton.setEnabled(state.canRecord(RecordMode.LOCAL) && !busy);
        if (active) {
            recordActionButton.setOnClickListener(v -> listener.onStopDeviceRecord());
            return;
        }
        recordActionButton.setOnClickListener(v -> listener.onStartDeviceRecord());
    }

    private void renderSyncCard(@NonNull RecordUiState state) {
        if (!state.syncing()) {
            syncProgressCard.setVisibility(View.GONE);
            return;
        }
        syncProgressCard.setVisibility(View.VISIBLE);
        syncTitleText.setText(activity.getString(R.string.earbuds_record_sync_transferring,
                displayNameOf(state, state.syncingFileId())));
        syncProgressBar.setProgress(state.syncPercent());
        syncPercentText.setText(activity.getString(R.string.earbuds_record_sync_progress,
                state.syncPercent(),
                DeviceFormats.size(state.syncTransferredBytes()),
                DeviceFormats.size(state.syncTotalBytes())));
    }

    private void renderDeviceFiles(@NonNull RecordUiState state) {
        List<RecordFileView> files = state.deviceFiles();
        deviceFilesTitleText.setText(activity.getString(R.string.earbuds_record_files_title_count,
                activity.getString(R.string.earbuds_record_device_files_title), files.size()));
        refreshActionLayout.setEnabled(state.canRecord(RecordMode.LOCAL));
        filesContainer.removeAllViews();

        if (files.isEmpty()) {
            TextView emptyText = theme.text(DeviceTheme.TEXT_CALLOUT, theme.hint);
            emptyText.setText(R.string.earbuds_record_device_empty);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, theme.dp(DeviceTheme.SPACE_XL), 0, theme.dp(DeviceTheme.SPACE_XL));
            filesContainer.addView(emptyText, matchWrap());
            return;
        }

        for (RecordFileView file : files) {
            filesContainer.addView(buildDeviceFileItemView(file, state), matchWrap());
        }
    }

    @StringRes
    private int hintRes(@NonNull RecordUiState state) {
        if (!state.canRecord(RecordMode.LOCAL)) {
            return R.string.earbuds_record_hint_unavailable;
        }
        return switch (state.deviceRecordPhase()) {
            case IDLE -> R.string.earbuds_record_device_idle_hint;
            case STARTING -> R.string.earbuds_record_device_starting_hint;
            case ACTIVE -> R.string.earbuds_record_device_recording_hint;
            case STOPPING -> R.string.earbuds_record_device_stopping_hint;
        };
    }

    /** 同步中的文件 id → 展示名：优先用设备上报的文件名，未上报时退回 id 本身。 */
    @NonNull
    private String displayNameOf(@NonNull RecordUiState state, @NonNull Optional<String> fileId) {
        if (fileId.isEmpty()) {
            return "";
        }
        String id = fileId.get();
        for (RecordFileView file : state.deviceFiles()) {
            if (file.fileId().equals(id)) {
                return file.name();
            }
        }
        return id;
    }

    private View buildDeviceFileItemView(@NonNull RecordFileView file, @NonNull RecordUiState state) {
        boolean isThisSyncing = state.syncing()
                && state.syncingFileId().equals(Optional.of(file.fileId()));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M));
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_ROW,
                theme.cardStroke, 1f));

        // 左侧波纹文件图标容器
        LinearLayout iconBox = new LinearLayout(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(theme.rounded(theme.fill, 20f));
        int circleSize = theme.dp(40f);
        card.addView(iconBox, new LinearLayout.LayoutParams(circleSize, circleSize));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_record_wave_file);
        icon.setColorFilter(theme.brand, PorterDuff.Mode.SRC_IN);
        int iconSize = theme.dp(20f);
        iconBox.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

        // 中间详情
        LinearLayout infoLayout = new LinearLayout(activity);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.leftMargin = theme.dp(DeviceTheme.SPACE_M);
        infoParams.rightMargin = theme.dp(DeviceTheme.SPACE_S);
        card.addView(infoLayout, infoParams);

        TextView nameText = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        nameText.setText(file.name());
        infoLayout.addView(nameText, wrapContent());

        TextView metaText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        metaText.setText(String.join(DeviceFormats.META_SEPARATOR,
                activity.getString(R.string.earbuds_record_file_id, file.fileId()),
                DeviceFormats.duration(file.durationSeconds() * 1000L),
                DeviceFormats.size(file.sizeBytes())));
        LinearLayout.LayoutParams metaParams = wrapContent();
        metaParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        infoLayout.addView(metaText, metaParams);

        // 同步按键
        YoButton syncBtn = YoButton.create(activity)
                .setSize(YoButton.Size.SMALL)
                .build();
        if (isThisSyncing) {
            syncBtn.setText(activity.getString(R.string.earbuds_record_device_syncing,
                    state.syncPercent()));
            syncBtn.setEnabled(false);
        } else {
            syncBtn.setText(activity.getString(R.string.earbuds_record_device_sync));
            syncBtn.setEnabled(state.canRecord(RecordMode.LOCAL));
            syncBtn.setOnClickListener(v -> listener.onSyncDeviceRecord(file.fileId()));
        }
        card.addView(syncBtn.getView(), wrapContent());

        // 删除按键（自研垃圾桶图标）
        ImageView deleteBtn = new ImageView(activity);
        deleteBtn.setImageResource(R.drawable.ic_record_trash);
        deleteBtn.setColorFilter(theme.text3, PorterDuff.Mode.SRC_IN);
        deleteBtn.setPadding(theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS));
        LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(theme.dp(36f), theme.dp(36f));
        delParams.leftMargin = theme.dp(DeviceTheme.SPACE_S);
        deleteBtn.setOnClickListener(v -> showDeleteConfirm(file.fileId()));
        card.addView(deleteBtn, delParams);

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = theme.dp(DeviceTheme.SPACE_S);
        return card;
    }

    private void showDeleteConfirm(@NonNull String fileId) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.earbuds_record_delete_confirm_title))
                .setMessage(activity.getString(R.string.earbuds_record_delete_confirm_msg))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_dialog_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.device_dialog_confirm),
                                () -> listener.onDeleteDeviceRecord(fileId))
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
