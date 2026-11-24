package com.ggec.earbuds.data.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class BluetoothStreamAudioSourceTest {

    @Test
    public void streamsPcmFramesAndStopsIdempotently() {
        FakeStreamRecordPort port = new FakeStreamRecordPort();
        BluetoothStreamAudioSource source = new BluetoothStreamAudioSource(port, "HS02");
        List<byte[]> received = new ArrayList<>();
        AiAudioSource.Listener listener = new AiAudioSource.Listener() {
            @Override
            public void onPcm(@NonNull byte[] pcm16, int sampleRateHz) {
                received.add(pcm16);
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
            }
        };

        source.start(listener);
        assertEquals(1, port.startCalls);
        port.observer.onStarted();
        port.observer.onPcm(new byte[]{1, 2, 3, 4}, 16_000);
        assertEquals(1, received.size());

        source.stop();
        source.stop(); // 幂等
        // stop() 仅解绑上行消费，不真正停止设备录音
        assertEquals(0, port.stopCalls);

        // 解绑后不再投递帧
        port.observer.onPcm(new byte[]{5, 6}, 16_000);
        assertEquals(1, received.size());

        // 重新绑定可直接恢复收流，无需重新 startStreamAudio
        source.start(listener);
        assertEquals(1, port.startCalls);
        port.observer.onPcm(new byte[]{7, 8}, 16_000);
        assertEquals(2, received.size());

        // release() 才真正停止设备随录随传
        source.release();
        source.release(); // 幂等
        assertEquals(1, port.stopCalls);
    }

    @Test
    public void restartDuringReleasingWaitsForStopCompletionThenStarts() {
        FakeStreamRecordPort port = new FakeStreamRecordPort();
        BluetoothStreamAudioSource source = new BluetoothStreamAudioSource(port, "WY01");
        List<byte[]> received = new ArrayList<>();
        AiAudioSource.Listener listener = new AiAudioSource.Listener() {
            @Override
            public void onPcm(@NonNull byte[] pcm16, int sampleRateHz) {
                received.add(pcm16);
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
            }
        };

        source.start(listener);
        port.observer.onStarted();
        source.release();
        // 停止尚未完成时立即再次启动
        source.start(listener);
        assertEquals(1, port.startCalls);

        // 完成停止后自动触发新一轮启动
        port.completeStop();
        assertEquals(2, port.startCalls);
        port.observer.onPcm(new byte[]{7, 8}, 16_000);
        assertEquals(1, received.size());
    }

    @Test
    public void notConnectedErrorDoesNotCrashCallWhileDeviceErrorPropagates() {
        FakeStreamRecordPort port = new FakeStreamRecordPort();
        BluetoothStreamAudioSource source = new BluetoothStreamAudioSource(port, "HS02");
        AiFailureKind[] failure = new AiFailureKind[1];
        AiAudioSource.Listener listener = new AiAudioSource.Listener() {
            @Override
            public void onPcm(@NonNull byte[] pcm16, int sampleRateHz) {
            }

            @Override
            public void onFailure(@NonNull AiFailureKind kind, @NonNull String detail) {
                failure[0] = kind;
            }
        };

        source.start(listener);
        port.observer.onError(RecordFailure.NOT_CONNECTED);
        assertNull(failure[0]);

        source.start(listener);
        port.observer.onError(RecordFailure.DEVICE_ERROR);
        assertEquals(AiFailureKind.UNKNOWN, failure[0]);
    }

    private static final class FakeStreamRecordPort implements RecordPort {
        int startCalls;
        int stopCalls;
        StreamAudioObserver observer;
        Consumer<RecordOutcome> pendingStop;

        @Override
        public void startStreamRecord(@NonNull StreamRecordObserver observer) {
        }

        @Override
        public void startStreamAudio(@NonNull StreamAudioObserver observer) {
            startCalls++;
            this.observer = observer;
        }

        @Override
        public void stopStreamRecord(@NonNull Consumer<RecordOutcome> onComplete) {
            stopCalls++;
            pendingStop = onComplete;
        }

        void completeStop() {
            if (pendingStop != null) {
                Consumer<RecordOutcome> cb = pendingStop;
                pendingStop = null;
                cb.accept(RecordOutcome.ok());
            }
        }

        @Override
        public boolean isStreamRecording() {
            return false;
        }

        @Override
        public void startDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        }

        @Override
        public void stopDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        }

        @Override
        public void refreshDeviceRecords(@NonNull Consumer<RecordOutcome> onComplete) {
        }

        @Override
        public void syncDeviceRecord(@NonNull String fileId, @NonNull SyncProgressObserver observer) {
        }

        @Override
        public void cancelSyncDeviceRecord(@NonNull Consumer<RecordOutcome> onComplete) {
        }

        @Override
        public void deleteDeviceRecord(@NonNull String fileId, @NonNull Consumer<RecordOutcome> onComplete) {
        }

        @Override
        public List<SavedRecordItem> listLocalSavedFiles() {
            return List.of();
        }

        @Override
        public RecordOutcome deleteLocalSavedFile(@NonNull String filePath) {
            return RecordOutcome.ok();
        }

        @NonNull
        @Override
        public Set<RecordMode> supportedModes() {
            return Set.of(RecordMode.STREAM);
        }
    }
}
