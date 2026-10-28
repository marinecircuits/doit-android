package com.marinecircuits.doit.ui.tasks

/** The release workflow writes the changelog before building the APK, so this is available offline. */
object ReleaseNotes {
    fun forVersion(changelog: String, version: String): String {
        val header = Regex("(?m)^## ${Regex.escape(version)}(?:\\s|$).*$").find(changelog) ?: return ""
        val start = header.range.last + 1
        val end = Regex("(?m)^## ").find(changelog, start)?.range?.first ?: changelog.length
        return changelog.substring(start, end).trim()
    }
}
