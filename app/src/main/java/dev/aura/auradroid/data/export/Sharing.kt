package dev.aura.auradroid.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands an export to whatever the user wants to open it with.
 *
 * A file rather than extra text in the intent: a long conversation exceeds
 * what many receivers accept as an EXTRA_TEXT, and arrives truncated with no
 * warning. Written into cacheDir and served through a FileProvider, so it is
 * readable by the chosen app for the life of the share and by nothing else —
 * a conversation can contain anything the user discussed, and it should not be
 * left sitting in shared storage.
 */
object Sharing {

    fun shareText(context: Context, fileName: String, content: String, subject: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // One file per name, overwritten: re-exporting the same conversation
        // should not accumulate copies in the cache.
        val file = File(dir, fileName).apply { writeText(content) }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (fileName.endsWith(".json")) "application/json" else "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            // Some receivers only read text; give them the content too, so the
            // common case of pasting into another agent works either way.
            putExtra(Intent.EXTRA_TEXT, content.take(100_000))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(send, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
