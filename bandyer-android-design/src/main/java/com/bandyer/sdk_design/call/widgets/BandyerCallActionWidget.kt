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

package com.bandyer.sdk_design.call.widgets

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Px
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.badoo.mobile.util.WeakHandler
import com.bandyer.sdk_design.R
import com.bandyer.sdk_design.bottom_sheet.BandyerActionBottomSheet
import com.bandyer.sdk_design.bottom_sheet.BandyerBottomSheet
import com.bandyer.sdk_design.bottom_sheet.BandyerClickableBottomSheet
import com.bandyer.sdk_design.bottom_sheet.BaseBandyerBottomSheet
import com.bandyer.sdk_design.bottom_sheet.OnStateChangedBottomSheetListener
import com.bandyer.sdk_design.bottom_sheet.behaviours.BandyerBottomSheetBehaviour
import com.bandyer.sdk_design.bottom_sheet.items.ActionItem
import com.bandyer.sdk_design.bottom_sheet.view.BottomSheetLayoutContent
import com.bandyer.sdk_design.call.bottom_sheet.AudioRouteBottomSheet
import com.bandyer.sdk_design.call.bottom_sheet.CallBottomSheet
import com.bandyer.sdk_design.call.bottom_sheet.OnAudioRouteBottomSheetListener
import com.bandyer.sdk_design.call.bottom_sheet.RingingBottomSheet
import com.bandyer.sdk_design.call.bottom_sheet.items.AudioRoute
import com.bandyer.sdk_design.call.bottom_sheet.items.CallAction
import com.bandyer.sdk_design.extensions.getCallThemeAttribute
import com.bandyer.sdk_design.extensions.getScreenSize
import com.bandyer.sdk_design.widgets.HideableWidget

/**
 * Widget used during a call to perform actions such as mute video/audio, change audioRoute etc.
 * @param context Context
 * @param coordinatorLayout CoordinatorLayout
 * @param callActionItems items to use
 * @constructor
 * @author kristiyan
 */
class BandyerCallActionWidget<T, F>(val context: AppCompatActivity, val coordinatorLayout: CoordinatorLayout, val callActionItems: List<CallAction>) : HideableWidget where T : ActionItem, F : BandyerBottomSheet {

    override var hidingTimer: CountDownTimer? = null

    /**
     * Click listener for when an item has been clicked
     */
    var onClickListener: OnClickListener? = null

    /**
     * Used to request available audioRoutes
     */
    var onAudioRoutesRequest: OnAudioRouteBottomSheetListener? = null

    private var anchoredViews: MutableList<Pair<View, Int>> = mutableListOf()
    private var currentBottomSheetLayout: BottomSheetLayoutContent? = null

    private var currentShownBottomSheet: BaseBandyerBottomSheet? = null

    private var isDraggingEnabled = true

    /**
     * Sliding listener for when the widget has been slided
     */
    var slidingListener: SlidingListener? = null

    /**
     * Current widget hidden state
     */
    var isHidden = false

    /**
     * Current onShowListener
     */
    private var onShowListener: OnShowListener? = null

    /**
     * Current onHiddenListener
     */
    private var onHiddenListener: OnHiddenListener? = null

    /**
     * Weak handler to safely call delayed tasks
     */
    private val uiHandler by lazy {
        WeakHandler()
    }

    /**
     * Widget states
     */
    enum class BandyerCallActionWidgetState {
        /**
         * Fully expanded
         */
        EXPANDED,

        /**
         * Anchored
         */
        ANCHORED,

        /**
         * Collapsed
         */
        COLLAPSED
    }


    init {

        callActionItems.forEachIndexed { index, callAction ->
            if (index < callActionItems.size - 1) {
                callAction.itemView?.nextFocusForwardId =
                        callActionItems[index + 1].itemView?.id!!
            }
        }
    }

    /**
     * Current widget state
     */
    @Suppress("SetterBackingFieldAssignment")
    var state: BandyerCallActionWidgetState? = null

    /**
     * Save current instance state
     * @param saveInstanceState Bundle? to store in
     * @return Bundle updated
     */
    fun saveInstanceState(saveInstanceState: Bundle?): Bundle? {
        val bundle = audioRouteBottomSheet.saveInstanceState(saveInstanceState)
        return callBottomSheet.saveInstanceState(bundle)
    }


    /**
     * Disable dragging capability of shown bottomsheet
     */
    fun disableDragging() {
        isDraggingEnabled = false
        currentShownBottomSheet?.bottomSheetBehaviour?.disableDragging = !isDraggingEnabled
    }

    /**
     * Enable dragging capability of shown bottomsheet
     */
    fun enableDragging() {
        isDraggingEnabled = true
        currentShownBottomSheet?.bottomSheetBehaviour?.disableDragging = !isDraggingEnabled
    }

    /**
     * Restore a previously saved instance state
     * @param bundle Bundle? to restore from
     */
    fun restoreInstanceState(bundle: Bundle?) {
        callBottomSheet.restoreInstanceState(bundle)
        audioRouteBottomSheet.restoreInstanceState(bundle)
    }

    /**
     * Toggle the widget.
     */
    fun toggle() {
        if (callBottomSheet.isVisible())
            callBottomSheet.toggle()
        if (audioRouteBottomSheet.isVisible())
            audioRouteBottomSheet.toggle()
    }

    /**
     * Expand the widget.
     */
    fun expand() {
        if (callBottomSheet.isVisible())
            callBottomSheet.expand()
        if (audioRouteBottomSheet.isVisible())
            audioRouteBottomSheet.expand()
    }

    /**
     * Request focus on current bottom sheet shown.
     */
    fun requestFocus(): View? {
        val toBeFocused = when {
            ringingBottomSheet.isVisible() -> ringingBottomSheet.bottomSheetLayoutContent.recyclerView?.layoutManager?.findViewByPosition(1)
            callBottomSheet.isVisible() -> callBottomSheet.bottomSheetLayoutContent.recyclerView?.layoutManager?.findViewByPosition(0)
            audioRouteBottomSheet.isVisible() -> audioRouteBottomSheet.bottomSheetLayoutContent.recyclerView?.layoutManager?.findViewByPosition(0)
            else -> null
        }
        toBeFocused?.requestFocus()
        return toBeFocused
    }

    /**
     * Collapse the widget.
     */
    fun collapse() {
        if (callBottomSheet.isVisible()) {
            if (collapsible) callBottomSheet.collapse()
            else callBottomSheet.anchor()
        }
        if (audioRouteBottomSheet.isVisible())
            audioRouteBottomSheet.hide()
    }

    /**
     * Hides current displayed bottomsheet
     */
    @JvmOverloads
    fun hide(onHiddenListener: OnHiddenListener? = null) {
        if (isHidden) {
            onHiddenListener?.onHidden()
            return
        }
        isHidden = true
        if (onHiddenListener != null) this.onHiddenListener = onHiddenListener
        when (currentShownBottomSheet) {
            callBottomSheet -> {
                callBottomSheet.hide(true)
            }
            else -> disposeBottomSheet(currentShownBottomSheet)
        }
    }

    /**
     * Shows bandyer call action widget based on previous last bottom sheet shown
     */
    @JvmOverloads
    fun show(onShowListener: OnShowListener? = null) {
        if (!isHidden) {
            onShowListener?.onShown()
            return
        }
        isHidden = false
        if (onShowListener != null) this.onShowListener = onShowListener
        when (currentShownBottomSheet) {
            ringingBottomSheet -> showRingingControls()
            else -> showCallControls(collapsible, fixed, collapsed = true)
        }
    }

    /**
     * Shows bandyer call action widget based on previous last bottom sheet shown
     * delayed by factor
     * @param millis delay factor in milliseconds
     */
    fun showDelayed(millis: Long, onShowListener: OnShowListener? = null) {
        uiHandler.postDelayed({ show(onShowListener) }, millis)
    }

    /**
     * Check expanded status.
     * @return true if widget is expanded, false otherwise.
     */
    fun isExpanded(): Boolean {
        return when {
            ringingBottomSheet.isVisible() -> true
            callBottomSheet.isVisible() -> callBottomSheet.isExpanded()
            audioRouteBottomSheet.isVisible() -> audioRouteBottomSheet.isExpanded()
            else -> false
        }
    }

    /**
     * Check collapsed status.
     * @return true if widget is expanded, false otherwise.
     */
    fun isCollapsed(): Boolean {
        return when {
            ringingBottomSheet.isVisible() -> false
            callBottomSheet.isVisible() -> callBottomSheet.isCollapsed()
            audioRouteBottomSheet.isVisible() -> audioRouteBottomSheet.isCollapsed()
            else -> false
        }
    }

    /**
     * Check anchored status.
     * @return true if widget is anchored, false otherwise.
     */
    fun isAnchored(): Boolean {
        return when {
            ringingBottomSheet.isVisible() -> true
            callBottomSheet.isVisible() -> callBottomSheet.isAnchored()
            audioRouteBottomSheet.isVisible() -> audioRouteBottomSheet.isAnchored()
            else -> false
        }
    }

    /**
     * Show ringing controls like Hangup and Answer
     */
    fun showRingingControls() = coordinatorLayout.post {
        isHidden = false
        currentBottomSheetLayout = ringingBottomSheet.bottomSheetLayoutContent

        ringingBottomSheet.bottomSheetLayoutContent.id = R.id.bandyer_id_bottom_sheet_ringing
        if (callBottomSheet.isVisible() || audioRouteBottomSheet.isVisible()) {
            disposeBottomSheet(callBottomSheet)
            disposeBottomSheet(audioRouteBottomSheet)
        }
        ringingBottomSheet.show()
    }

    /**
     * Anchor a view to the widget
     * @param anchoredView View to anchor
     * @param gravity gravity to apply
     * @param forceAnchor true to anchor no matter what, false otherwise
     */
    @JvmOverloads
    fun setAnchoredView(anchoredView: View, gravity: Int) {
        var hasAnchoredView = false
        anchoredViews.forEach {
            if (it.first == anchoredView) {
                hasAnchoredView = true
                return@forEach
            }
        }
        if (!hasAnchoredView) {
            anchoredViews.add(Pair(anchoredView, gravity))
            checkAnchoredViewInCoordinatorLayout()
        }
    }

    private fun anchorViews() = currentBottomSheetLayout?.post {
        removeAnchorFromAnchoredView()
        anchoredViews.forEach { pair ->
            val lp = (pair.first.layoutParams as CoordinatorLayout.LayoutParams)
            lp.anchorId = currentBottomSheetLayout?.id ?: View.NO_ID
            lp.anchorGravity = pair.second
            lp.gravity = pair.second
            pair.first.layoutParams = lp
            pair.first.alpha = 1f
        }
    }

    /**
     * Anchors current bottom sheet
     */
    fun anchor() {
        currentShownBottomSheet?.anchor()
    }

    /**
     */
    fun hideCallControls(withTimer: Boolean = false) {
        if (!withTimer) {
            onHidingTimerFinished()
            hidingTimer?.cancel()
            return
        }
        if (currentShownBottomSheet is CallBottomSheet<*>) hidingTimer?.start()
    }

    private fun checkAnchoredViewInCoordinatorLayout() {
        if (currentBottomSheetLayout == null) return
        anchoredViews.forEach { pair ->
            if (pair.second == Gravity.TOP)
                (pair.first.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = pair.first.height
        }
    }

    private fun removeAnchorFromAnchoredView() {
        anchoredViews.forEach { pair ->
            if (pair.first.parent == null || pair.first.parent !is CoordinatorLayout)
                return
            val lp = (pair.first.layoutParams as CoordinatorLayout.LayoutParams)
            lp.anchorId = View.NO_ID
            lp.anchorGravity = Gravity.TOP
            lp.gravity = Gravity.BOTTOM or Gravity.END
            pair.first.layoutParams = lp
            pair.first.alpha = 0f
        }
    }

    /**
     * Show the call controls
     * @param collapsible true if the bottomSheet should be collapsible, false otherwise
     * @param fixed true if it can not be moved, false otherwise
     * @param collapsed optional initial state collapsed
     */
    @JvmOverloads
    fun showCallControls(collapsible: Boolean, fixed: Boolean = false, collapsed: Boolean = false) = coordinatorLayout.post {
        isHidden = false
        currentBottomSheetLayout = callBottomSheet.bottomSheetLayoutContent
        disposeBottomSheet(ringingBottomSheet)
        disposeBottomSheet(audioRouteBottomSheet)
        this.collapsible = collapsible
        this.fixed = fixed
        callBottomSheet.bottomSheetLayoutContent.id = R.id.bandyer_id_bottom_sheet_call
        callBottomSheet.show(collapsible, fixed, collapsed)
        callBottomSheet.updateAudioRouteIcon(audioRouteBottomSheet.mCurrentAudioRoute)
    }

    override fun onHidingTimerFinished() {
        if (!collapsible) return
        callBottomSheet.collapse()
    }

    /**
     * Show the audioRoute bottomSheet
     */
    fun showAudioRouteBottomSheet() = coordinatorLayout.post {
        isHidden = false
        audioRouteBottomSheet.bottomSheetLayoutContent.id = R.id.bandyer_id_bottom_sheet_audio_route
        callBottomSheet.hide(true)
        audioRouteBottomSheet.show()
        currentBottomSheetLayout = audioRouteBottomSheet.bottomSheetLayoutContent
    }

    /**
     * Add an audioRoute item to the list of available routes
     * @param audioRoute AudioRoute to add
     */
    fun addAudioRouteItem(audioRoute: AudioRoute) {
        audioRouteBottomSheet.addAudioRouteItem(audioRoute)
    }

    /**
     * Select an audioRoute item from the list of available routes
     * @param audioRoute AudioRoute? to select
     */
    fun selectAudioRoute(audioRoute: AudioRoute?) {
        audioRouteBottomSheet.selectAudioRoute(audioRoute)
        callBottomSheet.updateAudioRouteIcon(audioRoute)
    }

    /**
     * Update an audioRoute item from the list of available routes
     * @param audioRoute AudioRoute? to select
     */
    fun updateAudioRoute(audioRoute: AudioRoute) {
        var position = -1

        audioRouteBottomSheet.fastAdapter.itemAdapter.adapterItems.forEach {
            if ((it.item as AudioRoute).identifier == audioRoute.identifier)
                position = audioRouteBottomSheet.fastAdapter.itemAdapter.getAdapterPosition(it)
        }
        if (position == -1) return
        audioRouteBottomSheet.setItem(audioRoute, position)
    }

    /**
     * Set audio route items to be displayed
     * @param items List<AudioRoute> items to be displayed
     */
    fun setAudioRouteItems(items: List<AudioRoute>) = audioRouteBottomSheet.setItems(items)

    /**
     * Remove an audioRoute item from the list of available routes
     * @param audioRoute AudioRoute to remove
     */
    fun removeAudioRouteItem(audioRoute: AudioRoute) = audioRouteBottomSheet.removeAudioRouteItem(audioRoute)

    private val onBottomSheetAction = BandyerActionBottomSheet.OnActionBottomSheetListener<ActionItem, BandyerBottomSheet> action@{ bottomSheet, action, position ->
        val consumed = when (action) {
            is AudioRoute -> onClickListener?.onAudioRouteClicked(action, position)
            is CallAction -> onClickListener?.onCallActionClicked(action, position)
            else -> false
        }
        if (consumed != false) return@action true
        when (action) {
            is AudioRoute -> {
                callBottomSheet.updateAudioRouteIcon(audioRouteBottomSheet.mCurrentAudioRoute)
                bottomSheet as AudioRouteBottomSheet<*>
                bottomSheet.mCurrentAudioRoute = action as AudioRoute
                bottomSheet.hide(true)

            }
            is CallAction.AUDIOROUTE -> showAudioRouteBottomSheet()
            is CallAction.OPTIONS -> expand()
            else -> return@action false
        }
        true
    }

    private val onBottomSheetCallBacks = object : OnStateChangedBottomSheetListener<BandyerBottomSheet> {

        override fun onExpand(bottomSheet: BandyerBottomSheet) {
            disableAutoHide()
            state = BandyerCallActionWidgetState.EXPANDED
            if (bottomSheet is RingingBottomSheet<*>) {
                onShowListener?.onShown()
                onShowListener = null
            }
            slidingListener?.onStateChanged(BandyerCallActionWidgetState.EXPANDED)
        }

        override fun onAnchor(bottomSheet: BandyerBottomSheet) {
            if (bottomSheet is CallBottomSheet<*>) hidingTimer?.start()
            state = BandyerCallActionWidgetState.ANCHORED
            slidingListener?.onSlide(currentBottomSheetLayout?.top
                    ?: context.getScreenSize().y, false)
            if (bottomSheet is CallBottomSheet<*>) {
                onShowListener?.onShown()
                onShowListener = null
            }
            slidingListener?.onStateChanged(BandyerCallActionWidgetState.ANCHORED)
        }

        override fun onShow(bottomSheet: BandyerBottomSheet) {
            currentShownBottomSheet = bottomSheet as BaseBandyerBottomSheet
            anchorViews()
            (bottomSheet as? CallBottomSheet<*>)?.updateAudioRouteIcon(audioRouteBottomSheet.mCurrentAudioRoute)
        }

        override fun onHide(bottomSheet: BandyerBottomSheet) {
            when (bottomSheet) {
                is CallBottomSheet<*> -> disableAutoHide()
                is AudioRouteBottomSheet<*> -> {
                    if (!isHidden) showCallControls(collapsible, fixed)
                    disposeBottomSheet(bottomSheet)
                }
                is RingingBottomSheet<*> -> disposeBottomSheet(bottomSheet)
            }
            onHiddenListener?.onHidden()
            onHiddenListener = null
        }

        override fun onDragging(bottomSheet: BandyerBottomSheet) {
            disableAutoHide()
        }

        override fun onCollapse(bottomSheet: BandyerBottomSheet) {
            hidingTimer?.onFinish()
            state = BandyerCallActionWidgetState.COLLAPSED
            slidingListener?.onSlide(currentBottomSheetLayout?.top
                    ?: context.getScreenSize().y, true)

            if (bottomSheet is CallBottomSheet<*>) {
                onShowListener?.onShown()
                onShowListener = null
            }
            slidingListener?.onStateChanged(BandyerCallActionWidgetState.COLLAPSED)
        }

        override fun onSlide(bottomSheet: BandyerBottomSheet, slideOffset: Float) {
            if (slideOffset == 0.0f) return
            disableAutoHide()
            val top = currentBottomSheetLayout?.top ?: return
            slidingListener?.onSlide(
                    top,
                    (currentShownBottomSheet?.state == BandyerBottomSheetBehaviour.STATE_COLLAPSED || currentShownBottomSheet?.state == BandyerBottomSheetBehaviour.STATE_HIDDEN)
            )
        }
    }

    private val onAudioRoutesListener = object : OnAudioRouteBottomSheetListener {
        override fun onAudioRoutesRequested(): List<AudioRoute>? {
            return onAudioRoutesRequest?.onAudioRoutesRequested()
        }
    }

    private var callBottomSheet: CallBottomSheet<T> = CallBottomSheet(
            context,
            callActionItems,
            context.getCallThemeAttribute(R.styleable.BandyerSDKDesign_Theme_Call_bandyer_bottomSheetCallStyle))

    private var ringingBottomSheet: BandyerClickableBottomSheet<T> = RingingBottomSheet(
            context,
            context.getCallThemeAttribute(R.styleable.BandyerSDKDesign_Theme_Call_bandyer_bottomSheetRingingStyle))


    private var audioRouteBottomSheet: AudioRouteBottomSheet<T> = AudioRouteBottomSheet(
            context = context,
            onAudioRoutesRequest = onAudioRoutesListener,
            audioRouteItems = onAudioRoutesRequest?.onAudioRoutesRequested(),
            initial_selection = -1,
            bottomSheetStyle = context.getCallThemeAttribute(R.styleable.BandyerSDKDesign_Theme_Call_bandyer_bottomSheetAudioRouteStyle))

    /**
     * Dispose the widget
     */
    fun dispose() {
        removeAnchorFromAnchoredView()
        onHiddenListener = null
        onShowListener = null
        slidingListener = null
        callBottomSheet.dispose()
        audioRouteBottomSheet.dispose()
        ringingBottomSheet.dispose()
    }

    private fun disposeBottomSheet(bottomSheet: BaseBandyerBottomSheet?) {
        removeAnchorFromAnchoredView()
        bottomSheet?.dispose()
    }

    /**
     * Returns true if the widget is collapsible, false otherwise.
     */
    var collapsible: Boolean = true
    private var fixed: Boolean = true

    init {
        ringingBottomSheet.onStateChangedBottomSheetListener = onBottomSheetCallBacks
        ringingBottomSheet.onActionBottomSheetListener = onBottomSheetAction

        audioRouteBottomSheet.onStateChangedBottomSheetListener = onBottomSheetCallBacks
        audioRouteBottomSheet.onActionBottomSheetListener = onBottomSheetAction

        callBottomSheet.onStateChangedBottomSheetListener = onBottomSheetCallBacks
        callBottomSheet.onActionBottomSheetListener = onBottomSheetAction
    }

    /**
     * Click Listener for the call action widget
     */
    interface OnClickListener {
        /**
         * Called when a call action has been requested
         * @param item CallAction requested
         * @param position position of item
         * @return true if has been handled, false otherwise
         */
        fun onCallActionClicked(item: CallAction, position: Int): Boolean

        /**
         * Called when an audioRoute has been selected
         * @param item AudioRoute requested
         * @param position position of item
         * @return true if has been handled, false otherwise
         */
        fun onAudioRouteClicked(item: AudioRoute, position: Int): Boolean
    }


    /**
     * Sliding Listener for the call action widget
     */
    interface SlidingListener {

        /**
         * Called when the widget slide offset changes.
         * @param top distance from the top of the parent in pixels
         * @param isCollapsed true if call action widget is collapsed
         */
        fun onSlide(@Px top: Int, isCollapsed: Boolean)

        /**
         * Called when the call action widget changes state.
         * @param state BandyerCallActionWidgetState
         */
        fun onStateChanged(state: BandyerCallActionWidgetState)
    }

    /**
     * Listener to be called after on shown process
     */
    interface OnShowListener {
        /**
         * Callback fired when the widget has been programmatically shown
         */
        fun onShown()
    }

    /**
     * Listener to be called after on hidden process
     */
    interface OnHiddenListener {
        /**
         * Callback fired when the widget has been programmatically hidden
         */
        fun onHidden()
    }
}