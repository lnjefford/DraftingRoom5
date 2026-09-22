package dev.draftingroom5.retirement.provider

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager

/** Installed at process start so SDK restoration is protected even before our task Activity exists. */
class ProviderPrivacyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private fun secure(activity: Activity) {
                if (activity.javaClass.name.startsWith("com.plaid.")) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = secure(activity)
            override fun onActivityResumed(activity: Activity) = secure(activity)
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
