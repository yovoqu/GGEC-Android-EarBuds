package com.yovo.yotheme.components.yo_model_panel;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.ModalPanelConstants;
import com.yovo.yotheme.components.yo_model_panel.panel_structure.PanelAnimationManager;
import com.yovo.yotheme.components.yo_model_panel.panel_structure.PanelContainer;
import com.yovo.yotheme.components.yo_model_panel.panel_structure.PanelStateManager;
import com.yovo.yotheme.components.yo_model_panel.panel_structure.PanelDragBar;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2025-08-15
 * @description 核心模态面板组件
 * @note 实现 YoModalPanelInterface 的主要功能
 */
public class YoModalPanel extends Dialog implements YoModalPanelInterface.YoModalPanel {

    // 核心组件
    private YoModalPanelInterface.PanelConfig config;
    private PanelStateManager stateManager;
    private PanelAnimationManager animationManager;
    
    // UI 组件
    private FrameLayout rootContainer;
    private View maskView;
    private PanelContainer panelContainer;
    
    // 拖拽相关
    private VelocityTracker velocityTracker;
    private float lastTouchY = 0f;
    private float panelStartY = 0f;
    private boolean isDragging = false;
    
    // 监听器
    private YoModalPanelInterface.OnPanelStateChangeListener stateChangeListener;
    private YoModalPanelInterface.OnPanelInteractionListener interactionListener;
    private YoModalPanelInterface.OnPanelLifecycleListener lifecycleListener;
    
    // 布局完成监听器
    private ViewTreeObserver.OnGlobalLayoutListener layoutCompleteListener;
    private boolean isLayoutReady = false;
    private boolean isInternalDismiss = false;

    // 缓存在 onCreate 之前添加的内容视图
    private List<View> pendingContentViews = new ArrayList<>();
    private boolean isUICreated = false;

    public YoModalPanel(@NonNull Context context) {
        this(context, new YoModalPanelInterface.PanelConfig());
    }

    public YoModalPanel(@NonNull Context context, YoModalPanelInterface.PanelConfig config) {
        super(context, android.R.style.Theme_Translucent_NoTitleBar);
        this.config = config != null ? config : new YoModalPanelInterface.PanelConfig();
        
        // 初始化状态管理器
        stateManager = new PanelStateManager();
        stateManager.addStateChangeListener(this::onInternalStateChanged);
        
        // 设置对话框属性
        setCancelable(this.config.cancelable);
        setCanceledOnTouchOutside(this.config.cancelableOnTouchOutside);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 通知生命周期
        notifyLifecycleListener(l -> l.onPanelCreated());

        // 创建UI
        createUI();

        // 标记UI已创建
        isUICreated = true;

        // 将缓存的内容视图添加到面板
        for (View view : pendingContentViews) {
            panelContainer.addContentView(view);
        }
        pendingContentViews.clear();

        // 设置窗口属性
        setupWindow();

        // 使用 super.setContentView() 设置对话框根视图
        // 注意：不能使用 this.setContentView()，因为我们重写了该方法
        super.setContentView(rootContainer);

        // 初始化动画管理器
        initAnimationManager();

        // 设置触摸事件
        setupTouchEvents();

        // 设置布局完成监听器，确保在布局完成后才进行显示准备
        setupLayoutCompleteListener();
    }

    /**
     * 创建UI布局
     */
    private void createUI() {
        Context context = getContext();
        
        // 根容器
        rootContainer = new FrameLayout(context);
        rootContainer.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        // 遮罩层
        createMaskView();
        
        // 面板容器
        createPanelContainer();
        
        // 添加到根容器
        rootContainer.addView(maskView);
        rootContainer.addView(panelContainer);
    }

    /**
     * 创建遮罩视图
     */
    private void createMaskView() {
        maskView = new View(getContext());
        // 使用纯黑背景，通过 View 的 alpha 实现目标遮罩强度，避免叠乘导致过淡与闪屏
        maskView.setBackgroundColor(Color.argb(
            ModalPanelConstants.OPAQUE_ALPHA_255.getIntValue(),
            0, 0, 0
        ));
        maskView.setAlpha(0f);
        maskView.setOnClickListener(v -> {
            if (config.cancelableOnTouchOutside) {
                notifyInteractionListener(l -> l.onOutsideClicked());
                if (shouldDismiss(YoModalPanelInterface.DismissReason.OUTSIDE_TOUCH)) {
                    if (animationManager != null) {
                        animationManager.animateHide();
                    }
                }
            }
        });
        
        FrameLayout.LayoutParams maskParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        maskView.setLayoutParams(maskParams);
    }

    /**
     * 创建面板容器
     */
    private void createPanelContainer() {
        panelContainer = new PanelContainer(getContext());
        panelContainer.setConfig(config);
        
        // 根据方向设置面板位置
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        
        if (config.direction == YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP) {
            panelParams.gravity = Gravity.BOTTOM;
        } else {
            panelParams.gravity = Gravity.TOP;
            // 当窗口已整体下移时，面板无需额外 topMargin
        }
        
        panelContainer.setLayoutParams(panelParams);
    }

    /**
     * 设置窗口属性
     */
    private void setupWindow() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                           WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            // 若为从顶部向下的面板，并且设置了 topOffset，则将整个对话框窗口下移，
            // 使得遮罩与面板均位于顶部栏之下，避免遮罩覆盖顶部栏与拦截顶部栏点击。
            if (config.direction == YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM && config.topOffset > 0f) {
                int offsetY = dpToPx(config.topOffset);
                int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
                int windowHeight = Math.max(0, screenHeight - offsetY);

                window.setGravity(Gravity.TOP);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.y = offsetY;
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = windowHeight > 0 ? windowHeight : WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(lp);
            } else {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                                 WindowManager.LayoutParams.MATCH_PARENT);
            }
        }
    }

    /**
     * 初始化动画管理器
     */
    private void initAnimationManager() {
        animationManager = new PanelAnimationManager(
            panelContainer, maskView, config, stateManager
        );
        
        animationManager.setAnimationListener(new PanelAnimationManager.OnAnimationListener() {
            @Override
            public void onShowStart() {
                notifyLifecycleListener(l -> l.onPanelWillShow());
            }
            
            @Override
            public void onShowEnd() {
                notifyLifecycleListener(l -> l.onPanelDidShow());
            }
            
            @Override
            public void onHideStart() {
                notifyLifecycleListener(l -> l.onPanelWillHide());
            }
            
            @Override
            public void onHideEnd() {
                isInternalDismiss = true;
                dismiss();
                notifyLifecycleListener(l -> l.onPanelDidHide());
            }
            
            @Override
            public void onBounceStart() {
                // 可以添加回弹开始的处理
            }
            
            @Override
            public void onBounceEnd() {
                // 可以添加回弹结束的处理
            }
        });
    }

    /**
     * 设置触摸事件处理
     */
    private void setupTouchEvents() {
        // 优先将拖拽绑定在拖拽条上，若不可用则回退到整个面板容器
        View dragTarget = panelContainer.getDragBar() != null ? panelContainer.getDragBar() : panelContainer;
        dragTarget.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!config.enableDamping || !stateManager.isInteractive()) {
                    return false;
                }
                // 拖拽条触摸时触发按压视觉反馈（可开关）
                if (v instanceof PanelDragBar) {
                    PanelDragBar bar = (PanelDragBar) v;
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            bar.onPressStateChanged(true);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            bar.onPressStateChanged(false);
                            break;
                    }
                }
                return handleTouchEvent(event);
            }
        });
    }

    /**
     * 处理触摸事件
     */
    private boolean handleTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(event);
                
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(event);
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleTouchUp(event);
        }
        
        return false;
    }

    /**
     * 处理按下事件
     */
    private boolean handleTouchDown(MotionEvent event) {
        lastTouchY = event.getRawY();
        panelStartY = panelContainer.getTranslationY();
        isDragging = true;
        
        // 停止正在进行的动画
        animationManager.cancelAllAnimations();
        
        return true;
    }

    /**
     * 处理移动事件
     */
    private boolean handleTouchMove(MotionEvent event) {
        if (!isDragging) return false;
        
        float deltaY = event.getRawY() - lastTouchY;
        float newTranslationY = panelStartY + deltaY;
        
        // 限制拖拽范围
        if (config.direction == YoModalPanelInterface.PanelDirection.BOTTOM_TO_TOP) {
            // 从底部滑入，只允许向下拖拽（正方向）
            newTranslationY = Math.max(0, newTranslationY);
        } else {
            // 从顶部滑入，只允许向上拖拽（负方向）
            newTranslationY = Math.min(0, newTranslationY);
        }
        
        // 计算拖拽进度
        float maxTranslation = getMaxTranslation();
        float progress = Math.abs(newTranslationY) / maxTranslation;
        progress = Math.min(1.0f, progress);
        
        // 计算速度
        velocityTracker.computeCurrentVelocity(ModalPanelConstants.VELOCITY_TIME_UNITS_PER_SECOND.getIntValue());
        float velocity = Math.abs(velocityTracker.getYVelocity()) / 1000f; // px/ms
        
        // 更新位置
        animationManager.updateDragPosition(newTranslationY, progress);
        
        // 更新状态
        stateManager.updateDragProgress(progress, velocity);
        
        // 通知监听器（lambda 捕获变量需为 final 或实际 final）
        final float dragProgress = progress;
        final float dragVelocity = velocity;
        notifyInteractionListener(l -> l.onPanelDragging(dragProgress, dragVelocity));
        
        return true;
    }

    /**
     * 处理抬起事件
     */
    private boolean handleTouchUp(MotionEvent event) {
        if (!isDragging) return false;
        
        isDragging = false;
        
        // 计算最终速度
        velocityTracker.computeCurrentVelocity(ModalPanelConstants.VELOCITY_TIME_UNITS_PER_SECOND.getIntValue());
        float velocity = Math.abs(velocityTracker.getYVelocity()) / 1000f; // px/ms
        
        // 决定是隐藏还是回弹
        float currentTranslation = Math.abs(panelContainer.getTranslationY());
        float maxTranslation = getMaxTranslation();
        float progress = currentTranslation / maxTranslation;
        
        boolean shouldHide = progress > ModalPanelConstants.DRAG_DISMISS_THRESHOLD.getFloatValue() ||
                           velocity > ModalPanelConstants.DRAG_VELOCITY_THRESHOLD.getFloatValue();
        
        if (shouldHide && shouldDismiss(YoModalPanelInterface.DismissReason.DRAG_DISMISS)) {
            if (animationManager != null) {
                animationManager.animateHide();
            }
        } else {
            animationManager.animateBounceToShow();
        }
        
        // 回收速度追踪器
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
        
        return true;
    }

    /**
     * 设置布局完成监听器
     * 使用ViewTreeObserver监听布局完成，确保在正确的时机进行初始化
     */
    private void setupLayoutCompleteListener() {
        if (panelContainer == null) return;
        
        // 先设置为不可见，防止首帧绘制
        panelContainer.setVisibility(View.INVISIBLE);
        maskView.setAlpha(0f);
        
        layoutCompleteListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!isLayoutReady && panelContainer.getWidth() > 0 && panelContainer.getHeight() > 0) {
                    isLayoutReady = true;
                    
                    // 移除监听器，避免重复调用
                    ViewTreeObserver observer = panelContainer.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnGlobalLayoutListener(layoutCompleteListener);
                    }
                    
                    // 布局完成后，立即设置隐藏位置并恢复可见性
                    initPanelPositionAfterLayout();
                }
            }
        };
        
        panelContainer.getViewTreeObserver().addOnGlobalLayoutListener(layoutCompleteListener);
    }
    
    /**
     * 布局完成后初始化面板位置
     */
    private void initPanelPositionAfterLayout() {
        if (animationManager != null) {
            // 同步设置隐藏位置
            animationManager.hideImmediately();
            // 恢复可见性，但面板在隐藏位置
            panelContainer.setVisibility(View.VISIBLE);
        }
    }

    // ==================== PanelManager 接口实现 ====================

    @Override
    public boolean showPanel() {
        try {
            super.show();
            
            // 如果布局已就绪，立即开始动画；否则等待布局完成
            if (isLayoutReady) {
                animationManager.animateShow();
            } else {
                // 布局未完成时，等待布局完成后再显示
                panelContainer.post(() -> {
                    if (isLayoutReady) {
                        animationManager.animateShow();
                    } else {
                        // 兜底：强制等待一帧后再检查
                        panelContainer.postDelayed(() -> {
                            animationManager.animateShow();
                        }, ModalPanelConstants.FRAME_TIME_MS.getLongValue()); // 一帧时间
                    }
                });
            }
            return true;
        } catch (Exception e) {
            stateManager.setState(YoModalPanelInterface.PanelState.ERROR);
            return false;
        }
    }

    @Override
    public boolean hidePanel() {
        if (!shouldDismiss(YoModalPanelInterface.DismissReason.PROGRAMMATIC)) {
            return false;
        }
        
        animationManager.animateHide();
        return true;
    }
    
    // ==================== 向后兼容的 Dialog 方法重写 ====================

    /**
     * 重写 Dialog.show() 方法，使其调用 showPanel() 以触发正确的动画
     * 这样调用 show() 和 showPanel() 效果一致
     */
    @Override
    public void show() {
        showPanel();
    }

    /**
     * 重写 Dialog.setContentView() 方法
     * 将内容添加到面板的 contentContainer 中，而不是替换整个对话框内容
     */
    @Override
    public void setContentView(@NonNull View view) {
        // 清空现有内容后添加新内容
        clearContent();
        addContentView(view);
    }

    @Override
    public void setContentView(int layoutResID) {
        View view = getLayoutInflater().inflate(layoutResID, null);
        setContentView(view);
    }

    @Override
    public void setContentView(@NonNull View view, ViewGroup.LayoutParams params) {
        view.setLayoutParams(params);
        setContentView(view);
    }

    @Override
    public boolean isShowing() {
        return stateManager.isShowing();
    }

    @Override
    public void dismiss() {
        try {
            // 若并非由动画回调内部触发的销毁，且当前处于显示相关状态，则改为走隐藏动画
            if (!isInternalDismiss && stateManager != null &&
                (stateManager.isShowing() || stateManager.isShown() || stateManager.isDragging())) {
                if (animationManager != null && !stateManager.isHiding()) {
                    animationManager.animateHide();
                }
                return;
            }
            // 清理布局监听器
            cleanupLayoutListener();
            
            super.dismiss();
            notifyLifecycleListener(l -> l.onPanelDestroyed());
        } catch (Exception e) {
            // 忽略异常
        } finally {
            isInternalDismiss = false;
        }
    }
    
    /**
     * 清理布局监听器
     */
    private void cleanupLayoutListener() {
        if (layoutCompleteListener != null && panelContainer != null) {
            ViewTreeObserver observer = panelContainer.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(layoutCompleteListener);
            }
            layoutCompleteListener = null;
        }
    }

    @Override
    public YoModalPanelInterface.PanelState getCurrentState() {
        return stateManager.getCurrentState().state;
    }

    @Override
    public void setState(YoModalPanelInterface.PanelState state) {
        stateManager.setState(state);
    }

    // ==================== PanelAppearanceManager 接口实现 ====================

    @Override
    public void setDirection(YoModalPanelInterface.PanelDirection direction) {
        config.direction = direction;
        if (panelContainer != null) {
            panelContainer.setConfig(config);
        }
    }

    @Override
    public void setHeightRatio(float ratio) {
        config.heightRatio = ModalPanelConstants.clampRatio(ratio);
        if (panelContainer != null) {
            panelContainer.setConfig(config);
        }
    }

    @Override
    public void setCornerRadius(float radiusDp) {
        config.cornerRadius = Math.max(0f, radiusDp);
        if (panelContainer != null) {
            panelContainer.setConfig(config);
        }
    }

    @Override
    public void setMaskAlpha(float alpha) {
        config.maskAlpha = ModalPanelConstants.clampRatio(alpha);
        if (animationManager != null) {
            animationManager.updateConfig(config);
        }
    }

    @Override
    public void setShowDragBar(boolean show) {
        config.showDragBar = show;
        if (panelContainer != null) {
            panelContainer.setConfig(config);
        }
    }

    @Override
    public void setTopOffset(float offsetDp) {
        config.topOffset = Math.max(0f, offsetDp);
        // 对于顶部滑入的面板，使用窗口偏移与高度裁剪来实现避让顶部栏
        if (config.direction == YoModalPanelInterface.PanelDirection.TOP_TO_BOTTOM) {
            Window window = getWindow();
            if (window != null) {
                int offsetY = dpToPx(config.topOffset);
                int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
                int windowHeight = Math.max(0, screenHeight - offsetY);

                window.setGravity(Gravity.TOP);
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.y = offsetY;
                lp.width = WindowManager.LayoutParams.MATCH_PARENT;
                lp.height = windowHeight > 0 ? windowHeight : WindowManager.LayoutParams.MATCH_PARENT;
                window.setAttributes(lp);
            }
            // 确保面板自身不再叠加 topMargin
            if (panelContainer != null) {
                ViewGroup.LayoutParams params = panelContainer.getLayoutParams();
                if (params instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) params).topMargin = 0;
                    panelContainer.setLayoutParams(params);
                }
            }
        } else {
            // 其他方向保留原有行为（如需要可设置容器 margin）
            if (panelContainer != null) {
                ViewGroup.LayoutParams params = panelContainer.getLayoutParams();
                if (params instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) params).topMargin = dpToPx(offsetDp);
                    panelContainer.setLayoutParams(params);
                }
            }
        }
    }

    @Override
    public void setAnimationDuration(long duration) {
        config.animationDuration = ModalPanelConstants.clampDuration(duration);
        if (animationManager != null) {
            animationManager.updateConfig(config);
        }
    }

    @Override
    public void setEnableDamping(boolean enable) {
        config.enableDamping = enable;
    }

    // ==================== PanelContentManager 接口实现 ====================

    @Override
    public void setTitle(String title) {
        config.title = title;
        if (panelContainer != null) {
            panelContainer.setTitle(title);
        }
    }

    @Override
    public void setMessage(String message) {
        config.message = message;
        if (panelContainer != null) {
            panelContainer.setMessage(message);
        }
    }

    @Override
    public void addContentView(View view) {
        if (view == null) return;

        if (isUICreated && panelContainer != null) {
            // UI 已创建，直接添加到面板容器
            panelContainer.addContentView(view);
        } else {
            // UI 尚未创建，缓存到待添加列表
            pendingContentViews.add(view);
        }
    }

    @Override
    public void removeContentView(View view) {
        if (view == null) return;

        if (isUICreated && panelContainer != null) {
            panelContainer.removeContentView(view);
        } else {
            // 从待添加列表中移除
            pendingContentViews.remove(view);
        }
    }

    @Override
    public void clearContent() {
        if (isUICreated && panelContainer != null) {
            panelContainer.clearContent();
        } else {
            // 清空待添加列表
            pendingContentViews.clear();
        }
    }

    @Override
    public void setHeaderView(View headerView) {
        config.headerView = headerView;
        if (panelContainer != null) {
            panelContainer.setHeaderView(headerView);
        }
    }

    @Override
    public void setFooterView(View footerView) {
        config.footerView = footerView;
        if (panelContainer != null) {
            panelContainer.setFooterView(footerView);
        }
    }

    @Override
    public List<View> getAllContentViews() {
        List<View> views = new ArrayList<>();
        if (panelContainer != null && panelContainer.getContentContainer() != null) {
            ViewGroup container = panelContainer.getContentContainer();
            for (int i = 0; i < container.getChildCount(); i++) {
                views.add(container.getChildAt(i));
            }
        }
        return views;
    }

    // ==================== PanelEventListener 接口实现 ====================

    @Override
    public void setOnPanelStateChangeListener(YoModalPanelInterface.OnPanelStateChangeListener listener) {
        this.stateChangeListener = listener;
    }

    @Override
    public void setOnPanelInteractionListener(YoModalPanelInterface.OnPanelInteractionListener listener) {
        this.interactionListener = listener;
    }

    @Override
    public void setOnPanelLifecycleListener(YoModalPanelInterface.OnPanelLifecycleListener listener) {
        this.lifecycleListener = listener;
    }

    // ==================== YoModalPanel 接口实现 ====================

    @Override
    public YoModalPanelInterface.PanelConfig getConfig() {
        return config;
    }

    @Override
    public void updateConfig(YoModalPanelInterface.PanelConfig config) {
        this.config = config != null ? config : new YoModalPanelInterface.PanelConfig();
        if (panelContainer != null) {
            panelContainer.setConfig(this.config);
        }
        if (animationManager != null) {
            animationManager.updateConfig(this.config);
        }
    }

    @Override
    public View getRootView() {
        return rootContainer;
    }

    @Override
    public View getContentContainer() {
        return panelContainer != null ? panelContainer.getContentContainer() : null;
    }

    // ==================== 内部方法 ====================

    /**
     * 检查是否应该关闭
     */
    private boolean shouldDismiss(YoModalPanelInterface.DismissReason reason) {
        return interactionListener == null || interactionListener.onPanelShouldDismiss(reason);
    }

    /**
     * 获取最大平移距离
     */
    private float getMaxTranslation() {
        if (panelContainer == null) return 0f;
        
        int panelHeight = panelContainer.getHeight();
        if (panelHeight == 0) {
            int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
            panelHeight = (int) (screenHeight * config.heightRatio);
        }
        
        return panelHeight;
    }

    /**
     * 内部状态变化处理
     */
    private void onInternalStateChanged(YoModalPanelInterface.PanelState oldState, YoModalPanelInterface.PanelState newState) {
        if (stateChangeListener != null) {
            stateChangeListener.onStateChanged(oldState, newState);
        }
    }

    /**
     * 通知交互监听器
     */
    private void notifyInteractionListener(InteractionNotifier notifier) {
        if (interactionListener != null) {
            try {
                notifier.notify(interactionListener);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }

    /**
     * 通知生命周期监听器
     */
    private void notifyLifecycleListener(LifecycleNotifier notifier) {
        if (lifecycleListener != null) {
            try {
                notifier.notify(lifecycleListener);
            } catch (Exception e) {
                // 忽略监听器异常
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (config.cancelable && shouldDismiss(YoModalPanelInterface.DismissReason.BACK_PRESSED)) {
            if (animationManager != null) {
                animationManager.animateHide();
            }
        }
    }

    @Override
    public void hide() {
        if (animationManager != null && stateManager != null &&
            (stateManager.isShowing() || stateManager.isShown() || stateManager.isDragging())) {
            animationManager.animateHide();
        } else {
            super.hide();
        }
    }

    /**
     * dp转px
     */
    private int dpToPx(float dp) {
        return (int) (dp * getContext().getResources().getDisplayMetrics().density);
    }

    /**
     * 销毁资源
     */
    @Override
    protected void onStop() {
        super.onStop();
        // 仅停止并清理当前动画，保留动画管理器以便下次显示仍有动画
        if (animationManager != null) {
            animationManager.cancelAllAnimations();
        }
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    // ==================== 内部接口 ====================

    private interface InteractionNotifier {
        void notify(YoModalPanelInterface.OnPanelInteractionListener listener);
    }

    private interface LifecycleNotifier {
        void notify(YoModalPanelInterface.OnPanelLifecycleListener listener);
    }
}
