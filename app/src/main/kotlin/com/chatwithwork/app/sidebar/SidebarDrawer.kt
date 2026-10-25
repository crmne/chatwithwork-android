package com.chatwithwork.app.sidebar

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
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

        fun chooseList()

        fun showWaiting()

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

    fun show(sidebar: Sidebar?, current: String?, botsKnown: Boolean) {
        this.sidebar = sidebar
        this.current = current
        this.botsKnown = botsKnown
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
                botsKnown = botsKnown
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

    private fun hideKeyboard(view: View) {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private inner class RowsAdapter : ListAdapter<SidebarRow, RecyclerView.ViewHolder>(Diff) {
        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is SidebarRow.Switcher -> TYPE_SWITCHER
            SidebarRow.Search -> TYPE_SEARCH
            is SidebarRow.Section -> TYPE_SECTION
            is SidebarRow.Empty -> TYPE_SECTION
            else -> TYPE_ROW
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout =
                when (viewType) {
                    TYPE_SWITCHER -> R.layout.sidebar_switcher
                    TYPE_SEARCH -> R.layout.sidebar_search
                    TYPE_SECTION -> R.layout.sidebar_section
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
                    SidebarRow.Nav.Kind.WAITING -> "back_hand" to R.string.sidebar_waiting
                    SidebarRow.Nav.Kind.BOTS -> "memory" to R.string.sidebar_bots
                }
            bindRow(
                view,
                icon,
                context.getString(label),
                row.meta,
                dot = if (row.attention) R.color.lw_attention else null,
                selected = false
            )
            view.setOnClickListener {
                when (row.kind) {
                    SidebarRow.Nav.Kind.NEW_CHAT -> actions.startNewChat()
                    SidebarRow.Nav.Kind.WAITING -> actions.showWaiting()
                    SidebarRow.Nav.Kind.BOTS -> actions.openBots()
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
        }

        @SuppressLint("UseCompatTextViewDrawableApis")
        private fun bindRow(view: View, icon: String?, label: String, meta: String?, dot: Int?, selected: Boolean) {
            val iconView = view.findViewById<ImageView>(R.id.sidebar_row_icon)
            iconView.isVisible = icon != null
            icon?.let { iconView.setImageDrawable(symbol(it)) }
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
            else -> oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: SidebarRow, newItem: SidebarRow): Boolean = oldItem == newItem
    }

    companion object {
        private const val TYPE_SWITCHER = 1
        private const val TYPE_SEARCH = 2
        private const val TYPE_SECTION = 3
        private const val TYPE_ROW = 4
        private const val LOAD_MORE_AHEAD = 8

        /** Two initials for a project's mark: "Website relaunch" is "WR". */
        fun initialsOf(name: String): String = name
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
    }
}
