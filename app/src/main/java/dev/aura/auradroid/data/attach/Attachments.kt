package dev.aura.auradroid.data.attach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

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

/**
 * Turns a picked or photographed [Uri] into something a model can be given.
 *
 * Two paths, because models take two things. An image is downscaled and sent as
 * an inline data URL, the format every OpenAI-compatible vision endpoint
 * accepts. Anything textual is read as text and inlined into the message, which
 * works with every model rather than only the vision ones.
 *
 * The downscaling is not a nicety. A modern phone camera produces 4–12 MB per
 * shot; base64 inflates that by a third, and it is uploaded over mobile data on
 * every subsequent turn of the conversation. 1024px on the long edge is past
 * what these models actually see and costs about 100 KB.
 */
object Attachments {

    suspend fun read(context: Context, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: "attachment"
        val mime = resolver.getType(uri) ?: guessMime(name)

        runCatching {
            when {
                mime.startsWith("image/") -> readImage(context, uri, name, mime)
                isTextual(mime, name) -> readText(context, uri, name, mime)
                else -> Attachment(
                    name = name,
                    mimeType = mime,
                    kind = AttachmentKind.BINARY,
                    sizeBytes = sizeOf(context, uri),
                )
            }
        }.getOrNull()
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

    private fun readImage(context: Context, uri: Uri, name: String, mime: String): Attachment? {
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
