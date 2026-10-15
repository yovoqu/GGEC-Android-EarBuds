package com.yovo.yotheme.background.shapeblurbiew.impl;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * @author tingfeng.yang
 * @date 2025-11-26
 * @description 高斯模糊实现接口
 */
public interface BlurImpl {

    boolean prepare(Context context, Bitmap buffer, float radius);

    void release();

    void blur(Bitmap input, Bitmap output);
}
