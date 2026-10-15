package com.yovo.yotheme.background.shapeblurbiew.enums;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author tingfeng.yang
 * @date 2025-11-26
 * @description  Blur 模式定义：矩形 / 圆形 / 椭圆
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({
        BlurMode.MODE_RECTANGLE,
        BlurMode.MODE_CIRCLE,
        BlurMode.MODE_OVAL
})
public @interface BlurMode {
    int MODE_RECTANGLE = 0;
    int MODE_CIRCLE = 1;
    int MODE_OVAL = 2;
}
