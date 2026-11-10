package com.yovo.lib_bluetooth.internal.ble.gatt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.error.YoBleErrorMapper;
import com.yovo.lib_bluetooth.internal.ble.error.YoBleGattStatus;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.CompletionHandler;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.OpKind;
import com.yovo.lib_bluetooth.internal.ble.gatt.YoBleGattOperationQueue.StartResult;
import com.yovo.lib_bluetooth.internal.ble.transfer.YoBleMtuPayloadCalculator;
import com.yovo.lib_bluetooth.model.ble.BleConnectionSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleGattCharacteristicInfo;
import com.yovo.lib_bluetooth.model.ble.BleGattDatabaseSnapshot;
import com.yovo.lib_bluetooth.model.ble.BleGattObserver;
import com.yovo.lib_bluetooth.model.ble.BleGattTarget;
import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;
import com.yovo.lib_bluetooth.model.ble.BleOperationPriority;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 读写入口。观察者回调发生在 state 线程；公开 API 由连接管理器再派发到用户回调线程。
 * 属性、长度在入队前检查，启动时再查一次目标是否仍在。
 */
public final class YoBleGattOperationHandler {

    private static final String MODULE = "ble-gatt";

    @NonNull
    private final YoBleSessionContext context;
    @NonNull
    private final YoBleReliableWriteCoordinator reliableWrite;
    private final AtomicLong seq = new AtomicLong();

    public YoBleGattOperationHandler(@NonNull YoBleSessionContext context,
                                     @NonNull YoBleReliableWriteCoordinator reliableWrite) {
        this.context = context;
        this.reliableWrite = reliableWrite;
    }

    @NonNull
    public BleOperationHandle read(@NonNull BleGattTarget target,
                                   @NonNull BleOperationPriority priority,
                                   @NonNull BleGattObserver observer) {
        String opId = "ble-read-" + seq.incrementAndGet();
        BleFailure rejected = rejectIfNotReady(target);
        if (rejected != null) {
            observer.onFailed(rejected);
            return BleOperationHandle.completed(opId);
        }
        submitRead(opId, target, priority, observer, new boolean[]{false});
        return cancelHandle(opId);
    }

    private void submitRead(@NonNull String opId,
                            @NonNull BleGattTarget target,
                            @NonNull BleOperationPriority priority,
                            @NonNull BleGattObserver observer,
                            @NonNull boolean[] authTried) {
        OpKind kind = target.isDescriptorTarget() ? OpKind.READ_DESC : OpKind.READ_CHAR;
        context.operationQueue().enqueue(opId, kind, target, priority, context.operationTimeoutMillis(),
                () -> startRead(target, kind),
                completion(opId, target, observer, false,
                        () -> submitRead(opId, target, priority, observer, authTried), authTried, payload -> {
                    byte[] bytes = payload instanceof byte[] ? (byte[]) payload : new byte[0];
                    if (target.isDescriptorTarget()) {
                        observer.onDescriptorRead(context.deviceId(), target, bytes);
                    } else {
                        observer.onRead(context.deviceId(), target, bytes);
                    }
                }));
    }

    @NonNull
    public BleOperationHandle write(@NonNull BleGattTarget target,
                                    @NonNull byte[] value,
                                    @NonNull BleWriteType writeType,
                                    @NonNull BleOperationPriority priority,
                                    @NonNull BleGattObserver observer) {
        String opId = "ble-write-" + seq.incrementAndGet();
        byte[] copy = value.clone();
        BleFailure rejected = rejectIfNotReady(target);
        if (rejected == null) {
            rejected = validateWrite(target, copy, writeType);
        }
        if (rejected != null) {
            observer.onFailed(rejected);
            return BleOperationHandle.completed(opId);
        }
        boolean transactional = reliableWrite.markIfTransaction(priority);
        BleOperationPriority queuedPriority = transactional ? BleOperationPriority.NORMAL : priority;
        submitWrite(opId, target, copy, writeType, queuedPriority, transactional, observer, new boolean[]{false});
        return cancelHandle(opId);
    }

    private void submitWrite(@NonNull String opId,
                             @NonNull BleGattTarget target,
                             @NonNull byte[] value,
                             @NonNull BleWriteType writeType,
                             @NonNull BleOperationPriority priority,
                             boolean transactional,
                             @NonNull BleGattObserver observer,
                             @NonNull boolean[] authTried) {
        OpKind kind = target.isDescriptorTarget() ? OpKind.WRITE_DESC : OpKind.WRITE_CHAR;
        context.operationQueue().enqueue(opId, kind, target, priority, context.operationTimeoutMillis(),
                transactional,
                () -> startWrite(target, value, writeType, kind),
                completion(opId, target, observer, transactional,
                        () -> submitWrite(opId, target, value, writeType, priority, transactional, observer, authTried),
                        authTried, payload -> {
                            if (target.isDescriptorTarget()) {
                                observer.onDescriptorWrite(context.deviceId(), target);
                            } else {
                                observer.onWrite(context.deviceId(), target);
                            }
                        }));
    }

    @Nullable
    private BleFailure rejectIfNotReady(@NonNull BleGattTarget target) {
        if (!context.isReady()) {
            return YoBleErrorMapper.failure(BleErrorCode.CONNECTION_NOT_READY, MODULE,
                    "connection is not READY", context.deviceId());
        }
        return missingTarget(target);
    }

    @Nullable
    private BleFailure missingTarget(@NonNull BleGattTarget target) {
        BleGattDatabaseSnapshot database = context.currentDatabase();
        if (database == null) {
            return null;
        }
        if (database.findCharacteristic(target) == null) {
            return YoBleErrorMapper.failure(
                    target.isDescriptorTarget() ? BleErrorCode.DESCRIPTOR_NOT_FOUND
                            : database.findService(target.serviceUuid(), target.serviceInstanceId()) == null
                            ? BleErrorCode.SERVICE_NOT_FOUND : BleErrorCode.CHARACTERISTIC_NOT_FOUND,
                    MODULE, "GATT target not in database", context.deviceId());
        }
        if (target.isDescriptorTarget() && database.findDescriptor(target) == null) {
            return YoBleErrorMapper.failure(BleErrorCode.DESCRIPTOR_NOT_FOUND, MODULE,
                    "descriptor not in database", context.deviceId());
        }
        return null;
    }

    @Nullable
    private BleFailure validateWrite(@NonNull BleGattTarget target,
                                     @NonNull byte[] value,
                                     @NonNull BleWriteType writeType) {
        if (target.isDescriptorTarget()) {
            return null;
        }
        BleGattDatabaseSnapshot database = context.currentDatabase();
        BleGattCharacteristicInfo info = database == null ? null : database.findCharacteristic(target);
        if (info != null) {
            boolean allowed = writeType == BleWriteType.WITH_RESPONSE ? info.isWritableWithResponse()
                    : writeType == BleWriteType.WITHOUT_RESPONSE ? info.isWritableWithoutResponse()
                    : info.isSignedWritable();
            if (!allowed) {
                return YoBleErrorMapper.failure(BleErrorCode.REQUEST_NOT_SUPPORTED, MODULE,
                        "characteristic does not support " + writeType, context.deviceId());
            }
        }
        int max = YoBleMtuPayloadCalculator.effectiveMaxChunkSize(
                context.currentMtu(), context.overriddenMtu(), writeType);
        int limit = writeType == BleWriteType.WITH_RESPONSE
                ? BleConnectionSnapshot.MAX_ATT_WRITE_PAYLOAD : max;
        if (value.length > limit) {
            return YoBleErrorMapper.failure(BleErrorCode.INVALID_OFFSET_OR_LENGTH, MODULE,
                    "write length " + value.length + " exceeds " + limit, context.deviceId());
        }
        return null;
    }

    @NonNull
    private StartResult startRead(@NonNull BleGattTarget target, @NonNull OpKind kind) {
        BleFailure missing = missingTarget(target);
        if (missing != null) {
            return StartResult.failed(missing);
        }
        YoBleGattTransport transport = context.transport();
        if (transport == null) {
            return StartResult.failed(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "transport closed", context.deviceId()));
        }
        boolean started = kind == OpKind.READ_DESC
                ? transport.readDescriptor(target) : transport.readCharacteristic(target);
        if (!started) {
            return StartResult.failed(YoBleErrorMapper.failure(
                    BleErrorCode.READ_FAILED, MODULE, "read rejected", context.deviceId()));
        }
        return StartResult.pending();
    }

    @NonNull
    private StartResult startWrite(@NonNull BleGattTarget target,
                                   @NonNull byte[] value,
                                   @NonNull BleWriteType writeType,
                                   @NonNull OpKind kind) {
        BleFailure missing = missingTarget(target);
        if (missing != null) {
            return StartResult.failed(missing);
        }
        if (!target.isDescriptorTarget()) {
            reliableWrite.recordExpectedEcho(value);
        }
        YoBleGattTransport transport = context.transport();
        if (transport == null) {
            return StartResult.failed(YoBleErrorMapper.failure(
                    BleErrorCode.CONNECTION_NOT_READY, MODULE, "transport closed", context.deviceId()));
        }
        boolean started = kind == OpKind.WRITE_DESC
                ? transport.writeDescriptor(target, value)
                : transport.writeCharacteristic(target, value, writeType);
        if (!started) {
            return StartResult.failed(YoBleErrorMapper.failure(
                    BleErrorCode.WRITE_FAILED, MODULE, "write rejected", context.deviceId()));
        }
        return StartResult.pending();
    }

    @NonNull
    private CompletionHandler completion(@NonNull String opId,
                                         @NonNull BleGattTarget target,
                                         @NonNull BleGattObserver observer,
                                         boolean transactional,
                                         @NonNull Runnable retry,
                                         @NonNull boolean[] authTried,
                                         @NonNull PayloadConsumer onOk) {
        return new CompletionHandler() {
            @Override
            public void onSuccess(@Nullable Object payload) {
                if (!target.isDescriptorTarget() && transactional) {
                    BleFailure mismatch = reliableWrite.verifyEcho(payload instanceof byte[]
                            ? (byte[]) payload : null);
                    if (mismatch != null) {
                        reliableWrite.poison(mismatch);
                        observer.onFailed(mismatch);
                        return;
                    }
                }
                onOk.accept(payload);
            }

            @Override
            public void onCancelled() {
                observer.onFailed(YoBleErrorMapper.failure(
                        BleErrorCode.OPERATION_CANCELLED, MODULE, "cancelled " + opId, context.deviceId()));
            }

            @Override
            public void onFailed(@NonNull BleFailure failure) {
                if (transactional) {
                    reliableWrite.poison(failure);
                }
                if (!authTried[0] && failure.rawStatusCode() >= 0
                        && YoBleGattStatus.isAuthOrEncryptionError(failure.rawStatusCode())) {
                    authTried[0] = true;
                    context.recoverFromAuthError(failure, retry, () -> observer.onFailed(failure));
                    return;
                }
                observer.onFailed(failure);
            }
        };
    }

    @NonNull
    private BleOperationHandle cancelHandle(@NonNull String opId) {
        return new BleOperationHandle(opId, () -> context.runtime().state(
                () -> context.operationQueue().cancel(opId)));
    }

    private interface PayloadConsumer {
        void accept(@Nullable Object payload);
    }
}
