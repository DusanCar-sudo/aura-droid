package dev.aura.auradroid.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** A fenced block pulled out of a reply, with somewhere sensible to save it. */
data class CodeBlock(
    val language: String,
    val code: String,
    /** Filename the agent named in the fence or the prose, else one derived. */
    val fileName: String,
)

/**
 * Finds code the agent produced so it can be saved rather than retyped.
 *
 * A reply containing a whole HTML page is useless if the only way out of it is
 * selecting text on a phone screen. Anything fenced is treated as a file the
 * user might want, and the extension follows the fence language so it opens in
 * the right thing.
 */
object CodeBlocks {

    private val FENCE = Regex("```([\\w.+-]*)\\s*\\n([\\s\\S]*?)```")

    private val EXTENSIONS = mapOf(
        "html" to "html", "htm" to "html", "xml" to "xml", "svg" to "svg",
        "css" to "css", "javascript" to "js", "js" to "js", "typescript" to "ts",
        "ts" to "ts", "tsx" to "tsx", "jsx" to "jsx", "json" to "json",
        "kotlin" to "kt", "kt" to "kt", "java" to "java", "python" to "py",
        "py" to "py", "bash" to "sh", "sh" to "sh", "shell" to "sh",
        "yaml" to "yml", "yml" to "yml", "sql" to "sql", "rust" to "rs",
        "go" to "go", "c" to "c", "cpp" to "cpp", "markdown" to "md", "md" to "md",
    )

    fun extract(content: String): List<CodeBlock> =
        FENCE.findAll(content).mapIndexedNotNull { i, m ->
            val lang = m.groupValues[1].trim().lowercase()
            val code = m.groupValues[2].trimEnd()
            // A one-line fence is almost always an inline command being shown,
            // not a file worth saving; offering a download for it is noise.
            if (code.count { it == '\n' } < 2) return@mapIndexedNotNull null
            CodeBlock(
                language = lang.ifEmpty { "text" },
                code = code,
                fileName = nameFor(lang, code, i),
            )
        }.toList()

    private fun nameFor(lang: String, code: String, index: Int): String {
        val ext = EXTENSIONS[lang] ?: guessExtension(code) ?: "txt"
        // An HTML page is nearly always the page, so give it the name a
        // browser expects rather than block-1.html.
        if (ext == "html") return "index.html"
        val stamp = System.currentTimeMillis().toString().takeLast(5)
        return if (index == 0) "aura-$stamp.$ext" else "aura-$stamp-${index + 1}.$ext"
    }

    /** An unlabelled fence still gets the right extension if it is obvious. */
    private fun guessExtension(code: String): String? {
        val head = code.trimStart().take(200).lowercase()
        return when {
            head.startsWith("<!doctype html") || head.startsWith("<html") -> "html"
            head.startsWith("<?xml") -> "xml"
            head.startsWith("<svg") -> "svg"
            head.startsWith("#!/bin/bash") || head.startsWith("#!/bin/sh") -> "sh"
            head.startsWith("{") && head.contains("\":") -> "json"
            else -> null
        }
    }

    /**
     * Write into the phone's Downloads folder.
     *
     * MediaStore on Android 10 and later, which needs no permission and puts
     * the file where every file manager and browser already looks. Older
     * versions fall back to the legacy path, which does need the storage
     * permission — the caller reports failure rather than this throwing.
     */
    fun saveToDownloads(context: Context, fileName: String, content: String): String? {
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeFor(fileName))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Downloads/$fileName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, fileName).writeText(content)
            "Downloads/$fileName"
        }
      } catch (_: Exception) {
        null
      }
    }

    fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "")) {
        "html" -> "text/html"
        "css" -> "text/css"
        "js" -> "text/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "xml" -> "text/xml"
        "md" -> "text/markdown"
        else -> "text/plain"
    }
}
