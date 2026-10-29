package com.chatwithwork.app.sidebar

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
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
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
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
    var query = ""
        private set

    init {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        list.itemAnimator = null
        list.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val manager = recyclerView.layoutManager as LinearLayoutManager
                    if (dy > 0 &&
                        adapter.currentList.none { it is SidebarRow.BotsCard } &&
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

    fun show(sidebar: Sidebar?, current: String?, botsKnown: Boolean, view: SidebarRow.View) {
        this.sidebar = sidebar
        this.current = current
        this.botsKnown = botsKnown
        this.view = view
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
                quietTitle = context.getString(R.string.sidebar_bots_quiet)
            )
        ) { if (atTop) list.scrollToPosition(0) }
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
        color: Int = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
    ) = SymbolDrawable(context, name, sizeDp = 22f, color = color).apply {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }

    /** The bots' mark, kept at its own size so its squares stay whole pixels. */
    private fun botMark(
        color: Int = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface),
        sizeDp: Float = 20f,
        blinks: Boolean = true
    ) = BotMarkDrawable(context, color, sizeDp, blinks)

    private fun hideKeyboard(view: View) {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private inner class RowsAdapter : ListAdapter<SidebarRow, RecyclerView.ViewHolder>(Diff) {
        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is SidebarRow.Switcher -> TYPE_SWITCHER
            is SidebarRow.BotsCard -> TYPE_SWITCHER
            is SidebarRow.Views -> TYPE_VIEWS
            is SidebarRow.Decisions -> TYPE_DECISIONS
            is SidebarRow.BotRow -> TYPE_BOT
            is SidebarRow.NoBots -> TYPE_NO_BOTS
            SidebarRow.Search -> TYPE_SEARCH
            is SidebarRow.Section -> TYPE_SECTION
            is SidebarRow.Empty -> TYPE_SECTION
            is SidebarRow.ChatSection -> TYPE_CHAT_SECTION
            else -> TYPE_ROW
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout =
                when (viewType) {
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
                is SidebarRow.Switcher -> bindSwitcher(view, row.list)

                SidebarRow.Search -> Unit

                is SidebarRow.Section -> (view as TextView).text = row.title

                is SidebarRow.Empty -> (view as TextView).setText(
                    if (row.kind ==
                        SidebarRow.Empty.Kind.NO_CHATS
                    ) {
                        R.string.sidebar_no_chats
                    } else {
                        R.string.sidebar_no_matches
                    }
                )

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

        private fun bindDecisions(view: View, row: SidebarRow.Decisions) {
            val waiting = row.count > 0
            view.findViewById<ImageView>(R.id.sidebar_decisions_icon).setImageDrawable(
                SymbolDrawable(
                    context,
                    "checklist",
                    sizeDp = 20f,
                    color = ContextCompat.getColor(context, R.color.lw_ink_muted)
                )
            )
            view.findViewById<TextView>(R.id.sidebar_decisions_count).apply {
                text = row.count.toString()
                isVisible = waiting
            }
            view.findViewById<View>(R.id.sidebar_decisions_none).isVisible = !waiting
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
            val ink = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
            val chats = view.findViewById<View>(R.id.sidebar_view_chats)
            val bots = view.findViewById<View>(R.id.sidebar_view_bots)
            view.findViewById<ImageView>(R.id.sidebar_view_chats_icon)
                .setImageDrawable(SymbolDrawable(context, "chat_bubble", sizeDp = 18f, color = ink))
            view.findViewById<ImageView>(R.id.sidebar_view_bots_icon)
                .setImageDrawable(botMark(ink, sizeDp = 16f, blinks = false))
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
            view.findViewById<ImageView>(R.id.sidebar_bot_mark).setImageDrawable(botMark(blinks = !paused))
            view.findViewById<View>(R.id.sidebar_bot_live).isVisible = bot.working
            view.findViewById<TextView>(R.id.sidebar_bot_name).text = bot.name
            view.findViewById<TextView>(R.id.sidebar_bot_project).apply {
                text = bot.project
                isVisible = bot.project != null
            }
            val waiting = bot.waiting > 0
            view.findViewById<TextView>(R.id.sidebar_bot_state).apply {
                // A count is a number, set in mono like the other rows' counts; words stay in Geist.
                val words = (tag as? Typeface) ?: typeface.also { tag = it }
                text = if (waiting) bot.waiting.toString() else bot.stateLabel
                isVisible = !text.isNullOrEmpty()
                typeface = if (waiting) Typeface.MONOSPACE else words
            }
            val dot = if (waiting) R.color.lw_attention else toneColor(bot.tone)
            view.findViewById<View>(R.id.sidebar_bot_dot).apply {
                isVisible = waiting || bot.stateLabel != null
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, dot))
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
            val link = view.findViewById<TextView>(R.id.sidebar_bots_recipe)
            link.isVisible = row.url != null
            link.setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                SymbolDrawable(
                    context,
                    "arrow_forward",
                    sizeDp = 16f,
                    color = MaterialColors.getColor(link, com.google.android.material.R.attr.colorOnSurface)
                ).apply { setBounds(0, 0, intrinsicWidth, intrinsicHeight) },
                null
            )
            link.setOnClickListener { row.url?.let(actions::openPath) }
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
                symbol(
                    "search",
                    MaterialColors.getColor(field, com.google.android.material.R.attr.colorOnSurfaceVariant)
                ),
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
                        if (query.isEmpty() && text.isNotEmpty()) actions.loadAll()
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

        private fun bindSwitcher(view: View, list: Sidebar.ChatList) {
            view.findViewById<TextView>(R.id.sidebar_switcher_kicker).setText(
                if (list.isProject) R.string.sidebar_project else R.string.sidebar_showing
            )
            view.findViewById<TextView>(R.id.sidebar_switcher_name).text = list.name
            view.findViewById<TextView>(R.id.sidebar_switcher_states).isVisible = false
            view.findViewById<ImageView>(R.id.sidebar_switcher_caret).setImageResource(R.drawable.ic_expand_more)
            val symbol =
                when {
                    list.isProject -> null
                    list.kind == "all" -> "forum"
                    else -> "lock"
                }
            view.findViewById<TextView>(R.id.sidebar_switcher_mark).text =
                if (symbol == null) initialsOf(list.name) else null
            view.findViewById<ImageView>(R.id.sidebar_switcher_symbol).apply {
                isVisible = symbol != null
                scaleType = ImageView.ScaleType.FIT_CENTER
                symbol?.let {
                    setImageDrawable(
                        SymbolDrawable(
                            context,
                            it,
                            sizeDp = 18f,
                            color = ContextCompat.getColor(context, R.color.lw_ink_inverted)
                        )
                    )
                }
            }
            view.contentDescription = context.getString(R.string.sidebar_switcher_description, list.name)
            view.setOnClickListener { actions.chooseList() }
        }

        private fun bindNav(view: View, row: SidebarRow.Nav) {
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
            bindRow(view, null, chat.title, null, dot, row.selected)
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
                iconView.setImageDrawable(if (it == BOT_MARK) botMark() else symbol(it))
            }
            view.findViewById<TextView>(R.id.sidebar_row_label).text = label
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

            oldItem is SidebarRow.Switcher && newItem is SidebarRow.Switcher -> true

            oldItem is SidebarRow.BotsCard && newItem is SidebarRow.BotsCard -> true

            oldItem is SidebarRow.Views && newItem is SidebarRow.Views -> true

            oldItem is SidebarRow.Decisions && newItem is SidebarRow.Decisions -> true

            oldItem is SidebarRow.BotRow && newItem is SidebarRow.BotRow -> oldItem.bot.id == newItem.bot.id

            oldItem is SidebarRow.ChatSection && newItem is SidebarRow.ChatSection ->
                oldItem.section.id == newItem.section.id

            else -> oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SidebarRow, newItem: SidebarRow): Boolean = oldItem == newItem
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
        private const val LOAD_MORE_AHEAD = 8
        private const val BOT_MARK = "bot"

        /** A bot's state's tone (`attention`, `negative`, `live`, `positive`, or none) as a dot's colour. */
        fun toneColor(tone: String?): Int = when (tone) {
            "attention" -> R.color.lw_attention
            "negative" -> R.color.lw_negative
            "live" -> R.color.lw_live
            "positive" -> R.color.lw_positive
            else -> R.color.lw_ink_faint
        }

        /** Two initials for a project's mark: "Website relaunch" is "WR". */
        fun initialsOf(name: String): String = name
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
}
