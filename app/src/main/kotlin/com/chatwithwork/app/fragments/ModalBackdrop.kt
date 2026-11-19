package com.chatwithwork.app.fragments

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.core.graphics.createBitmap

/**
 * A still picture of the screen a modal comes up over. The navigator
 * replaces that screen's view with the modal's, so a modal's frame
 * (ModalFrame) shows this behind its scrim: around a dialog on a large
 * window, and behind a full-screen one while a predictive back gesture
 * shrinks it. Taken from the window as it's shown (PixelCopy, which has the
 * web view's pixels as they are), at most one at a time, and let go once no
 * modal is up. A picture taken in the other theme (light or dark) shows
 * nothing: after the theme changes it's of a screen that no longer looks so.
 */
object ModalBackdrop {
    private var picture: Bitmap? = null
    private var pictureNight: Int = 0
    private val observers = mutableListOf<(Bitmap?) -> Unit>()

    /** Takes the picture of [view] as the window shows it now, for the modal about to open over it. */
    fun capture(activity: Activity, view: View) {
        clear()
        if (view.width <= 0 || view.height <= 0 || !view.isAttachedToWindow) return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val area = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val bitmap = createBitmap(view.width, view.height)
        runCatching {
            PixelCopy.request(activity.window, area, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && picture == null) {
                    picture = bitmap
                    pictureNight = night(activity)
                    observers.toList().forEach { it(bitmap) }
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    /** Calls [onPicture] with the picture now (null if there's none yet) and when one arrives, while [view] lasts. */
    fun observe(view: View, onPicture: (Bitmap?) -> Unit) {
        val forView: (Bitmap?) -> Unit = { onPicture(it?.takeIf { pictureNight == night(view.context) }) }
        forView(picture)
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    if (forView !in observers) observers += forView
                    forView(picture)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    observers -= forView
                }
            }
        )
    }

    private fun night(context: Context): Int =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

    /** No modal is up: the picture goes. */
    fun clear() {
        picture = null
    }
}
