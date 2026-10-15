package com.yovo.lib_common.system.refresh_rate.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight QA snapshot collected alongside apply results.
 */
public final class RefreshRateDiagnostics {

    private final int sdkVersion;
    @NonNull
    private final String brand;
    @NonNull
    private final String manufacturer;
    @NonNull
    private final String model;
    private final int displayId;
    @NonNull
    private final List<Float> supportedRefreshRatesHz;
    @Nullable
    private final RefreshRateSystemModeSnapshot selectedMode;
    @Nullable
    private final RefreshRateSystemModeSnapshot currentMode;
    private final boolean powerSaveMode;
    private final int thermalStatus;
    private final boolean thermalStatusAvailable;
    private final boolean multiWindowMode;
    @Nullable
    private final String note;

    public RefreshRateDiagnostics(int sdkVersion,
                           @NonNull String brand,
                           @NonNull String manufacturer,
                           @NonNull String model,
                           int displayId,
                           @NonNull List<Float> supportedRefreshRatesHz,
                           @Nullable RefreshRateSystemModeSnapshot selectedMode,
                           @Nullable RefreshRateSystemModeSnapshot currentMode,
                           boolean powerSaveMode,
                           int thermalStatus,
                           boolean thermalStatusAvailable,
                           boolean multiWindowMode,
                           @Nullable String note) {
        this.sdkVersion = sdkVersion;
        this.brand = brand;
        this.manufacturer = manufacturer;
        this.model = model;
        this.displayId = displayId;
        this.supportedRefreshRatesHz = Collections.unmodifiableList(
                new ArrayList<>(supportedRefreshRatesHz));
        this.selectedMode = selectedMode;
        this.currentMode = currentMode;
        this.powerSaveMode = powerSaveMode;
        this.thermalStatus = thermalStatus;
        this.thermalStatusAvailable = thermalStatusAvailable;
        this.multiWindowMode = multiWindowMode;
        this.note = note;
    }

    public int sdkVersion() {
        return sdkVersion;
    }

    @NonNull
    public String brand() {
        return brand;
    }

    @NonNull
    public String manufacturer() {
        return manufacturer;
    }

    @NonNull
    public String model() {
        return model;
    }

    public int displayId() {
        return displayId;
    }

    @NonNull
    public List<Float> supportedRefreshRatesHz() {
        return supportedRefreshRatesHz;
    }

    @Nullable
    public RefreshRateSystemModeSnapshot selectedMode() {
        return selectedMode;
    }

    @Nullable
    public RefreshRateSystemModeSnapshot currentMode() {
        return currentMode;
    }

    public boolean powerSaveMode() {
        return powerSaveMode;
    }

    public int thermalStatus() {
        return thermalStatus;
    }

    public boolean thermalStatusAvailable() {
        return thermalStatusAvailable;
    }

    public boolean multiWindowMode() {
        return multiWindowMode;
    }

    @Nullable
    public String note() {
        return note;
    }

    @NonNull
    public static RefreshRateDiagnostics empty() {
        return new RefreshRateDiagnostics(
                0, "", "", "", -1,
                Collections.emptyList(), null, null,
                false, 0, false, false, null);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                "RefreshRateDiagnostics{sdk=%d, device=%s/%s/%s, displayId=%d, "
                        + "supported=%s, selected=%s, current=%s, powerSave=%b, "
                        + "thermal=%d(available=%b), multiWindow=%b, note=%s}",
                sdkVersion, brand, manufacturer, model, displayId,
                supportedRefreshRatesHz, selectedMode, currentMode,
                powerSaveMode, thermalStatus, thermalStatusAvailable,
                multiWindowMode, note);
    }
}
