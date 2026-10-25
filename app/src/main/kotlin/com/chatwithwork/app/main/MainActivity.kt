package com.chatwithwork.app.main

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.auth.AuthTabColorSchemeParams
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.BuildConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.ActionSheet
import com.chatwithwork.app.bridge.AuthTabHost
import com.chatwithwork.app.bridge.BrowserReturnHost
import com.chatwithwork.app.bridge.NotificationPermissionHost
import com.chatwithwork.app.bridge.NotificationTokenComponent
import com.chatwithwork.app.bridge.OfferReason
import com.chatwithwork.app.bridge.SidebarHost
import com.chatwithwork.app.bridge.SnackbarHost
import com.chatwithwork.app.fragments.WebScreenHost
import com.chatwithwork.app.push.PushNotifications
import com.chatwithwork.app.routing.AppUrls
import com.chatwithwork.app.routing.BrowserTabs
import com.chatwithwork.app.routing.RouteDecisionHandlers
import com.chatwithwork.app.sidebar.Sidebar
import com.chatwithwork.app.sidebar.SidebarClient
import com.chatwithwork.app.sidebar.SidebarDrawer
import com.chatwithwork.app.sidebar.SidebarRow
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.nav.PresentationContext
import dev.hotwire.core.turbo.visit.VisitOptions
import dev.hotwire.core.turbo.visit.VisitProposal
import dev.hotwire.core.turbo.webview.WebViewInfo
import dev.hotwire.core.turbo.webview.WebViewVersionCompatibility
import dev.hotwire.navigation.activities.HotwireActivity
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.navigator.Navigator
import dev.hotwire.navigation.navigator.NavigatorConfiguration
import dev.hotwire.navigation.routing.Router
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The app's one activity: the sidebar shell. One navigator, whose root is
 * always a chat (a new one, or one picked in the sidebar: the path
 * configuration replaces the root with every chat), with Bots, projects and
 * other pages pushed over it and Settings and forms as modals. The sidebar is
 * Material 3's modal navigation drawer, drawn natively from the server's JSON
 * (SidebarDrawer). The welcome screen covers it all when nobody is signed in.
 *
 * The server tells it when things change, and it listens in the navigator's
 * traffic:
 *
 * - A 401, or a page landing on the sign-in form, means there's no session:
 *   show the welcome screen (and the sign-in form, if a session just ran out).
 * - `/recede_historical_location`, or the sign-in modal moving on to a page
 *   inside the app, means signing in worked: start afresh.
 * - A link into another organization means the person switched: the
 *   navigator starts afresh inside it, on its new chat (or the chat linked).
 * - A form in a modal that recedes closes the modal.
 * - The page's hidden `sidebar` component says when the chat lists changed.
 *
 * See docs/server-contract.md for the server's half.
 */
class MainActivity :
    HotwireActivity(),
    WebScreenHost,
    RouteDecisionHandlers.Host,
    SnackbarHost,
    NotificationPermissionHost,
    AuthTabHost,
    BrowserReturnHost,
    SidebarHost,
    SidebarDrawer.Actions {
    private val viewModel: MainViewModel by viewModels()
    private val memory by lazy { SessionMemory(this) }

    private lateinit var drawer: DrawerLayout
    private lateinit var root: View
    private lateinit var welcome: View
    private lateinit var sidebar: SidebarDrawer

    private var keyboardVisible = false

    /** True while the app itself opens the sign-in form (see presentSignIn). */
    private var presentingSignIn = false

    private val returnActions = mutableListOf<() -> Unit>()
    private var awaitingReturn = false

    private var fetchJob: Job? = null
    private var pollJob: Job? = null
    private var loadingMore = false

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            PushNotifications.markAsked(this)
            if (granted) PushNotifications.optIn(this)
            permissionCallback?.invoke(granted)
            permissionCallback = null
            // Pages register the token themselves: the ones on screen hear
            // the new state now, rather than on the next page.
            if (granted) NotificationTokenComponent.answerConnected()
        }

    // Sign-ins in the browser come back here when it has Auth Tabs; a
    // cancelled one is noticed on return (onResume), after any callback
    // intent a Custom Tab fallback delivers.
    private val authTab = AuthTabIntent.registerActivityResultLauncher(this) { result ->
        val uri = result.resultUri
        if (result.resultCode == AuthTabIntent.RESULT_OK && uri != null) deliverAuthCallback(uri.toString())
    }

    // Development builds on Android 17 need the local network permission to
    // reach a server at 10.0.2.2 or on the LAN; the pages reload once it's given.
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) delegate.resetNavigators()
        }

    /** Back closes the sidebar first. Added after Hotwire's own, so it goes first. */
    private val closeSidebar =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                drawer.closeDrawer(GravityCompat.START)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        val link = takeLink(intent, restored = savedInstanceState != null)
        // A chat linked on a cold start (a notification) is where the navigator starts.
        if (link != null && savedInstanceState == null && AppUrls.isChat(link)) {
            viewModel.startLocation = link
            AppUrls.account(link)?.let { viewModel.account = it }
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The canvas gives the navigation buttons their
            // contrast; the system's scrim would only tint them.
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_main)
        splash.setKeepOnScreenCondition { viewModel.keepSplash() }

        drawer = findViewById(R.id.drawer)
        root = findViewById(R.id.root)
        welcome = findViewById(R.id.welcome)

        setUpSidebar()
        setUpWelcome()
        setUpInsets()
        onBackPressedDispatcher.addCallback(this, closeSidebar)

        if (savedInstanceState == null && !memory.hasSignedIn) {
            showSignedOut(presentSignIn = false)
        } else if (viewModel.signedOut) {
            showSignedOut(presentSignIn = false)
        }

        link?.takeIf { viewModel.startLocation != it }?.let(::open)
        requestLocalNetworkIfNeeded()

        WebViewVersionCompatibility.displayUpdateDialogIfOutdated(
            activity = this,
            requiredVersion = WebViewInfo.REQUIRED_WEBVIEW_VERSION
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeLink(intent)?.let(::open)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingReturn) {
            awaitingReturn = false
            val actions = returnActions.toList()
            returnActions.clear()
            actions.forEach { it() }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back in the foreground: what's answering may have finished meanwhile.
        if (!viewModel.signedOut) refreshSidebar()
    }

    override fun onPause() {
        super.onPause()
        // Anything that asked to hear about the return is waiting on a browser tab.
        if (returnActions.isNotEmpty()) awaitingReturn = true
    }

    override fun onStop() {
        super.onStop()
        rememberAnsweringChat()
    }

    override fun navigatorConfigurations(): List<NavigatorConfiguration> = listOf(
        NavigatorConfiguration(
            name = NAVIGATOR,
            startLocation = startLocation(),
            navigatorHostId = R.id.main_navigator_host
        )
    )

    /**
     * Where the app opens: a chat it was sent to, else the chat the person
     * left under ten minutes ago while it was still answering, else a new
     * chat in the list the sidebar last showed. Before the app knows an
     * organization, the unprefixed new chat, which the server redirects into
     * the one used last.
     */
    private fun startLocation(): String {
        viewModel.startLocation?.let { return it }
        val account = viewModel.account ?: memory.lastAccount
        memory
            .answeringChatLeftSince(System.currentTimeMillis(), RESUME_WITHIN_MS)
            ?.takeIf { account == null || AppUrls.account(it) == account }
            ?.let { return it }
        return account?.let { memory.newChatPath(it) }?.let(AppUrls::absolute) ?: AppUrls.newChat(account)
    }

    override fun onNavigatorReady(navigator: Navigator) {
        navigator.host.navController.addOnDestinationChangedListener { _, _, _ ->
            // The destination is attached once the transaction runs.
            root.post { updateChrome() }
        }
    }

    private fun requestLocalNetworkIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < ANDROID_17 || !AppUrls.serverIsOnPrivateNetwork()) return
        if (checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
    }

    private val navigator: Navigator?
        get() = delegate.currentNavigator

    // region The sidebar

    private fun setUpSidebar() {
        val panel = findViewById<View>(R.id.sidebar)
        sidebar = SidebarDrawer(panel, this)
        drawer.setScrimColor(ContextCompat.getColor(this, R.color.scrim))
        drawer.addDrawerListener(
            object : DrawerLayout.SimpleDrawerListener() {
                override fun onDrawerOpened(drawerView: View) {
                    closeSidebar.isEnabled = true
                    startPolling()
                }

                override fun onDrawerClosed(drawerView: View) {
                    closeSidebar.isEnabled = false
                    pollJob?.cancel()
                    sidebar.resetSearch()
                }

                override fun onDrawerStateChanged(newState: Int) {
                    // Starting to open: fetch now, so it's fresh as it lands,
                    // and put the composer's keyboard away.
                    if (newState != DrawerLayout.STATE_IDLE &&
                        !drawer.isDrawerOpen(GravityCompat.START)
                    ) {
                        refreshSidebar()
                        WindowCompat.getInsetsController(window, drawer).hide(WindowInsetsCompat.Type.ime())
                    }
                }
            }
        )
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, bottom = bars.bottom)
            insets
        }
        // A sidebar from the last run of this activity (a theme change) shows at once.
        showSidebar()
    }

    override fun openSidebar() {
        refreshSidebar()
        drawer.openDrawer(GravityCompat.START)
    }

    private fun closeSidebarNow() {
        drawer.closeDrawer(GravityCompat.START)
    }

    /** The sidebar as known now, for the organization showing. */
    private fun showSidebar() {
        val data = viewModel.sidebar?.takeIf { it.organization.id == viewModel.account }
        sidebar.show(data, navigator?.currentDestination?.location, botsKnown = viewModel.account != null)
    }

    override fun onSidebarChanged() {
        viewModel.sidebarStale = true
        if (drawer.isDrawerOpen(GravityCompat.START) || drawer.isDrawerVisible(GravityCompat.START)) {
            refreshSidebar(debounce = true)
        }
    }

    /** Fetches the sidebar for the organization showing (the first page, or every chat while searching). */
    private fun refreshSidebar(debounce: Boolean = false, all: Boolean = sidebar.query.isNotEmpty()) {
        val account = viewModel.account ?: return
        fetchJob?.cancel()
        fetchJob =
            lifecycleScope.launch {
                if (debounce) delay(DEBOUNCE_MS)
                val url = AppUrls.sidebar(account, memory.listKey(account), all)
                when (val result = SidebarClient.fetch(url, userAgent())) {
                    is SidebarClient.Result.Loaded -> {
                        if (viewModel.account != account) return@launch
                        viewModel.sidebar = result.sidebar
                        viewModel.sidebarStale = false
                        rememberSidebar(account, result.sidebar)
                        showSidebar()
                    }

                    SidebarClient.Result.SignedOut -> showSignedOut(presentSignIn = true)

                    SidebarClient.Result.Failed -> showSidebar()
                }
            }
    }

    private fun rememberSidebar(account: String, sidebar: Sidebar) {
        sidebar.links.newChat?.let { memory.setNewChatPath(account, it) }
        sidebar.list?.key?.let { memory.setListKey(account, it) }
    }

    /** While the sidebar is open and something in it answers, it stays fresh. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob =
            lifecycleScope.launch {
                while (true) {
                    delay(POLL_MS)
                    if (viewModel.sidebar?.anyProcessing == true || viewModel.sidebarStale) refreshSidebar()
                }
            }
    }

    private fun userAgent(): String? = navigator?.session?.webView?.settings?.userAgentString

    override fun openPath(path: String) {
        closeSidebarNow()
        navigator?.route(AppUrls.absolute(path))
    }

    /**
     * Starts a new chat in the list the sidebar shows, as the web's New chat
     * does. It replaces the root (the path configuration), so nothing stacks.
     */
    override fun startNewChat() {
        closeSidebarNow()
        navigator?.route(newChatUrl())
    }

    /** The new chat in the list the sidebar shows (or last showed) in the organization showing. */
    private fun newChatUrl(): String {
        val account = viewModel.account ?: memory.lastAccount
        val path =
            viewModel.sidebar?.takeIf { it.organization.id == account }?.links?.newChat
                ?: account?.let { memory.newChatPath(it) }
        return path?.let(AppUrls::absolute) ?: AppUrls.newChat(account)
    }

    override fun openBots() {
        val path = viewModel.sidebar?.links?.bots ?: viewModel.account?.let { "/$it/bots" } ?: return
        openPath(path)
    }

    override fun chooseList() {
        val data = viewModel.sidebar ?: return
        val account = viewModel.account ?: return
        val actions =
            data.lists.map { list ->
                ActionSheet.Action(
                    title = list.name,
                    image =
                        when {
                            list.hq -> "home_work"
                            list.isProject -> "folder"
                            list.kind == "all" -> "forum"
                            else -> "lock"
                        },
                    checked = list.current
                )
            } +
                listOfNotNull(
                    data.links.projects?.let {
                        ActionSheet.Action(getString(R.string.sidebar_all_projects), "folder_open")
                    },
                    data.links.newProject?.let { ActionSheet.Action(getString(R.string.sidebar_new_project), "add") }
                )
        ActionSheet.show(this, getString(R.string.sidebar_lists_title), actions) { index ->
            val list = data.lists.getOrNull(index)
            when {
                list != null -> {
                    memory.setListKey(account, list.key)
                    refreshSidebar(all = false)
                }

                index == data.lists.size && data.links.projects != null -> openPath(data.links.projects)

                else -> data.links.newProject?.let(::openPath)
            }
        }
    }

    override fun showWaiting() {
        val waiting = viewModel.sidebar?.waiting ?: return
        if (waiting.items.isEmpty()) {
            ActionSheet.show(this, getString(R.string.sidebar_nothing_waiting), emptyList()) {}
            return
        }
        val actions =
            waiting.items.map { item ->
                ActionSheet.Action(
                    title = listOfNotNull(item.title, item.project).joinToString(" · "),
                    image =
                        when (item.kind) {
                            "question" -> "help"
                            "bot" -> "memory"
                            else -> "back_hand"
                        }
                )
            }
        ActionSheet.show(this, getString(R.string.sidebar_waiting), actions) { index ->
            waiting.items.getOrNull(index)?.let { openPath(it.url) }
        }
    }

    override fun showPersonMenu() {
        val data = viewModel.sidebar
        if (data == null) {
            viewModel.account?.let { openPath("/$it/settings") }
            return
        }
        val organizations = data.organizations.filter { it.url != null }
        val actions =
            organizations.map { ActionSheet.Action(it.name, image = "apartment", checked = it.current) } +
                listOfNotNull(
                    data.links.organizations?.let {
                        ActionSheet.Action(getString(R.string.sidebar_all_organizations), "domain")
                    },
                    data.links.newOrganization?.let {
                        ActionSheet.Action(getString(R.string.sidebar_new_organization), "add")
                    },
                    data.links.settings?.let { ActionSheet.Action(getString(R.string.settings), "settings") }
                )
        ActionSheet.show(this, data.person?.email ?: data.person?.name, actions) { index ->
            val links =
                listOfNotNull(data.links.organizations, data.links.newOrganization, data.links.settings)
            when {
                index < organizations.size -> organizations[index].takeIf { !it.current }?.url?.let(::openPath)
                    ?: closeSidebarNow()

                else -> links.getOrNull(index - organizations.size)?.let(::openPath)
            }
        }
    }

    override fun loadAll() {
        refreshSidebar(all = true)
    }

    override fun loadMore() {
        val data = viewModel.sidebar ?: return
        val next = data.next ?: return
        if (loadingMore || sidebar.query.isNotEmpty()) return
        loadingMore = true
        lifecycleScope.launch {
            val result = SidebarClient.fetch(AppUrls.absolute(next), userAgent())
            loadingMore = false
            if (result is SidebarClient.Result.Loaded && viewModel.sidebar === data) {
                viewModel.sidebar = data.plus(result.sidebar)
                showSidebar()
            }
        }
    }

    /** Remembers the chat showing, if it's answering, so a launch soon after reopens it. */
    private fun rememberAnsweringChat() {
        val location = navigator?.currentDestination?.location
        val sidebar = viewModel.sidebar
        val chat =
            if (location != null && sidebar != null) {
                (sidebar.chats + sidebar.pins).firstOrNull { it.isProcessing && SidebarRow.isCurrent(it, location) }
            } else {
                null
            }
        memory.leftAnswering(if (chat != null) location else null, System.currentTimeMillis())
    }

    // endregion

    // region Chrome

    /**
     * The sidebar opens from the root (a chat) by its button or a swipe from
     * the edge; over pushed pages and modals it stays shut, and back goes
     * back instead.
     */
    private fun updateChrome() {
        val navigator = navigator
        val destination = navigator?.currentDestination
        val modal = destination?.pathProperties?.context == PresentationContext.MODAL
        val atRoot = navigator?.isAtStartDestination() != false && !modal
        drawer.setDrawerLockMode(
            if (atRoot &&
                !viewModel.signedOut
            ) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )
        showSidebar()
    }

    private fun setUpInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // The pages keep clear of the gesture bar; the keyboard pushes
            // everything up instead, so the composer stays above it. Applied
            // once, here: applying it again further down would double it.
            view.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = if (keyboardVisible) ime.bottom else bars.bottom
            )
            insets
        }
    }

    // endregion

    // region Signing in and out

    private fun setUpWelcome() {
        findViewById<Button>(R.id.welcome_sign_in).setOnClickListener { presentSignIn() }
        findViewById<Button>(R.id.welcome_sign_up).setOnClickListener { presentSignIn(AppUrls.signUp) }

        findViewById<TextView>(R.id.welcome_host).apply {
            // Non-production builds say which server they talk to.
            if (AppConfig.environment != "production") {
                text = AppConfig.baseUrl.toUri().authority
                isVisible = true
            }
        }

        val content = findViewById<View>(R.id.welcome_content)
        val padding = resources.getDimensionPixelSize(R.dimen.welcome_padding)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = padding + bars.left,
                top = bars.top,
                right = padding + bars.right,
                bottom = padding + bars.bottom
            )
            insets
        }
    }

    override fun presentSignIn() = presentSignIn(AppUrls.signIn)

    /**
     * Shows or hides the welcome screen. While it's up, screen readers skip
     * the page under it, which loads behind it.
     */
    private fun showWelcome(visible: Boolean) {
        welcome.isVisible = visible
        findViewById<View>(R.id.main_navigator_host).importantForAccessibility =
            if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        if (visible) closeSidebarNow()
    }

    private fun presentSignIn(url: String) {
        val navigator = navigator ?: return
        if (!viewModel.signingIn) {
            viewModel.signingIn = true
            showWelcome(false)
        }
        // Routing decides synchronously; the flag tells this sign-in form
        // apart from a page that bounced to it (routeDecision).
        presentingSignIn = true
        try {
            navigator.route(url)
        } finally {
            presentingSignIn = false
        }
    }

    /**
     * There's no session: from a 401, from a page that landed on the sign-in
     * form, or on first launch. If a session just ran out, the sign-in form
     * comes straight up; the person was in the middle of something.
     */
    private fun showSignedOut(presentSignIn: Boolean) {
        if (!viewModel.signedOut) {
            viewModel.signedOut = true
            viewModel.sidebar = null
            memory.hasSignedIn = false
            PushNotifications.forget(this)
        }

        if (viewModel.signingIn) return
        showWelcome(true)
        updateChrome()

        if (presentSignIn) {
            welcome.postDelayed({ if (viewModel.signedOut && !viewModel.signingIn) presentSignIn() }, SIGN_IN_DELAY_MS)
        }
    }

    /** Signing in worked: the app starts afresh, then any link that waited opens. */
    private fun didSignIn() {
        viewModel.signingIn = false
        viewModel.signedOut = false
        viewModel.account = null
        viewModel.sidebar = null
        memory.hasSignedIn = true
        showWelcome(false)
        delegate.resetNavigators()
        updateChrome()
    }

    override fun onSignedOut(destination: HotwireDestination) {
        viewModel.firstPageShown = true
        if (viewModel.signingIn) return
        showSignedOut(presentSignIn = memory.hasSignedIn || !viewModel.signedOut)
    }

    // endregion

    // region What pages do

    override fun onVisitCompleted(destination: HotwireDestination, location: String) {
        viewModel.firstPageShown = true

        // The server may redirect a signed-out request to the sign-in form
        // instead of answering 401; the page then shows that form in place.
        val shown = destination.navigator.session.webView.url ?: location
        if (!viewModel.signingIn && !viewModel.signedOut && AppUrls.isSignInForm(shown)) {
            showSignedOut(presentSignIn = true)
            return
        }

        if (viewModel.signingIn || viewModel.signedOut) {
            // The sign-in modal reaching a page inside the app means a session
            // exists. The root behind the welcome screen proves nothing: under
            // its own address it may hold the sign-in form the server
            // redirected it to.
            if (destination.isModal && !AppUrls.isAuthentication(shown) && isSignedInPage(shown)) didSignIn()
            return
        }

        // The start location did its job; the next start uses the sidebar's.
        viewModel.startLocation = null
        memory.leftAnswering(null, 0)
        AppUrls.account(shown)?.let(::onAccount)
        openPendingLink(destination.navigator)
        updateChrome()
        if (viewModel.sidebarStale || drawer.isDrawerVisible(GravityCompat.START)) refreshSidebar(debounce = true)
    }

    private fun isSignedInPage(url: String): Boolean =
        AppUrls.account(url) != null || AppUrls.isNewChat(url) || AppUrls.pathWithinAccount(url) == "/accounts"

    /** A page that names its organization: the sidebar follows it. */
    private fun onAccount(account: String) {
        val previous = viewModel.account
        viewModel.account = account
        memory.lastAccount = account
        if (previous != account) {
            viewModel.sidebarStale = true
            refreshSidebar()
        }
    }

    /**
     * Starts the navigator over inside [account], on [location] when it's a
     * chat, else on the organization's new chat with [location] opened on
     * top of it.
     */
    private fun moveToOrganization(account: String, location: String) {
        viewModel.account = account
        viewModel.sidebar = null
        memory.lastAccount = account
        if (AppUrls.isChat(location)) {
            viewModel.startLocation = location
            viewModel.pendingLink = null
        } else {
            viewModel.startLocation = null
            viewModel.pendingLink = location.takeIf { opensOnTop(it) }
        }
        closeSidebarNow()
        delegate.resetNavigators()
        refreshSidebar()
        updateChrome()
    }

    override fun routeDecision(proposal: VisitProposal, configuration: NavigatorConfiguration): Router.Decision? {
        val location = proposal.location

        if (AppUrls.isHistoricalLocation(location)) {
            showNotice(location)
            if (viewModel.signingIn || viewModel.signedOut) {
                didSignIn()
                return Router.Decision.CANCEL
            }
            // A form in a modal that recedes (a rename saved) closes the modal
            // and nothing else, as on iOS: Hotwire Native would also go back
            // from the page under it. That page loads again, changed.
            val navigator = navigator
            if (AppUrls.isRecede(location) && navigator?.currentDestination?.isModal == true) {
                root.post { navigator.route(AppUrls.resume) }
                return Router.Decision.CANCEL
            }
            // A recede on the chat itself (deleted from its menu) has nothing
            // to go back to: a new chat takes its place.
            if (AppUrls.isRecede(location) && navigator?.isAtStartDestination() == true) {
                viewModel.sidebarStale = true
                root.post { startNewChat() }
                return Router.Decision.CANCEL
            }
            return null
        }

        if (AppUrls.isFile(location)) {
            BrowserTabs.open(this, location.toUri())
            return Router.Decision.CANCEL
        }

        if (viewModel.signingIn || viewModel.signedOut) {
            val inModal = navigator?.currentDestination?.isModal == true
            if (AppUrls.isSignInForm(location) && !inModal && !presentingSignIn) {
                // The root bounced to the sign-in form behind the welcome
                // screen (or as the sign-in modal closed and the page under
                // it loaded again). Signing in starts from the welcome screen.
                return Router.Decision.CANCEL
            }
            if (!AppUrls.isAuthentication(location) && isSignedInPage(location)) {
                // Leaving the sign-in pages for a page inside the app means
                // the server signed someone in, even if it didn't recede.
                viewModel.pendingLink = viewModel.pendingLink ?: location.takeIf { opensOnTop(it) }
                didSignIn()
                return Router.Decision.CANCEL
            }
            return null
        }

        if (AppUrls.isSignInForm(location)) {
            // A form that ends on the sign-in form is signing out; anything
            // else landing there means the session ran out.
            showSignedOut(presentSignIn = proposal.options.response == null)
            return Router.Decision.CANCEL
        }

        if (AppUrls.isHome(location) && proposal.options.response != null) {
            // Signing out on the web ends on the home page, which isn't an app page.
            showSignedOut(presentSignIn = false)
            return Router.Decision.CANCEL
        }

        // A link into another organization (the sidebar's switcher, a
        // notification): the app starts over there.
        val account = AppUrls.account(location)
        val current = viewModel.account
        if (account != null && current != null && account != current) {
            moveToOrganization(account, location)
            return Router.Decision.CANCEL
        }

        return null
    }

    /** `recede_or_redirect_to(url, notice: "...")` puts the notice on the URL; show it the native way. */
    private fun showNotice(location: String) {
        val uri = location.toUri()
        val notice = uri.getQueryParameter("notice")?.takeIf { it.isNotBlank() }
        val alert = uri.getQueryParameter("alert")?.takeIf { it.isNotBlank() }
        when {
            notice != null -> showSnackbar(notice)
            alert != null -> showSnackbar(alert, long = true)
        }
    }

    // endregion

    // region Links from outside: app links and notifications

    /**
     * Takes the link an intent carries, if it's one of ours, and removes it
     * from the intent so the Navigation library doesn't act on it too.
     */
    private fun takeLink(intent: Intent?, restored: Boolean = false): String? {
        intent ?: return null
        if (!restored && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            LaunchLinks
                .shared(
                    intent.action,
                    intent.type,
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
                    intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)
                )?.let { shared ->
                    intent.action = Intent.ACTION_MAIN
                    return shared.into(newChatUrl())
                }
        }
        val link = LaunchLinks.from(intent.action, intent.dataString, intent.flags, restored)
        if (intent.action == Intent.ACTION_VIEW) intent.data = null
        return link
    }

    private fun open(url: String) {
        if (AppUrls.isAuthCallback(url)) {
            deliverAuthCallback(url)
            return
        }

        if (viewModel.signedOut) {
            if (AppUrls.isAuthentication(url)) {
                // A password reset or confirmation link: show it over the welcome screen.
                presentSignIn(url)
            } else {
                viewModel.pendingLink = url
                presentSignIn()
            }
            return
        }

        closeSidebarNow()
        val navigator = navigator
        if (navigator == null) {
            viewModel.pendingLink = url
        } else {
            // A chat replaces the root (the path configuration); a link into
            // another organization starts over there (routeDecision).
            navigator.route(url, VisitOptions())
        }
    }

    /**
     * Hands a browser sign-in's chatwithwork:// callback to the page that
     * started it. When that page is gone (Android ended the app while the
     * browser was in front), a connected service still opens where the person
     * started, with the server's message; a sign-in can't finish without the
     * page's PKCE verifier, so it asks to try again.
     */
    private fun deliverAuthCallback(url: String) {
        if (AuthCallbacks.deliver(url)) return

        val uri = url.toUri()
        val message = uri.getQueryParameter("message")?.takeIf { it.isNotBlank() }
        when (uri.host) {
            "handoff" -> {
                message?.let { showSnackbar(it, long = uri.getQueryParameter("status") == "failed") }
                val path = uri.getQueryParameter("return_to")
                if (!viewModel.signedOut && path != null && path.startsWith("/") && !path.startsWith("//")) {
                    open(AppConfig.baseUrl + path)
                }
            }

            "sign-in" -> if (viewModel.signedOut) showSnackbar(getString(R.string.sign_in_interrupted), long = true)
        }
    }

    /** Opens the waiting link once the navigator has drawn its first page. */
    private fun openPendingLink(navigator: Navigator) {
        val ready = navigator.isReady() && navigator.currentDestination?.fragment?.view != null
        val url = viewModel.takePendingLink(ready) ?: return
        navigator.route(url, VisitOptions())
    }

    /**
     * Pages opened on top of the root after signing in: not the new chat (the
     * root already is one), and not the chat list the server lands people on
     * (the sidebar is the app's list).
     */
    private fun opensOnTop(url: String): Boolean =
        !AppUrls.isNewChat(url) && !AppUrls.isAuthentication(url) && AppUrls.pathWithinAccount(url) != "/chats"

    // endregion

    // region Hosts for bridge components

    override fun showSnackbar(message: String, long: Boolean) {
        Snackbar.make(root, message, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Once per install, when a page says it's a good moment (a change waits
     * for approval, an answer takes a while): a word on what notifications
     * are for, then Android 13's prompt, or on earlier versions turning push
     * on. Never again after a decision, here or in Settings › Notifications.
     */
    override fun offerNotifications(reason: OfferReason, onDone: () -> Unit) {
        val rationale =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        if (isFinishing || !PushNotifications.isAvailable(this) || !PushNotifications.shouldOffer(this, rationale)) {
            onDone()
            return
        }

        PushNotifications.markOffered(this)
        var continued = false
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notifications_offer_title)
            .setMessage(
                if (reason ==
                    OfferReason.APPROVAL
                ) {
                    R.string.notifications_offer_approval
                } else {
                    R.string.notifications_offer_answer
                }
            ).setNegativeButton(R.string.notifications_offer_not_now, null)
            .setPositiveButton(R.string.notifications_offer_continue) { _, _ ->
                continued = true
                if (PushNotifications.needsRuntimePermission(this)) {
                    requestNotificationPermission { onDone() }
                } else {
                    PushNotifications.optIn(this)
                    NotificationTokenComponent.answerConnected()
                    onDone()
                }
            }.setOnDismissListener { if (!continued) onDone() }
            .show()
    }

    override fun requestNotificationPermission(onResult: (granted: Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(PushNotifications.permission(this) == PushNotifications.Permission.GRANTED)
            return
        }
        permissionCallback = onResult
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNextReturn(action: () -> Unit) {
        returnActions += action
    }

    override fun launchAuthTab(uri: Uri, ephemeral: Boolean): Boolean {
        val colors = { color: Int ->
            AuthTabColorSchemeParams.Builder()
                .setToolbarColor(ContextCompat.getColor(this, color))
                .setNavigationBarColor(ContextCompat.getColor(this, color))
                .build()
        }
        val intent = AuthTabIntent.Builder()
            .setEphemeralBrowsingEnabled(ephemeral)
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, colors(R.color.lw_canvas_light_mode))
            .setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colors(R.color.lw_canvas_dark_mode))
            .build()
        return try {
            intent.launch(authTab, uri, AppUrls.CALLBACK_SCHEME)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    // endregion

    private companion object {
        const val NAVIGATOR = "main"
        const val ANDROID_17 = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

        /** Lets the welcome screen settle before the sign-in form slides up. */
        const val SIGN_IN_DELAY_MS = 350L

        /** A chat left while it answered reopens on a launch within this time. */
        const val RESUME_WITHIN_MS = 10 * 60 * 1000L

        /** The page's `changed` messages come in bursts as an answer streams. */
        const val DEBOUNCE_MS = 500L

        /** While the sidebar is open and something answers. */
        const val POLL_MS = 5_000L
    }
}
