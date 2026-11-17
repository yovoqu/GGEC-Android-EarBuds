package com.ggec.earbuds.ui.device.record;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.record.RecordEffect;
import com.ggec.earbuds.domain.device.record.RecordFailure;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

import java.util.Objects;

/**
 * 耳机录音功能页面：承载随录随传与本地录音双模式。
 *
 * <p>本类只做三件事：把 {@link RecordUiState} 交给 {@link RecordScreen} 渲染、把界面意图转发给
 * ViewModel、把 {@link RecordEffect.Notice} 分类映射成 {@code strings.xml} 里的文案。
 * 分类到文案的映射是页面职责，ViewModel 与状态机里没有用户句子。</p>
 */
public final class RecordActivity extends AppCompatActivity implements RecordScreen.Listener {

    private RecordViewModel viewModel;
    private RecordScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, RecordActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);

        AppContainer container = AppContainer.from(this);
        Objects.requireNonNull(container, "AppContainer must not be null");

        viewModel = new ViewModelProvider(this, container.device().recordFactory())
                .get(RecordViewModel.class);

        screen = new RecordScreen(this, this);
        setContentView(screen.view());

        viewModel.uiState().observe(this, state -> {
            if (state != null) {
                screen.render(state);
            }
        });

        viewModel.notices().observe(this, notice -> {
            if (notice != null) {
                YoToast.show(this, noticeText(notice));
            }
        });
    }

    /** 通知分类 → 文案。穷尽 {@link RecordEffect.Notice}，没有兜底分支。 */
    @NonNull
    private String noticeText(@NonNull RecordEffect.Notice notice) {
        return switch (notice) {
            case RecordEffect.Notice.DeviceNotConnected ignored ->
                    getString(R.string.earbuds_record_error_not_connected);
            case RecordEffect.Notice.ModeUnsupported ignored ->
                    getString(R.string.earbuds_record_error_unsupported_mode);
            case RecordEffect.Notice.StreamSaved saved ->
                    getString(R.string.earbuds_record_save_success, saved.fileName());
            case RecordEffect.Notice.StreamFailed failed -> getString(
                    R.string.earbuds_record_error_stream_failed, failureText(failed.failure()));
            case RecordEffect.Notice.StreamStopFailed failed -> getString(
                    R.string.earbuds_record_error_stream_stop_failed,
                    failureText(failed.failure()));
            case RecordEffect.Notice.DeviceRecordStarted ignored ->
                    getString(R.string.earbuds_record_device_started);
            case RecordEffect.Notice.DeviceRecordStartFailed failed -> getString(
                    R.string.earbuds_record_error_device_start_failed,
                    failureText(failed.failure()));
            case RecordEffect.Notice.DeviceRecordStopped ignored ->
                    getString(R.string.earbuds_record_device_stopped);
            case RecordEffect.Notice.DeviceRecordStopFailed failed -> getString(
                    R.string.earbuds_record_error_device_stop_failed,
                    failureText(failed.failure()));
            case RecordEffect.Notice.RefreshFailed failed -> getString(
                    R.string.earbuds_record_error_refresh_failed, failureText(failed.failure()));
            case RecordEffect.Notice.SyncBusy ignored ->
                    getString(R.string.earbuds_record_sync_busy);
            case RecordEffect.Notice.SyncSucceeded saved ->
                    getString(R.string.earbuds_record_sync_success, saved.fileName());
            case RecordEffect.Notice.SyncFailed failed ->
                    getString(R.string.earbuds_record_sync_failed, failureText(failed.failure()));
            case RecordEffect.Notice.SyncCancelled ignored ->
                    getString(R.string.earbuds_record_sync_cancelled);
            case RecordEffect.Notice.SyncCancelFailed failed -> getString(
                    R.string.earbuds_record_error_sync_cancel_failed,
                    failureText(failed.failure()));
            case RecordEffect.Notice.LocalFileDeleted ignored ->
                    getString(R.string.earbuds_record_delete_success);
            case RecordEffect.Notice.LocalFileDeleteFailed failed -> getString(
                    R.string.earbuds_record_error_delete_failed, failureText(failed.failure()));
            case RecordEffect.Notice.DeviceFileDeleteSent ignored ->
                    getString(R.string.earbuds_record_device_delete_sent);
            case RecordEffect.Notice.DeviceFileDeleteFailed failed -> getString(
                    R.string.earbuds_record_error_device_delete_failed,
                    failureText(failed.failure()));
        };
    }

    /** 失败分类 → 文案。穷尽 {@link RecordFailure}，没有兜底分支。 */
    @NonNull
    private String failureText(@NonNull RecordFailure failure) {
        @StringRes int textRes = switch (failure) {
            case NOT_CONNECTED -> R.string.earbuds_record_error_not_connected;
            case UNSUPPORTED -> R.string.earbuds_record_failure_unsupported;
            case REJECTED -> R.string.device_error_rejected;
            case TIMEOUT -> R.string.device_error_timeout;
            case BUSY -> R.string.earbuds_record_failure_busy;
            case IO -> R.string.earbuds_record_failure_io;
            case DEVICE_ERROR -> R.string.device_error_device;
        };
        return getString(textRes);
    }

    @Override
    public void onBack() {
        finish();
    }

    @Override
    public void onModeChanged(@NonNull RecordMode mode) {
        viewModel.setMode(mode);
    }

    @Override
    public void onStartStreamRecord() {
        viewModel.startStreamRecord();
    }

    @Override
    public void onStopStreamRecord() {
        viewModel.stopStreamRecord();
    }

    @Override
    public void onTogglePlay(@NonNull SavedRecordItem item) {
        viewModel.togglePlayLocalFile(item);
    }

    @Override
    public void onDeleteLocalFile(@NonNull SavedRecordItem item) {
        viewModel.deleteLocalFile(item);
    }

    @Override
    public void onStartDeviceRecord() {
        viewModel.startDeviceRecord();
    }

    @Override
    public void onStopDeviceRecord() {
        viewModel.stopDeviceRecord();
    }

    @Override
    public void onRefreshDeviceRecords() {
        viewModel.refreshDeviceRecords();
    }

    @Override
    public void onSyncDeviceRecord(@NonNull String fileId) {
        viewModel.syncDeviceRecord(fileId);
    }

    @Override
    public void onCancelSync() {
        viewModel.cancelSync();
    }

    @Override
    public void onDeleteDeviceRecord(@NonNull String fileId) {
        viewModel.deleteDeviceRecord(fileId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (screen != null) {
            screen.destroy();
        }
    }
}
