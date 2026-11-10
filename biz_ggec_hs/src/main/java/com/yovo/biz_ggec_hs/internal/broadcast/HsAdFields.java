package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 把原始广播拆成独立的 AD 结构。HS02 的基础帧和补充帧必须按结构分开，不能先按厂商号合并。
 */
final class HsAdFields {
    static final int COMPLETE_NAME = 0x09;
    static final int MANUFACTURER = 0xFF;

    private HsAdFields() {
    }

    static List<Field> decode(byte[] record) {
        List<Field> fields = new ArrayList<>();
        if (record == null || record.length == 0) {
            return fields;
        }
        int offset = 0;
        while (offset < record.length) {
            int length = record[offset] & 0xFF;
            if (length == 0 || offset + 1 + length > record.length) {
                break;
            }
            int type = record[offset + 1] & 0xFF;
            byte[] data = Arrays.copyOfRange(record, offset + 2, offset + 1 + length);
            fields.add(new Field(type, data));
            offset += length + 1;
        }
        return fields;
    }

    static byte[] encode(int type, byte[] data) {
        byte[] field = new byte[data.length + 2];
        field[0] = (byte) (data.length + 1);
        field[1] = (byte) type;
        System.arraycopy(data, 0, field, 2, data.length);
        return field;
    }

    @Nullable
    static String decodeUtf8(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            String name = decoded.toString();
            return name.isEmpty() ? null : name;
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    static final class Field {
        final int type;
        final byte[] data;

        Field(int type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }
}
