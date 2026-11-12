package com.ggec.earbuds.ui;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * 单次消费的 LiveData。观察者收到值后立即清空，旋转重建不会重放副作用。
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
