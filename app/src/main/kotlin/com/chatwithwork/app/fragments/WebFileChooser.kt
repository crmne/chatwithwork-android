package com.chatwithwork.app.fragments

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.core.content.FileProvider
import com.chatwithwork.app.R
import com.chatwithwork.app.bridge.ActionSheet
import com.chatwithwork.app.bridge.ComposerHost
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A page's file input (the web composer's + menu, Attach files) on this
 * app's own pages: a sheet of where the files come from, as the native
 * composer's "+" offered: Camera, Photos (Android's photo picker, no
 * permission), Files, and Scan (ML Kit's document scanner, one PDF). An
 * input that takes only pictures offers Camera and Photos. What's picked goes
 * back to the page's input, which uploads it like any file it takes.
 */
object WebFileChooser {
    enum class Source { CAMERA, PHOTOS, FILES, SCAN }

    /**
     * Which sources an input offers: everything for one that takes any file,
     * the camera and photos for pictures only, and null (Hotwire Native's own
     * chooser) for anything narrower, which the sheet's pickers can't keep to.
     */
    fun sources(acceptTypes: List<String>): List<Source>? {
        val types = acceptTypes.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        return when {
            types.isEmpty() || types.any { it == "*/*" } -> Source.entries
            types.all { it.startsWith("image/") } -> listOf(Source.CAMERA, Source.PHOTOS)
            else -> null
        }
    }

    /** Shows the sheet; true when it did, so the page waits for its answer. */
    fun show(
        context: Context,
        host: ComposerHost,
        params: FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ): Boolean {
        val sources = sources(params.acceptTypes?.toList().orEmpty()) ?: return false
        val multiple = params.mode == FileChooserParams.MODE_OPEN_MULTIPLE
        var chosen = false
        var answered = false

        // The input takes exactly one answer, whatever comes back.
        fun answer(uris: List<Uri>) {
            if (answered) return
            answered = true
            val picked = if (multiple) uris else uris.take(1)
            callback.onReceiveValue(picked.takeIf { it.isNotEmpty() }?.toTypedArray())
        }

        val dialog =
            ActionSheet.show(
                context,
                null,
                sources.map { source ->
                    when (source) {
                        Source.CAMERA -> ActionSheet.Action(
                            context.getString(R.string.composer_camera),
                            image = "photo_camera"
                        )

                        Source.PHOTOS -> ActionSheet.Action(
                            context.getString(R.string.composer_photos),
                            image = "photo_library"
                        )

                        Source.FILES -> ActionSheet.Action(
                            context.getString(R.string.composer_files),
                            image = "attach_file"
                        )

                        Source.SCAN -> ActionSheet.Action(
                            context.getString(R.string.composer_scan),
                            image = "document_scanner"
                        )
                    }
                }
            ) { index ->
                chosen = true
                when (sources[index]) {
                    Source.CAMERA -> host.takePhoto { answer(listOfNotNull(it)) }

                    Source.PHOTOS -> host.pickPhotos(::answer)

                    Source.FILES -> host.pickFiles(::answer)

                    Source.SCAN -> host.scanDocument { uri ->
                        answer(listOfNotNull(uri?.let { shareable(context, it) }))
                    }
                }
            }
        // Closed without a choice: the input hears nothing was picked, or it
        // wouldn't open again. A choice dismisses the sheet first, so wait for it.
        dialog.setOnDismissListener { Handler(Looper.getMainLooper()).post { if (!chosen) answer(emptyList()) } }
        return true
    }

    /**
     * The scanner's PDF, a file in its own folder, copied where this app's
     * FileProvider shares from, named for when it was scanned.
     */
    private fun shareable(context: Context, uri: Uri): Uri? = runCatching {
        val folder = File(context.cacheDir, "camera").apply { mkdirs() }
        val name = "Scan ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date())}.pdf"
        val file = File(folder, name)
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            ?: return null
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }.getOrNull()
}
