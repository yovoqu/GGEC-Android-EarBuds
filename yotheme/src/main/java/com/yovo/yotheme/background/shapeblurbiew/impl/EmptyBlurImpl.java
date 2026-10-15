package com.yovo.yotheme.background.shapeblurbiew.impl;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * @author tingfeng.yang
 * @date 2025-11-26
 * @description 空实现：当 RenderScript 不可用时降级，无模糊效果
 */
public class EmptyBlurImpl implements BlurImpl {

    @Override
    public boolean prepare(Context context, Bitmap buffer, float radius) {
        return false;
    }

    @Override
    public void release() {
        // no-op
    }

    @Override
    public void blur(Bitmap input, Bitmap output) {
        // no-op
    }
}
