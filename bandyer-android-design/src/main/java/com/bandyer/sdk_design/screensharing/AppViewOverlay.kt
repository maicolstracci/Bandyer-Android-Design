/*
 * Copyright 2021-2022 Bandyer @ https://www.bandyer.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.bandyer.sdk_design.screensharing

import android.app.Activity
import android.app.AppOpsManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import com.badoo.mobile.util.WeakHandler
import com.bandyer.sdk_design.extensions.canDrawOverlays
import com.bandyer.sdk_design.extensions.startAppOpsWatch
import com.bandyer.sdk_design.extensions.stopAppOpsWatch
import com.bandyer.sdk_design.views.ViewOverlayAttacher

/**
 * Represents a view that can is attached to the views of an app or attached to the system's window
 * if the device has permissions to do it.
 * @property view the View that will be placed upon app or system's window.
 */
class AppViewOverlay(val view: View) {

    private val viewOverlayAttacher = ViewOverlayAttacher(view)

    private var appOpsCallback: ((String, String) -> Unit)? = null

    private var initialized = false

    private val mainThreadHandler = WeakHandler(Looper.getMainLooper())

    /**
     * Shows the overlay view. If the context is an Activity the overlay will be attached to its decor view otherwise
     * if the context is an application context the view it will be attached to the system's window.
     * @param context Context used to attach the overlay view.
     */
    fun show(context: Context) {
        if (!initialized) {
            initialized = true
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(activityCallbacks)
            watchOverlayPermission(context)
        }

        viewOverlayAttacher.attach(context, getOverlayType(context))
    }

    /**
     * Hides the screen share overlay.
     * @param context Context used to detach the overlay view.
     */
    fun hide(context: Context) {
        viewOverlayAttacher.detach()
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(activityCallbacks)
        appOpsCallback?.let { context.applicationContext.stopAppOpsWatch(it) }
        appOpsCallback = null
        initialized = false
        mainThreadHandler.removeCallbacksAndMessages(null)

    }

    private fun getOverlayType(context: Context): ViewOverlayAttacher.OverlayType {
        return if (context.canDrawOverlays()) ViewOverlayAttacher.OverlayType.GLOBAL
        else ViewOverlayAttacher.OverlayType.CURRENT_APPLICATION
    }

    //////////////////////////////////// ACTIVITY CALLBACKS ////////////////////////////////////////////////////////////////////////

    private var activityCallbacks = object : Application.ActivityLifecycleCallbacks {

        override fun onActivityResumed(activity: Activity) = viewOverlayAttacher.attach(activity, getOverlayType(activity))

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    }

    //////////////////////////////////// OVERLAY PERMISSION ////////////////////////////////////////////////////////////////////////

    private fun watchOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (appOpsCallback != null) return
        val applicationContext = context.applicationContext
        val pckName = applicationContext.packageName
        appOpsCallback = callback@{ op, packageName ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW != op || packageName != pckName) return@callback
            mainThreadHandler.post { show(applicationContext) }
        }
        applicationContext.startAppOpsWatch(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, appOpsCallback!!)
    }
}