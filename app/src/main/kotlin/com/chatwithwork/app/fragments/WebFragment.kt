package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LiveData
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.chatwithwork.app.routing.AppUrls
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
 * has the sidebar's button where back would be, with the Decisions pill
 * beside it; every screen but a modal has New chat in its bar.
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

    /** The sidebar's button and Decisions on the root, and New chat on every screen but a modal. */
    private fun addShellActions(toolbar: Toolbar) {
        val ink = MaterialColors.getColor(toolbar, com.google.android.material.R.attr.colorOnSurface)
        if (navigator.isAtStartDestination()) {
            toolbar.navigationIcon = SymbolDrawable(toolbar.context, "menu", color = ink)
            toolbar.navigationContentDescription = getString(R.string.sidebar_open)
            toolbar.setNavigationOnClickListener { host?.openSidebar() }
            addDecisions(toolbar)
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

    /**
     * Decisions, a small pill just after the sidebar's button, as on the web
     * (DecisionsPillDrawable, in the toolbar's logo slot, which sits between
     * the button and the title). It shows once the sidebar's JSON has said
     * how many wait, and opens the Decisions page.
     */
    private fun addDecisions(toolbar: Toolbar) {
        val host = host ?: return
        // The pill sits beside the button, not at the title's usual inset.
        toolbar.contentInsetStartWithNavigation = 0
        host.decisionsCount.observe(viewLifecycleOwner) { count ->
            if (count == null) {
                toolbar.logo = null
                return@observe
            }
            val pill = DecisionsPillDrawable(toolbar.context, count)
            toolbar.logo = pill
            toolbar.logoDescription =
                if (count > 0) {
                    resources.getQuantityString(R.plurals.decisions_pill_description, count, count)
                } else {
                    getString(R.string.sidebar_waiting)
                }
            toolbar.children.firstOrNull { it is ImageView && it.drawable === pill }?.let { logo ->
                logo.isClickable = true
                logo.isFocusable = true
                logo.setOnClickListener { host.openDecisions() }
                logo.updateLayoutParams<Toolbar.LayoutParams> {
                    marginEnd =
                        (12 * resources.displayMetrics.density).toInt()
                }
            }
        }
    }

    private fun isModalLocation(location: String): Boolean =
        Hotwire.config.pathConfiguration.properties(location).context == PresentationContext.MODAL

    // The bar shows the page's own name, tidied (see onViewCreated).
    override fun shouldObserveTitleChanges(): Boolean = false

    // The chrome client belongs to the screen showing the web view, so a page
    // that retitles itself (a chat's automatic title) retitles this screen.
    override fun createWebChromeClient(): HotwireWebChromeClient = AppWebChromeClient(navigator.session) { title ->
        if (isAdded && view != null) fragmentViewModel.setTitle(title)
    }

    @SuppressLint("InflateParams") // Hotwire Native adds it to its own container.
    override fun createProgressView(location: String): View = layoutInflater.inflate(R.layout.view_progress, null)

    override fun createErrorView(error: VisitError): View = ScreenStates.view(
        inflater = layoutInflater,
        error = error,
        onSignIn = { host?.presentSignIn() },
        onRetry = { refresh(displayProgress = true) }
    )

    override fun onVisitStarted(location: String) {
        // A file's pages may have been zoomed; the page after them starts whole.
        if (!AppUrls.isFileViewer(location)) unzoom()
        super.onVisitStarted(location)
    }

    override fun onVisitCompleted(location: String, completedOffline: Boolean) {
        super.onVisitCompleted(location, completedOffline)
        allowZoom(AppUrls.isFileViewer(location))
        host?.onVisitCompleted(this, location)
    }

    /**
     * Two fingers zoom a file's pages, as in a document viewer. The web view
     * is the screens' shared one, so every other page turns it off again,
     * zoomed back out first: a page keeps the scale it was left at.
     */
    private fun allowZoom(allowed: Boolean) {
        if (!allowed) unzoom()
        navigator.session.webView.settings.apply {
            builtInZoomControls = allowed
            displayZoomControls = false
        }
    }

    /** Zooms out as far as the page goes, if it could have been zoomed. */
    private fun unzoom() {
        val webView = navigator.session.webView
        if (webView.settings.builtInZoomControls) webView.zoomBy(MIN_ZOOM_FACTOR)
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

    /** How many decisions wait, from the sidebar's JSON; null until it's known. */
    val decisionsCount: LiveData<Int?>

    /** The Decisions pill: the Decisions page. */
    fun openDecisions()

    fun startNewChat()
}

/**
 * Small enough that any zoom comes out at the page's own minimum scale,
 * which WebView.zoomBy clamps to; it refuses 0.01 and below.
 */
private const val MIN_ZOOM_FACTOR = 0.02f

private const val GROUP_SHELL = 0x0C0
private const val ITEM_NEW_CHAT = 0x0C001

/** After the page's own actions, before the overflow menu. */
private const val ORDER_NEW_CHAT = 80
