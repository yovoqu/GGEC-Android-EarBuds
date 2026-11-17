package com.ggec.earbuds.data.device.json;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.data.store.DocumentStore;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.WearState;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 台账文档的编解码：持久化模型（{@link RosterDto} 等）与领域模型在这里显式转换，
 * 领域 record 不参与序列化。IO 只经 {@link DocumentStore}。
 *
 * <p>版本必须精确匹配：{@code version} 读不出来或不等于 {@link #VERSION} 一律抛
 * {@link DocumentFormatException}，没有迁移分支。读侧只认 {@code id.modelId} /
 * {@code id.address} / {@code classicAddress} / {@code classicName} 这组键，无双读。</p>
 *
 * <p>损坏的文档不静默清空：读到格式错误时把它原样写到 {@code <document>.bad}
 * 再删掉读不出来的原文件，然后把失败上抛，由仓库转成 {@code STORE_FAILED}。</p>
 */
public final class DeviceRosterJson {

    static final int VERSION = 1;
    private static final String QUARANTINE_SUFFIX = ".bad";
    private static final char KEY_SEPARATOR = '@';

    private final DocumentStore documents;
    private final String document;

    public DeviceRosterJson(DocumentStore documents, String document) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.document = Objects.requireNonNull(document, "document");
    }

    /** 缺失 = 没有台账（不是空台账）；损坏 = 抛。 */
    public Optional<DeviceRoster> read() {
        Optional<String> text = documents.read(document);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(decode(text.get()));
        } catch (DocumentFormatException corrupt) {
            documents.write(document + QUARANTINE_SUFFIX, text.get());
            documents.delete(document);
            throw corrupt;
        }
    }

    public void write(DeviceRoster roster) {
        Objects.requireNonNull(roster, "roster");
        List<DeviceDto> devices = new ArrayList<>(roster.devices().size());
        for (DeviceRecord record : roster.devices()) {
            devices.add(encode(record));
        }
        String active = roster.activeId().map(DeviceId::key).orElse(null);
        documents.write(document, new RosterDto(VERSION, active, devices).toJson().toString());
    }

    private static DeviceRoster decode(String text) {
        RosterDto dto;
        try {
            dto = RosterDto.fromJson(new JSONObject(text));
        } catch (JSONException malformed) {
            throw new DocumentFormatException("document", "roster is not a JSON object", malformed);
        }
        if (dto.version() != VERSION) {
            throw new DocumentFormatException("version",
                    "roster has unsupported version " + dto.version());
        }
        List<DeviceRecord> devices = new ArrayList<>(dto.devices().size());
        for (DeviceDto device : dto.devices()) {
            devices.add(decode(device));
        }
        return new DeviceRoster(devices, active(dto.activeKey()));
    }

    private static DeviceRecord decode(DeviceDto dto) {
        try {
            DecodedDevice decoded = decodeDevice(dto);
            return new DeviceRecord(
                    new DeviceId(dto.modelId(), dto.address()),
                    dto.alias(),
                    new DeviceIdentity(dto.identity().bleName(), dto.identity().classicAddress(),
                            dto.identity().classicName()),
                    DeviceStatus.archived(dto.lastSeenMillis()),
                    decoded.facts(),
                    new DeviceState(decoded.values()));
        } catch (IllegalArgumentException invalid) {
            throw new DocumentFormatException("devices",
                    "roster contains an invalid device: " + dto.modelId(), invalid);
        }
    }

    private static DecodedDevice decodeDevice(DeviceDto dto) {
        FactsDto stored = dto.facts();
        BatterySnapshot battery = new BatterySnapshot(stored.batteryLeft(), stored.batteryRight(),
                stored.batteryCase(), stored.batteryCharging());
        Map<String, ControlValue> values = new LinkedHashMap<>();
        for (Map.Entry<String, ValueDto> entry : dto.state().values().entrySet()) {
            values.put(entry.getKey(), toDomain(entry.getValue()));
        }
        return new DecodedDevice(new DeviceFacts(battery, stored.firmware(),
                WearState.valueOf(stored.wearState()), stored.earColor()), values);
    }

    private static ControlValue toDomain(ValueDto value) {
        return switch (value) {
            case ValueDto.Toggle toggle -> new ControlValue.Toggle(toggle.enabled());
            case ValueDto.Level level -> new ControlValue.Level(level.value());
            case ValueDto.Choice choice -> new ControlValue.Choice(choice.optionId());
            case ValueDto.Curve curve -> new ControlValue.Curve(curve.points());
        };
    }

    private static DeviceDto encode(DeviceRecord record) {
        Map<String, ValueDto> values = new LinkedHashMap<>();
        for (Map.Entry<String, ControlValue> entry : record.state().values().entrySet()) {
            values.put(entry.getKey(), fromDomain(entry.getValue()));
        }
        return new DeviceDto(
                record.id().modelId(),
                record.id().address(),
                record.alias(),
                new IdentityDto(record.identity().bleName(), record.identity().classicAddress(),
                        record.identity().classicName()),
                record.status().lastSeenMillis(),
                new FactsDto(record.facts().battery().left(), record.facts().battery().right(),
                        record.facts().battery().caseLevel(), record.facts().battery().caseCharging(),
                        record.facts().firmware(), record.facts().wearState().name(),
                        record.facts().earColor()),
                new StateDto(values));
    }

    private static ValueDto fromDomain(ControlValue value) {
        return switch (value) {
            case ControlValue.Toggle toggle -> new ValueDto.Toggle(toggle.enabled());
            case ControlValue.Level level -> new ValueDto.Level(level.value());
            case ControlValue.Choice choice -> new ValueDto.Choice(choice.optionId());
            case ControlValue.Curve curve -> new ValueDto.Curve(curve.points());
        };
    }

    private static Optional<DeviceId> active(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        int split = key.lastIndexOf(KEY_SEPARATOR);
        if (split <= 0 || split == key.length() - 1) {
            throw new DocumentFormatException("active",
                    "active device key is broken in the roster");
        }
        try {
            return Optional.of(new DeviceId(key.substring(0, split), key.substring(split + 1)));
        } catch (IllegalArgumentException invalid) {
            throw new DocumentFormatException("active",
                    "active device key is invalid in the roster", invalid);
        }
    }

    /** 一次解码里的事实与能力值，避免在 decode 中间态上建两个临时变量。 */
    private record DecodedDevice(DeviceFacts facts, Map<String, ControlValue> values) {
    }
}
