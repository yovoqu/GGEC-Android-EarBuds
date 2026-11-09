package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.diagnostics.YoBleTimelineRecorder;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一处理 Characteristic 与 Descriptor 的序列化读写入队及回调分发（含 pre-O 认证失败自动重试）。
 */
public final class YoBleGattOperationHandler {

    private static final String MODULE = "ble-gatt-io";

    private final YoBleSessionContext context;
    private final YoBleReliableWriteCoordinator reliableWriteCoordinator;
    private final AtomicLong opSeq = new AtomicLong();

    public YoBleGattOperationHandler(@NonNull YoBleSessionContext context,
                                     @NonNull YoBleReliableWriteCoordinator reliableWriteCoordinator) {
        this.context = context;
        this.reliableWriteCoordinator = reliableWriteCoordinator;
    }

    @NonNull
    public BleOperationHandle read(@NonNull BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   @NonNull BleGattObserver observer) {
        String opId = "ble-read-" + opSeq.incrementAndGet();
        if (!context.isReady()) {
            BleFailure failure = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for read", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(failure));
            return BleOperationHandle.completed(opId);
        }

        BleFailure existenceError = checkTargetExists(target);
        if (existenceError != null) {
            context.runtime().callback(() -> observer.onFailed(existenceError));
            return BleOperationHandle.completed(opId);
        }

        YoBleGattOperationQueue.OpKind kind = target.isDescriptorTarget()
                ? YoBleGattOperationQueue.OpKind.READ_DESC
                : YoBleGattOperationQueue.OpKind.READ_CHAR;

        context.operationQueue().enqueue(
                opId,
                kind,
                target,
                priority,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE,
                                "Transport closed", context.deviceId());
                    }
                    boolean ok = target.isDescriptorTarget()
                            ? transport.readDescriptor(target)
                            : transport.readCharacteristic(target);
                    if (!ok) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.READ_FAILED, MODULE,
                                (target.isDescriptorTarget() ? "readDescriptor" : "readCharacteristic")
                                        + " returned false",
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
                    }
                    context.timeline().record(MODULE, "read tx " + target);
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        byte[] bytes = payload instanceof byte[] ? ((byte[]) payload).clone() : new byte[0];
                        context.timeline().record(MODULE, "read rx " + target
                                + " " + YoBleTimelineRecorder.summarizeBytes(bytes));
                        if (target.isDescriptorTarget()) {
                            context.runtime().callback(() ->
                                    observer.onDescriptorRead(context.deviceId(), target, bytes));
                        } else {
                            context.runtime().callback(() ->
                                    observer.onRead(context.deviceId(), target, bytes));
                        }
                    }

                    @Override
                    public void onCancelled() {
                        BleFailure f = YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "Read cancelled: " + opId,
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
                        context.runtime().callback(() -> observer.onFailed(f));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        if (failure.rawStatusCode() != BleFailure.NO_RAW_STATUS
                                && YoBleGattStatus.isAuthOrEncryptionError(failure.rawStatusCode())
                                && context.handleAuthErrorIfConfigured(failure, () ->
                                        read(target, BleOperationPriority.HIGH, observer))) {
                            return;
                        }
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @NonNull
    public BleOperationHandle write(@NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleOperationPriority priority,
                                    @NonNull BleGattObserver observer) {
        String opId = "ble-write-" + opSeq.incrementAndGet();
        byte[] copy = value.clone();

        if (!context.isReady()) {
            BleFailure failure = YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "BLE connection is not READY for write", context.deviceId());
            context.runtime().callback(() -> observer.onFailed(failure));
            return BleOperationHandle.completed(opId);
        }

        BleFailure existenceError = checkTargetExists(target);
        if (existenceError != null) {
            context.runtime().callback(() -> observer.onFailed(existenceError));
            return BleOperationHandle.completed(opId);
        }

        YoBleGattOperationQueue.OpKind kind = target.isDescriptorTarget()
                ? YoBleGattOperationQueue.OpKind.WRITE_DESC
                : YoBleGattOperationQueue.OpKind.WRITE_CHAR;

        context.operationQueue().enqueue(
                opId,
                kind,
                target,
                priority,
                context.operationTimeoutMillis(),
                () -> {
                    YoBleGattTransport transport = context.transport();
                    if (transport == null) {
                        return YoBleErrorMapper.failure(
                                BleErrorCode.CONNECTION_NOT_READY, MODULE,
                                "Transport closed", context.deviceId());
                    }
                    if (!target.isDescriptorTarget() && reliableWriteCoordinator.isInTransaction()) {
                        reliableWriteCoordinator.recordExpectedEcho(target, copy);
                    }
                    boolean ok = target.isDescriptorTarget()
                            ? transport.writeDescriptor(target, copy)
                            : transport.writeCharacteristic(target, copy, writeType);
                    if (!ok) {
                        if (!target.isDescriptorTarget() && reliableWriteCoordinator.isInTransaction()) {
                            reliableWriteCoordinator.popExpectedEcho(target);
                        }
                        return YoBleErrorMapper.failure(
                                BleErrorCode.WRITE_FAILED, MODULE,
                                (target.isDescriptorTarget() ? "writeDescriptor" : "writeCharacteristic")
                                        + " returned false",
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
                    }
                    context.timeline().record(MODULE, "write tx " + target
                            + " " + YoBleTimelineRecorder.summarizeBytes(copy));
                    return null;
                },
                new YoBleGattOperationQueue.CompletionHandler() {
                    @Override
                    public void onSuccess(@Nullable Object payload) {
                        if (!target.isDescriptorTarget() && reliableWriteCoordinator.isInTransaction()) {
                            byte[] echoed = payload instanceof byte[] ? (byte[]) payload : null;
                            if (!reliableWriteCoordinator.verifyEcho(target, echoed)) {
                                BleFailure mismatch = YoBleErrorMapper.failure(
                                        BleErrorCode.RELIABLE_WRITE_MISMATCH, MODULE,
                                        "Reliable write echo value mismatch on " + target,
                                        null, BleFailure.NO_RAW_STATUS, context.deviceId(), target,
                                        "Abort reliable write transaction and retry");
                                context.runtime().callback(() -> observer.onFailed(mismatch));
                                return;
                            }
                        }
                        if (target.isDescriptorTarget()) {
                            context.runtime().callback(() ->
                                    observer.onDescriptorWrite(context.deviceId(), target));
                        } else {
                            context.runtime().callback(() ->
                                    observer.onWrite(context.deviceId(), target));
                        }
                    }

                    @Override
                    public void onCancelled() {
                        BleFailure f = YoBleErrorMapper.failure(
                                BleErrorCode.OPERATION_CANCELLED, MODULE,
                                "Write cancelled: " + opId,
                                null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
                        context.runtime().callback(() -> observer.onFailed(f));
                    }

                    @Override
                    public void onFailed(@NonNull BleFailure failure) {
                        if (failure.rawStatusCode() != BleFailure.NO_RAW_STATUS
                                && YoBleGattStatus.isAuthOrEncryptionError(failure.rawStatusCode())
                                && context.handleAuthErrorIfConfigured(failure, () ->
                                        write(target, copy, writeType, BleOperationPriority.HIGH, observer))) {
                            return;
                        }
                        context.runtime().callback(() -> observer.onFailed(failure));
                    }
                });

        return new BleOperationHandle(opId, () -> context.operationQueue().cancel(opId));
    }

    @Nullable
    private BleFailure checkTargetExists(@NonNull BleGattTarget target) {
        BleGattDatabaseSnapshot db = context.currentDatabase();
        if (db == null) {
            return null; // 未自动服务发现时不阻塞；底层 getService 兜底
        }
        if (db.findService(target.serviceUuid(), target.serviceInstanceId()) == null) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.SERVICE_NOT_FOUND, MODULE,
                    "GATT service not found: " + target.serviceUuid(),
                    null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
        }
        if (db.findCharacteristic(target) == null) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.CHARACTERISTIC_NOT_FOUND, MODULE,
                    "GATT characteristic not found: " + target.characteristicUuid(),
                    null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
        }
        if (target.isDescriptorTarget() && db.findDescriptor(target) == null) {
            return YoBleErrorMapper.failure(
                    BleErrorCode.DESCRIPTOR_NOT_FOUND, MODULE,
                    "GATT descriptor not found: " + target.descriptorUuid(),
                    null, BleFailure.NO_RAW_STATUS, context.deviceId(), target, null);
        }
        return null;
    }
}
