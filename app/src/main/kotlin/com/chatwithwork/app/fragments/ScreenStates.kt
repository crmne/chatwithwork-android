package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.chatwithwork.app.R
import dev.hotwire.core.turbo.errors.HttpError
import dev.hotwire.core.turbo.errors.LoadError
import dev.hotwire.core.turbo.errors.VisitError
import dev.hotwire.core.turbo.errors.WebError
import dev.hotwire.core.turbo.errors.WebSslError

/**
 * What a screen shows when its page can't: signed out, offline, missing, or
 * broken, each with the one action that helps.
 */
object ScreenStates {
    enum class Kind { SIGNED_OUT, OFFLINE, NOT_FOUND, ERROR }

    fun kindOf(error: VisitError): Kind = when (error) {
        is HttpError.ClientError.Unauthorized -> Kind.SIGNED_OUT
        is HttpError.ClientError.NotFound, is HttpError.ClientError.Forbidden -> Kind.NOT_FOUND
        is WebError.HostLookup, is WebError.Connect, is WebError.Timeout, is WebError.IO -> Kind.OFFLINE
        is HttpError, is LoadError, is WebError, is WebSslError -> Kind.ERROR
    }

    @SuppressLint("InflateParams") // Hotwire Native adds it to its own container.
    fun view(inflater: LayoutInflater, error: VisitError, onSignIn: () -> Unit, onRetry: () -> Unit): View {
        val view = inflater.inflate(R.layout.view_state, null)
        val kind = kindOf(error)

        when (kind) {
            Kind.SIGNED_OUT -> {
                view.fill(
                    icon = R.drawable.ic_mark,
                    tint = false,
                    title = R.string.signed_out_title,
                    message = R.string.signed_out_message,
                    action = R.string.sign_in,
                    onAction = onSignIn
                )
            }

            Kind.OFFLINE -> {
                view.fill(
                    icon = R.drawable.ic_cloud_off,
                    title = R.string.offline_title,
                    message = R.string.offline_message,
                    action = R.string.try_again,
                    onAction = onRetry
                )
            }

            Kind.NOT_FOUND -> {
                view.fill(
                    icon = R.drawable.ic_lock,
                    title = R.string.not_found_title,
                    message = R.string.not_found_message,
                    action = R.string.try_again,
                    onAction = onRetry
                )
            }

            Kind.ERROR -> {
                view.fill(
                    icon = R.drawable.ic_refresh,
                    title = R.string.error_title,
                    message = R.string.error_message,
                    action = R.string.try_again,
                    onAction = onRetry
                )
            }
        }

        if (error is HttpError && kind != Kind.SIGNED_OUT) {
            view.findViewById<TextView>(R.id.state_detail).apply {
                text = context.getString(R.string.error_code, error.statusCode)
                isVisible = true
            }
        }

        return view
    }

    private fun View.fill(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes message: Int,
        @StringRes action: Int,
        onAction: () -> Unit,
        tint: Boolean = true
    ) {
        findViewById<ImageView>(R.id.state_icon).apply {
            setImageResource(icon)
            if (tint) {
                imageTintList =
                    android.content.res.ColorStateList.valueOf(
                        com.google.android.material.color.MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorOnSurfaceVariant
                        )
                    )
            }
        }
        findViewById<TextView>(R.id.state_title).setText(title)
        findViewById<TextView>(R.id.state_message).setText(message)
        findViewById<Button>(R.id.state_action).apply {
            setText(action)
            setOnClickListener { onAction() }
        }
    }
}
