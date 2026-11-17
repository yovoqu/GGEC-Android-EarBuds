package com.yovo.biz_ggec_hs.internal.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.api.HsRecordApi;
import com.yovo.biz_ggec_hs.api.StreamRecordConfig;
import com.yovo.biz_ggec_hs.api.StreamRecordListener;
import com.yovo.biz_ggec_hs.api.SyncProgressListener;
import com.yovo.biz_ggec_hs.internal.center.port.HsAudioDataLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.DeleteRecordFileCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.link.HsFailureMapper;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 录音 API 统一实现类。
 *
 * <p>命令完成码到对外失败的翻译只走 {@link HsFailureMapper}：本类不保留任何
 * 「成功/失败 → 失败枚举」的本地判断。</p>
 */
public final class HsRecordManager implements HsRecordApi {

    private static final String TAG = "HsRecordManager";

    /** 设备文件 ID 契约违约：显式失败，绝不下发 0 号下标。 */
    private static final String ERROR_INVALID_FILE_ID = "设备录音文件 ID 非法";

    private final HsProtocolEngine engine;
    private final HsRecordPathResolver pathResolver;
    private final HsStreamRecordSession streamSession;
    private final HsDeviceRecordSyncSession syncSession;
    private final BiConsumer<Command, CommandCompletion> completionSink;

    public HsRecordManager(@NonNull HsProtocolEngine engine,
                           @NonNull HsAudioDataLink audioLink,
                           @NonNull HsRecordPathResolver pathResolver,
                           @NonNull AudioCodecProviderPort codecProvider) {
        this(engine, audioLink, pathResolver, codecProvider, null);
    }

    public HsRecordManager(@NonNull HsProtocolEngine engine,
                           @NonNull HsAudioDataLink audioLink,
                           @NonNull HsRecordPathResolver pathResolver,
                           @NonNull AudioCodecProviderPort codecProvider,
                           @Nullable BiConsumer<Command, CommandCompletion> completionSink) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.pathResolver = Objects.requireNonNull(pathResolver, "pathResolver");
        this.completionSink = completionSink;
        this.streamSession = new HsStreamRecordSession(engine, audioLink, pathResolver, codecProvider);
        this.syncSession = new HsDeviceRecordSyncSession(engine, audioLink, pathResolver, codecProvider);
    }

    @Override
    public void startStreamRecord(@NonNull StreamRecordConfig config,
                                  @NonNull StreamRecordListener listener) {
        streamSession.startRecord(config, listener);
    }

    @Override
    public void stopStreamRecord(@NonNull HsControl.Callback callback) {
        streamSession.stopRecord(callback);
    }

    @Override
    public boolean isStreamRecording() {
        return streamSession.isRecording();
    }

    @Override
    public void startDeviceRecord(@NonNull HsControl.Callback callback) {
        engine.submit(new StartLocalRecordCommand(), (cmd, completion) ->
                callback.onResult(HsFailureMapper.writeResult(completion)));
    }

    @Override
    public void stopDeviceRecord(@NonNull HsControl.Callback callback) {
        engine.submit(new StopLocalRecordCommand(), (cmd, completion) ->
                callback.onResult(HsFailureMapper.writeResult(completion)));
    }

    @Override
    public void refreshDeviceRecords(@NonNull HsControl.Callback callback) {
        // 请求获取第 0 页，最多 12 个文件
        engine.submit(new GetRecordIdListCommand(0, 12), (cmd, completion) -> {
            if (completionSink != null) {
                completionSink.accept(cmd, completion);
            }
            callback.onResult(HsFailureMapper.writeResult(completion));
        });
    }

    @Override
    public void syncDeviceRecord(@NonNull String fileId, @Nullable File targetFile,
                                 @NonNull SyncProgressListener listener) {
        syncSession.startSync(fileId, 0, targetFile, listener);
    }

    @Override
    public void cancelSyncDeviceRecord(@NonNull HsControl.Callback callback) {
        syncSession.cancelSync(callback);
    }

    @Override
    public void deleteDeviceRecord(@NonNull String fileId, @NonNull HsControl.Callback callback) {
        Integer index = HsRecordFileId.indexOf(fileId);
        if (index == null) {
            HsLog.e(TAG, "拒绝删除非法设备文件 ID: " + fileId);
            callback.onResult(new HsControl.Outcome.Err<>(HsControl.Failure.REJECTED));
            return;
        }
        engine.submit(new DeleteRecordFileCommand(new int[]{index}), (cmd, completion) ->
                callback.onResult(HsFailureMapper.writeResult(completion)));
    }

    @NonNull
    @Override
    public File getRecordStorageDirectory() {
        return pathResolver.getRootDir();
    }

    @NonNull
    @Override
    public List<File> listLocalSavedFiles() {
        File root = pathResolver.getRootDir();
        List<File> result = new ArrayList<>();
        collectAudioFiles(root, result);
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    @Override
    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    private void collectAudioFiles(File dir, List<File> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectAudioFiles(file, result);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".pcm") || name.endsWith(".wav") || name.endsWith(".mp3")) {
                    result.add(file);
                }
            }
        }
    }

    public void release() {
        streamSession.release();
        syncSession.release();
    }
}
