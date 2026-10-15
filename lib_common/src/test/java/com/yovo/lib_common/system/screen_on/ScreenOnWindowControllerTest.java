package com.yovo.lib_common.system.screen_on;

import android.app.Activity;
import android.view.WindowManager;

import com.yovo.lib_common.system.screen_on.internal.ScreenOnControllerImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ScreenOnWindowControllerTest {

    private Activity activity;
    private ScreenOnControllerImpl controller;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        controller = new ScreenOnControllerImpl(activity);
    }

    @Test
    public void attach_appliesKeepScreenOnFlag() {
        controller.attach();
        assertTrue(controller.isApplied());
        assertTrue(hasKeepScreenOnFlag());
    }

    @Test
    public void detach_clearsKeepScreenOnFlag() {
        controller.attach();
        controller.detach();
        assertFalse(controller.isApplied());
        assertFalse(hasKeepScreenOnFlag());
    }

    @Test
    public void onResume_reappliesWhenAttached() {
        controller.attach();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        assertFalse(hasKeepScreenOnFlag());

        controller.onResume();
        assertTrue(hasKeepScreenOnFlag());
    }

    @Test
    public void onResume_doesNothingWhenDetached() {
        controller.onResume();
        assertFalse(hasKeepScreenOnFlag());
    }

    private boolean hasKeepScreenOnFlag() {
        return (activity.getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
    }
}
