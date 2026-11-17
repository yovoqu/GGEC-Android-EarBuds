package com.ggec.earbuds.domain.device.usecase;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.RecordOrigin;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 会话事实 → 台账记录的<b>唯一</b>合成函数。
 *
 * <p>台账有两处消费实时事实：{@link ObserveDeviceRoster} 的只读投影与
 * {@link SyncActiveDevice} 的落盘。两处曾各写一份「建活记录」的逻辑，断开语义与
 * {@code activeId} 的处置很容易分叉；本类把它们收成一份纯函数，两处都调它。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>{@code READY}：写入快照的全部实时事实与能力值。</li>
 *   <li>{@code DISCONNECTED}：归档，实时值清空，只留身份元信息与最后同步时间。</li>
 *   <li>其余状态（含 {@code CONNECTING}）：保留既有事实，只更新状态与时间。</li>
 *   <li>{@code activeId}：断开时指针不动（改选哪台设备由用户决定），其余指向这台设备。</li>
 * </ul>
 */
final class LiveRecord {

    private LiveRecord() {
    }

    /** 只有连接中/就绪的快照会把实时值写进台账；断开走归档，其余状态不改台账。 */
    static boolean ongoing(@NonNull ConnectionState connection) {
        return connection == ConnectionState.CONNECTING || connection == ConnectionState.READY;
    }

    /** 把一次会话快照合成进台账，返回新台账。 */
    @NonNull
    static DeviceRoster apply(@NonNull DeviceRoster roster, @NonNull DeviceTarget target,
                              @NonNull DeviceSnapshot snapshot, long nowMillis) {
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(snapshot, "snapshot");
        DeviceRecord record = recordOf(roster, target, snapshot, nowMillis);
        DeviceRoster next = roster.upsert(record);
        if (snapshot.connection() == ConnectionState.DISCONNECTED) {
            return next;
        }
        return next.withActive(Optional.of(target.id()));
    }

    @NonNull
    private static DeviceRecord recordOf(@NonNull DeviceRoster roster, @NonNull DeviceTarget target,
                                         @NonNull DeviceSnapshot snapshot, long nowMillis) {
        Optional<DeviceRecord> existing = roster.find(target.id());
        ConnectionState connection = snapshot.connection();
        boolean ready = connection == ConnectionState.READY;
        boolean disconnected = connection == ConnectionState.DISCONNECTED;
        // 断开后只留身份元信息：电量/固件/佩戴这些实时值属于「会话内的事实」，
        // 掉线后继续展示旧数值会把历史当成现状。
        DeviceFacts facts = ready
                ? new DeviceFacts(snapshot.battery(), snapshot.firmware(),
                        snapshot.wearState(), snapshot.earColor())
                : disconnected
                        ? DeviceFacts.unknown()
                        : existing.map(DeviceRecord::facts).orElse(DeviceFacts.unknown());
        DeviceState state = ready
                ? snapshot.state()
                : disconnected
                        ? new DeviceState(Map.of())
                        : existing.map(DeviceRecord::state).orElse(new DeviceState(Map.of()));
        DeviceStatus status = disconnected
                ? DeviceStatus.archived(nowMillis)
                : new DeviceStatus(RecordOrigin.LIVE, connection, nowMillis);
        return new DeviceRecord(target.id(), existing.map(DeviceRecord::alias).orElse(""),
                target.identity(), status, facts, state);
    }
}
