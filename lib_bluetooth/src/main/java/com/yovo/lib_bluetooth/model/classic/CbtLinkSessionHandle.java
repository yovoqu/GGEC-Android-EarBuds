package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 链路会话句柄：取消订阅并释放会话资源。
 *
 * <p>幂等；取消后不再有任何回调。</p>
 */
public interface CbtLinkSessionHandle {

    /** 取消会话：停止监听、撤销定时器、不再回调。幂等。 */
    void cancel();

    /** 会话是否仍然有效。 */
    boolean isActive();
}
