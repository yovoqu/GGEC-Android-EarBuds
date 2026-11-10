package com.yovo.biz_ggec_hs.model;

import androidx.annotation.NonNull;

/**
 * 耳机电量快照。电量字段沿用协议文本，未知时可能是 {@code --}。
 */
public final class HsBattery {
    @NonNull
    private final String leftLevel;
    @NonNull
    private final String rightLevel;
    @NonNull
    private final String caseLevel;
    private final boolean caseCharging;
    private final int earInCaseStatus;

    public HsBattery(@NonNull String leftLevel,
                     @NonNull String rightLevel,
                     @NonNull String caseLevel,
                     boolean caseCharging,
                     int earInCaseStatus) {
        this.leftLevel = leftLevel;
        this.rightLevel = rightLevel;
        this.caseLevel = caseLevel;
        this.caseCharging = caseCharging;
        this.earInCaseStatus = earInCaseStatus;
    }

    @NonNull
    public String leftLevel() {
        return leftLevel;
    }

    @NonNull
    public String rightLevel() {
        return rightLevel;
    }

    @NonNull
    public String caseLevel() {
        return caseLevel;
    }

    public boolean caseCharging() {
        return caseCharging;
    }

    /**
     * 0 都不在盒内，1 仅左耳，2 仅右耳，3 都在盒内。
     */
    public int earInCaseStatus() {
        return earInCaseStatus;
    }
}
