package com.yovo.biz_ggec_hs.internal.scan;





import com.yovo.biz_ggec_hs.api.HsScan;
/**
 * 过滤规则求值：维度内 OR、维度间 AND、空集 = 不限。
 *
 * <p>未登记型号（{@code modelId} 为空）没有设备类型与项目归属：空规则下照常出现；
 * 任一维度参与筛选时按「不匹配」排除——不新增隐式分类（例如 {@code OTHER}）。</p>
 */
final class HsNearbyRules {

    private HsNearbyRules() {
    }

    static boolean matches(HsScan.Rule rule, HsNearbyRegistry.Entry entry) {
        if (rule == null || entry == null) {
            return false;
        }
        if (!rule.deviceTypes().isEmpty()
                && (entry.deviceType() == null
                || !rule.deviceTypes().contains(entry.deviceType()))) {
            return false;
        }
        if (!rule.projectIds().isEmpty()
                && (entry.projectId() == null
                || !rule.projectIds().contains(entry.projectId()))) {
            return false;
        }
        if (!rule.modelIds().isEmpty()
                && (entry.modelId().isEmpty()
                || !rule.modelIds().contains(entry.modelId()))) {
            return false;
        }
        if (rule.onlyConnectable() && !entry.connectable()) {
            return false;
        }
        return entry.rssi() >= rule.minRssi();
    }
}
