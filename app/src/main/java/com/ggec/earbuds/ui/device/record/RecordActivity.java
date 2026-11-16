package com.ggec.earbuds.ui.device.record;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

import java.util.Objects;

/**
 * 耳机录音功能页面：承载随录随传与本地录音双模式。
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

        viewModel.events().observe(this, event -> {
            if (event instanceof RecordEvent.ToastMessage toast) {
                YoToast.show(this, toast.message());
            }
        });
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
