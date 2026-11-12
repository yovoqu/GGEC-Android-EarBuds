package com.yovo.biz_ggec_hs.internal.center.protocol;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命令完成结果的不可变快照。
 */
public final class CommandCompletion {

    public enum Shape {
        STRING,
        OBJECT,
        INTEGER
    }

    private final Shape shape;
    private final int code;
    private final Object data;
    private final int resultValue;
    private final String message;

    private CommandCompletion(Shape shape, int code, Object data, int resultValue, String message) {
        this.shape = shape;
        this.code = code;
        this.data = copyContainer(data);
        this.resultValue = resultValue;
        this.message = message;
    }

    public static CommandCompletion stringResult(int code, String message) {
        return new CommandCompletion(Shape.STRING, code, null, 0, message);
    }

    public static CommandCompletion objectResult(int code, Object data, String message) {
        return new CommandCompletion(Shape.OBJECT, code, data, 0, message);
    }

    public static CommandCompletion integerResult(int code, int resultValue, String message) {
        return new CommandCompletion(Shape.INTEGER, code, null, resultValue, message);
    }

    public Shape getShape() {
        return shape;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return copyContainer(data);
    }

    public int getResultValue() {
        return resultValue;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return code == Command.ResultCode.SUCCESS;
    }

    private static Object copyContainer(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                copy.put(copyContainer(entry.getKey()), copyContainer(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List) {
            List<?> source = (List<?>) value;
            List<Object> copy = new ArrayList<>(source.size());
            for (Object item : source) {
                copy.add(copyContainer(item));
            }
            return copy;
        }
        if (value instanceof Set) {
            Set<?> source = (Set<?>) value;
            Set<Object> copy = new LinkedHashSet<>();
            for (Object item : source) {
                copy.add(copyContainer(item));
            }
            return copy;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(type.getComponentType(), length);
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, copyContainer(Array.get(value, i)));
            }
            return copy;
        }
        return value;
    }
}
