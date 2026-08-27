package dev.aura.auradroid.data.agent

/**
 * Decides whether a file-tool path leaves the app workspace.
 *
 * The sandbox-out gate is deliberately conservative: any path that could
 * leave the workspace (absolute, or containing `..`) counts as escaping, so
 * the user gets to approve it. The false negatives here — a relative path
 * that stays inside — are what keep the common case (working in the
 * workspace) quiet.
 */
object PathGuard {

    /** True when [path] would resolve outside the workspace. */
    fun escapes(path: String): Boolean {
        if (path.isBlank()) return false
        // Relative paths resolve inside the workspace; absolute or `..`
        // escape it. This is a conservative pre-check that matches how
        // PhoneTools.resolve() treats paths.
        return path.startsWith("/") ||
            path.split('/').any { it == ".." } ||
            path.split('\\').any { it == ".." }
    }
}
