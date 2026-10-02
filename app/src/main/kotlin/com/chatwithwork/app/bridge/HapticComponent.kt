package com.chatwithwork.app.bridge

import android.view.View
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import dev.hotwire.core.bridge.BridgeComponent
import dev.hotwire.core.bridge.BridgeComponentFactory
import dev.hotwire.core.bridge.BridgeDelegate
import dev.hotwire.core.bridge.Message
import dev.hotwire.navigation.destinations.HotwireDestination
import kotlinx.serialization.Serializable

/**
 * `haptic`: a short vibration that confirms what just happened, like sending a
 * question or approving a change. Same message as Joe Masilotti's component:
 * `vibrate` with `feedback` (see [constantFor] for the names). Android's own
 * feedback constants respect the person's touch-feedback setting, and older
 * versions fall back to the nearest effect they have.
 */
class HapticComponent(name: String, private val bridgeDelegate: BridgeDelegate<HotwireDestination>) :
    BridgeComponent<HotwireDestination>(name, bridgeDelegate) {
    override fun onReceive(message: Message) {
        if (message.event != "vibrate") return
        val view: View = bridgeDelegate.destination.fragment.view ?: return
        val feedback = message.data<Data>()?.feedback
        ViewCompat.performHapticFeedback(view, constantFor(feedback))
    }

    @Serializable
    data class Data(val feedback: String? = null)

    companion object {
        val factory = BridgeComponentFactory("haptic", ::HapticComponent)

        /**
         * Maps the feedback names (Joe Masilotti's `success`, `warning`,
         * `error`, and the iOS app's `selection`, `light`, `medium`,
         * `heavy`, `soft`, `rigid`) to Android's. Unknown names confirm,
         * as `success` does.
         */
        fun constantFor(feedback: String?): Int = when (feedback) {
            "warning", "error" -> HapticFeedbackConstantsCompat.REJECT
            "selection" -> HapticFeedbackConstantsCompat.SEGMENT_TICK
            "light", "soft" -> HapticFeedbackConstantsCompat.CLOCK_TICK
            "medium", "rigid" -> HapticFeedbackConstantsCompat.KEYBOARD_TAP
            "heavy" -> HapticFeedbackConstantsCompat.LONG_PRESS
            else -> HapticFeedbackConstantsCompat.CONFIRM
        }
    }
}
