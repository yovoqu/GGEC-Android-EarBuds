package com.yovo.yotheme.background.shapeblurbiew.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;

import com.yovo.yotheme.background.shapeblurbiew.impl.AndroidStockBlurImpl;
import com.yovo.yotheme.background.shapeblurbiew.impl.AndroidXBlurImpl;
import com.yovo.yotheme.background.shapeblurbiew.impl.BlurImpl;
import com.yovo.yotheme.background.shapeblurbiew.impl.EmptyBlurImpl;
import com.yovo.yotheme.background.shapeblurbiew.impl.SupportLibraryBlurImpl;

/**
 * @author tingfeng.yang
 * @date 2025-11-27
 * @description 模糊引擎管理器，负责选择和管理最优的模糊实现
 */
public class BlurEngine {
    
    private static final int IMPL_UNKNOWN = 0;
    private static final int IMPL_ANDROIDX = 1;
    private static final int IMPL_SUPPORT = 2;
    private static final int IMPL_STOCK = 3;
    private static final int IMPL_EMPTY = -1;
    
    private static int sBlurImpl = IMPL_UNKNOWN;
    
    private final BlurImpl blurImpl;
    private final Context context;
    
    public BlurEngine(Context context) {
        this.context = context.getApplicationContext();
        this.blurImpl = createBlurImpl();
    }
    
    /**
     * 自动选择最优的模糊实现
     */
    private BlurImpl createBlurImpl() {
        if (sBlurImpl == IMPL_UNKNOWN) {
            detectBlurImpl();
        }
        
        switch (sBlurImpl) {
            case IMPL_ANDROIDX:
                return new AndroidXBlurImpl();
            case IMPL_SUPPORT:
                return new SupportLibraryBlurImpl();
            case IMPL_STOCK:
                return new AndroidStockBlurImpl();
            default:
                return new EmptyBlurImpl();
        }
    }
    
    /**
     * 检测可用的模糊实现（仅执行一次）
     */
    private void detectBlurImpl() {
        // 优先尝试系统自带 RenderScript (Android 4.2+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (tryBlurImpl(new AndroidStockBlurImpl())) {
                sBlurImpl = IMPL_STOCK;
                return;
            }
        }
        
        // 尝试 AndroidX RenderScript
        if (tryLoadClass("androidx.renderscript.RenderScript")) {
            if (tryBlurImpl(new AndroidXBlurImpl())) {
                sBlurImpl = IMPL_ANDROIDX;
                return;
            }
        }
        
        // 尝试 Support Library RenderScript
        if (tryLoadClass("android.support.v8.renderscript.RenderScript")) {
            if (tryBlurImpl(new SupportLibraryBlurImpl())) {
                sBlurImpl = IMPL_SUPPORT;
                return;
            }
        }
        
        // 降级为空实现
        sBlurImpl = IMPL_EMPTY;
    }
    
    private boolean tryLoadClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
    
    private boolean tryBlurImpl(BlurImpl impl) {
        try {
            Bitmap testBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            boolean success = impl.prepare(context, testBitmap, 4);
            impl.release();
            testBitmap.recycle();
            return success;
        } catch (Throwable e) {
            return false;
        }
    }
    
    public BlurImpl getImpl() {
        return blurImpl;
    }
    
    public void release() {
        if (blurImpl != null) {
            blurImpl.release();
        }
    }
}
