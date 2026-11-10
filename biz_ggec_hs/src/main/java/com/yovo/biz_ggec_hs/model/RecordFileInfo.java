package com.yovo.biz_ggec_hs.model;

/**
 * Author: tingfeng.yang
 * Date: 2026-06-26
 * Description:
 * 耳机录音文件信息数据模型
 * 对应协议 EC C0 响应中每条录音记录（固定 20 字节）的解析结果。
 * <p>
 * 单条记录字节布局：
 * 创建时间 8 字节（年 uint16 小端 + 月 + 日 + 周 + 时 + 分 + 秒）
 * + 录音索引 1 字节 + 录音类型 1 字节 + 录音时长 2 字节（小端）
 * + 录音大小 4 字节（小端） + 录音 ID 4 字节（小端）
 * <p>
 * 录音类型字节 = 三段位域按位或组合：
 * <ul>
 *   <li>bit7：0x80 = 前半段；bit6：0x40 = 后半段（无分段时为 0）</li>
 *   <li>高 4 位低 2 bit 为耳侧：0x20 左耳 / 0x10 右耳</li>
 *   <li>低 4 位为录音类型：0x01 本地 / 0x02 通话</li>
 * </ul>
 * 例如 0x21 = 左耳 + 本地录音，0x12 = 右耳 + 通话录音，
 * 0x91 = 前半段 + 右耳 + 本地录音，0x62 = 后半段 + 左耳 + 通话录音。
 * 解析规则仅供协议说明；接口对外直接透传设备原始录音类型字节，不做拆分解析。
 * <p>
 * 注意：此类是 SDK 的公开 API 之一，不会被混淆。
 */
public class RecordFileInfo {

    // ---------- 录音类型字节解析规则（对外透传原始字节） ----------

    /** 类型字节掩码：低 4 位为录音类型 */
    public static final int RECORD_TYPE_MASK = 0x0F;

    /** 录音类型：本地录音 */
    public static final int RECORD_TYPE_LOCAL = 0x01;
    /** 录音类型：通话录音 */
    public static final int RECORD_TYPE_CALL = 0x02;

    /**
     * 从协议录音类型字节解析录音类型（低 4 位）。
     *
     * @param typeByte 协议录音类型原始字节
     * @return 0x01 本地录音 / 0x02 通话录音 / 0x00 未知
     */
    public static int recordTypeOf(int typeByte) {
        return typeByte & RECORD_TYPE_MASK;
    }

    // ---------- 字段 ----------

    /** 文件名（与索引相同，字符串形式） */
    private final String name;

    /** 录音创建时间，格式 YYYYMMDDHHmmss */
    private final String createTime;

    /** 录音索引（1 字节） */
    private final int index;

    /** 录音类型：设备原始录音类型字节（直传，不做拆分解析） */
    private final int recordType;

    /** 录音时长（2 字节小端，单位以协议为准） */
    private final int duration;

    /** 录音大小（4 字节小端，单位字节） */
    private final long size;

    /** 录音 ID（4 字节小端无符号值，用 long 承载） */
    private final long id;

    // ---------- 构造方法（仅供命令解析器使用） ----------

    /**
     * @param name       文件名（与索引相同，字符串形式）
     * @param createTime 创建时间，格式 YYYYMMDDHHmmss
     * @param index      录音索引（0~255）
     * @param recordType 设备原始录音类型字节（直传，不做拆分解析）
     * @param duration   录音时长
     * @param size       录音大小（字节）
     * @param id         录音 ID（4 字节小端无符号值）
     */
    public RecordFileInfo(String name, String createTime, int index,
                          int recordType, int duration, long size, long id) {
        this.name = name;
        this.createTime = createTime;
        this.index = index;
        this.recordType = recordType;
        this.duration = duration;
        this.size = size;
        this.id = id;
    }

    // ---------- 公开 getter ----------

    /**
     * 获取文件名（与索引相同）
     *
     * @return 文件名
     */
    public String getName() {
        return name;
    }

    /**
     * 获取录音创建时间
     *
     * @return 创建时间字符串，格式 YYYYMMDDHHmmss
     */
    public String getCreateTime() {
        return createTime;
    }

    /**
     * 获取录音索引
     *
     * @return 录音索引（0~255）
     */
    public int getIndex() {
        return index;
    }

    /**
     * 获取设备原始录音类型字节（直传，不做拆分解析）
     *
     * @return 设备原始录音类型字节
     */
    public int getRecordType() {
        return recordType;
    }

    /**
     * 获取录音时长
     *
     * @return 录音时长（2 字节小端原始值）
     */
    public int getDuration() {
        return duration;
    }

    /**
     * 获取录音大小
     *
     * @return 录音大小（字节）
     */
    public long getSize() {
        return size;
    }

    /**
     * 获取录音 ID。
     *
     * @return 录音 ID（4 字节小端无符号值，范围 0~4294967295）
     */
    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "RecordFileInfo{" +
                "name=" + name +
                ", createTime=" + createTime +
                ", index=" + index +
                ", recordType=" + recordType +
                ", duration=" + duration +
                ", size=" + size +
                ", id=" + id +
                '}';
    }
}
