package com.chatwithwork.app.push

import androidx.annotation.DrawableRes
import com.chatwithwork.app.R

/**
 * What a Live Update shows, from what the server said and nothing else: no
 * percentage, no estimate, no planned stages. The same state drives the
 * native card, its road ([LiveRoad]) and the status bar chip.
 */
data class LivePresentation(
    val phase: String,
    val step: String? = null,
    val steps: Int = 0,
    val trail: String? = null,
    val service: String? = null,
    val services: List<String> = emptyList(),
    val agents: Int? = null
) {
    enum class State { STARTING, WORKING, AGENTS, DECISION, INPUT, FINISHED, FAILED, STOPPED }

    /** The bot's face, as a tracker on the road and as an alpha mask for the status bar. */
    enum class Face(@param:DrawableRes val tracker: Int, @param:DrawableRes val chip: Int) {
        OPEN(R.drawable.live_bot_open, R.drawable.live_chip_open),
        CLOSED(R.drawable.live_bot_closed, R.drawable.live_chip_closed),
        RIGHT(R.drawable.live_bot_right, R.drawable.live_chip_right),
        LEFT(R.drawable.live_bot_left, R.drawable.live_chip_left),
        DOWN(R.drawable.live_bot_down, R.drawable.live_chip_down),
        UP(R.drawable.live_bot_up, R.drawable.live_chip_up),
        FOCUS(R.drawable.live_bot_focus, R.drawable.live_chip_focus),
        WIDE(R.drawable.live_bot_wide, R.drawable.live_chip_wide),
        BROW(R.drawable.live_bot_brow, R.drawable.live_chip_brow),
        HAPPY(R.drawable.live_bot_happy, R.drawable.live_chip_happy),
        FLAT(R.drawable.live_bot_flat, R.drawable.live_chip_flat)
    }

    val isWorking: Boolean get() = phase == LiveProgress.ANSWERING

    /** Steps seen, by the count or by the trail where an end message lost the count. */
    val walked: Int get() = maxOf(steps, trail?.length ?: 0)

    val state: State get() = when (phase) {
        LiveProgress.ANSWERING -> when {
            walked == 0 && step == "answer" -> State.STARTING
            (agents ?: 0) >= 2 -> State.AGENTS
            else -> State.WORKING
        }

        "approval_waiting" -> State.DECISION

        "input_requested" -> State.INPUT

        "answered" -> State.FINISHED

        "failed" -> State.FAILED

        else -> State.STOPPED
    }

    val face: Face get() = when (state) {
        State.STARTING -> Face.OPEN

        State.AGENTS, State.WORKING -> when (step) {
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

        State.FAILED -> Face.CLOSED

        State.STOPPED -> Face.FLAT
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

    /** The status bar chip: a service whose name fits, else the step's word. */
    fun chip(string: (Int) -> String): String = when (state) {
        State.STARTING -> string(R.string.live_update_chip_start)

        State.AGENTS -> string(R.string.live_update_chip_agents).format(agents)

        State.DECISION -> string(R.string.live_update_review)

        State.INPUT -> string(R.string.live_update_reply)

        State.FINISHED -> string(R.string.live_update_chip_ready)

        State.FAILED -> string(R.string.live_update_chip_failed)

        State.STOPPED -> string(R.string.live_update_chip_stopped)

        State.WORKING ->
            LiveServices.of(service)?.name?.takeIf { it.isNotEmpty() && it.length < CHIP_NAME_LENGTH }
                ?: string(LiveUpdates.chipWord(step))
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

    /** The big picture beside the words: the service at work or asking, or the agents. */
    val largeIcon: Int? get() = when (state) {
        State.AGENTS -> R.drawable.live_bot_agents
        State.WORKING, State.DECISION, State.INPUT -> LiveServices.of(service)?.logo
        else -> null
    }

    private companion object {
        const val CHIP_NAME_LENGTH = 7
        val KNOWN_PHASES = setOf("answering", "approval_waiting", "input_requested", "answered", "stopped")
    }
}
