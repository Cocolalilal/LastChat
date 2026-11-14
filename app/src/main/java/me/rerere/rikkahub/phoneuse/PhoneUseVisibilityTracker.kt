package me.rerere.rikkahub.phoneuse

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Started, rather than resumed: a translucent activity still shows the app underneath. */
class PhoneUseVisibilityTracker(private val manager: PhoneUseManager) : Application.ActivityLifecycleCallbacks {
    private val visible = mutableSetOf<Activity>()
    private val resumed = mutableSetOf<Activity>()
    override fun onActivityStarted(activity: Activity) { visible += activity; update() }
    override fun onActivityStopped(activity: Activity) {
        visible -= activity; resumed -= activity; update(); updateForeground()
    }
    override fun onActivityDestroyed(activity: Activity) {
        visible -= activity; resumed -= activity; update(); updateForeground()
    }
    private fun update() {
        manager.appUiVisible.value = visible.isNotEmpty()
        manager.otherAppUiVisible.value = visible.any {
            it !is me.rerere.rikkahub.ui.activity.AssistantOverlayActivity
        }
        manager.assistantUiVisible.value = visible.any {
            it is me.rerere.rikkahub.ui.activity.AssistantOverlayActivity
        }
    }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    private fun updateForeground() {
        manager.setMainAppResumed(resumed.any {
            it !is me.rerere.rikkahub.ui.activity.AssistantOverlayActivity
        })
    }
    override fun onActivityResumed(activity: Activity) { resumed += activity; updateForeground() }
    override fun onActivityPaused(activity: Activity) { resumed -= activity; updateForeground() }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
