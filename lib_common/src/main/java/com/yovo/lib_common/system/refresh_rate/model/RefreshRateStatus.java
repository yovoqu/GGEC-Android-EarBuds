package com.yovo.lib_common.system.refresh_rate.model;

/**
 * Outcome of a refresh-rate apply or clear operation.
 */
public enum RefreshRateStatus {
    /** Request was written to window attributes and observed rate matches expectation. */
    APPLIED,
    /** Preference was accepted by the window; display switching is asynchronous. */
    REQUESTED,
    /** Request was written but observed display rate differs from the selected mode. */
    APPLIED_BUT_NOT_OBSERVED,
    NO_DISPLAY,
    NO_SUPPORTED_MODES,
    NO_HIGH_REFRESH_MODE,
    UNSUPPORTED_API,
    ERROR,
    CLEARED
}
