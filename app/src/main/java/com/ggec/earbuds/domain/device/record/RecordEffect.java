package com.ggec.earbuds.domain.device.record;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 状态机要执行的效果（design §4.2）：全部是「对端口的命令」或「对用户的分类通知」。
 *
 * <p>效果是纯数据，由 ViewModel 按类型分派给 {@code RecordPort} / {@code PcmPlaybackPort}；
 * 状态机自己不下发任何指令，也不拼文案。{@link Notify} 用分类 + 结构化载荷表达通知，
 * 用户句子在 {@code RecordActivity} 里映射到 {@code strings.xml}。</p>
 */
public sealed interface RecordEffect {

    /** 启动随录随传（落盘为本地文件）。 */
    record StartStream() implements RecordEffect {
    }

    record StopStream() implements RecordEffect {
    }

    record StartDeviceRecord() implements RecordEffect {
    }

    record StopDeviceRecord() implements RecordEffect {
    }

    /** 重新读取耳机内的录音文件列表。 */
    record RefreshDeviceFiles() implements RecordEffect {
    }

    /** 把耳机内的一条录音同步到手机。 */
    record SyncFile(@NonNull String fileId) implements RecordEffect {

        public SyncFile {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record CancelSync() implements RecordEffect {
    }

    record DeleteDeviceFile(@NonNull String fileId) implements RecordEffect {

        public DeleteDeviceFile {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    /** 删除手机本地已存录音（端口同步返回结果，无需异步回调）。 */
    record DeleteLocalFile(@NonNull String filePath) implements RecordEffect {

        public DeleteLocalFile {
            Objects.requireNonNull(filePath, "filePath");
        }
    }

    /** 播放一个本地已存录音。 */
    record Play(@NonNull String filePath) implements RecordEffect {

        public Play {
            Objects.requireNonNull(filePath, "filePath");
        }
    }

    record StopPlayback() implements RecordEffect {
    }

    /** 重新读取手机本地已存录音列表。 */
    record ReloadLocal() implements RecordEffect {
    }

    /** 分类通知：由 View 翻成用户文案。 */
    record Notify(@NonNull Notice notice) implements RecordEffect {

        public Notify {
            Objects.requireNonNull(notice, "notice");
        }
    }

    /**
     * 通知分类。每个变体只带结构化载荷（文件名、失败分类），不带句子。
     */
    sealed interface Notice {

        /** 没有活动连接，指令未下发。 */
        record DeviceNotConnected() implements Notice {
        }

        /** 当前设备不支持该录音模式（含 supported 为空集）。 */
        record ModeUnsupported() implements Notice {
        }

        record StreamSaved(@NonNull String fileName) implements Notice {

            public StreamSaved {
                Objects.requireNonNull(fileName, "fileName");
            }
        }

        record StreamFailed(@NonNull RecordFailure failure) implements Notice {

            public StreamFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record StreamStopFailed(@NonNull RecordFailure failure) implements Notice {

            public StreamStopFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record DeviceRecordStarted() implements Notice {
        }

        record DeviceRecordStartFailed(@NonNull RecordFailure failure) implements Notice {

            public DeviceRecordStartFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record DeviceRecordStopped() implements Notice {
        }

        record DeviceRecordStopFailed(@NonNull RecordFailure failure) implements Notice {

            public DeviceRecordStopFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record RefreshFailed(@NonNull RecordFailure failure) implements Notice {

            public RefreshFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        /** 已有同步任务在进行中。 */
        record SyncBusy() implements Notice {
        }

        record SyncSucceeded(@NonNull String fileName) implements Notice {

            public SyncSucceeded {
                Objects.requireNonNull(fileName, "fileName");
            }
        }

        record SyncFailed(@NonNull RecordFailure failure) implements Notice {

            public SyncFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record SyncCancelled() implements Notice {
        }

        record SyncCancelFailed(@NonNull RecordFailure failure) implements Notice {

            public SyncCancelFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        record LocalFileDeleted() implements Notice {
        }

        record LocalFileDeleteFailed(@NonNull RecordFailure failure) implements Notice {

            public LocalFileDeleteFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }

        /** 删除耳机文件的指令已被接受，列表随后刷新。 */
        record DeviceFileDeleteSent() implements Notice {
        }

        record DeviceFileDeleteFailed(@NonNull RecordFailure failure) implements Notice {

            public DeviceFileDeleteFailed {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }
}
