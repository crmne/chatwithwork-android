package com.chatwithwork.app.main

import dev.hotwire.core.turbo.config.PathConfigurationProperties
import dev.hotwire.core.turbo.config.context
import dev.hotwire.core.turbo.config.presentation
import dev.hotwire.core.turbo.nav.Presentation
import dev.hotwire.core.turbo.nav.PresentationContext

/**
 * Everything the sidebar opens is a place, never a step: it becomes the one
 * root screen, with the sidebar's button and no back, so there's no history
 * of places to walk back through (docs/server-contract.md, section 5). The
 * path configuration names the places every link opens as the root
 * (`replace_root`: chats, Decisions, Bots, the lists, Settings); a page it
 * pushes elsewhere (a bot from Bots, a project from Projects) is a place
 * too when the sidebar opens it. Forms and pickers, its modals, still come
 * up over what's on screen.
 */
object Places {
    enum class Opening {
        /** As the path configuration says: a modal, or a place it replaces the root with already. */
        ROUTE,

        /** Clear back to the root, then replace it. */
        REPLACE_ROOT
    }

    fun opening(properties: PathConfigurationProperties): Opening = when {
        properties.context == PresentationContext.MODAL -> Opening.ROUTE
        properties.presentation == Presentation.REPLACE_ROOT -> Opening.ROUTE
        else -> Opening.REPLACE_ROOT
    }
}
