package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.ComposerHost
import com.chatwithwork.app.bridge.WebComposerState
import com.chatwithwork.app.composer.ComposerView
import com.chatwithwork.app.composer.PendingShares
import com.chatwithwork.app.routing.AppUrls
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.PathConfigurationProperties
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.config.presentation
import dev.hotwire.core.turbo.config.title
import dev.hotwire.core.turbo.errors.HttpError
import dev.hotwire.core.turbo.errors.VisitError
import dev.hotwire.core.turbo.nav.Presentation
import dev.hotwire.core.turbo.nav.PresentationContext
import dev.hotwire.core.turbo.visit.VisitAction
import dev.hotwire.core.turbo.visit.VisitProposal
import dev.hotwire.core.turbo.webview.HotwireWebChromeClient
import dev.hotwire.navigation.R as HotwireR
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment
import dev.hotwire.navigation.routing.Router
import dev.hotwire.navigation.session.SessionModalResult

/**
 * A page: the native top app bar over the shared web view. The root (the
 * place on screen: a chat, Decisions, Bots...) has the sidebar's button where
 * back would be, and a drill-down pushed over it has back; every screen but a modal has
 * New chat in its bar. Every screen's bar is the same TopBar: no surface of
 * its own, its title and buttons over a fade into the page. New chat, New
 * bot, the home and Decisions give way to the chat their question starts in
 * place (NewChatHandOff).
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web")
open class WebFragment : HotwireWebFragment() {
    /** The bar every screen shares (TopBar), from the view's creation on. */
    private var topBar: TopBar? = null

    /** The page's skeleton, while this view lasts (SkeletonOverlay). */
    private var skeleton: SkeletonOverlay? = null

    /** It shows the first time this screen appears, never when coming back to a page already there. */
    private var skeletonShown = false

    /** This screen is giving way to the chat its question started (NewChatHandOff). */
    private var handingOff = false

    /** Hotwire Native asks this screen to decide while it routes the hand-off itself. */
    private var routingHandOff = false

    /** This screen is that chat, taking over the shared web view that already shows its question. */
    private var handedOver = false

    /** New chat or New bot opened from a page, which it turns into in place (OpensInPlace). */
    private var openedInPlace = false

    /**
     * The page's own composer as it last said (`web-composer`), where the app
     * draws none: whether an answer runs, for a Live Update as the person
     * leaves. It holds for the page it came from ([webComposerUrl]) only, so
     * a failed refresh keeps it and another page's leaves it behind.
     */
    var webComposer: WebComposerState? = null
        private set
    private var webComposerUrl: String? = null

    fun keepWebComposer(state: WebComposerState, url: String) {
        webComposer = state
        webComposerUrl = url
    }

    /** The composer state, when it came from the page now at [url]. */
    fun webComposerFor(url: String?): WebComposerState? =
        webComposer?.takeIf { url != null && webComposerUrl?.let { samePage(it, url) } == true }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_web, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        OfflineCopies.install(navigator.session.webView, navigator.session::reset)
        pathProperties.title?.let(fragmentViewModel::setTitle)
        fragmentViewModel.title.observe(viewLifecycleOwner) { title ->
            val clean = PageTitles.clean(title)
            // Only the page's address so far (PageTitles): the bar keeps what it shows.
            if (clean.isEmpty() && !title.isNullOrBlank()) return@observe
            toolbarForNavigation()?.title = clean
            if (clean.isNotBlank()) skeleton?.titleKnown()
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

        // The chat a question just turned into: its page is already on screen
        // in the shared web view, so neither a skeleton nor a blank bar covers it.
        if (!skeletonShown && NewChatHandOff.take(location)) {
            handedOver = true
            skeletonShown = true
            navigator.session.webView.title?.let(fragmentViewModel::setTitle)
            NewChatHandOff.takeComposer()?.let { handed ->
                view.findViewById<ComposerView>(R.id.composer)?.apply {
                    show(handed.state)
                    if (handed.focused) focusField()
                }
            }
        }

        // New chat or New bot opened from a page turns the page into it in
        // place: the web view goes on showing the page until New chat draws.
        if (!skeletonShown && OpensInPlace.take(location)) {
            skeletonShown = true
            openedInPlace = true
        }

        val kind = PageSkeleton.kind(location, pathProperties.skeleton)
        if (kind != null && !skeletonShown) {
            val toolbar = toolbarForNavigation() ?: return
            skeletonShown = true
            skeleton = SkeletonOverlay(view, kind, toolbar, view.findViewById(R.id.app_bar)).apply { show() }
            FirstPaint.listener = firstPaint
            host?.onSkeletonShown()
        }
    }

    /** A fresh document painted (a cold boot; a Turbo visit has no such thing, only its render). */
    private val firstPaint: () -> Unit = {
        stopHearingFirstPaint()
        val current = skeleton
        // Painted in the page's process: wait until the frame holding it can be drawn here.
        if (current != null) FirstPaint.afterNextFrame(navigator.session.webView) { current.pagePainted() }
    }

    /** The next screen may already be listening as this one goes. */
    private fun stopHearingFirstPaint() {
        if (FirstPaint.listener === firstPaint) FirstPaint.listener = null
    }

    override fun onStart() {
        super.onStart()
        // Shown again (a hand-off the navigator dropped): it moves as any screen does.
        handingOff = false
        takeOverPullToRefresh()
    }

    override fun onStartAfterModalResult(result: SessionModalResult) {
        super.onStartAfterModalResult(result)
        takeOverPullToRefresh()
    }

    override fun onStartAfterDialogCancel() {
        super.onStartAfterDialogCancel()
        takeOverPullToRefresh()
    }

    /** Hotwire Native sets its own refresh as it prepares the screen (each start): the page's in-place one replaces it. */
    private fun takeOverPullToRefresh() {
        val spinner = view?.findViewById<SwipeRefreshLayout>(HotwireR.id.hotwire_webView_container) ?: return
        PullToRefresh.install(spinner, navigator.session.webView) { refresh(displayProgress = false) }
    }

    override fun onDestroyView() {
        skeleton?.remove()
        skeleton = null
        stopHearingFirstPaint()
        topBar = null
        super.onDestroyView()
    }

    /** The web view is shared: each screen says how much of its top is covered as it takes over. */
    private fun tellPageItsTop() {
        PageScripts.setTopBarHeight(navigator.session.webView, topBar?.covered ?: 0)
    }

    /** The sidebar's button on the root, and New chat on every screen but a modal. */
    private fun addShellActions(toolbar: Toolbar) {
        if (navigator.isAtStartDestination()) {
            toolbar.setNavigationIcon(R.drawable.ic_menu)
            toolbar.navigationContentDescription = getString(R.string.sidebar_open)
            toolbar.setNavigationOnClickListener { host?.openSidebar() }
        }
        toolbar.menu.removeGroup(GROUP_SHELL)
        toolbar.menu.add(GROUP_SHELL, ITEM_NEW_CHAT, ORDER_NEW_CHAT, R.string.new_chat).apply {
            icon = ContextCompat.getDrawable(toolbar.context, R.drawable.ic_new_chat)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = !decisionsInBar
            setOnMenuItemClickListener {
                host?.startNewChatOverPage()
                true
            }
        }
    }

    private fun isModalLocation(location: String): Boolean =
        Hotwire.config.pathConfiguration.properties(location).context == PresentationContext.MODAL

    /**
     * The visit to the chat a question on this screen just started replaces
     * this screen (NewChatHandOff): in its place in the stack, so a New chat
     * pushed over a page leaves that page under the chat, with Back to it.
     * The chat's own rule replaces the root, which is this screen already
     * when it's the root.
     */
    override fun customRouteDecision(proposal: VisitProposal): Router.Decision? {
        if (routingHandOff) return null
        val fromResponse = proposal.options.response != null
        if (!NewChatHandOff.turnsInPlace(location, proposal.location, fromResponse)) return null
        handingOff = true
        val composer =
            view?.findViewById<ComposerView>(R.id.composer)?.takeIf { it.visibility == View.VISIBLE }?.let {
                NewChatHandOff.Composer(it.state, it.typing)
            }
        NewChatHandOff.expect(proposal.location, composer)
        if (navigator.isAtStartDestination()) return null
        routingHandOff = true
        try {
            NewChatHandOff.withPresentation(proposal.location, Presentation.REPLACE) {
                navigator.route(proposal.location, proposal.options, proposal.bundle)
            }
        } finally {
            routingHandOff = false
        }
        return Router.Decision.CANCEL
    }

    /**
     * Modals move as Material 3's full-screen dialog (ModalMotion), and the
     * screen one opens over is pictured for its frame (ModalBackdrop), unless
     * the sidebar covers it. The chat a question turns into takes this
     * screen's place without moving; Back from it slides as from any page.
     */
    override fun customNavigationOptions(
        newLocation: String,
        newPathProperties: PathConfigurationProperties,
        action: VisitAction
    ): NavOptions? {
        if (handingOff && NewChatHandOff.isExpected(newLocation)) {
            return navOptions {
                anim {
                    enter = NO_ANIMATION
                    exit = NO_ANIMATION
                    popEnter = HotwireR.anim.enter_slide_in_left
                    popExit = HotwireR.anim.exit_slide_out_right
                }
            }
        }
        val toModal = newPathProperties.context == PresentationContext.MODAL
        if (OpensInPlace.applies(newLocation, toModal, newPathProperties.presentation, action)) {
            OpensInPlace.expect(newLocation)
            return navOptions {
                anim {
                    enter = NO_ANIMATION
                    exit = NO_ANIMATION
                    popEnter = HotwireR.anim.enter_slide_in_left
                    popExit = HotwireR.anim.exit_slide_out_right
                }
            }
        }
        val anims =
            ModalMotion.anims(
                fromModal = isModal,
                toModal = toModal,
                replacesRoot = newPathProperties.presentation == Presentation.REPLACE_ROOT,
                replacesCurrent = action == VisitAction.REPLACE || samePage(newLocation, location)
            ) ?: return null
        if (toModal && !isModal && host?.coversPage != true) activity?.let { ModalBackdrop.capture(it, requireView()) }
        return navOptions {
            anim {
                enter = anims.enter
                exit = anims.exit
                popEnter = anims.popEnter
                popExit = anims.popExit
            }
        }
    }

    private fun samePage(first: String, second: String): Boolean = first.toUri().path == second.toUri().path

    /**
     * Giving way to the chat its question started, this screen goes at once:
     * the chat shows the same web view, and the slide back it was pushed with
     * would show the page under it for a moment.
     */
    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? =
        if (handingOff && !enter) {
            AlphaAnimation(1f, 1f).apply { duration = 0 }
        } else {
            super.onCreateAnimation(transit, enter, nextAnim)
        }

    // The bar shows the page's own name, tidied (see onViewCreated).
    override fun shouldObserveTitleChanges(): Boolean = false

    // The chrome client belongs to the screen showing the web view, so a page
    // that retitles itself (a chat's automatic title) retitles this screen.
    override fun createWebChromeClient(): HotwireWebChromeClient = AppWebChromeClient(
        navigator.session,
        host = { activity as? ComposerHost }
    ) { title ->
        if (isAdded && view != null) fragmentViewModel.setTitle(title)
    }

    @SuppressLint("InflateParams") // Hotwire Native adds it to its own container.
    override fun createProgressView(location: String): View =
        // The skeleton already covers the page, the chat a question turned into
        // already shows, or the page goes on showing until New chat opened in
        // place draws; Hotwire Native still wants a view to show.
        if (skeleton?.covering == true || handedOver || openedInPlace) {
            View(requireContext())
        } else {
            layoutInflater.inflate(R.layout.view_progress, null)
        }

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
        // A page that held shared files is going: they wait again for the next New chat.
        PendingShares.returnUnanswered()
        super.onVisitStarted(location)
    }

    override fun onVisitCompleted(location: String, completedOffline: Boolean) {
        // Only the opening visit keeps the page under it; a later one (a retry) shows its progress.
        openedInPlace = false
        topBar?.follow(navigator.session.webView)
        super.onVisitCompleted(location, completedOffline)
        allowZoom(AppUrls.isFileViewer(location))
        host?.onVisitCompleted(this, location)
        if (!keyboardOffered) {
            keyboardOffered = true
            if (!AppConfig.NATIVE_COMPOSER && AppUrls.opensAsPlace(location)) offerKeyboard()
        }
    }

    /** The page put Decisions at the bar's end (DecisionsComponent), in New chat's place. */
    private var decisionsInBar = false

    /** Decisions takes the bar's last place, or gives it back to New chat (on Decisions itself). */
    fun showDecisionsInBar(shown: Boolean) {
        decisionsInBar = shown
        toolbarForNavigation()?.menu?.findItem(ITEM_NEW_CHAT)?.isVisible = !shown
    }

    /** Asked once per screen: a page coming back from a pushed one doesn't bring the keyboard up again. */
    private var keyboardOffered = false

    /**
     * New chat and New bot focus the page's own composer as they load (the
     * server does, where the app draws no composer of its own), but a focus
     * from script brings no keyboard while the web view itself hasn't the
     * focus: it takes it, and asks for the keyboard for the field the page
     * focused.
     */
    private fun offerKeyboard() {
        // Opened from the sidebar, the page loads as the drawer slides; the
        // keyboard waits for it to have shut, which would otherwise put it away.
        host?.whenDrawerShut {
            if (!isAdded || view == null) return@whenDrawerShut
            val webView = navigator.session.webView
            webView.requestFocus()
            showKeyboardSoon(webView)
        }
    }

    /**
     * New chat already on screen, opened again (a shortcut, the bar): its
     * composer takes the focus, and the keyboard with it.
     */
    fun focusPageComposer() {
        if (view == null) return
        val webView = navigator.session.webView
        webView.requestFocus()
        webView.evaluateJavascript(PageScripts.FOCUS_COMPOSER, null)
        showKeyboardSoon(webView)
    }

    private fun showKeyboardSoon(webView: View) {
        webView.post {
            if (isAdded && view != null) {
                WindowCompat.getInsetsController(requireActivity().window, webView).show(WindowInsetsCompat.Type.ime())
            }
        }
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

    override fun onVisitErrorReceivedWithCachedSnapshotAvailable(location: String, error: VisitError) {
        skeleton?.remove()
        super.onVisitErrorReceivedWithCachedSnapshotAvailable(location, error)
    }

    override fun onVisitErrorReceived(location: String, error: VisitError) {
        openedInPlace = false
        skeleton?.remove()
        super.onVisitErrorReceived(location, error)
        if (error is HttpError.ClientError.Unauthorized) host?.onSignedOut(this)
        // A server from before the organization's home answers it 404: the chat list instead.
        if (AppUrls.homeMissing(location, (error as? HttpError)?.statusCode)) host?.onHomeMissing(this)
    }

    override fun onVisitRendered(location: String) {
        super.onVisitRendered(location)
        skeleton?.settle()
    }

    override fun onColdBootPageCompleted(location: String) {
        super.onColdBootPageCompleted(location)
        PageScripts.install(navigator.session.webView)
        NetworkCost.apply(navigator.session.webView)
        tellPageItsTop()
    }

    private val host: WebScreenHost?
        get() = activity as? WebScreenHost
}

/**
 * A page presented over the app: a form (Rename, Move to project, every new
 * and edit but New chat and New bot), signing in, a chat's file. Material 3's full-screen dialog, in a frame of its own
 * (ModalFrame): it slides up over the screen it opened from, with a close
 * button at the start of its bar, and closes by that, by back, or by a
 * predictive back gesture that shrinks it; on a large window it's a dialog
 * over that screen instead.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web/modal")
class WebModalFragment : WebFragment() {
    /** The frame the page sits in, while this view lasts. */
    var frame: ModalFrame? = null
        private set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val page = checkNotNull(super.onCreateView(inflater, container, savedInstanceState))
        return ModalFrame(inflater.context, page) { if (isAdded) navigator.pop() }.also { frame = it }
    }

    override fun onDestroyView() {
        frame = null
        super.onDestroyView()
    }

    /** The frame moves its card itself as the modal opens and closes (ModalMotion); the fragment's own animation only holds it. */
    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        val frame = frame
        if (frame != null) {
            when (nextAnim) {
                R.anim.modal_present -> frame.animateIn()
                R.anim.modal_dismiss -> frame.animateOut()
            }
        }
        return super.onCreateAnimation(transit, enter, nextAnim)
    }
}

/** Implemented by the activity, which reacts to what pages do. */
interface WebScreenHost {
    fun onVisitCompleted(destination: HotwireDestination, location: String)

    /** A page answered 401: the session is gone. */
    fun onSignedOut(destination: HotwireDestination)

    fun presentSignIn()

    fun openSidebar()

    /** The bar's New chat: pushed over this page, with Back to it. */
    fun startNewChatOverPage()

    /** Runs [action] now, or once the sidebar has slid shut and the screen has been drawn without it. */
    fun whenDrawerShut(action: () -> Unit)

    /** The bar's Decisions (DecisionsComponent): back to it if it's the place under this screen, else it as the place. */
    fun openDecisionsFromBar(url: String?)

    /** The organization's home, in [destination], answered 404 (a server from before it): open the chat list instead. */
    fun onHomeMissing(destination: HotwireDestination)

    /** A page's skeleton is up: something page-shaped is on screen. */
    fun onSkeletonShown()

    /** Whether something of the app's own (the sidebar) covers the page, so a picture of it would show that too. */
    val coversPage: Boolean
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

private const val NO_ANIMATION = 0
