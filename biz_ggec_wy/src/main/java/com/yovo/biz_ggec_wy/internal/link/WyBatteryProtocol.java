package com.yovo.biz_ggec_wy.internal.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.WyDevice;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * WY01 电量协议（0x80）：
 * <ul>
 *   <li>单路查询（与 HS02 一致）：{@code 99 EC 80 00 01 01/02/03 12 34}，
 *       响应 {@code 99 EC 80 00 02 01/02/03 xx 12 34}</li>
 *   <li>三部分查询 / 耳机主动上报（WY01 新增）：{@code 99 EC 80 00 01 00 12 34}，
 *       响应 / 上报 {@code 99 EC 80 00 04 00 xx xx xx 12 34}（依次为左耳、右耳、充电盒）</li>
 * </ul>
 */
public final class WyBatteryProtocol {

    public static final int PART_ALL = 0x00;
    public static final int PART_LEFT = 0x01;
    public static final int PART_RIGHT = 0x02;
    public static final int PART_CASE = 0x03;

    static final int HEADER_0 = 0x99;
    static final int HEADER_1 = 0xEC;
    static final int CMD_BATTERY = 0x80;
    static final int TAIL_0 = 0x12;
    static final int TAIL_1 = 0x34;

    private static final int MIN_FRAME_LEN = 7;
    private static final int MAX_PAYLOAD_LEN = 256;
    private static final int MAX_BUFFER_BYTES = 1024;

    /** APP 主动查询三部分电量：99 EC 80 00 01 00 12 34。 */
    public static final byte[] QUERY_ALL = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x00, 0x12, 0x34
    };

    /** 查询左耳电量：99 EC 80 00 01 01 12 34。 */
    public static final byte[] QUERY_LEFT = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x01, 0x12, 0x34
    };

    /** 查询右耳电量：99 EC 80 00 01 02 12 34。 */
    public static final byte[] QUERY_RIGHT = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x02, 0x12, 0x34
    };

    /** 查询充电盒电量：99 EC 80 00 01 03 12 34。 */
    public static final byte[] QUERY_CASE = new byte[]{
            (byte) 0x99, (byte) 0xEC, (byte) 0x80, 0x00, 0x01, 0x03, 0x12, 0x34
    };

    public sealed interface BatteryUpdate {
        record All(int leftLevel, int rightLevel, int caseLevel) implements BatteryUpdate {
        }

        record Single(int partId, int level) implements BatteryUpdate {
        }
    }

    private WyBatteryProtocol() {
    }

    @NonNull
    public static byte[] queryFrame(int partId) {
        return switch (partId) {
            case PART_ALL -> QUERY_ALL.clone();
            case PART_LEFT -> QUERY_LEFT.clone();
            case PART_RIGHT -> QUERY_RIGHT.clone();
            case PART_CASE -> QUERY_CASE.clone();
            default -> throw new IllegalArgumentException("Unsupported battery partId: " + partId);
        };
    }

    public static int normalizeLevel(int rawUnsignedByte) {
        if (rawUnsignedByte >= 1 && rawUnsignedByte <= 100) {
            return rawUnsignedByte;
        }
        return WyDevice.Battery.UNKNOWN_LEVEL;
    }

    @NonNull
    public static Optional<BatteryUpdate> parseFrame(@Nullable byte[] frame) {
        if (frame == null || frame.length < MIN_FRAME_LEN) {
            return Optional.empty();
        }
        if ((frame[0] & 0xFF) != HEADER_0
                || (frame[1] & 0xFF) != HEADER_1
                || (frame[2] & 0xFF) != CMD_BATTERY) {
            return Optional.empty();
        }
        int payloadLen = ((frame[3] & 0xFF) << 8) | (frame[4] & 0xFF);
        int expectedLen = MIN_FRAME_LEN + payloadLen;
        if (frame.length != expectedLen) {
            return Optional.empty();
        }
        if ((frame[expectedLen - 2] & 0xFF) != TAIL_0
                || (frame[expectedLen - 1] & 0xFF) != TAIL_1) {
            return Optional.empty();
        }

        // 99 EC 80 00 04 00 <left> <right> <case> 12 34
        if (payloadLen == 4 && (frame[5] & 0xFF) == PART_ALL) {
            int left = normalizeLevel(frame[6] & 0xFF);
            int right = normalizeLevel(frame[7] & 0xFF);
            int caseLevel = normalizeLevel(frame[8] & 0xFF);
            return Optional.of(new BatteryUpdate.All(left, right, caseLevel));
        }

        // 99 EC 80 00 02 <01|02|03> <level> 12 34
        if (payloadLen == 2) {
            int partId = frame[5] & 0xFF;
            if (partId >= PART_LEFT && partId <= PART_CASE) {
                int level = normalizeLevel(frame[6] & 0xFF);
                return Optional.of(new BatteryUpdate.Single(partId, level));
            }
        }

        return Optional.empty();
    }

    @NonNull
    public static WyDevice.Battery applyUpdate(@NonNull WyDevice.Battery current,
                                               @NonNull BatteryUpdate update) {
        if (update instanceof BatteryUpdate.All all) {
            return new WyDevice.Battery(
                    all.leftLevel(),
                    all.rightLevel(),
                    all.caseLevel(),
                    current.caseCharging());
        }
        BatteryUpdate.Single single = (BatteryUpdate.Single) update;
        return switch (single.partId()) {
            case PART_LEFT -> new WyDevice.Battery(
                    single.level(), current.rightLevel(), current.caseLevel(), current.caseCharging());
            case PART_RIGHT -> new WyDevice.Battery(
                    current.leftLevel(), single.level(), current.caseLevel(), current.caseCharging());
            case PART_CASE -> new WyDevice.Battery(
                    current.leftLevel(), current.rightLevel(), single.level(), current.caseCharging());
            default -> current;
        };
    }

    /**
     * 粘包/半包帧提取器：从连续的控制流字节中提取完整的 {@code 99 EC ... 12 34} 帧。
     */
    static final class FrameAccumulator {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(64);

        synchronized void reset() {
            buffer.reset();
        }

        @NonNull
        synchronized List<byte[]> append(@Nullable byte[] chunk) {
            if (chunk == null || chunk.length == 0) {
                return List.of();
            }
            if (buffer.size() + chunk.length > MAX_BUFFER_BYTES) {
                buffer.reset();
            }
            buffer.write(chunk, 0, chunk.length);

            byte[] data = buffer.toByteArray();
            List<byte[]> frames = new ArrayList<>();
            int pos = 0;

            while (pos <= data.length - 2) {
                if ((data[pos] & 0xFF) != HEADER_0 || (data[pos + 1] & 0xFF) != HEADER_1) {
                    pos++;
                    continue;
                }
                if (data.length - pos < MIN_FRAME_LEN) {
                    break;
                }
                int payloadLen = ((data[pos + 3] & 0xFF) << 8) | (data[pos + 4] & 0xFF);
                if (payloadLen > MAX_PAYLOAD_LEN) {
                    pos++;
                    continue;
                }
                int totalLen = MIN_FRAME_LEN + payloadLen;
                if (data.length - pos < totalLen) {
                    break;
                }
                if ((data[pos + totalLen - 2] & 0xFF) == TAIL_0
                        && (data[pos + totalLen - 1] & 0xFF) == TAIL_1) {
                    frames.add(Arrays.copyOfRange(data, pos, pos + totalLen));
                    pos += totalLen;
                } else {
                    pos++;
                }
            }

            buffer.reset();
            if (pos < data.length) {
                buffer.write(data, pos, data.length - pos);
            }
            return frames;
        }
    }
}
