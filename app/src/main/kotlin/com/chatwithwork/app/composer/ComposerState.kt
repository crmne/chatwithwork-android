package com.chatwithwork.app.composer

import kotlinx.serialization.Serializable

/**
 * What the web composer is now, as `bridge--composer` sends it with `connect`
 * and `state` (docs/server-contract.md, "The composer"). The native composer
 * draws exactly this: the web one, hidden in the page, keeps every rule
 * (locks, who may steer, the queue, the model, web search).
 */
@Serializable
data class ComposerState(
    val overlay: Boolean = false,
    val placeholder: String = "",
    /** The words the web composer already holds (shared in, or a restored draft), sent on `connect`. */
    val text: String? = null,
    val locked: Boolean = false,
    val lockReason: String? = null,
    val working: Boolean = false,
    val stopping: Boolean = false,
    val models: List<Model> = emptyList(),
    val webSearch: WebSearch? = null,
    val dictation: Dictation? = null,
    val csrfToken: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val acceptsFiles: Boolean = true
) {
    @Serializable
    data class Model(
        val name: String = "",
        val rate: String? = null,
        val selected: Boolean = false,
        val searchesWeb: Boolean = false
    )

    @Serializable
    data class WebSearch(val on: Boolean = false, val disabled: Boolean = false, val note: String? = null)

    @Serializable
    data class Dictation(val url: String)

    @Serializable
    data class Attachment(
        val id: String,
        val name: String = "file",
        val size: String? = null,
        val contentType: String? = null,
        val uploading: Boolean = false,
        val progress: Double = 100.0
    )

    val selectedModel: Model? get() = models.firstOrNull { it.selected }

    val uploading: Boolean get() = attachments.any { it.uploading }

    /** Send goes out with words or files, never while a file still uploads (the web would refuse). */
    fun canSend(text: String): Boolean = !locked && !uploading && (text.isNotBlank() || attachments.isNotEmpty())

    /** Stop shows for as long as an answer runs, beside Send once there's something to steer with. */
    fun showsStop(): Boolean = working

    /** Send shows unless an answer runs with nothing typed or attached, when Stop stands alone. */
    fun showsSend(text: String): Boolean = !working || text.isNotBlank() || attachments.isNotEmpty()

    /** Stop can't be pressed again while the answer is already stopping. */
    val canStop: Boolean get() = working && !stopping
}

/** One file for `attach`: its name, media type, and bytes as base64. */
@Serializable
data class ComposerFile(val name: String, val type: String, val data: String)
