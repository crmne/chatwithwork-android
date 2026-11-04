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
import com.chatwithwork.app.routing.AppUrls
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
 * New chat in its bar. Every screen's bar is the same TopBar: no surface of
 * its own, its title and buttons over a fade into the page.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web")
open class WebFragment : HotwireWebFragment() {
    /** This screen is where the sidebar went, so its bar offers the sidebar rather than back. */
    private var openedFromSidebar = false

    /** The bar every screen shares (TopBar), from the view's creation on. */
    private var topBar: TopBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedFromSidebar = savedInstanceState?.getBoolean(OPENED_FROM_SIDEBAR)
            ?: (host?.takeSidebarOpening(location) == true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPENED_FROM_SIDEBAR, openedFromSidebar)
    }

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

        topBar = TopBar(view, under = AppUrls.isChat(location) || pathProperties.pageUnderTopBar).apply {
            onCoveredChanged(::tellPageItsTop)
        }
    }

    override fun onDestroyView() {
        topBar = null
        super.onDestroyView()
    }

    /** The web view is shared: each screen says how much of its top is covered as it takes over. */
    private fun tellPageItsTop() {
        PageScripts.setTopBarHeight(navigator.session.webView, topBar?.covered ?: 0)
    }

    /** The sidebar's button on the root and on what the sidebar opened, and New chat on every screen but a modal. */
    private fun addShellActions(toolbar: Toolbar) {
        if (navigator.isAtStartDestination() || openedFromSidebar) {
            toolbar.setNavigationIcon(R.drawable.ic_menu)
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
        tellPageItsTop()
        super.onVisitStarted(location)
    }

    override fun onVisitCompleted(location: String, completedOffline: Boolean) {
        topBar?.follow(navigator.session.webView)
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
        tellPageItsTop()
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

    /** Asked once by each new screen: whether the sidebar opened it, at [location]. */
    fun takeSidebarOpening(location: String): Boolean

    fun startNewChat()
}

/**
 * Small enough that any zoom comes out at the page's own minimum scale,
 * which WebView.zoomBy clamps to; it refuses 0.01 and below.
 */
private const val MIN_ZOOM_FACTOR = 0.02f

private const val OPENED_FROM_SIDEBAR = "opened_from_sidebar"

private const val GROUP_SHELL = 0x0C0
private const val ITEM_NEW_CHAT = 0x0C001

/** After the page's own actions, before the overflow menu. */
private const val ORDER_NEW_CHAT = 80
