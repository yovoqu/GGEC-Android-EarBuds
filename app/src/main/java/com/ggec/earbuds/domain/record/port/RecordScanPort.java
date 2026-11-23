package com.ggec.earbuds.domain.record.port;

/**
 * Record 附近设备扫描端口（领域侧）。由 provider/biz_ggec_record 实现，装配根注入。
 */
public interface RecordScanPort {

    void start(RecordScanObserver observer);

    void stop();
}
