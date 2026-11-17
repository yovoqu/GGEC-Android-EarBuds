package com.yovo.lib_bluetooth.internal.common.error;

import androidx.annotation.NonNull;

/**
 * 统一错误码接口。
 *
 * <p>实现类必须<b>自带对外失败分类</b>（{@link #publicClass()}）：内部失败沿调用链直接携带
 * 对外契约枚举，门面投影只做一次类型收窄，不存在「内部字符串 → 公开枚举」的对表。
 * {@link #code()} 只是诊断标识（日志、时间线、traceId 去重），不参与分类判断。</p>
 *
 * @param <C> 对外公开分类枚举（属于 api/model 契约，实现层只能读、不能新增）
 */
public interface YoErrorCode<C> {

    /** 诊断标识：稳定、可 grep，仅用于日志与诊断去重。 */
    @NonNull
    String code();

    /** 对外失败分类：内部失败携带的公开契约枚举值。 */
    @NonNull
    C publicClass();
}
