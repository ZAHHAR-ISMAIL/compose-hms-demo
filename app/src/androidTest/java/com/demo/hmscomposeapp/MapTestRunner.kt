package com.demo.hmscomposeapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.test.runner.AndroidJUnitRunner

/**
 * Test activities may render above a secure keyguard without unlocking the device or accessing
 * other apps. This is Android's normal show-when-locked API, restricted to the test process;
 * production activities receive no flags. Force-stopping the test app restores the lock surface.
 */
class MapTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    activity.setShowWhenLocked(true)
                    activity.setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        super.callApplicationOnCreate(app)
    }
}
