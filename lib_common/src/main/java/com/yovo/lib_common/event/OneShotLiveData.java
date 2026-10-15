package com.yovo.lib_common.event;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * 单次消费的 LiveData：观察者收到值后立即清空，旋转重建不会重放副作用。
 *
 * <p>用于 Effect / Event，不用于可持续的 UI 状态。</p>
 */
public final class OneShotLiveData<T> extends MutableLiveData<T> {

    @MainThread
    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, value -> {
            if (value == null) {
                return;
            }
            observer.onChanged(value);
            if (value.equals(getValue())) {
                setValue(null);
            }
        });
    }
}
