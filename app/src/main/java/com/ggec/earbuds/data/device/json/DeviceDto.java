package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONObject;

/**
 * 台账里一条设备的持久化记录。
 *
 * <p>键名单点：{@code id.modelId} + {@code id.address} 是唯一身份键，读侧只认这一组，
 * 没有第二套字段名可读（旧实现会退回去读 {@code id.bleAddress}，那是双读，已删除）。</p>
 */
record DeviceDto(
        String modelId,
        String address,
        String alias,
        IdentityDto identity,
        long lastSeenMillis,
        FactsDto facts,
        StateDto state) {

    @NonNull
    JSONObject toJson() {
        JSONObject id = new JSONObject();
        JsonValues.put(id, "modelId", modelId);
        JsonValues.put(id, "address", address);

        JSONObject json = new JSONObject();
        JsonValues.put(json, "id", id);
        JsonValues.put(json, "alias", alias);
        JsonValues.put(json, "identity", identity.toJson());
        JsonValues.put(json, "lastSeen", lastSeenMillis);
        JsonValues.put(json, "facts", facts.toJson());
        JsonValues.put(json, "state", state.toJson());
        return json;
    }

    @NonNull
    static DeviceDto fromJson(@NonNull JSONObject json, @NonNull String path) {
        JSONObject id = json.optJSONObject("id");
        if (id == null) {
            throw new DocumentFormatException(path + ".id", "device id is missing in the roster");
        }
        JSONObject identity = json.optJSONObject("identity");
        if (identity == null) {
            throw new DocumentFormatException(path + ".identity",
                    "device identity is missing in the roster");
        }
        JSONObject facts = json.optJSONObject("facts");
        if (facts == null) {
            throw new DocumentFormatException(path + ".facts",
                    "device facts are missing in the roster");
        }
        JSONObject state = json.optJSONObject("state");
        if (state == null) {
            throw new DocumentFormatException(path + ".state",
                    "device state is missing in the roster");
        }
        return new DeviceDto(
                JsonValues.requiredString(id, "modelId"),
                JsonValues.requiredString(id, "address"),
                JsonValues.requiredString(json, "alias"),
                IdentityDto.fromJson(identity),
                JsonValues.requiredLong(json, "lastSeen"),
                FactsDto.fromJson(facts, path + ".facts"),
                StateDto.fromJson(state, path));
    }
}
