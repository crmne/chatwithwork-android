package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chatwithwork.app.R
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.config.title
import dev.hotwire.core.turbo.errors.HttpError
import dev.hotwire.core.turbo.errors.VisitError
import dev.hotwire.core.turbo.nav.PresentationContext
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient
import dev.hotwire.navigation.R as HotwireR
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment
import java.lang.ref.WeakReference

/**
 * A page: the native top app bar over the shared web view. The root (a chat)
 * has the sidebar's button where back would be; every screen but a modal has
 * New chat in its bar. In a chat the bar has no surface of its own and the
 * page runs under it.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web")
open class WebFragment : HotwireWebFragment() {
    /** This screen is where the sidebar went, so its bar offers the sidebar rather than back. */
    private var openedFromSidebar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedFromSidebar = savedInstanceState?.getBoolean(OPENED_FROM_SIDEBAR)
            ?: (host?.takeSidebarOpening(location) == true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(OPENED_FROM_SIDEBAR, openedFromSidebar)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val themed =
            if (runsUnderBar) {
                inflater.cloneInContext(
                    ContextThemeWrapper(inflater.context, R.style.ThemeOverlay_ChatWithWork_ChatScreen)
                )
            } else {
                inflater
            }
        return themed.inflate(R.layout.fragment_web, container, false)
    }

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

        if (runsUnderBar) runPageUnderBar(view)
    }

    /** A chat, whose page the bar floats over. */
    private val runsUnderBar: Boolean
        get() = AppUrls.isChat(location)

    /**
     * The bar's title and buttons sit straight on the page: no surface, no
     * hairline, and what scrolls up fades into the page's background behind
     * them. The page leaves room for the bar (--native-top-bar-height), and
     * so do the pull to refresh's spinners, the page's and an error's.
     */
    private fun runPageUnderBar(view: View) {
        view.findViewById<View>(R.id.toolbar_divider).isVisible = false
        view.findViewById<View>(R.id.page_area).updateLayoutParams<RelativeLayout.LayoutParams> {
            removeRule(RelativeLayout.BELOW)
        }
        view.findViewById<View>(R.id.top_fade).apply {
            background = topFade()
            isVisible = true
        }
        val spinners =
            listOf(HotwireR.id.hotwire_webView_container, HotwireR.id.hotwire_error_refresh)
                .map { view.findViewById<SwipeRefreshLayout>(it) }
        val bar = view.findViewById<View>(R.id.app_bar)
        bar.background = null
        bar.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height != oldBottom - oldTop) {
                spinners.forEach { spinner ->
                    val circle = spinner.progressCircleDiameter
                    spinner.setProgressViewOffset(false, height - circle, height + circle / 2)
                }
                tellPageItsTop()
            }
        }
    }

    /** The page's canvas down past the title, then an ease rather than a ramp, whose ends would show as lines. */
    private fun topFade(): Drawable {
        val canvas = ContextCompat.getColor(requireContext(), R.color.lw_canvas)
        val colors = TOP_FADE_ALPHAS.map { ColorUtils.setAlphaComponent(canvas, it) }.toIntArray()
        return PaintDrawable().apply {
            // Without a shape it ignores the shader and fills in black.
            shape = RectShape()
            shaderFactory =
                object : ShapeDrawable.ShaderFactory() {
                    override fun resize(width: Int, height: Int): Shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        height.toFloat(),
                        colors,
                        TOP_FADE_STOPS,
                        Shader.TileMode.CLAMP
                    )
                }
        }
    }

    /** The web view is shared: each screen says how much of its top is covered as it takes over. */
    private fun tellPageItsTop() {
        val covered = if (runsUnderBar) view?.findViewById<View>(R.id.app_bar)?.height ?: 0 else 0
        PageScripts.setTopBarHeight(navigator.session.webView, covered)
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

    /** Flat at the top of a page, as Material 3's bar is; the hairline comes in as the page goes under it. */
    private fun showBarEdgeOnceScrolled() {
        val edge = view?.findViewById<View>(R.id.toolbar_divider) ?: return
        val webView = navigator.session.webView
        edge.alpha = if (webView.scrollY > 0) 1f else 0f
        // The web view outlives this screen, which it mustn't hold on to.
        val weakEdge = WeakReference(edge)
        webView.setOnScrollChangeListener { _, _, y, _, _ -> weakEdge.get()?.alpha = if (y > 0) 1f else 0f }
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
        if (!runsUnderBar) showBarEdgeOnceScrolled()
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

private val TOP_FADE_STOPS = floatArrayOf(0f, 0.68f, 0.76f, 0.84f, 0.92f, 1f)
private val TOP_FADE_ALPHAS = listOf(255, 255, 224, 140, 46, 0)

private const val OPENED_FROM_SIDEBAR = "opened_from_sidebar"

private const val GROUP_SHELL = 0x0C0
private const val ITEM_NEW_CHAT = 0x0C001

/** After the page's own actions, before the overflow menu. */
private const val ORDER_NEW_CHAT = 80
