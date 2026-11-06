package com.marinecircuits.doit.ui.tasks

/** Plain text and web addresses taken from a Vikunja task description. */
object TaskDescriptions {
    private val href = Regex("""(?i)href\s*=\s*["'](https?://[^"']+)["']""")
    private val markdown = Regex("""\[([^]]*)]\((https?://[^)\s]+)\)""")
    private val bare = Regex("""(?i)https?://[^\s<>"']+""")
    private val trailing = Regex("""[),.;:!?]+$""")
    private val tags = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")

    /** Unique http(s) addresses, in the order they appear. The description text itself is not a link. */
    fun links(description: String): List<String> {
        if (description.isBlank()) return emptyList()
        val found = linkedSetOf<String>()
        fun add(raw: String) {
            val url = raw.replace(trailing, "")
            if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) found += url
        }
        href.findAll(description).forEach { add(it.groupValues[1]) }
        markdown.findAll(description).forEach { add(it.groupValues[2]) }
        bare.findAll(description).forEach { add(it.value) }
        return found.toList()
    }

    /** Description text for display. Markup is removed and nothing in it is made tappable. */
    fun plain(description: String): String {
        if (description.isBlank()) return ""
        val decoded = description
            .replace(markdown, "$1")
            .replace(tags, " ")
            .replace("\u0026nbsp;", " ")
            .replace("\u0026amp;", "\u0026")
            .replace("\u0026lt;", "<")
            .replace("\u0026gt;", ">")
            .replace("\u0026quot;", "\"")
            .replace("\u0026#39;", "'")
        return decoded.replace(whitespace, " ").trim()
    }
}
