package com.marinecircuits.doit.share

/** A proposed task from something shared into the app. */
data class SharedTask(val title: String, val description: String?)

object SharedText {
    /**
     * Browsers share the page title as the subject and the URL as the text: the subject becomes the title and the
     * text the description. Plain text becomes the title, with any further lines as the description.
     */
    fun propose(subject: String?, text: String?): SharedTask {
        val cleanSubject = subject?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty()
        if (cleanSubject.isNotEmpty()) {
            return SharedTask(cleanSubject, cleanText.takeIf { it.isNotEmpty() && it != cleanSubject })
        }
        val lines = cleanText.lines()
        val first = lines.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val rest = lines.dropWhile { it.isBlank() }.drop(1).joinToString("\n").trim()
        return SharedTask(first, rest.takeIf { it.isNotEmpty() })
    }
}
