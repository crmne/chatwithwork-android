package com.chatwithwork.app.push

import androidx.annotation.DrawableRes
import com.chatwithwork.app.R

/**
 * What a Live Update shows, from what the server said and nothing else: no
 * percentage, no estimate, no planned stages. The same state drives the
 * card ([LiveCard]), its road ([LiveArtwork]) and the status bar icon.
 */
data class LivePresentation(
    val phase: String,
    val step: String? = null,
    val steps: Int = 0,
    val trail: String? = null,
    val service: String? = null,
    val services: List<String> = emptyList(),
    val agents: Int? = null,
    val pace: String? = null
) {
    enum class State { STARTING, WORKING, AGENTS, WRITING, DECISION, INPUT, FINISHED, FAILED, STOPPED, QUIET }

    /** The small thing the bot holds up beside it for a step. */
    enum class Tool { SEARCH, READ, LIST, USE, PLAN, RUN, WRITE, ASK }

    /** One step seen, as tall as the time it took where the server says. [refused]: it failed or was turned down. */
    data class Tick(val height: Float, val refused: Boolean)

    /** The bot's face, and it as an alpha mask for the status bar. */
    enum class Face(@param:DrawableRes val chip: Int) {
        OPEN(R.drawable.live_chip_open),
        CLOSED(R.drawable.live_chip_closed),
        RIGHT(R.drawable.live_chip_right),
        LEFT(R.drawable.live_chip_left),
        DOWN(R.drawable.live_chip_down),
        UP(R.drawable.live_chip_up),
        FOCUS(R.drawable.live_chip_focus),
        WIDE(R.drawable.live_chip_wide),
        BROW(R.drawable.live_chip_brow),
        HAPPY(R.drawable.live_chip_happy),
        FLAT(R.drawable.live_chip_flat)
    }

    val isWorking: Boolean get() = phase == LiveProgress.ANSWERING

    /** Steps seen, by the count or by the trail where an end message lost the count. */
    val walked: Int get() = maxOf(steps, trail?.length ?: 0)

    val state: State get() = when (phase) {
        LiveProgress.ANSWERING -> when {
            walked == 0 && step == "answer" -> State.STARTING
            (agents ?: 0) >= 2 -> State.AGENTS
            step == "write" -> State.WRITING
            else -> State.WORKING
        }

        "approval_waiting" -> State.DECISION

        "input_requested" -> State.INPUT

        "answered" -> State.FINISHED

        "failed" -> State.FAILED

        "stopped" -> State.STOPPED

        else -> State.QUIET
    }

    val face: Face get() = when (state) {
        State.STARTING -> Face.OPEN

        State.AGENTS, State.WORKING, State.WRITING -> when (step) {
            "search" -> Face.RIGHT
            "list" -> Face.LEFT
            "read", "write" -> Face.DOWN
            "use", "run" -> Face.FOCUS
            "ask" -> Face.BROW
            else -> Face.UP
        }

        State.DECISION -> Face.WIDE

        State.INPUT -> Face.BROW

        State.FINISHED -> Face.HAPPY

        // Calm on purpose: a failure may be serious, and x eyes would joke about it.
        State.FAILED, State.QUIET -> Face.CLOSED

        State.STOPPED -> Face.FLAT
    }

    /** What the bot holds up while it works on a step. */
    val tool: Tool? get() = when (state) {
        State.WORKING, State.WRITING -> when (step) {
            "search" -> Tool.SEARCH
            "list" -> Tool.LIST
            "read" -> Tool.READ
            "use" -> Tool.USE
            "plan" -> Tool.PLAN
            "run" -> Tool.RUN
            "write" -> Tool.WRITE
            "ask" -> Tool.ASK
            else -> null
        }

        else -> null
    }

    val isWaiting: Boolean get() = state == State.DECISION || state == State.INPUT

    /** The card ends in a band when it needs the person, or has gone wrong. */
    val hasBand: Boolean get() = isWaiting || state == State.FAILED

    /** The road's ticks: the trail's, or as many even ones as steps where an older server sends no trail. */
    val ticks: List<Tick> get() {
        val letters = trail ?: return List(minOf(steps, MAX_TICKS)) { Tick(EVEN_HEIGHT, false) }
        val heights =
            pace?.takeIf { it.length == letters.length }?.map { PACE_HEIGHTS[it] }?.takeIf { null !in it }
        return letters.mapIndexed { index, letter ->
            Tick(heights?.get(index) ?: EVEN_HEIGHT, letter.isUpperCase())
        }.takeLast(MAX_TICKS)
    }

    /** The trail is drawn compressed, with its origin scrolled off, from here. */
    val isDeep: Boolean get() = ticks.size >= DEEP_AFTER

    /** Services behind the bot, oldest first. The one it's in now is the words' or the held card's. */
    val stops: List<String> get() =
        services.distinct().filter { it != service && LiveServices.of(it) != null }.takeLast(MAX_STOPS)

    /** Bots drawn side by side on a fork, and the rest, which read as `+n`. */
    val agentBots: Int get() = minOf(agents ?: 0, MAX_AGENT_BOTS)
    val moreAgents: Int get() = maxOf((agents ?: 0) - MAX_AGENT_BOTS, 0)

    /** The words before and after [service]'s logo: "Searching" and "Drive", or null when they don't name it. */
    fun split(message: String): Pair<String, String>? {
        val name = LiveServices.of(service)?.name?.takeIf { it.isNotEmpty() } ?: return null
        return listOf(" in $name", " $name").firstNotNullOfOrNull { suffix ->
            message.takeIf { it.endsWith(suffix) && it.length > suffix.length }
                ?.let { it.removeSuffix(suffix) to name }
        }
    }

    val headline: Int get() = when (phase) {
        "answered" -> R.string.live_update_ready

        "approval_waiting" -> R.string.live_update_approval

        "input_requested" -> R.string.live_update_input

        "failed" -> R.string.live_update_failed

        "stopped" -> R.string.live_update_stopped

        "answering" -> when (step) {
            "search" -> R.string.live_update_searching
            "list" -> R.string.live_update_browsing
            "read" -> R.string.live_update_reading
            "use" -> R.string.live_update_using_tools
            "plan" -> R.string.live_update_planning
            "run" -> R.string.live_update_running
            "ask" -> R.string.live_update_asking
            "write" -> R.string.live_update_writing
            else -> R.string.live_update_answering
        }

        else -> R.string.live_update_unknown
    }

    /** Only a working step uses the server's content-free activity label. */
    fun title(message: String?, string: (Int) -> String): String {
        val named = LiveServices.of(service)?.name?.takeIf { it.isNotEmpty() }
        return when {
            state == State.STARTING -> string(R.string.live_update_starting)
            isWorking -> message?.trim()?.takeIf { it.isNotEmpty() } ?: string(headline)
            state == State.DECISION && named != null -> string(R.string.live_update_approval_in).format(named)
            else -> string(headline)
        }
    }

    /** The one button a card carries, when it needs the person. It only ever opens the chat. */
    val action: Int? get() = when (state) {
        State.DECISION -> R.string.live_update_review
        State.INPUT -> R.string.live_update_reply
        State.FAILED -> R.string.live_update_open
        else -> if (phase in KNOWN_PHASES) null else R.string.live_update_open
    }

    val color: Int get() = when (phase) {
        "answered" -> R.color.lw_positive_ink
        "approval_waiting", "input_requested" -> R.color.lw_attention_ink
        "failed" -> R.color.lw_negative_ink
        "stopped" -> R.color.lw_ink_muted
        "answering" -> R.color.lw_live_ink
        else -> R.color.lw_ink_muted
    }

    private companion object {
        const val MAX_TICKS = 48
        const val DEEP_AFTER = 24
        const val MAX_STOPS = 5
        const val MAX_AGENT_BOTS = 3
        const val EVEN_HEIGHT = 14f
        val PACE_HEIGHTS = mapOf('1' to 6f, '2' to 13f, '3' to 21f, '4' to 29f)
        val KNOWN_PHASES = setOf("answering", "approval_waiting", "input_requested", "answered", "stopped")
    }
}
