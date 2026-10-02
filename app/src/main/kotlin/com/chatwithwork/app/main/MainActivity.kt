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
import androidx.appcompat.content.res.AppCompatResources
import androidx.browser.auth.AuthTabColorSchemeParams
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.chatwithwork.app.AppConfig
import com.chatwithwork.app.BuildConfig
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.AuthTabHost
import com.chatwithwork.app.bridge.BrowserReturnHost
import com.chatwithwork.app.bridge.FloatingAction
import com.chatwithwork.app.bridge.FloatingActionHost
import com.chatwithwork.app.bridge.NotificationPermissionHost
import com.chatwithwork.app.bridge.SnackbarHost
import com.chatwithwork.app.fragments.WebScreenHost
import com.chatwithwork.app.push.PushNotifications
import com.chatwithwork.app.routing.AppUrls
import com.chatwithwork.app.routing.BrowserTabs
import com.chatwithwork.app.routing.RouteDecisionHandlers
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
import dev.hotwire.navigation.tabs.HotwireBottomNavigationController

/**
 * The app's one activity: the bottom bar with a navigator per tab, the New
 * chat button, the welcome screen when nobody is signed in, and everything
 * that reacts to what the pages do.
 *
 * The server tells it when things change, and it listens in the navigators'
 * traffic:
 *
 * - A 401, or a page landing on the sign-in form, means there's no session:
 *   show the welcome screen (and the sign-in form, if a session just ran out).
 * - `/recede_historical_location`, or the sign-in modal moving on to a page
 *   inside the app, means signing in worked: start every tab afresh, so none
 *   keeps a signed-out page.
 * - A link into another organization means the person switched: every tab
 *   starts afresh, inside it.
 * - A link to another tab's list switches tabs instead of pushing.
 * - A form in a modal that recedes closes the modal.
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
    FloatingActionHost {
    private val viewModel: MainViewModel by viewModels()
    private val memory by lazy { SessionMemory(this) }

    private lateinit var root: View
    private lateinit var hosts: View
    private lateinit var bottomBar: BottomNavigationView
    private lateinit var tabs: HotwireBottomNavigationController
    private lateinit var newChatButton: ExtendedFloatingActionButton
    private lateinit var welcome: View

    private var keyboardVisible = false
    private var bottomInset = 0
    private var sideInsets = 0 to 0

    /** Each tab's floating action, from its first page's `button`. */
    private val floatingActions = mutableMapOf<String, FloatingAction>()

    /** True while the app itself opens the sign-in form (see presentSignIn). */
    private var presentingSignIn = false

    private val returnActions = mutableListOf<() -> Unit>()
    private var awaitingReturn = false

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            PushNotifications.markAsked(this)
            permissionCallback?.invoke(granted)
            permissionCallback = null
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

    /** Back from another tab's first page goes to Chats before leaving the app. */
    private val backToChats =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                tabs.selectTab(MainTab.CHATS.ordinal)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        val link = takeLink(intent)
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The bottom bar and the canvas give the navigation buttons their
            // contrast; the system's scrim would only tint them.
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_main)
        splash.setKeepOnScreenCondition { viewModel.keepSplash() }

        root = findViewById(R.id.root)
        hosts = findViewById(R.id.navigator_hosts)
        bottomBar = findViewById(R.id.bottom_bar)
        newChatButton = findViewById(R.id.new_chat)
        welcome = findViewById(R.id.welcome)

        setUpTabs()
        setUpNewChatButton()
        setUpWelcome()
        setUpInsets()
        onBackPressedDispatcher.addCallback(this, backToChats)

        if (savedInstanceState == null && !memory.hasSignedIn) {
            showSignedOut(presentSignIn = false)
        } else if (viewModel.signedOut) {
            showSignedOut(presentSignIn = false)
        }

        link?.let(::open)
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

    override fun onPause() {
        super.onPause()
        // Anything that asked to hear about the return is waiting on a browser tab.
        if (returnActions.isNotEmpty()) awaitingReturn = true
    }

    // Read again whenever a navigator starts over, so tabs reset after moving
    // to another organization start inside it.
    override fun navigatorConfigurations(): List<NavigatorConfiguration> =
        MainTab.entries.map { it.configuration(viewModel.tabsAccount) }

    override fun onNavigatorReady(navigator: Navigator) {
        navigator.host.navController.addOnDestinationChangedListener { _, _, _ ->
            // The destination is attached once the transaction runs.
            root.post { onDestinationChanged() }
        }
    }

    private fun requestLocalNetworkIfNeeded() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < ANDROID_17 || !AppUrls.serverIsOnPrivateNetwork()) return
        if (checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(ACCESS_LOCAL_NETWORK)
    }

    // region Tabs and chrome

    private fun setUpTabs() {
        tabs =
            HotwireBottomNavigationController(
                activity = this,
                view = bottomBar,
                lazyLoadTabs = true
            )
        tabs.load(MainTab.bottomTabs(this), viewModel.selectedTab.ordinal)
        tabs.setOnTabSelectedListener { index, _ ->
            viewModel.selectedTab = MainTab.entries[index]
            updateChrome()
        }
    }

    private fun setUpNewChatButton() {
        newChatButton.setOnClickListener { floatingAction()?.onClick?.invoke() }
    }

    /**
     * The current tab's primary action: the `button` its first page sends,
     * or for Chats, New chat until the server sends one.
     */
    private fun floatingAction(): FloatingAction? = floatingActions[currentTab.configuration.name]
        ?: if (currentTab == MainTab.CHATS) {
            FloatingAction(getString(R.string.new_chat), AppCompatResources.getDrawable(this, R.drawable.ic_new_chat)) {
                startNewChat()
            }
        } else {
            null
        }

    override fun setFloatingAction(navigatorName: String, action: FloatingAction?) {
        if (action == null) floatingActions.remove(navigatorName) else floatingActions[navigatorName] = action
        updateChrome()
    }

    private val currentTab: MainTab
        get() = viewModel.selectedTab

    private fun navigator(tab: MainTab): Navigator? = delegate.findNavigatorHost(tab.navigatorHostId)?.navigator

    private fun onDestinationChanged() {
        if (viewModel.signingIn && delegate.currentNavigator?.currentDestination?.isModal != true) {
            // The sign-in modal was closed without signing in.
            viewModel.signingIn = false
            if (viewModel.signedOut) showWelcome(true)
        }
        updateChrome()
    }

    /**
     * The bottom bar shows on each tab's first page and hides on the pages
     * pushed from it (a chat gets the whole screen, as in Messages), in
     * modals, and under the keyboard. New chat floats over the chat list only.
     */
    private fun updateChrome() {
        val navigator = delegate.currentNavigator
        val destination = navigator?.currentDestination
        val modal = destination?.pathProperties?.context == PresentationContext.MODAL
        val atStart = navigator?.isAtStartDestination() != false
        val tabsHidden = !atStart || viewModel.signedOut

        tabs.visibility =
            if (tabsHidden) {
                HotwireBottomNavigationController.Visibility.HIDDEN
            } else {
                HotwireBottomNavigationController.Visibility.DEFAULT
            }

        val bottomBarShown = !tabsHidden && !modal && !keyboardVisible
        val action = floatingAction().takeIf { bottomBarShown }
        if (action != null) {
            newChatButton.text = action.title
            newChatButton.contentDescription = action.title
            newChatButton.icon = action.icon
        }
        newChatButton.isVisible = action != null

        backToChats.isEnabled = currentTab != MainTab.CHATS && atStart && !modal && !viewModel.signedOut

        // Without the bottom bar, the pages reach the screen's bottom edge, so
        // they keep clear of the gesture bar themselves.
        hosts.updatePadding(
            left = sideInsets.first,
            right = sideInsets.second,
            bottom = if (bottomBarShown || keyboardVisible) 0 else bottomInset
        )
    }

    private fun setUpInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomInset = bars.bottom
            sideInsets = bars.left to bars.right

            // The keyboard pushes everything up, so the composer stays above
            // it. Applied once, here: applying it again further down the
            // hierarchy would double it.
            view.updatePadding(bottom = if (keyboardVisible) ime.bottom else 0)
            updateChrome()
            insets
        }
    }

    // endregion

    // region New chat

    /** New chat opens as a modal over the chat list, so the chat it starts lands in Chats. */
    private fun startNewChat() {
        showTab(MainTab.CHATS) {
            navigator(MainTab.CHATS)?.route(AppUrls.newChat(viewModel.account ?: memory.lastAccount))
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
     * the tabs under it, which load behind it.
     */
    private fun showWelcome(visible: Boolean) {
        welcome.isVisible = visible
        val hidden =
            if (visible) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        listOf(hosts, bottomBar, newChatButton).forEach { it.importantForAccessibility = hidden }
    }

    private fun presentSignIn(url: String) {
        val navigator = delegate.currentNavigator ?: return
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

    /** Signing in worked: every tab starts afresh, then any link that waited opens. */
    private fun didSignIn() {
        viewModel.signingIn = false
        viewModel.signedOut = false
        viewModel.account = null
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
            // exists. A tab's page behind the welcome screen proves nothing:
            // under its own address it may hold the sign-in form the server
            // redirected it to.
            if (destination.isModal && !AppUrls.isAuthentication(shown) && isSignedInPage(shown)) didSignIn()
            return
        }

        AppUrls.account(shown)?.let(::onAccount)
        openPendingLink(destination.navigator)
        updateChrome()
    }

    private fun isSignedInPage(url: String): Boolean =
        AppUrls.account(url) != null || AppUrls.isTabRoot(url) || AppUrls.pathWithinAccount(url) == "/accounts"

    /**
     * A page that names its organization. When it's another one than the
     * tabs show (the server redirected there), the other tabs start over in
     * it.
     */
    private fun onAccount(account: String) {
        val previous = viewModel.account
        viewModel.account = account
        memory.lastAccount = account
        if (previous == null || previous == account) return

        viewModel.tabsAccount = account
        MainTab.entries.filter { it != currentTab }.forEach { tab ->
            navigator(tab)?.takeIf { it.isReady() }?.reset()
        }
    }

    /**
     * Rebuilds every tab inside [account] and opens [location] in its tab
     * (on top of the tab's list, unless it is the list).
     */
    private fun moveToOrganization(account: String, location: String) {
        viewModel.account = account
        viewModel.tabsAccount = account
        memory.lastAccount = account
        floatingActions.clear()
        viewModel.pendingLink = location.takeIf { opensOnTop(it) }

        val tab = MainTab.forUrl(location) ?: currentTab
        if (currentTab != tab) tabs.selectTab(tab.ordinal)
        delegate.resetNavigators()
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
            val navigator = MainTab.forNavigatorName(configuration.name)?.let(::navigator)
            if (AppUrls.isRecede(location) && navigator?.currentDestination?.isModal == true) {
                root.post { navigator.route(AppUrls.resume) }
                return Router.Decision.CANCEL
            }
            return null
        }

        if (AppUrls.isFile(location)) {
            BrowserTabs.open(this, location.toUri())
            return Router.Decision.CANCEL
        }

        if (viewModel.signingIn || viewModel.signedOut) {
            val inModal =
                MainTab.forNavigatorName(configuration.name)?.let(::navigator)?.currentDestination?.isModal == true
            if (AppUrls.isSignInForm(location) && !inModal && !presentingSignIn) {
                // A tab's page bounced to the sign-in form behind the welcome
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

        // A link into another organization (the switcher in Settings, a
        // notification): every tab starts over there.
        val account = AppUrls.account(location)
        val current = viewModel.account
        if (account != null && current != null && account != current) {
            moveToOrganization(account, location)
            return Router.Decision.CANCEL
        }

        // Another tab's list is a tab switch, not a push.
        val tab = MainTab.forUrl(location)
        if (AppUrls.isTabRoot(location) && tab != null && tab.configuration.name != configuration.name) {
            showTab(tab) { navigator(tab)?.clearAll() }
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
    private fun takeLink(intent: Intent?): String? {
        intent ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val url = intent.dataString ?: return null
        intent.data = null
        return url.takeIf { AppUrls.isAppUrl(it) || AppUrls.isAuthCallback(it) }
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

        val tab = MainTab.forUrl(url) ?: currentTab
        when {
            AppUrls.isTabRoot(url) -> {
                showTab(tab) { navigator(tab)?.clearAll() }
            }

            AppUrls.isNewChat(url) -> {
                startNewChat()
            }

            else -> {
                viewModel.pendingLink = url
                showTab(tab) { navigator(tab)?.let(::openPendingLink) }
            }
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

    /** Opens the waiting link in [navigator] if it belongs there and the navigator can take it. */
    private fun openPendingLink(navigator: Navigator) {
        val url = viewModel.pendingLink ?: return
        val tab = MainTab.forUrl(url) ?: currentTab
        if (navigator.configuration.name != tab.configuration.name) return
        if (!navigator.isReady() || navigator.currentDestination?.fragment?.view == null) return

        viewModel.pendingLink = null
        if (tab != currentTab) tabs.selectTab(tab.ordinal)
        navigator.route(url, VisitOptions())
    }

    /** Selects [tab], then runs [then] once its navigator exists. */
    private fun showTab(tab: MainTab, then: () -> Unit = {}) {
        if (currentTab != tab) tabs.selectTab(tab.ordinal)
        root.post(then)
    }

    /** Tab roots and the new chat page aren't pushed on top after signing in: the tab already shows them. */
    private fun opensOnTop(url: String): Boolean =
        !AppUrls.isTabRoot(url) && !AppUrls.isNewChat(url) && !AppUrls.isAuthentication(url)

    // endregion

    // region Hosts for bridge components

    override fun showSnackbar(message: String, long: Boolean) {
        val snackbar = Snackbar.make(root, message, if (long) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        when {
            newChatButton.isVisible -> snackbar.anchorView = newChatButton
            bottomBar.isVisible && bottomBar.translationY == 0f -> snackbar.anchorView = bottomBar
        }
        snackbar.show()
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
        const val ANDROID_17 = 37
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

        /** Lets the welcome screen settle before the sign-in form slides up. */
        const val SIGN_IN_DELAY_MS = 350L
    }
}
