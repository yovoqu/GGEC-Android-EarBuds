package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;

import com.ggec.earbuds.data.json.JsonValues;

import org.json.JSONObject;

/**
 * 台账里一条设备的身份（名字与经典蓝牙地址）。
 *
 * <p>只做 JSON ↔ 持久化记录：领域模型不参与序列化，字段名在这里是单点定义。</p>
 */
record IdentityDto(String bleName, String classicAddress, String classicName) {

    @NonNull
    JSONObject toJson() {
        JSONObject json = new JSONObject();
        JsonValues.put(json, "bleName", bleName);
        JsonValues.put(json, "classicAddress", classicAddress);
        JsonValues.put(json, "classicName", classicName);
        return json;
    }

    @NonNull
    static IdentityDto fromJson(@NonNull JSONObject json) {
        return new IdentityDto(
                JsonValues.requiredString(json, "bleName"),
                JsonValues.requiredString(json, "classicAddress"),
                JsonValues.requiredString(json, "classicName"));
    }
}
