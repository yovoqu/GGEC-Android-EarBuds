package com.ggec.earbuds.provider.biz_ggec_record.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Record 音视频产品不走耳机 {@link RecordPort}（随录随传/本地录音）。
 * 能力空集；其余调用显式 {@link RecordFailure#UNSUPPORTED}。
 */
public final class RecordAvRecordPort implements RecordPort {

    @Override
    public void startStreamRecord(@NonNull StreamRecordObserver observer) {
        observer.onError(RecordFailure.UNSUPPORTED);
    }

    @Override
    public void startStreamAudio(@NonNull StreamAudioObserver observer) {
        observer.onError(RecordFailure.UNSUPPORTED);
    }

    @Override
    public void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public boolean isStreamRecording() {
        return false;
    }

    @Override
    public void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        observer.onError(RecordFailure.UNSUPPORTED);
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<RecordOutcome> onComplete) {
        onComplete.accept(RecordOutcome.failed(RecordFailure.UNSUPPORTED));
    }

    @Override
    public List<SavedRecordItem> listLocalSavedFiles() {
        return List.of();
    }

    @Override
    public RecordOutcome deleteLocalSavedFile(@NonNull String filePath) {
        return RecordOutcome.failed(RecordFailure.UNSUPPORTED);
    }

    @NonNull
    @Override
    public Set<RecordMode> supportedModes() {
        return Set.of();
    }
}
