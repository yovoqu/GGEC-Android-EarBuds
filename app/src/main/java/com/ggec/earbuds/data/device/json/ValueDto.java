package com.ggec.earbuds.data.device.json;

import androidx.annotation.NonNull;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 台账里一个能力项的持久化取值。
 *
 * <p>用密封类型而不是"kind 字符串 + 一堆可空字段"：每种取值的形状在类型上就是确定的，
 * 读的时候不可能出现"TOGGLE 却只有 level"。未知 kind 是损坏，不是忽略。</p>
 */
sealed interface ValueDto {

    @NonNull
    JSONObject toJson();

    @NonNull
    static ValueDto fromJson(@NonNull JSONObject json, @NonNull String path) {
        String kind = JsonValues.requiredString(json, "kind");
        return switch (kind) {
            case "TOGGLE" -> new Toggle(JsonValues.requiredBoolean(json, "enabled"));
            case "LEVEL" -> new Level(JsonValues.requiredInt(json, "value"));
            case "CHOICE" -> new Choice(JsonValues.requiredString(json, "optionId"));
            case "CURVE" -> new Curve(points(json, path));
            default -> throw new DocumentFormatException(path + ".kind",
                    "unknown control kind " + kind + " in the roster");
        };
    }

    private static List<Integer> points(JSONObject json, String path) {
        var array = json.optJSONArray("points");
        if (array == null) {
            throw new DocumentFormatException(path + ".points",
                    "curve points are missing in the roster");
        }
        List<Integer> points = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            Object raw = array.opt(index);
            if (!(raw instanceof Number number)) {
                throw new DocumentFormatException(path + ".points[" + index + "]",
                        "curve point is not a number in the roster");
            }
            points.add(number.intValue());
        }
        return points;
    }

    record Toggle(boolean enabled) implements ValueDto {

        @NonNull
        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            JsonValues.put(json, "kind", "TOGGLE");
            JsonValues.put(json, "enabled", enabled);
            return json;
        }
    }

    record Level(int value) implements ValueDto {

        @NonNull
        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            JsonValues.put(json, "kind", "LEVEL");
            JsonValues.put(json, "value", value);
            return json;
        }
    }

    record Choice(String optionId) implements ValueDto {

        @NonNull
        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            JsonValues.put(json, "kind", "CHOICE");
            JsonValues.put(json, "optionId", optionId);
            return json;
        }
    }

    record Curve(List<Integer> points) implements ValueDto {

        // 接口里的 record 隐式 public，规范构造器也必须 public，否则 javac 判为无效规范构造器。
        public Curve {
            points = List.copyOf(points);
        }

        @NonNull
        @Override
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            JsonValues.put(json, "kind", "CURVE");
            var array = new org.json.JSONArray();
            for (int point : points) {
                array.put(point);
            }
            JsonValues.put(json, "points", array);
            return json;
        }
    }
}
