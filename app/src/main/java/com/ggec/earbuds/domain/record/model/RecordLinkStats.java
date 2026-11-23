package com.ggec.earbuds.domain.record.model;

/**
 * 链路统计快照（约每秒刷新一次）。只承载 APP 呈现所需的三项速率，不含队列/丢帧等内部诊断字段。
 */
public record RecordLinkStats(double videoKbps, double audioKbps, double videoFps) {
}
