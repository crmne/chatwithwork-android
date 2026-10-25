package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.config.title
import dev.hotwire.core.turbo.errors.HttpError
import dev.hotwire.core.turbo.errors.VisitError
import dev.hotwire.core.turbo.nav.PresentationContext
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment

/**
 * A page: the native top app bar over the shared web view. The root (a chat)
 * has the sidebar's button where back would be; every screen but a modal has
 * New chat in its bar.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web")
open class WebFragment : HotwireWebFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_web, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pathProperties.title?.let(fragmentViewModel::setTitle)
        fragmentViewModel.title.observe(viewLifecycleOwner) { title ->
            toolbarForNavigation()?.title = PageTitles.clean(title)
        }

        // Hotwire Native closes every modal screen with an X. A screen pushed
        // inside a modal (sign-up after sign-in) goes back instead, and only
        // the modal's first screen closes it. Either way the button is named
        // for screen readers, which Hotwire Native leaves out.
        val pushedInModal = isModal && navigator.previousLocation?.let(::isModalLocation) == true
        toolbarForNavigation()?.let { toolbar ->
            if (pushedInModal) toolbar.setNavigationIcon(R.drawable.ic_back)
            if (toolbar.navigationIcon != null) {
                toolbar.navigationContentDescription =
                    getString(
                        if (isModal &&
                            !pushedInModal
                        ) {
                            R.string.close
                        } else {
                            AppCompatR.string.abc_action_bar_up_description
                        }
                    )
            }
            if (!isModal) addShellActions(toolbar)
        }
    }

    /** The sidebar's button on the root, and New chat on every screen but a modal. */
    private fun addShellActions(toolbar: Toolbar) {
        val ink = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        if (navigator.isAtStartDestination()) {
            toolbar.navigationIcon = SymbolDrawable(toolbar.context, "menu", color = ink)
            toolbar.navigationContentDescription = getString(R.string.sidebar_open)
            toolbar.setNavigationOnClickListener { host?.openSidebar() }
        }
        toolbar.menu.removeGroup(GROUP_SHELL)
        toolbar.menu.add(GROUP_SHELL, ITEM_NEW_CHAT, ORDER_NEW_CHAT, R.string.new_chat).apply {
            icon = ContextCompat.getDrawable(toolbar.context, R.drawable.ic_new_chat)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                host?.startNewChat()
                true
            }
        }
    }

    private fun isModalLocation(location: String): Boolean =
        Hotwire.config.pathConfiguration.properties(location).context == PresentationContext.MODAL

    // The bar shows the page's own name, tidied (see onViewCreated).
    override fun shouldObserveTitleChanges(): Boolean = false

    override fun createWebChromeClient(): HotwireWebChromeClient = AppWebChromeClient(navigator.session)

    @SuppressLint("InflateParams") // Hotwire Native adds it to its own container.
    override fun createProgressView(location: String): View = layoutInflater.inflate(R.layout.view_progress, null)

    override fun createErrorView(error: VisitError): View = ScreenStates.view(
        inflater = layoutInflater,
        error = error,
        onSignIn = { host?.presentSignIn() },
        onRetry = { refresh(displayProgress = true) }
    )

    override fun onVisitCompleted(location: String, completedOffline: Boolean) {
        super.onVisitCompleted(location, completedOffline)
        host?.onVisitCompleted(this, location)
    }

    override fun onVisitErrorReceived(location: String, error: VisitError) {
        super.onVisitErrorReceived(location, error)
        if (error is HttpError.ClientError.Unauthorized) host?.onSignedOut(this)
    }

    override fun onColdBootPageCompleted(location: String) {
        super.onColdBootPageCompleted(location)
        PageScripts.install(navigator.session.webView)
    }

    private val host: WebScreenHost?
        get() = activity as? WebScreenHost
}

/**
 * A page presented over the app: Settings, a form, signing in. It slides up
 * and has a close button.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web/modal")
class WebModalFragment : WebFragment()

/** Implemented by the activity, which reacts to what pages do. */
interface WebScreenHost {
    fun onVisitCompleted(destination: HotwireDestination, location: String)

    /** A page answered 401: the session is gone. */
    fun onSignedOut(destination: HotwireDestination)

    fun presentSignIn()

    fun openSidebar()

    fun startNewChat()
}

private const val GROUP_SHELL = 0x0C0
private const val ITEM_NEW_CHAT = 0x0C001

/** After the page's own actions, before the overflow menu. */
private const val ORDER_NEW_CHAT = 80
