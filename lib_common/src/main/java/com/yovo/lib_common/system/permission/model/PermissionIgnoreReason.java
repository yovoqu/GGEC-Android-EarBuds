package com.yovo.lib_common.system.permission.model;

/**
 * 权限结果未被本服务处理时的原因分类。
 *
 * <p>取代旧行为：把「未知 Activity / 未知 requestCode / 跨 Activity 歧义」统一折叠成一个
 * 无信息的 {@code ignored()}。上层据此可以区分「不是我的请求」（正常忽略）与
 * 「请求码配错 / 多 Activity 撞码」（配置错误，需要修调用点）。</p>
 */
public enum PermissionIgnoreReason {

    /** 没有任何待处理的权限请求（从未发起，或已被上一次结果消费）。 */
    NO_PENDING_REQUEST,

    /** 传入了 Activity，但该 Activity 名下没有任何待处理请求。 */
    UNKNOWN_ACTIVITY,

    /** Activity 已知，但它没有登记该 requestCode 的待处理请求。 */
    UNKNOWN_REQUEST_CODE,

    /** 未传 Activity，且多个 Activity 登记了同一 requestCode，无法唯一消歧。 */
    AMBIGUOUS_ACTIVITY
}
