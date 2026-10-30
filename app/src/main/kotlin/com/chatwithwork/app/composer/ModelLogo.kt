package com.chatwithwork.app.composer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R

/** Exact maker artwork from the web picker. Never substitute an inference host logo. */
object ModelLogo {
    private val resources = mapOf(
        "anthropic" to R.drawable.model_anthropic,
        "openai" to R.drawable.model_openai,
        "gemini" to R.drawable.model_gemini,
        "qwen" to R.drawable.model_qwen,
        "deepseek" to R.drawable.model_deepseek,
        "mistral" to R.drawable.model_mistral,
        "xai" to R.drawable.model_xai
    )

    fun drawable(context: Context, model: ComposerState.Model, sizeDp: Int = 24): Drawable? {
        val resource = resources[model.iconName] ?: return null
        val image = ContextCompat.getDrawable(context, resource)?.mutate() ?: return null
        if (model.invertIcon) {
            val dark =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            image.setTint(if (dark) Color.WHITE else Color.BLACK)
        }
        val size = (sizeDp * context.resources.displayMetrics.density).toInt()
        image.setBounds(0, 0, size, size)
        return image
    }
}
