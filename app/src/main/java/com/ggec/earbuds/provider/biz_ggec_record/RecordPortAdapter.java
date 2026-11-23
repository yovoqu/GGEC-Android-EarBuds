package com.ggec.earbuds.provider.biz_ggec_record;

import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.record.model.RecordAsrModel;
import com.ggec.earbuds.domain.record.model.RecordNearbyDevice;
import com.ggec.earbuds.domain.record.model.RecordVideoCodec;
import com.ggec.earbuds.domain.record.port.RecordControlPort;
import com.ggec.earbuds.domain.record.port.RecordObserver;
import com.ggec.earbuds.domain.record.port.RecordScanObserver;
import com.ggec.earbuds.domain.record.port.RecordScanPort;
import com.yovo.biz_ggec_record.api.RecordConnectionObserver;
import com.yovo.biz_ggec_record.api.RecordNearbyObserver;
import com.yovo.biz_ggec_record.api.RecordRuntime;
import com.yovo.biz_ggec_record.api.RecordSessionObserver;
import com.yovo.biz_ggec_record.api.YoRecordClient;
import com.yovo.biz_ggec_record.api.YoRecordNearby;

import java.util.ArrayList;
import java.util.List;

/**
 * biz_ggec_record 运行时的唯一适配器：实现 app 的 {@link RecordControlPort} 与 {@link RecordScanPort}，
 * 把 biz 的两组 observer 归并为领域单一 {@link RecordObserver}，并把不透明 Surface 句柄向下转型。
 * 不含业务策略，只做类型翻译与生命周期转发。
 */
public final class RecordPortAdapter implements RecordControlPort, RecordScanPort {

    private final RecordRuntime runtime;
    private final YoRecordClient client;
    private final YoRecordNearby nearby;

    @Nullable
    private RecordObserver observer;

    private final RecordConnectionObserver connectionBridge = new RecordConnectionObserver() {
        @Override
        public void onConnectionStateChanged(
                @NonNull com.yovo.biz_ggec_record.model.RecordConnectionState state) {
            RecordObserver target = observer;
            if (target != null) {
                target.onConnectionStateChanged(RecordMappers.state(state));
            }
        }

        @Override
        public void onConnectionFailed(@NonNull String message) {
            RecordObserver target = observer;
            if (target != null) {
                target.onConnectionFailed(message);
            }
        }
    };

    private final RecordSessionObserver sessionBridge = new RecordSessionObserver() {
        @Override
        public void onLinkStats(@NonNull com.yovo.biz_ggec_record.model.RecordLinkStats stats) {
            RecordObserver target = observer;
            if (target != null) {
                target.onLinkStats(RecordMappers.stats(stats));
            }
        }

        @Override
        public void onSubtitle(@NonNull com.yovo.biz_ggec_record.model.RecordSubtitleEvent event) {
            RecordObserver target = observer;
            if (target != null) {
                target.onSubtitle(RecordMappers.subtitle(event));
            }
        }

        @Override
        public void onSessionError(@NonNull String message) {
            RecordObserver target = observer;
            if (target != null) {
                target.onSessionError(message);
            }
        }

        @Override
        public void onRecordingStarted(@NonNull String path) {
            RecordObserver target = observer;
            if (target != null) {
                target.onRecordingStarted(path);
            }
        }

        @Override
        public void onRecordingFinished(@NonNull String path, long bytes, long durationMs) {
            RecordObserver target = observer;
            if (target != null) {
                target.onRecordingFinished(path, bytes, durationMs);
            }
        }

        @Override
        public void onRecordingFailed(@NonNull String message) {
            RecordObserver target = observer;
            if (target != null) {
                target.onRecordingFailed(message);
            }
        }
    };

    public RecordPortAdapter(@NonNull RecordRuntime runtime) {
        this.runtime = runtime;
        this.client = runtime.client();
        this.nearby = runtime.nearby();
    }

    // ---- RecordControlPort ----

    @Override
    public void connect(String macAddress, String deviceName) {
        client.connect(macAddress, deviceName);
    }

    @Override
    public void disconnect() {
        client.disconnect();
    }

    @Override
    public boolean isReady() {
        return client.isReady();
    }

    @Override
    public void setMuted(boolean muted) {
        client.setMuted(muted);
    }

    @Override
    public void setAsrEnabled(boolean enabled) {
        client.setAsrEnabled(enabled);
    }

    @Override
    public void setAsrModel(RecordAsrModel model) {
        client.setAsrModel(RecordMappers.asr(model));
    }

    @Override
    public void setVideoCodec(RecordVideoCodec codec) {
        client.setVideoCodec(RecordMappers.codec(codec));
    }

    @Override
    public void setVideoSurface(Object surface) {
        client.setVideoSurface(surface == null ? null : (Surface) surface);
    }

    @Override
    public void startRecording() {
        client.startRecording();
    }

    @Override
    public void stopRecording() {
        client.stopRecording();
    }

    @Override
    public boolean isRecording() {
        return client.isRecording();
    }

    @Override
    public void setObserver(RecordObserver observer) {
        this.observer = observer;
        if (observer == null) {
            client.setConnectionObserver(null);
            client.setSessionObserver(null);
        } else {
            client.setConnectionObserver(connectionBridge);
            client.setSessionObserver(sessionBridge);
        }
    }

    @Override
    public void close() {
        runtime.close();
    }

    // ---- RecordScanPort ----

    @Override
    public void start(RecordScanObserver scanObserver) {
        nearby.start(new RecordNearbyObserver() {
            @Override
            public void onDevicesChanged(
                    @NonNull List<com.yovo.biz_ggec_record.model.RecordNearbyDevice> devices) {
                List<RecordNearbyDevice> mapped = new ArrayList<>(devices.size());
                for (com.yovo.biz_ggec_record.model.RecordNearbyDevice device : devices) {
                    mapped.add(RecordMappers.device(device));
                }
                scanObserver.onDevicesChanged(mapped);
            }

            @Override
            public void onScanFailed(
                    @NonNull com.yovo.biz_ggec_record.model.RecordScanFailure failure) {
                scanObserver.onScanFailed(RecordMappers.failure(failure));
            }
        });
    }

    @Override
    public void stop() {
        nearby.stop();
    }
}
