package com.chatwithwork.app.sidebar

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Rect
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.SymbolDrawable
import com.google.android.material.color.MaterialColors

/**
 * Draws the sidebar's content into the drawer's views and tells [actions]
 * what the person picked. It holds no state of its own beyond the search
 * text: the activity hands it the latest [Sidebar] (or null before one
 * arrives) and the page showing.
 */
class SidebarDrawer(private val root: View, private val actions: Actions) {
    interface Actions {
        fun openPath(path: String)

        fun startNewChat()

        fun openBots()

        /** The Chats | Bots switch: show [view] under it, and remember it. */
        fun chooseView(view: SidebarRow.View)

        fun chooseList()

        /** Decisions: the Decisions page, or, from an older server, the list of what waits. */
        fun openDecisions()

        /** A long press on [chat]'s row, [anchor]: its actions as a menu. */
        fun showChatActions(chat: Sidebar.Chat, anchor: View)

        /** A tap on one of the person's sections: open it, or close it. */
        fun toggleSection(section: Sidebar.Section)

        /** A long press on a section's heading, [anchor]: Rename. */
        fun showSectionActions(section: Sidebar.Section, anchor: View)

        fun showPersonMenu()

        /** The search needs every chat, not one page (`all=1`). */
        fun loadAll()

        /** The list scrolled near its end: load the next page, if any. */
        fun loadMore()
    }

    private val context = root.context
    private val list = root.findViewById<RecyclerView>(R.id.sidebar_list)
    private val adapter = RowsAdapter()
    private var sidebar: Sidebar? = null
    private var current: String? = null
    private var botsKnown = false
    private var view = SidebarRow.View.CHATS
    private var botList: String? = null

    /** Whether the bots show, which a remembered Bots doesn't mean where the server sends none. */
    private val showsBots: Boolean
        get() = adapter.currentList.any { it is SidebarRow.Views && it.view == SidebarRow.View.BOTS }

    var query = ""
        private set

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.addItemDecoration(Gaps())
        list.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    if (dy > 0 &&
                        !showsBots &&
                        manager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_AHEAD
                    ) {
                        actions.loadMore()
                    }
                }
            }
        )
        root.findViewById<View>(R.id.sidebar_person).setOnClickListener { actions.showPersonMenu() }
        render()
    }

    fun show(sidebar: Sidebar?, current: String?, botsKnown: Boolean, view: SidebarRow.View, botList: String?) {
        this.sidebar = sidebar
        this.current = current
        this.botsKnown = botsKnown
        this.view = view
        this.botList = botList
        render()
    }

    /** Clears the search, as the drawer closes. */
    fun resetSearch() {
        if (query.isEmpty()) return
        val field = list.findViewById<EditText>(R.id.sidebar_search)
        if (field != null) {
            hideKeyboard(field)
            field.setText("")
        } else {
            query = ""
            render()
        }
    }

    private fun render() {
        // Rows that arrive above the first one (the switcher and search, once
        // the server answers) would otherwise push the top out of sight.
        val atTop = !list.canScrollVertically(-1)
        adapter.submitList(
            SidebarRow.build(
                sidebar = sidebar,
                query = query,
                current = current,
                pinnedTitle = context.getString(R.string.sidebar_pinned),
                botsKnown = botsKnown,
                otherTitle = context.getString(R.string.sidebar_other_chats),
                view = view,
                needsYouTitle = context.getString(R.string.sidebar_bots_needs_you),
                quietTitle = context.getString(R.string.sidebar_bots_quiet),
                botList = botList
            )
        ) {
            list.invalidateItemDecorations()
            if (atTop) list.scrollToPosition(0)
        }
        renderPerson()
    }

    private fun renderPerson() {
        val initials = root.findViewById<TextView>(R.id.sidebar_person_initials)
        val name = root.findViewById<TextView>(R.id.sidebar_person_name)
        val organization = root.findViewById<TextView>(R.id.sidebar_person_organization)
        val person = sidebar?.person
        if (person == null) {
            // Nothing from the server yet: the foot still leads to Settings.
            initials.text = null
            initials.background = null
            initials.setCompoundDrawablesRelativeWithIntrinsicBounds(symbol("settings"), null, null, null)
            name.setText(R.string.settings)
            organization.text = sidebar?.organization?.name
            organization.isVisible = organization.text.isNotEmpty()
            root.findViewById<View>(R.id.sidebar_person_more).isVisible = false
        } else {
            root.findViewById<View>(R.id.sidebar_person_more).isVisible = true
            initials.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            initials.setBackgroundResource(R.drawable.sidebar_avatar)
            initials.text = person.initials
            name.text = person.name
            organization.text = sidebar?.organization?.name
            organization.isVisible = true
        }
        root.findViewById<View>(R.id.sidebar_person).contentDescription =
            context.getString(R.string.sidebar_person_menu, name.text)
    }

    private fun symbol(
        name: String,
        color: Int = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface),
        sizeDp: Float = 20f
    ) = SymbolDrawable(context, name, sizeDp = sizeDp, color = color, weight = SYMBOL_WEIGHT).apply {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }

    /** The bots' mark, kept at its own size so its squares stay whole pixels. */
    private fun botMark(
        color: Int = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface),
        sizeDp: Float = 18f,
        blinks: Boolean = true,
        face: List<IntArray>? = null
    ) = BotMarkDrawable(context, color, sizeDp, blinks, face)

    private fun ink(color: Int) = ContextCompat.getColor(context, color)

    /**
     * The gaps around rows, the web's: they depend on the neighbours, which
     * change without a row being bound again, so they aren't in its layout.
     */
    private inner class Gaps : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            val rows = adapter.currentList
            val row = rows.getOrNull(position) ?: return
            val previous = rows.getOrNull(position - 1)
            val unit = context.resources.displayMetrics.density
            when {
                row is SidebarRow.BotRow && previous is SidebarRow.Search -> outRect.top = (GAP_DP * unit).toInt()

                row is SidebarRow.Section && previous is SidebarRow.Search -> outRect.top = (-GAP_DP * unit).toInt()

                row is SidebarRow.ChatRow -> {
                    val lastPin = row.pinned && (rows.getOrNull(position + 1) as? SidebarRow.ChatRow)?.pinned != true
                    val under =
                        when {
                            lastPin -> GAP_DP
                            row.pinned -> PIN_GAP_DP
                            else -> 1
                        }
                    if (row.pinned && previous is SidebarRow.Section) outRect.top = (PIN_GAP_DP * unit).toInt()
                    outRect.bottom = (under * unit).toInt()
                }
            }
        }
    }

    private fun hideKeyboard(view: View) {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private inner class RowsAdapter : ListAdapter<SidebarRow, RecyclerView.ViewHolder>(Diff) {
        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is SidebarRow.Head -> TYPE_HEAD
            is SidebarRow.SeeAll -> TYPE_SEE_ALL
            is SidebarRow.BotsCard -> TYPE_SWITCHER
            is SidebarRow.Views -> TYPE_VIEWS
            is SidebarRow.Decisions -> TYPE_DECISIONS
            is SidebarRow.BotRow -> TYPE_BOT
            is SidebarRow.NoBots -> TYPE_NO_BOTS
            is SidebarRow.Search -> TYPE_SEARCH
            is SidebarRow.Section -> TYPE_SECTION
            is SidebarRow.Empty -> TYPE_SECTION
            is SidebarRow.ChatSection -> TYPE_CHAT_SECTION
            else -> TYPE_ROW
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout =
                when (viewType) {
                    TYPE_HEAD -> R.layout.sidebar_head
                    TYPE_SEE_ALL -> R.layout.sidebar_see_all
                    TYPE_SWITCHER -> R.layout.sidebar_switcher
                    TYPE_SEARCH -> R.layout.sidebar_search
                    TYPE_SECTION -> R.layout.sidebar_section
                    TYPE_CHAT_SECTION -> R.layout.sidebar_chat_section
                    TYPE_VIEWS -> R.layout.sidebar_views
                    TYPE_DECISIONS -> R.layout.sidebar_decisions
                    TYPE_BOT -> R.layout.sidebar_bot
                    TYPE_NO_BOTS -> R.layout.sidebar_bots_empty
                    else -> R.layout.sidebar_row
                }
            val view = inflater.inflate(layout, parent, false)
            if (viewType == TYPE_SEARCH) setUpSearch(view as EditText)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView
            when (val row = getItem(position)) {
                is SidebarRow.Head -> bindHead(view, row)
                is SidebarRow.Search -> bindSearch(view as EditText, row)
                is SidebarRow.SeeAll -> bindSeeAll(view as TextView, row)
                is SidebarRow.Section -> (view as TextView).text = row.title
                is SidebarRow.Empty -> (view as TextView).text = emptyText(row)
                is SidebarRow.Nav -> bindNav(view, row)
                is SidebarRow.ChatRow -> bindChat(view, row)
                is SidebarRow.ChatSection -> bindChatSection(view, row.section)
                is SidebarRow.BotsCard -> bindBotsCard(view, row)
                is SidebarRow.Views -> bindViews(view, row)
                is SidebarRow.Decisions -> bindDecisions(view, row)
                is SidebarRow.BotRow -> bindBot(view, row)
                is SidebarRow.NoBots -> bindNoBots(view, row)
            }
        }

        private fun emptyText(row: SidebarRow.Empty): String = when (row.kind) {
            SidebarRow.Empty.Kind.NO_CHATS -> context.getString(R.string.sidebar_no_chats)
            SidebarRow.Empty.Kind.NO_LISTED_CHATS -> context.getString(R.string.sidebar_no_listed_chats)
            SidebarRow.Empty.Kind.NO_PROJECT_CHATS -> context.getString(R.string.sidebar_no_project_chats, row.project)
            SidebarRow.Empty.Kind.NO_MATCHES -> context.getString(R.string.sidebar_no_matches)
            SidebarRow.Empty.Kind.NO_BOTS -> context.getString(R.string.sidebar_no_bots_here)
            SidebarRow.Empty.Kind.NO_BOT_MATCHES -> context.getString(R.string.sidebar_no_bot_matches)
        }

        private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

        private fun bindDecisions(view: View, row: SidebarRow.Decisions) {
            val waiting = row.count > 0
            view.findViewById<ImageView>(R.id.sidebar_decisions_icon).setImageDrawable(
                symbol("checklist", ink(R.color.lw_ink_muted), sizeDp = 20f)
            )
            view.findViewById<TextView>(R.id.sidebar_decisions_count).apply {
                text = row.count.toString()
                isVisible = waiting
            }
            view.findViewById<TextView>(R.id.sidebar_decisions_none).apply {
                isVisible = !waiting
                if (!waiting && compoundDrawablesRelative[0] == null) {
                    val mark =
                        botMark(ink(R.color.lw_ink_faint), sizeDp = 16f, blinks = false, face = BotMarkDrawable.HAPPY)
                    mark.setBounds(0, 0, mark.intrinsicWidth, mark.intrinsicHeight)
                    setCompoundDrawablesRelative(mark, null, null, null)
                }
            }
            view.isActivated = waiting
            view.isSelected = row.selected
            view.contentDescription =
                if (waiting) {
                    context.resources.getQuantityString(R.plurals.sidebar_decisions_description, row.count, row.count)
                } else {
                    context.getString(R.string.sidebar_decisions_none_description)
                }
            view.setOnClickListener { actions.openDecisions() }
        }

        private fun bindViews(view: View, row: SidebarRow.Views) {
            val chosen = ink(R.color.lw_ink)
            val muted = ink(R.color.lw_ink_muted)
            val chatsInk = if (row.view == SidebarRow.View.CHATS) chosen else muted
            val botsInk = if (row.view == SidebarRow.View.BOTS) chosen else muted
            val chats = view.findViewById<View>(R.id.sidebar_view_chats)
            val bots = view.findViewById<View>(R.id.sidebar_view_bots)
            view.findViewById<ImageView>(R.id.sidebar_view_chats_icon)
                .setImageDrawable(symbol("forum", chatsInk, sizeDp = 18f))
            view.findViewById<ImageView>(R.id.sidebar_view_bots_icon)
                .setImageDrawable(botMark(botsInk, sizeDp = 16f, blinks = false))
            view.findViewById<TextView>(R.id.sidebar_view_chats_label).setTextColor(chatsInk)
            view.findViewById<TextView>(R.id.sidebar_view_bots_label).setTextColor(botsInk)
            view.findViewById<View>(R.id.sidebar_view_bots_dot).isVisible = row.botsAttention
            chats.isSelected = row.view == SidebarRow.View.CHATS
            bots.isSelected = row.view == SidebarRow.View.BOTS
            chats.contentDescription = context.getString(R.string.sidebar_views_chats_description)
            bots.contentDescription =
                context.getString(
                    if (row.botsAttention) {
                        R.string.sidebar_views_bots_attention
                    } else {
                        R.string.sidebar_views_bots_description
                    }
                )
            listOf(chats to SidebarRow.View.CHATS, bots to SidebarRow.View.BOTS).forEach { (half, choice) ->
                ViewCompat.setAccessibilityDelegate(
                    half,
                    object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.className = RadioButton::class.java.name
                            info.isCheckable = true
                            info.isChecked = host.isSelected
                        }
                    }
                )
                half.setOnClickListener { if (row.view != choice) actions.chooseView(choice) }
            }
        }

        private fun bindBotsCard(view: View, row: SidebarRow.BotsCard) {
            view.findViewById<TextView>(R.id.sidebar_switcher_kicker).text =
                context.getString(R.string.sidebar_bots_in, row.organization)
            val name =
                if (row.count > 0) {
                    context.resources.getQuantityString(R.plurals.sidebar_bots_count, row.count, row.count)
                } else {
                    context.getString(R.string.sidebar_no_bots_yet)
                }
            view.findViewById<TextView>(R.id.sidebar_switcher_name).text = name
            view.findViewById<TextView>(R.id.sidebar_switcher_mark).text = null
            view.findViewById<ImageView>(R.id.sidebar_switcher_symbol).apply {
                isVisible = true
                scaleType = ImageView.ScaleType.CENTER
                setImageDrawable(
                    botMark(ContextCompat.getColor(context, R.color.lw_ink_inverted), sizeDp = 16f, blinks = false)
                )
            }
            val states = SpannableStringBuilder()
            if (row.working > 0) {
                states.append(
                    context.resources.getQuantityString(R.plurals.sidebar_bots_working, row.working, row.working),
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.lw_live_ink)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (row.waiting > 0) {
                if (states.isNotEmpty()) states.append(" · ")
                states.append(
                    context.resources.getQuantityString(R.plurals.sidebar_bots_waiting, row.waiting, row.waiting),
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.lw_attention_ink)),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            view.findViewById<TextView>(R.id.sidebar_switcher_states).apply {
                text = states
                isVisible = states.isNotEmpty()
            }
            view.findViewById<ImageView>(R.id.sidebar_switcher_caret).setImageDrawable(
                SymbolDrawable(
                    context,
                    "chevron_right",
                    sizeDp = 20f,
                    color = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
            )
            val summary =
                listOf(context.getString(R.string.sidebar_bots_in, row.organization), name)
                    .plus(states.toString().split(" · ").filter { it.isNotEmpty() })
                    .joinToString(", ")
            view.contentDescription = context.getString(R.string.sidebar_bots_card_description, summary)
            view.setOnClickListener { row.url?.let(actions::openPath) ?: actions.openBots() }
        }

        private fun bindBot(view: View, row: SidebarRow.BotRow) {
            val bot = row.bot
            val paused = bot.state == "paused"
            val faint = ink(R.color.lw_ink_faint)
            val nameInk =
                when {
                    row.selected -> ink(R.color.lw_ink)
                    paused -> faint
                    else -> ink(R.color.lw_ink_muted)
                }
            view.findViewById<ImageView>(R.id.sidebar_bot_mark)
                .setImageDrawable(botMark(nameInk, blinks = !paused))
            view.findViewById<View>(R.id.sidebar_bot_live).isVisible = bot.working
            view.findViewById<TextView>(R.id.sidebar_bot_name).apply {
                text = bot.name
                setTextColor(nameInk)
            }
            view.findViewById<ImageView>(R.id.sidebar_bot_project).apply {
                isVisible = bot.project != null
                setImageDrawable(symbol("folder", faint, sizeDp = 16f))
            }
            val waiting = bot.waiting > 0
            view.findViewById<View>(R.id.sidebar_bot_dot).isVisible = waiting
            view.findViewById<TextView>(R.id.sidebar_bot_state).apply {
                // Its one state, as the web's end of the row; numbers and times are mono.
                val (words, color) =
                    when {
                        waiting -> bot.waiting.toString() to faint

                        bot.state == "failed" -> context.getString(R.string.sidebar_bot_failed) to
                            ink(R.color.lw_negative_ink)

                        paused -> context.getString(R.string.sidebar_bot_paused) to faint

                        bot.state == "next" -> bot.nextRun to faint

                        else -> null to faint
                    }
                val mono = waiting || bot.state == "next"
                text = words
                isVisible = words != null
                setTextColor(color)
                typeface =
                    ResourcesCompat.getFont(context, if (mono) R.font.geist_mono_medium else R.font.geist_regular)
                textSize = if (mono) 11f else 12f
            }
            view.isSelected = row.selected
            val state =
                if (waiting) {
                    context.resources.getQuantityString(R.plurals.sidebar_bot_waiting, bot.waiting, bot.waiting)
                } else {
                    bot.stateLabel
                }
            view.contentDescription =
                listOfNotNull(
                    bot.name,
                    bot.project?.let { context.getString(R.string.sidebar_bot_shared_in, it) },
                    state,
                    context.getString(R.string.sidebar_bot_working).takeIf { bot.working && bot.state != "working" }
                ).joinToString(", ")
            view.setOnClickListener { actions.openPath(bot.url) }
            view.setOnLongClickListener(null)
            view.isLongClickable = false
        }

        private fun bindNoBots(view: View, row: SidebarRow.NoBots) {
            val button = view.findViewById<TextView>(R.id.sidebar_bots_new)
            button.isVisible = row.url != null
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(
                symbol("add", ink(R.color.lw_ink_inverted), sizeDp = 20f),
                null,
                null,
                null
            )
            button.setOnClickListener { row.url?.let(actions::openPath) }
        }

        private fun bindHead(view: View, row: SidebarRow.Head) {
            val list = row.list
            val muted = ink(R.color.lw_ink_muted)
            val bots = row.view == SidebarRow.View.BOTS
            val button = view.findViewById<View>(R.id.sidebar_head_list)
            view.findViewById<ImageView>(R.id.sidebar_head_icon).setImageDrawable(
                when {
                    list.hq -> symbol("home_work", muted, sizeDp = 18f)
                    list.isProject -> symbol("folder", muted, sizeDp = 18f)
                    list.kind == "recent" -> symbol("lock", muted, sizeDp = 18f)
                    bots -> botMark(muted, sizeDp = 16f, blinks = false)
                    else -> symbol("forum", muted, sizeDp = 18f)
                }
            )
            view.findViewById<TextView>(R.id.sidebar_head_name).text = list.name
            val waiting = row.waiting > 0
            val state = context.resources.getQuantityString(R.plurals.sidebar_head_waiting, row.waiting, row.waiting)
            view.findViewById<View>(R.id.sidebar_head_dot).isVisible = waiting
            view.findViewById<TextView>(R.id.sidebar_head_state).apply {
                text = state
                isVisible = waiting
            }
            view.findViewById<ImageView>(R.id.sidebar_head_caret)
                .setImageDrawable(symbol("unfold_more", ink(R.color.lw_ink_faint), sizeDp = 18f))
            button.contentDescription =
                listOfNotNull(
                    context.getString(R.string.sidebar_switcher_description, list.name),
                    state.takeIf { waiting }
                ).joinToString(" ")
            button.setOnClickListener { actions.chooseList() }

            val add = view.findViewById<View>(R.id.sidebar_head_new)
            val name = context.getString(if (bots) R.string.new_bot else R.string.new_chat)
            add.isVisible = row.canAdd
            add.isSelected = row.adding
            add.contentDescription =
                if (list.isProject && row.newUrl?.contains("project_id=") == true) {
                    context.getString(R.string.sidebar_new_in, name, list.name)
                } else {
                    name
                }
            view.findViewById<ImageView>(R.id.sidebar_head_plus)
                .setImageDrawable(symbol("add", ink(R.color.lw_ink), sizeDp = 24f))
            add.setOnClickListener {
                if (bots) row.newUrl?.let(actions::openPath) else actions.startNewChat()
            }
        }

        private fun bindSearch(field: EditText, row: SidebarRow.Search) {
            field.hint =
                when {
                    row.project != null -> context.getString(R.string.sidebar_search_in, row.project)
                    row.view == SidebarRow.View.BOTS -> context.getString(R.string.sidebar_search_bots)
                    else -> context.getString(R.string.sidebar_search)
                }
        }

        private fun bindSeeAll(view: TextView, row: SidebarRow.SeeAll) {
            view.text =
                row.project?.let { context.getString(R.string.sidebar_open_project, it) }
                    ?: context.getString(R.string.sidebar_see_all)
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                symbol("arrow_forward", ink(R.color.lw_ink_faint), sizeDp = 16f),
                null
            )
            view.setOnClickListener { actions.openPath(row.url) }
        }

        private fun bindChatSection(view: View, section: Sidebar.Section) {
            val muted = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)
            view.findViewById<ImageView>(R.id.sidebar_chat_section_caret).setImageDrawable(
                SymbolDrawable(
                    context,
                    if (section.collapsed) "chevron_right" else "expand_more",
                    sizeDp = 18f,
                    color = muted
                )
            )
            view.findViewById<TextView>(R.id.sidebar_chat_section_name).text = section.name
            view.findViewById<TextView>(R.id.sidebar_chat_section_count).text = section.chats.size.toString()
            val chats = context.resources.getQuantityString(
                R.plurals.sidebar_section_chats,
                section.chats.size,
                section.chats.size
            )
            val state =
                context.getString(
                    if (section.collapsed) R.string.sidebar_section_closed else R.string.sidebar_section_open
                )
            view.contentDescription = listOf(section.name, chats, state).joinToString(", ")
            view.setOnClickListener { actions.toggleSection(section) }
            if (section.editUrl == null) {
                view.setOnLongClickListener(null)
                view.isLongClickable = false
            } else {
                view.setOnLongClickListener {
                    actions.showSectionActions(section, it)
                    true
                }
            }
        }

        private fun setUpSearch(field: EditText) {
            field.setCompoundDrawablesRelativeWithIntrinsicBounds(
                symbol("search", ink(R.color.lw_ink_faint), sizeDp = 18f),
                null,
                null,
                null
            )
            field.setText(query)
            field.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        val text = s?.toString().orEmpty()
                        if (text == query) return
                        if (query.isEmpty() && text.isNotEmpty() && !showsBots) actions.loadAll()
                        query = text
                        render()
                    }
                }
            )
            field.setOnEditorActionListener { view, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) hideKeyboard(view)
                action == EditorInfo.IME_ACTION_SEARCH
            }
        }

        private fun bindNav(view: View, row: SidebarRow.Nav) {
            view.updateLayoutParams<RecyclerView.LayoutParams> { height = dp(35) }
            val (icon, label) =
                when (row.kind) {
                    SidebarRow.Nav.Kind.NEW_CHAT -> "edit_square" to R.string.new_chat
                    SidebarRow.Nav.Kind.BOTS -> BOT_MARK to R.string.sidebar_bots
                    SidebarRow.Nav.Kind.NEW_BOT -> "add" to R.string.new_bot
                }
            bindRow(
                view,
                icon,
                context.getString(label),
                row.meta,
                dot = if (row.attention) R.color.lw_attention else null,
                selected = false
            )
            view.setOnLongClickListener(null)
            view.isLongClickable = false
            view.setOnClickListener {
                when (row.kind) {
                    SidebarRow.Nav.Kind.NEW_CHAT -> actions.startNewChat()
                    SidebarRow.Nav.Kind.BOTS -> actions.openBots()
                    SidebarRow.Nav.Kind.NEW_BOT -> row.url?.let(actions::openPath) ?: actions.openBots()
                }
            }
        }

        private fun bindChat(view: View, row: SidebarRow.ChatRow) {
            val chat = row.chat
            val dot =
                when {
                    chat.waiting -> R.color.lw_attention
                    chat.isProcessing -> R.color.lw_live
                    else -> null
                }
            bindRow(view, CHAT_ICON.takeIf { row.pinned }, chat.title, null, dot, row.selected)
            // A pin is the web's plain row; a chat's row is a little taller.
            view.updateLayoutParams<RecyclerView.LayoutParams> { height = dp(if (row.pinned) 35 else 38) }
            val state =
                when {
                    chat.waiting -> context.getString(R.string.sidebar_chat_waiting)
                    chat.isProcessing -> context.getString(R.string.sidebar_chat_answering)
                    else -> null
                }
            view.contentDescription = listOfNotNull(chat.title, chat.project, state).joinToString(", ")
            view.setOnClickListener { actions.openPath(chat.url) }
            if (chat.actions.isEmpty()) {
                view.setOnLongClickListener(null)
                view.isLongClickable = false
            } else {
                view.setOnLongClickListener {
                    actions.showChatActions(chat, it)
                    true
                }
            }
        }

        @SuppressLint("UseCompatTextViewDrawableApis")
        private fun bindRow(view: View, icon: String?, label: String, meta: String?, dot: Int?, selected: Boolean) {
            val iconView = view.findViewById<ImageView>(R.id.sidebar_row_icon)
            iconView.isVisible = icon != null
            icon?.let {
                iconView.scaleType = if (it == BOT_MARK) ImageView.ScaleType.CENTER else ImageView.ScaleType.FIT_CENTER
                iconView.setImageDrawable(
                    when (it) {
                        BOT_MARK -> botMark()
                        CHAT_ICON -> symbol(it, ink(R.color.lw_ink_muted))
                        else -> symbol(it)
                    }
                )
            }
            view.findViewById<TextView>(R.id.sidebar_row_label).apply {
                text = label
                setTextColor(ink(if (selected) R.color.lw_ink else R.color.lw_ink_muted))
            }
            view.findViewById<TextView>(R.id.sidebar_row_meta).apply {
                text = meta
                isVisible = meta != null
            }
            view.findViewById<View>(R.id.sidebar_row_dot).apply {
                isVisible = dot != null
                dot?.let { backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, it)) }
            }
            view.isSelected = selected
            view.contentDescription = listOfNotNull(label, meta).joinToString(", ")
        }
    }

    private object Diff : DiffUtil.ItemCallback<SidebarRow>() {
        override fun areItemsTheSame(oldItem: SidebarRow, newItem: SidebarRow): Boolean = when {
            oldItem is SidebarRow.ChatRow && newItem is SidebarRow.ChatRow -> oldItem.chat.url == newItem.chat.url

            oldItem is SidebarRow.Nav && newItem is SidebarRow.Nav -> oldItem.kind == newItem.kind

            oldItem is SidebarRow.Head && newItem is SidebarRow.Head -> true

            // The field stays put across the views, keeping its focus and text.
            oldItem is SidebarRow.Search && newItem is SidebarRow.Search -> true

            oldItem is SidebarRow.BotsCard && newItem is SidebarRow.BotsCard -> true

            oldItem is SidebarRow.Views && newItem is SidebarRow.Views -> true

            oldItem is SidebarRow.Decisions && newItem is SidebarRow.Decisions -> true

            oldItem is SidebarRow.BotRow && newItem is SidebarRow.BotRow -> oldItem.bot.id == newItem.bot.id

            oldItem is SidebarRow.ChatSection && newItem is SidebarRow.ChatSection ->
                oldItem.section.id == newItem.section.id

            else -> oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SidebarRow, newItem: SidebarRow): Boolean = oldItem == newItem

        // Any change rebinds the same view, never a copy of it.
        override fun getChangePayload(oldItem: SidebarRow, newItem: SidebarRow): Any = Unit
    }

    companion object {
        private const val TYPE_SWITCHER = 1
        private const val TYPE_SEARCH = 2
        private const val TYPE_SECTION = 3
        private const val TYPE_ROW = 4
        private const val TYPE_CHAT_SECTION = 5
        private const val TYPE_VIEWS = 6
        private const val TYPE_BOT = 7
        private const val TYPE_NO_BOTS = 8
        private const val TYPE_DECISIONS = 9
        private const val TYPE_HEAD = 10
        private const val TYPE_SEE_ALL = 11
        private const val LOAD_MORE_AHEAD = 8
        private const val BOT_MARK = "bot"
        private const val CHAT_ICON = "chat_bubble"
        private const val SYMBOL_WEIGHT = 400
        private const val GAP_DP = 8
        private const val PIN_GAP_DP = 2

        /** A bot's state's tone (`attention`, `negative`, `live`, `positive`, or none) as a dot's colour. */
        fun toneColor(tone: String?): Int = when (tone) {
            "attention" -> R.color.lw_attention
            "negative" -> R.color.lw_negative
            "live" -> R.color.lw_live
            "positive" -> R.color.lw_positive
            else -> R.color.lw_ink_faint
        }
    }
}
