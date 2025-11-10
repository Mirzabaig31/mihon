package eu.kanade.tachiyomi.ui.reader.model

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

open class ReaderPage(
    index: Int,
    url: String = "",
    imageUrl: String? = null,
    var stream: (() -> InputStream)? = null,
) : Page(index, url, imageUrl, null) {

    open lateinit var chapter: ReaderChapter

    /**
     * Stream function for translated page image.
     * If set, this will be used instead of [stream] when displaying the page.
     */
    var translatedStream: (() -> InputStream)? = null

    /**
     * Returns the stream to use for displaying this page.
     * Prioritizes translated stream if available, otherwise falls back to original stream.
     */
    fun getDisplayStream(): (() -> InputStream)? {
        return translatedStream ?: stream
    }
}
