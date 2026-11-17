package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 台账文档的持久化根：版本、活动设备键、设备列表。
 *
 * <p>版本号在文档里叫 {@code version}，读侧只认这一个键；不等于当前版本就是损坏，
 * 没有"按旧版猜结构"的分支。</p>
 */
record RosterDto(int version, @Nullable String activeKey, List<DeviceDto> devices) {

    RosterDto {
        devices = List.copyOf(devices);
    }

    @NonNull
    JSONObject toJson() {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "version", version);
        JsonValues.put(root, "active", activeKey);
        JSONArray array = new JSONArray();
        for (DeviceDto device : devices) {
            array.put(device.toJson());
        }
        JsonValues.put(root, "devices", array);
        return root;
    }

    @NonNull
    static RosterDto fromJson(@NonNull JSONObject root) {
        int version = JsonValues.requiredInt(root, "version");
        JSONArray array = root.optJSONArray("devices");
        if (array == null) {
            throw new DocumentFormatException("devices", "roster devices are missing");
        }
        List<DeviceDto> devices = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            String path = "devices[" + index + "]";
            JSONObject item = array.optJSONObject(index);
            if (item == null) {
                throw new DocumentFormatException(path, path + " is not an object in the roster");
            }
            devices.add(DeviceDto.fromJson(item, path));
        }
        return new RosterDto(version, JsonValues.optionalString(root, "active").orElse(null), devices);
    }
}
