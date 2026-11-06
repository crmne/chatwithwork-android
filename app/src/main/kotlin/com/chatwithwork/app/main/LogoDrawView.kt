package com.chatwithwork.app.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.Animatable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.chatwithwork.app.R

/**
 * What stands in while a page loads: the mark writing itself (the web's
 * `application/logo_draw`). It waits a moment, so fast pages never flash it,
 * and it's the still mark where animations are off.
 */
class LogoDrawView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatImageView(context, attrs) {
    private val reveal = Runnable { visibility = VISIBLE }

    init {
        visibility = INVISIBLE
        setImageResource(if (ValueAnimator.areAnimatorsEnabled()) R.drawable.logo_draw else R.drawable.ic_mark)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(reveal, SHOW_DELAY_MS)
        (drawable as? Animatable)?.start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(reveal)
        (drawable as? Animatable)?.stop()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val SHOW_DELAY_MS = 400L
    }
}
