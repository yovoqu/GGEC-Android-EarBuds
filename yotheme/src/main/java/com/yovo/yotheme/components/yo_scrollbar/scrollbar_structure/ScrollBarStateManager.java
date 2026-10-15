package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import com.yovo.yotheme.constants.ScrollBarConstants;

/**
 * 滚动条按压/拖拽交互态，与 {@link BarState}（显示策略）分离。
 */
public final class ScrollBarStateManager {

    public enum InteractionState {
        NORMAL,
        PRESS,
        DRAGGING
    }

    public interface Listener {
        void onInteractionStateChanged(InteractionState state);
    }

    private InteractionState state = InteractionState.NORMAL;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setState(InteractionState newState) {
        if (state == newState) {
            return;
        }
        state = newState;
        if (listener != null) {
            listener.onInteractionStateChanged(newState);
        }
    }

    public InteractionState getState() {
        return state;
    }

    public float resolveAlphaMultiplier() {
        switch (state) {
            case PRESS:
                return ScrollBarConstants.SCROLLBAR_ALPHA_PRESS.getFloatValue()
                        / ScrollBarConstants.SCROLLBAR_ALPHA_NORMAL.getFloatValue();
            case DRAGGING:
                return ScrollBarConstants.SCROLLBAR_ALPHA_DRAGGING.getFloatValue()
                        / ScrollBarConstants.SCROLLBAR_ALPHA_NORMAL.getFloatValue();
            case NORMAL:
            default:
                return 1f;
        }
    }

    public boolean isActive() {
        return state == InteractionState.PRESS || state == InteractionState.DRAGGING;
    }
}
