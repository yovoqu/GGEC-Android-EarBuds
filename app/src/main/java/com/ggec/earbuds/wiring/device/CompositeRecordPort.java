package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.port.RecordPort;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 聚合录音端口：根据当前活跃的驱动转发录音请求。
 */
public final class CompositeRecordPort implements RecordPort {

    private final Supplier<RecordPort> activeRecordPortSupplier;

    public CompositeRecordPort(@NonNull Supplier<RecordPort> activeRecordPortSupplier) {
        this.activeRecordPortSupplier = Objects.requireNonNull(activeRecordPortSupplier, "activeRecordPortSupplier");
    }

    private RecordPort current() {
        return activeRecordPortSupplier.get();
    }

    @Override
    public void startStreamRecord(@Nullable String customFileName, @Nullable File customSaveDir,
                                  @NonNull StreamRecordObserver observer) {
        current().startStreamRecord(customFileName, customSaveDir, observer);
    }

    @Override
    public void stopStreamRecord(@NonNull Consumer<Boolean> onComplete) {
        current().stopStreamRecord(onComplete);
    }

    @Override
    public boolean isStreamRecording() {
        return current().isStreamRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        current().startDeviceRecord(onComplete);
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        current().stopDeviceRecord(onComplete);
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<Boolean> onComplete) {
        current().refreshDeviceRecords(onComplete);
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        current().syncDeviceRecord(fileId, observer);
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<Boolean> onComplete) {
        current().cancelSyncDeviceRecord(onComplete);
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<Boolean> onComplete) {
        current().deleteDeviceRecord(fileId, onComplete);
    }

    @Override
    public List<SavedRecordItem> listLocalSavedFiles() {
        return current().listLocalSavedFiles();
    }

    @Override
    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        return current().deleteLocalSavedFile(filePath);
    }

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        RecordPort port = current();
        return port != null ? port.supportedModes() : Set.of(RecordMode.STREAM);
    }
}
