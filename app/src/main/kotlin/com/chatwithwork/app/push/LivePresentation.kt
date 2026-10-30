package com.chatwithwork.app.push

import com.chatwithwork.app.R

/** Content-free presentation shared by the native card and status chip. */
data class LivePresentation(val phase: String, val step: String? = null) {
    val isWorking: Boolean get() = phase == LiveProgress.ANSWERING

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
    fun title(message: String?, string: (Int) -> String): String =
        if (isWorking) message?.trim()?.takeIf { it.isNotEmpty() } ?: string(headline) else string(headline)

    val action: Int get() = when (phase) {
        "approval_waiting" -> R.string.live_update_review
        "input_requested" -> R.string.live_update_reply
        "answered" -> R.string.live_update_read
        else -> R.string.live_update_open
    }

    val color: Int get() = when (phase) {
        "answered" -> R.color.lw_positive_ink
        "approval_waiting", "input_requested" -> R.color.lw_attention_ink
        "failed" -> R.color.lw_negative_ink
        "stopped" -> R.color.lw_ink_muted
        "answering" -> R.color.lw_live_ink
        else -> R.color.lw_ink_muted
    }

    val symbol: String get() = when (phase) {
        "answered" -> "check_circle"

        "approval_waiting" -> "front_hand"

        "input_requested" -> "question_answer"

        "failed" -> "error"

        "stopped" -> "stop_circle"

        "answering" -> when (step) {
            "search" -> "search"
            "list" -> "folder"
            "read" -> "description"
            "use" -> "build"
            "plan" -> "checklist"
            "run" -> "terminal"
            "ask" -> "question_answer"
            "write" -> "edit_note"
            "answer" -> "more_horiz"
            else -> "more_horiz"
        }

        else -> "sync_problem"
    }
}
