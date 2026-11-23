package com.ggec.earbuds.record.ui;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.ggec.earbuds.R;
import com.ggec.earbuds.provider.lib_common.permission.PermissionProvider;
import com.ggec.earbuds.record.wiring.RecordGraph;
import com.google.android.material.button.MaterialButton;
import com.yohu.youi.api.feedback.YoToast;
import com.yovo.biz_ggec_record.api.RecordConnectionObserver;
import com.yovo.biz_ggec_record.api.RecordNearbyObserver;
import com.yovo.biz_ggec_record.api.RecordSessionObserver;
import com.yovo.biz_ggec_record.model.RecordAsrModel;
import com.yovo.biz_ggec_record.model.RecordConnectionState;
import com.yovo.biz_ggec_record.model.RecordLinkStats;
import com.yovo.biz_ggec_record.model.RecordNearbyDevice;
import com.yovo.biz_ggec_record.model.RecordScanFailure;
import com.yovo.biz_ggec_record.model.RecordSubtitleEvent;
import com.yovo.biz_ggec_record.model.RecordVideoCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Record 独立入口页：深色播放器布局，视频为主、控件沉底。
 */
public final class RecordMainActivity extends AppCompatActivity {

    private RecordGraph graph;
    private TextView statusView;
    private TextView statsView;
    private TextView subtitleFinalView;
    private TextView subtitlePartialView;
    private TextView videoPlaceholder;
    private TextView deviceSectionTitle;
    private ScrollView subtitleScroll;
    private TextureView videoView;
    private MaterialButton muteButton;
    private MaterialButton asrToggleButton;
    private MaterialButton asrModelButton;
    private MaterialButton codecH264;
    private MaterialButton codecH265;
    private MaterialButton captureButton;
    private ListView deviceList;
    private DeviceAdapter adapter;
    private final List<RecordNearbyDevice> devices = new ArrayList<>();
    private final StringBuilder finals = new StringBuilder();
    private boolean pendingScan;
    private boolean muted;
    private boolean asrEnabled = true;
    @NonNull
    private RecordAsrModel asrModel = RecordAsrModel.ZIPFORMER_SMALL;
    @NonNull
    private RecordVideoCodec videoCodec = RecordVideoCodec.H265;
    @Nullable
    private Surface videoSurface;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_record_main);
        graph = new RecordGraph(getApplicationContext());

        View root = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        statusView = findViewById(R.id.record_status);
        statsView = findViewById(R.id.record_stats);
        subtitleFinalView = findViewById(R.id.record_subtitle_final);
        subtitlePartialView = findViewById(R.id.record_subtitle_partial);
        subtitleScroll = findViewById(R.id.record_subtitle_scroll);
        videoPlaceholder = findViewById(R.id.record_video_placeholder);
        deviceSectionTitle = findViewById(R.id.record_device_section_title);
        videoView = findViewById(R.id.record_video);
        muteButton = findViewById(R.id.record_mute);
        asrToggleButton = findViewById(R.id.record_asr_toggle);
        asrModelButton = findViewById(R.id.record_asr_model);
        codecH264 = findViewById(R.id.record_codec_h264);
        codecH265 = findViewById(R.id.record_codec_h265);
        captureButton = findViewById(R.id.record_capture);
        deviceList = findViewById(R.id.record_device_list);

        adapter = new DeviceAdapter();
        deviceList.setAdapter(adapter);
        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= devices.size()) {
                return;
            }
            connectDevice(devices.get(position));
        });
        refreshDeviceSection();

        findViewById(R.id.record_scan).setOnClickListener(v -> startScanFlow());
        findViewById(R.id.record_stop_scan).setOnClickListener(v -> {
            stopScan();
            statusView.setText(R.string.record_status_idle);
            refreshDeviceSection();
        });
        findViewById(R.id.record_disconnect).setOnClickListener(v -> {
            stopScan();
            graph.client().disconnect();
            videoPlaceholder.setVisibility(View.VISIBLE);
        });
        codecH264.setOnClickListener(v -> selectCodec(RecordVideoCodec.H264));
        codecH265.setOnClickListener(v -> selectCodec(RecordVideoCodec.H265));
        muteButton.setOnClickListener(v -> {
            muted = !muted;
            graph.client().setMuted(muted);
            muteButton.setText(muted ? R.string.record_unmute : R.string.record_mute);
            styleChip(muteButton, muted);
        });
        asrToggleButton.setOnClickListener(v -> {
            asrEnabled = !asrEnabled;
            graph.client().setAsrEnabled(asrEnabled);
            asrToggleButton.setText(asrEnabled ? R.string.record_asr_off : R.string.record_asr_on);
            styleChip(asrToggleButton, asrEnabled);
        });
        asrModelButton.setOnClickListener(v -> {
            asrModel = asrModel == RecordAsrModel.ZIPFORMER_SMALL
                    ? RecordAsrModel.PARAFORMER
                    : RecordAsrModel.ZIPFORMER_SMALL;
            graph.client().setAsrModel(asrModel);
            asrModelButton.setText(asrModel == RecordAsrModel.ZIPFORMER_SMALL
                    ? R.string.record_asr_model_zipformer
                    : R.string.record_asr_model_paraformer);
        });
        findViewById(R.id.record_asr_clear).setOnClickListener(v -> {
            finals.setLength(0);
            subtitleFinalView.setText("");
            subtitlePartialView.setText("");
        });
        captureButton.setOnClickListener(v -> {
            if (graph.client().isRecording()) {
                graph.client().stopRecording();
            } else {
                graph.client().startRecording();
            }
        });
        resetCaptureButton();

        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface,
                                                  int width,
                                                  int height) {
                bindSurface(new Surface(surface));
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface,
                                                    int width,
                                                    int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                bindSurface(null);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        selectCodec(RecordVideoCodec.H265);
        graph.client().setAsrEnabled(true);
        graph.client().setAsrModel(RecordAsrModel.ZIPFORMER_SMALL);
        styleChip(asrToggleButton, true);
        styleChip(muteButton, false);

        graph.client().setConnectionObserver(new RecordConnectionObserver() {
            @Override
            public void onConnectionStateChanged(@NonNull RecordConnectionState state) {
                statusView.setText(labelFor(state));
                boolean playing = state == RecordConnectionState.READY;
                videoPlaceholder.setVisibility(playing ? View.GONE : View.VISIBLE);
                if (!playing) {
                    statsView.setText(R.string.record_stats_idle);
                    resetCaptureButton();
                }
                if (state == RecordConnectionState.READY
                        || state == RecordConnectionState.CONNECTING
                        || state == RecordConnectionState.CONNECTED) {
                    deviceList.setVisibility(View.GONE);
                    deviceSectionTitle.setVisibility(View.GONE);
                } else {
                    refreshDeviceSection();
                }
            }

            @Override
            public void onConnectionFailed(@NonNull String message) {
                statusView.setText(getString(R.string.record_status_failed, message));
                videoPlaceholder.setVisibility(View.VISIBLE);
                refreshDeviceSection();
            }
        });
        graph.client().setSessionObserver(new RecordSessionObserver() {
            @Override
            public void onLinkStats(@NonNull RecordLinkStats stats) {
                statsView.setText(getString(R.string.record_stats_format,
                        stats.videoKbps(),
                        stats.videoFps(),
                        stats.audioKbps()));
            }

            @Override
            public void onSubtitle(@NonNull RecordSubtitleEvent event) {
                if (event.isFinalResult()) {
                    if (!event.text().isBlank()) {
                        if (finals.length() > 0) {
                            finals.append('\n');
                        }
                        finals.append(event.text());
                        subtitleFinalView.setText(finals.toString());
                        subtitleScroll.post(() -> subtitleScroll.fullScroll(View.FOCUS_DOWN));
                    }
                    subtitlePartialView.setText("");
                } else {
                    subtitlePartialView.setText(event.text());
                }
            }

            @Override
            public void onSessionError(@NonNull String message) {
                statusView.setText(getString(R.string.record_status_failed, message));
            }

            @Override
            public void onRecordingStarted(@NonNull String path) {
                captureButton.setText(R.string.record_capture_stop);
                captureButton.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(getColor(R.color.record_danger)));
                statusView.setText(R.string.record_capture_started);
            }

            @Override
            public void onRecordingFinished(@NonNull String path, long bytes, long durationMs) {
                resetCaptureButton();
                long kb = Math.max(1L, bytes / 1024L);
                long sec = Math.max(1L, durationMs / 1000L);
                int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
                String name = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
                String msg = getString(R.string.record_capture_saved, name, kb, sec);
                statusView.setText(msg);
                YoToast.show(RecordMainActivity.this, msg);
            }

            @Override
            public void onRecordingFailed(@NonNull String message) {
                resetCaptureButton();
                statusView.setText(getString(R.string.record_capture_failed, message));
            }
        });
    }

    private void resetCaptureButton() {
        captureButton.setText(R.string.record_capture_start);
        captureButton.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.record_accent)));
    }

    @Override
    protected void onDestroy() {
        stopScan();
        bindSurface(null);
        if (graph != null) {
            graph.client().setConnectionObserver(null);
            graph.client().setSessionObserver(null);
            graph.close();
            graph = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionProvider.Result result = PermissionProvider.onRequestResult(
                this, requestCode, permissions, grantResults);
        if (!result.handled()) {
            return;
        }
        if (result.allGranted() && pendingScan) {
            pendingScan = false;
            doStartScan();
        } else if (pendingScan) {
            pendingScan = false;
            statusView.setText(getString(R.string.record_status_failed,
                    getString(R.string.device_scan_error_permission)));
        }
    }

    private void selectCodec(@NonNull RecordVideoCodec codec) {
        videoCodec = codec;
        graph.client().setVideoCodec(codec);
        styleChip(codecH264, codec == RecordVideoCodec.H264);
        styleChip(codecH265, codec == RecordVideoCodec.H265);
    }

    private void styleChip(@NonNull MaterialButton button, boolean selected) {
        button.setBackgroundResource(selected
                ? R.drawable.record_chip_selected_bg
                : R.drawable.record_chip_bg);
        button.setTextColor(getColor(selected
                ? R.color.record_accent
                : R.color.record_text_primary));
    }

    private void bindSurface(@Nullable Surface surface) {
        if (videoSurface != null && videoSurface != surface) {
            videoSurface.release();
        }
        videoSurface = surface;
        if (graph != null) {
            graph.client().setVideoSurface(surface);
        }
    }

    private void startScanFlow() {
        if (PermissionProvider.requestBluetoothIfNeeded(this)) {
            doStartScan();
            return;
        }
        pendingScan = true;
        statusView.setText(getString(R.string.record_status_failed,
                getString(R.string.device_scan_error_permission)));
    }

    private void doStartScan() {
        devices.clear();
        adapter.notifyDataSetChanged();
        refreshDeviceSection();
        statusView.setText(getString(R.string.record_status_scanning, 0));
        graph.nearby().start(new RecordNearbyObserver() {
            @Override
            public void onDevicesChanged(@NonNull List<RecordNearbyDevice> next) {
                devices.clear();
                devices.addAll(next);
                adapter.notifyDataSetChanged();
                refreshDeviceSection();
                statusView.setText(getString(R.string.record_status_scanning, devices.size()));
            }

            @Override
            public void onScanFailed(@NonNull RecordScanFailure failure) {
                statusView.setText(getString(R.string.record_status_failed, failure.message()));
            }
        });
    }

    private void connectDevice(@NonNull RecordNearbyDevice device) {
        stopScan();
        statusView.setText(R.string.record_state_connecting);
        statsView.setText(R.string.record_stats_idle);
        deviceList.setVisibility(View.GONE);
        deviceSectionTitle.setVisibility(View.GONE);
        graph.client().connect(device.macAddress(), device.name());
    }

    private void stopScan() {
        pendingScan = false;
        if (graph != null) {
            graph.nearby().stop();
        }
    }

    private void refreshDeviceSection() {
        boolean show = !devices.isEmpty();
        deviceSectionTitle.setVisibility(View.VISIBLE);
        deviceSectionTitle.setText(show
                ? getString(R.string.record_nearby_title)
                : getString(R.string.record_nearby_empty));
        deviceList.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private String labelFor(@NonNull RecordConnectionState state) {
        return switch (state) {
            case CONNECTING -> getString(R.string.record_state_connecting);
            case CONNECTED -> getString(R.string.record_state_connected);
            case READY -> getString(R.string.record_state_ready);
            case DISCONNECTED -> getString(R.string.record_state_disconnected);
            case IDLE -> getString(R.string.record_status_idle);
        };
    }

    private final class DeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public RecordNearbyDevice getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_record_device, parent, false);
            }
            RecordNearbyDevice device = getItem(position);
            TextView name = row.findViewById(R.id.record_device_name);
            TextView meta = row.findViewById(R.id.record_device_meta);
            String display = device.name().isEmpty()
                    ? getString(R.string.record_unnamed_device)
                    : device.name();
            name.setText(display);
            meta.setText(getString(R.string.record_device_meta, device.macAddress(), device.rssi()));
            return row;
        }
    }
}
