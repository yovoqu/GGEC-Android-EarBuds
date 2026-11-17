package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONObject;

/**
 * 台账里一条设备的最近观测值：电量、固件、佩戴状态、耳机颜色。
 *
 * <p>电量在文档里是一层 {@code battery} 对象，其他字段平铺在 {@code facts} 下；
 * 这些键名是本层单点定义。所有字段都是必填——缺一个就是台账损坏，
 * 不能拿 {@code UNKNOWN} 哨兵顶上，那会让"没读到"和"设备没上报"混成同一件事。</p>
 */
record FactsDto(
        int batteryLeft,
        int batteryRight,
        int batteryCase,
        boolean batteryCharging,
        String firmware,
        String wearState,
        int earColor) {

    @NonNull
    JSONObject toJson() {
        JSONObject battery = new JSONObject();
        JsonValues.put(battery, "left", batteryLeft);
        JsonValues.put(battery, "right", batteryRight);
        JsonValues.put(battery, "case", batteryCase);
        JsonValues.put(battery, "charging", batteryCharging);

        JSONObject json = new JSONObject();
        JsonValues.put(json, "battery", battery);
        JsonValues.put(json, "firmware", firmware);
        JsonValues.put(json, "wear", wearState);
        JsonValues.put(json, "earColor", earColor);
        return json;
    }

    @NonNull
    static FactsDto fromJson(@NonNull JSONObject json, @NonNull String path) {
        JSONObject battery = json.optJSONObject("battery");
        if (battery == null) {
            throw new DocumentFormatException(path + ".battery",
                    "device battery is missing in the roster");
        }
        return new FactsDto(
                JsonValues.requiredInt(battery, "left"),
                JsonValues.requiredInt(battery, "right"),
                JsonValues.requiredInt(battery, "case"),
                JsonValues.requiredBoolean(battery, "charging"),
                JsonValues.requiredString(json, "firmware"),
                JsonValues.requiredString(json, "wear"),
                JsonValues.requiredInt(json, "earColor"));
    }
}
