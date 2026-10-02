package com.chatwithwork.app.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import com.chatwithwork.app.R
import com.chatwithwork.app.main.MainTab
import dev.hotwire.core.config.Hotwire
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.config.title
import dev.hotwire.core.turbo.errors.HttpError
import dev.hotwire.core.turbo.errors.VisitError
import dev.hotwire.core.turbo.nav.PresentationContext
import dev.hotwire.navigation.destinations.HotwireDestination
import dev.hotwire.navigation.destinations.HotwireDestinationDeepLink
import dev.hotwire.navigation.fragments.HotwireWebFragment

/**
 * A page in a tab: the native top app bar over the shared web view.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web")
open class WebFragment : HotwireWebFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_web, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pathProperties.title?.let(fragmentViewModel::setTitle)
        fragmentViewModel.title.observe(viewLifecycleOwner) { title ->
            toolbarForNavigation()?.title = PageTitles.clean(title).ifEmpty { tabTitle().orEmpty() }
        }
        // A tab's first page is named after the tab until its own title arrives
        // (or if it never does, offline).
        if (fragmentViewModel.title.value.isNullOrEmpty()) toolbarForNavigation()?.title = tabTitle()

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
        }
    }

    private fun tabTitle(): String? = if (navigator.isAtStartDestination() &&
        !isModal
    ) {
        MainTab.forNavigatorName(navigator.configuration.name)?.let { getString(it.title) }
    } else {
        null
    }

    private fun isModalLocation(location: String): Boolean =
        Hotwire.config.pathConfiguration.properties(location).context == PresentationContext.MODAL

    // The bar shows the page's own name, tidied (see onViewCreated).
    override fun shouldObserveTitleChanges(): Boolean = false

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
 * A page presented over the tabs: new chat, signing in. It slides up, has a
 * close button, and hides the bottom bar.
 */
@HotwireDestinationDeepLink(uri = "hotwire://fragment/web/modal")
class WebModalFragment : WebFragment()

/** Implemented by the activity, which reacts to what pages do. */
interface WebScreenHost {
    fun onVisitCompleted(destination: HotwireDestination, location: String)

    /** A page answered 401: the session is gone. */
    fun onSignedOut(destination: HotwireDestination)

    fun presentSignIn()
}
