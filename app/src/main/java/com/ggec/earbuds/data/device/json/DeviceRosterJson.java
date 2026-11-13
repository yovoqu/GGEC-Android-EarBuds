package com.ggec.earbuds.data.device.json;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.WearState;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备台账的持久化编解码。
 *
 * <p>只做 JSON ↔ 领域模型：台账规则（排序、去重、活动指针）在 domain。
 * 格式版本必须精确匹配；未知版本或字段损坏一律抛 {@link IOException}，
 * 由仓库转成 {@code STORE_FAILED} 并隔离坏文件，不静默清空。</p>
 */
public final class DeviceRosterJson {

    private static final int VERSION = 1;

    public DeviceRoster read(File file) throws IOException {
        if (!file.exists()) {
            return DeviceRoster.empty();
        }
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        try {
            JSONObject root = new JSONObject(text);
            int version = (int) JsonValues.number(root, "version", -1);
            if (version != VERSION) {
                throw new IOException("unsupported roster version " + version);
            }
            List<DeviceRecord> devices = new ArrayList<>();
            JSONArray array = root.optJSONArray("devices");
            if (array != null) {
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) {
                        throw new IOException("roster device #" + index + " is not an object");
                    }
                    devices.add(readDevice(item));
                }
            }
            Optional<DeviceId> active = readId(JsonValues.string(root, "active"));
            return new DeviceRoster(devices, active);
        } catch (JSONException | IllegalArgumentException broken) {
            throw new IOException("roster json is broken", broken);
        }
    }

    public void write(File file, DeviceRoster roster) throws IOException {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "version", VERSION);
        roster.activeId().ifPresent(id -> JsonValues.put(root, "active", id.key()));
        JSONArray array = new JSONArray();
        for (DeviceRecord record : roster.devices()) {
            array.put(writeDevice(record));
        }
        JsonValues.put(root, "devices", array);
        writeAtomically(file, root.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 把损坏文件挪到 {@code <name>.bad}，返回是否挪成功。调用方仍按失败上报。 */
    public boolean quarantine(File file) {
        if (!file.exists()) {
            return true;
        }
        File bad = new File(file.getParentFile(), file.getName() + ".bad");
        try {
            Files.move(file.toPath(), bad.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static JSONObject writeDevice(DeviceRecord record) {
        JSONObject item = new JSONObject();
        JSONObject id = new JSONObject();
        JsonValues.put(id, "modelId", record.id().modelId());
        JsonValues.put(id, "bleAddress", record.id().bleAddress());
        JsonValues.put(item, "id", id);
        JsonValues.put(item, "alias", record.alias());

        JSONObject identity = new JSONObject();
        JsonValues.put(identity, "bleName", record.identity().bleName());
        JsonValues.put(identity, "classicAddress", record.identity().classicAddress());
        JsonValues.put(identity, "classicName", record.identity().classicName());
        JsonValues.put(item, "identity", identity);

        JsonValues.put(item, "lastSeen", record.status().lastSeenMillis());

        JSONObject facts = new JSONObject();
        JSONObject battery = new JSONObject();
        JsonValues.put(battery, "left", record.facts().battery().left());
        JsonValues.put(battery, "right", record.facts().battery().right());
        JsonValues.put(battery, "case", record.facts().battery().caseLevel());
        JsonValues.put(battery, "charging", record.facts().battery().caseCharging());
        JsonValues.put(facts, "battery", battery);
        JsonValues.put(facts, "firmware", record.facts().firmware());
        JsonValues.put(facts, "wear", record.facts().wearState().name());
        JsonValues.put(facts, "earColor", record.facts().earColor());
        JsonValues.put(item, "facts", facts);

        JSONObject state = new JSONObject();
        for (Map.Entry<String, ControlValue> entry : record.state().values().entrySet()) {
            JsonValues.put(state, entry.getKey(), writeValue(entry.getValue()));
        }
        JsonValues.put(item, "state", state);
        return item;
    }

    private static DeviceRecord readDevice(JSONObject item) {
        JSONObject id = item.optJSONObject("id");
        if (id == null) {
            throw new IllegalArgumentException("device id is missing");
        }
        DeviceId deviceId = new DeviceId(JsonValues.string(id, "modelId", ""),
                JsonValues.string(id, "bleAddress", ""));

        JSONObject identity = item.optJSONObject("identity");
        DeviceIdentity identityValue = identity == null
                ? DeviceIdentity.unknown()
                : new DeviceIdentity(JsonValues.string(identity, "bleName", ""),
                        JsonValues.string(identity, "classicAddress", ""),
                        JsonValues.string(identity, "classicName", ""));

        long lastSeen = JsonValues.number(item, "lastSeen", 0);

        JSONObject facts = item.optJSONObject("facts");
        BatterySnapshot battery = facts == null
                ? BatterySnapshot.unknown()
                : readBattery(facts.optJSONObject("battery"));
        String firmware = facts == null ? "" : JsonValues.string(facts, "firmware", "");
        WearState wear = facts == null
                ? WearState.UNKNOWN
                : WearState.valueOf(JsonValues.string(facts, "wear", WearState.UNKNOWN.name()));
        int earColor = facts == null
                ? DeviceSnapshot.UNKNOWN_EAR_COLOR
                : (int) JsonValues.number(facts, "earColor", DeviceSnapshot.UNKNOWN_EAR_COLOR);
        DeviceFacts factsValue = new DeviceFacts(battery, firmware, wear, earColor);

        return new DeviceRecord(deviceId, JsonValues.string(item, "alias", ""),
                identityValue, DeviceStatus.archived(lastSeen), factsValue,
                readState(item.optJSONObject("state")));
    }

    private static BatterySnapshot readBattery(JSONObject battery) {
        if (battery == null) {
            return BatterySnapshot.unknown();
        }
        return new BatterySnapshot(
                (int) JsonValues.number(battery, "left", BatterySnapshot.UNKNOWN),
                (int) JsonValues.number(battery, "right", BatterySnapshot.UNKNOWN),
                (int) JsonValues.number(battery, "case", BatterySnapshot.UNKNOWN),
                JsonValues.flag(battery, "charging", false));
    }

    private static DeviceState readState(JSONObject state) {
        if (state == null) {
            return new DeviceState(Map.of());
        }
        Map<String, ControlValue> values = new LinkedHashMap<>();
        java.util.Iterator<String> keys = state.keys();
        while (keys.hasNext()) {
            String capabilityId = keys.next();
            JSONObject value = state.optJSONObject(capabilityId);
            if (value == null) {
                throw new IllegalArgumentException("capability " + capabilityId + " is not an object");
            }
            values.put(capabilityId, readValue(value));
        }
        return new DeviceState(values);
    }

    private static Optional<DeviceId> readId(String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        int split = key.lastIndexOf('@');
        if (split <= 0 || split == key.length() - 1) {
            throw new IllegalArgumentException("active device key is broken: " + key);
        }
        return Optional.of(new DeviceId(key.substring(0, split), key.substring(split + 1)));
    }

    private static JSONObject writeValue(ControlValue value) {
        JSONObject json = new JSONObject();
        switch (value) {
            case ControlValue.Toggle toggle -> {
                JsonValues.put(json, "kind", "TOGGLE");
                JsonValues.put(json, "enabled", toggle.enabled());
            }
            case ControlValue.Level level -> {
                JsonValues.put(json, "kind", "LEVEL");
                JsonValues.put(json, "value", level.value());
            }
            case ControlValue.Choice choice -> {
                JsonValues.put(json, "kind", "CHOICE");
                JsonValues.put(json, "optionId", choice.optionId());
            }
            case ControlValue.Curve curve -> {
                JsonValues.put(json, "kind", "CURVE");
                JSONArray points = new JSONArray();
                for (int point : curve.points()) {
                    points.put(point);
                }
                JsonValues.put(json, "points", points);
            }
        }
        return json;
    }

    private static ControlValue readValue(JSONObject json) {
        String kind = JsonValues.string(json, "kind", "");
        return switch (kind) {
            case "TOGGLE" -> new ControlValue.Toggle(JsonValues.flag(json, "enabled", false));
            case "LEVEL" -> new ControlValue.Level((int) JsonValues.number(json, "value", 0));
            case "CHOICE" -> new ControlValue.Choice(JsonValues.string(json, "optionId", ""));
            case "CURVE" -> new ControlValue.Curve(readPoints(json.optJSONArray("points")));
            default -> throw new IllegalArgumentException("unknown control kind: " + kind);
        };
    }

    private static List<Integer> readPoints(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<Integer> points = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            points.add(array.optInt(index));
        }
        return points;
    }

    private static void writeAtomically(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        Files.write(tmp.toPath(), bytes,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.move(tmp.toPath(), file.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
