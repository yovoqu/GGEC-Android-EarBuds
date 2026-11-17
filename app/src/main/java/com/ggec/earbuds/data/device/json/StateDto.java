package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 台账里一条设备的能力取值表（能力 id → 取值）。
 *
 * <p>键是能力 id 本身：能力目录来自 provider，不在这层解释；这里只保证"每个键都有确定形状"。</p>
 */
record StateDto(Map<String, ValueDto> values) {

    StateDto {
        values = Map.copyOf(values);
    }

    @NonNull
    JSONObject toJson() {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ValueDto> entry : values.entrySet()) {
            JsonValues.put(json, entry.getKey(), entry.getValue().toJson());
        }
        return json;
    }

    @NonNull
    static StateDto fromJson(@NonNull JSONObject json, @NonNull String path) {
        Map<String, ValueDto> values = new LinkedHashMap<>();
        for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
            String capabilityId = keys.next();
            String valuePath = path + ".state." + capabilityId;
            JSONObject value = json.optJSONObject(capabilityId);
            if (value == null) {
                throw new DocumentFormatException(valuePath,
                        "capability " + capabilityId + " is not an object in the roster");
            }
            values.put(capabilityId, ValueDto.fromJson(value, valuePath));
        }
        return new StateDto(values);
    }
}
