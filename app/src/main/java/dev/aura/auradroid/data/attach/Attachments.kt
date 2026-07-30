package dev.aura.auradroid.data.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** What kind of thing was attached, which decides how it reaches the model. */
enum class AttachmentKind { IMAGE, TEXT, BINARY }

/**
 * A file the user attached to a message.
 *
 * Held in memory rather than copied into app storage: an attachment lives from
 * the moment it is picked to the moment the message is sent, and writing every
 * photo someone considered attaching into the app's directory would leave a
 * pile nobody knows about and nothing ever deletes.
 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val kind: AttachmentKind,
    /** Text content, for anything readable as text. */
    val text: String? = null,
    /** JPEG bytes, base64, already downscaled — for images. */
    val base64: String? = null,
    val sizeBytes: Long = 0,
    /** True when [text] is only the opening of a longer file. */
    val truncated: Boolean = false,
)

/** What came of trying to read an attachment. */
sealed interface ReadResult {
    /** One file can become several: a PDF arrives as a page image each. */
    data class Ok(val attachments: List<Attachment>) : ReadResult

    /** Said in words the user can act on, never swallowed. */
    data class Failed(val reason: String) : ReadResult
}

/**
 * Turns a picked or photographed [Uri] into something a model can be given.
 *
 * Everything ends up as one of two things, because that is all a model takes:
 * an image sent as an inline data URL, which every OpenAI-compatible vision
 * endpoint accepts, or text inlined into the message, which works with every
 * model rather than only the vision ones.
 *
 * Getting there differs by format. Photos and pictures downscale. Source and
 * config files are read as they are. A PDF is rendered to page images by the
 * platform and sent as pictures — a phone has no text extractor, and rendering
 * has the side benefit of working on scanned documents, where extraction would
 * find nothing. Word, Excel and PowerPoint files are ZIP archives of XML, so
 * their text is unpacked directly. None of this needs a third-party library.
 *
 * The downscaling is not a nicety. A modern phone camera produces 4–12 MB per
 * shot; base64 inflates that by a third, and it is uploaded over mobile data on
 * every subsequent turn of the conversation. 1024px on the long edge is past
 * what these models actually see and costs about 100 KB.
 */
object Attachments {

    /**
     * Read [uri], or say why not.
     *
     * Every failure returns a reason rather than null. The previous version
     * wrapped the whole thing in runCatching and discarded the exception, so a
     * file that could not be read produced a picker that closed and nothing
     * else — no attachment, no message, nothing to act on.
     */
    suspend fun read(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: "attachment"
        val mime = resolver.getType(uri) ?: guessMime(name)
        val extension = name.substringAfterLast('.', "").lowercase()

        try {
            when {
                mime.startsWith("image/") ->
                    readImage(context, uri, name)
                        ?.let { ReadResult.Ok(listOf(it)) }
                        ?: ReadResult.Failed("$name could not be decoded as an image.")

                mime == "application/pdf" || extension == "pdf" ->
                    readPdf(context, uri, name)

                extension in OFFICE_EXTENSIONS ->
                    readOffice(context, uri, name, extension)

                isTextual(mime, name) ->
                    readText(context, uri, name, mime)
                        ?.let { ReadResult.Ok(listOf(it)) }
                        ?: ReadResult.Failed("$name could not be opened.")

                else -> ReadResult.Failed(
                    "Cannot read $name. Attach an image, a PDF, an Office document, " +
                        "or a text file.",
                )
            }
        } catch (e: OutOfMemoryError) {
            // Its own branch because it is not an Exception and would otherwise
            // take the whole app down on a large scan.
            ReadResult.Failed("$name is too large to open on this phone.")
        } catch (e: Exception) {
            ReadResult.Failed("Could not read $name: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** Where a photo about to be taken should be written. */
    fun newCameraTarget(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        // Timestamped, so a burst of photos in one conversation does not
        // overwrite itself before any of them is sent.
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    /**
     * Delete camera captures older than an hour.
     *
     * The shot is read into the message and the file has no further use, but
     * deleting it immediately races the system camera still writing it. A sweep
     * on the next launch is simpler than trying to time that.
     */
    fun sweepCameraCache(context: Context) {
        val dir = File(context.cacheDir, CAMERA_DIR)
        if (!dir.isDirectory) return
        val cutoff = System.currentTimeMillis() - CAMERA_KEEP_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /**
     * Render a PDF to page images with the platform's own renderer.
     *
     * Pictures rather than extracted text, which sounds backwards until you
     * consider what people photograph and forward on a phone: scans, invoices,
     * forms. Those carry no text layer at all, and an extractor returns an
     * empty string for them while a vision model reads them fine. Rendering
     * also keeps tables and layout, which extraction flattens into noise.
     *
     * Capped at four pages. Each one is an image in the request, and a
     * forty-page document would cost more than anyone intends to spend asking
     * what a letter says.
     */
    private fun readPdf(context: Context, uri: Uri, name: String): ReadResult {
        // Copied to a real file first. PdfRenderer needs a seekable descriptor,
        // and plenty of providers hand back a pipe, which it rejects outright.
        val scratch = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
            .let { File(it, "pdf_${System.currentTimeMillis()}.pdf") }
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                scratch.outputStream().use { input.copyTo(it) }
            } ?: return ReadResult.Failed("$name could not be opened.")

            ParcelFileDescriptor.open(scratch, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount == 0) {
                        return ReadResult.Failed("$name has no pages.")
                    }
                    val wanted = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    val pages = ArrayList<Attachment>(wanted)

                    for (index in 0 until wanted) {
                        renderer.openPage(index).use { page ->
                            val scale = PDF_EDGE.toFloat() / maxOf(page.width, page.height)
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            // Pages render with a transparent background, and
                            // transparent flattens to black in a JPEG — black
                            // text on black. White first.
                            Canvas(bitmap).drawColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val bytes = ByteArrayOutputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, PDF_QUALITY, out)
                                out.toByteArray()
                            }
                            bitmap.recycle()

                            pages += Attachment(
                                name = if (wanted == 1) name else "$name p${index + 1}",
                                mimeType = "image/jpeg",
                                kind = AttachmentKind.IMAGE,
                                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                sizeBytes = bytes.size.toLong(),
                                truncated = renderer.pageCount > wanted,
                            )
                        }
                    }
                    return ReadResult.Ok(pages)
                }
            }
        } finally {
            scratch.delete()
        }
    }

    /**
     * Pull the words out of a .docx, .xlsx or .pptx.
     *
     * All three are ZIP archives of XML, so this needs no library — open the
     * archive, take the parts that hold text, and strip the markup. Formatting
     * is discarded on purpose: the model wants the content, and the run-level
     * markup in these formats splits a single sentence across a dozen tags.
     */
    private fun readOffice(
        context: Context,
        uri: Uri,
        name: String,
        extension: String,
    ): ReadResult {
        val parts = sortedMapOf<String, String>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                var budget = MAX_TEXT_BYTES
                while (entry != null && budget > 0) {
                    if (wantsEntry(extension, entry.name)) {
                        val xml = zip.readBytes().toString(Charsets.UTF_8)
                        budget -= xml.length
                        parts[entry.name] = xml
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: return ReadResult.Failed("$name could not be opened.")

        if (parts.isEmpty()) {
            return ReadResult.Failed(
                "$name has no readable text — it may be an older .doc/.xls, " +
                    "which is a different format entirely.",
            )
        }

        // Slides and sheets are numbered, and "slide10" sorts before "slide2"
        // as a string, which silently reorders a deck.
        val text = parts.entries
            .sortedBy { numberIn(it.key) }
            .joinToString("\n\n") { (path, xml) -> sectionFor(extension, path, xml) }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        if (text.isBlank()) {
            return ReadResult.Failed("$name appears to contain no text.")
        }

        return ReadResult.Ok(
            listOf(
                Attachment(
                    name = name,
                    mimeType = "text/plain",
                    kind = AttachmentKind.TEXT,
                    text = text.take(MAX_TEXT_BYTES),
                    sizeBytes = text.length.toLong(),
                    truncated = text.length > MAX_TEXT_BYTES,
                ),
            ),
        )
    }

    private fun wantsEntry(extension: String, path: String): Boolean = when (extension) {
        "docx" -> path == "word/document.xml"
        "pptx" -> path.startsWith("ppt/slides/slide") && path.endsWith(".xml")
        "xlsx" -> path == "xl/sharedStrings.xml" ||
            (path.startsWith("xl/worksheets/sheet") && path.endsWith(".xml"))
        else -> false
    }

    private fun sectionFor(extension: String, path: String, xml: String): String {
        val body = stripXml(
            xml
                // Paragraph and row ends become line breaks before the tags go,
                // or the whole document arrives as one unbroken line.
                .replace("</w:p>", "\n")
                .replace("</a:p>", "\n")
                .replace("</row>", "\n")
                .replace("<w:tab/>", "\t")
                .replace("</si>", "\n"),
        )
        return if (extension == "pptx") {
            "--- Slide ${numberIn(path)} ---\n$body"
        } else {
            body
        }
    }

    /** Tag soup to words, with the XML entities put back. */
    private fun stripXml(xml: String): String = xml
        .replace(Regex("<[^>]+>"), " ")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        // Ampersand last, or the replacements above would be re-decoded.
        .replace("&amp;", "&")
        .replace(Regex("[ \\t]{2,}"), " ")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    private fun numberIn(path: String): Int =
        Regex("(\\d+)").findAll(path).lastOrNull()?.value?.toIntOrNull() ?: 0

    private fun readImage(context: Context, uri: Uri, name: String): Attachment? {
        val resolver = context.contentResolver

        // Two passes: measure first, then decode with inSampleSize, so a 12
        // megapixel photo never lands in memory at full size. Decoding it
        // outright is the classic way to OutOfMemory on a mid-range phone.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scaled = scaleToBound(decoded)
        val upright = applyExifRotation(context, uri, scaled)
        val bytes = ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        if (upright !== decoded) decoded.recycle()

        return Attachment(
            name = name,
            mimeType = "image/jpeg",
            kind = AttachmentKind.IMAGE,
            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            sizeBytes = bytes.size.toLong(),
        )
    }

    private fun readText(context: Context, uri: Uri, name: String, mime: String): Attachment? {
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            // Bounded read: a 40 MB log would otherwise be pulled into memory in
            // full only to have all but the first few thousand characters
            // thrown away a line later.
            val buffer = ByteArray(MAX_TEXT_BYTES + 1)
            var read = 0
            while (read < buffer.size) {
                val n = input.read(buffer, read, buffer.size - read)
                if (n <= 0) break
                read += n
            }
            String(buffer, 0, minOf(read, MAX_TEXT_BYTES), Charsets.UTF_8) to (read > MAX_TEXT_BYTES)
        } ?: return null

        return Attachment(
            name = name,
            mimeType = mime,
            kind = AttachmentKind.TEXT,
            text = raw.first,
            sizeBytes = raw.first.length.toLong(),
            truncated = raw.second,
        )
    }

    /**
     * Photos carry their rotation in EXIF rather than in the pixels.
     *
     * Skipping this sends every landscape-held shot to the model sideways,
     * which is exactly the case where someone photographs a screen or a page
     * and asks what it says.
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (degrees == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleToBound(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= MAX_EDGE) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    private fun sizeOf(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
            } ?: 0L
        }.getOrDefault(0L)

    /**
     * Whether this is worth reading as text.
     *
     * The MIME type alone is not enough: pickers report `application/octet-stream`
     * for plenty of source files, and Kotlin, JSON and YAML all matter more here
     * than whatever the resolver decided. So the extension gets a say too.
     */
    private fun isTextual(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime in TEXTUAL_MIMES) return true
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.isNotEmpty() && extension in TEXTUAL_EXTENSIONS
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private const val MAX_EDGE = 1024
    private const val JPEG_QUALITY = 80
    private const val MAX_TEXT_BYTES = 200_000
    private const val CAMERA_DIR = "camera"
    private const val CAMERA_KEEP_MS = 60 * 60 * 1000L

    /** Larger than a photo: small print in a document has to survive. */
    private const val PDF_EDGE = 1400
    private const val PDF_QUALITY = 85
    private const val MAX_PDF_PAGES = 4

    private val OFFICE_EXTENSIONS = setOf("docx", "xlsx", "pptx")

    private val TEXTUAL_MIMES = setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/x-yaml",
        "application/x-sh",
        "application/sql",
    )

    private val TEXTUAL_EXTENSIONS = setOf(
        "txt", "md", "markdown", "log", "csv", "tsv", "json", "xml", "yaml", "yml",
        "toml", "ini", "cfg", "conf", "properties", "env", "gradle", "kts",
        "kt", "java", "py", "js", "ts", "tsx", "jsx", "rs", "go", "rb", "php",
        "c", "h", "cpp", "hpp", "cs", "swift", "sh", "bash", "zsh", "sql",
        "html", "htm", "css", "scss", "vue", "svelte", "dart", "lua", "r",
        "gitignore", "dockerfile", "makefile", "diff", "patch",
    )
}
