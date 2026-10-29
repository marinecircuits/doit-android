package com.marinecircuits.doit.data

import androidx.core.text.HtmlCompat
import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.sync.PendingChanges
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Quantities are interoperable title text, not a separate server field. */
object Shopping {
    const val MARKER = "doit:type=shopping"
    private val suffix = Regex("""(.+?)\s+\[([^\[\]\r\n]+)\]\s*""")
    private val blocks = setOf("p", "div", "br", "li", "ul", "ol", "blockquote", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "tr")
    private val tokens = Regex("""<!--.*?-->|<(?:"[^"]*"|'[^']*'|[^'">])*>|&(?:#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]*);|[^<&]+|[<&]""", RegexOption.DOT_MATCHES_ALL)
    private val tagName = Regex("""^<\s*/?\s*([a-zA-Z0-9]+)""")

    fun isProject(description: String): Boolean = markerRanges(description).isNotEmpty()

    fun projectIds(projects: List<com.marinecircuits.doit.data.local.ProjectEntity>): Set<Long> =
        projects.filter { isProject(it.description) }.mapTo(HashSet()) { it.id }

    /** Remove only marker text, preserving all unrelated HTML exactly as received. */
    fun withMarker(description: String, active: Boolean): String {
        val ranges = markerRanges(description)
        if (active) return if (ranges.isEmpty()) description + "<p>$MARKER</p>" else description
        val removed = ranges.flatMap { it.toList() }.toSet()
        return description.filterIndexed { index, _ -> index !in removed }
    }

    private fun markerRanges(source: String): List<IntRange> {
        val line = mutableListOf<Pair<Char, IntRange>>()
        val ranges = mutableListOf<IntRange>()
        var ignored = 0
        fun boundary() {
            if (line.map { it.first }.joinToString("").trim() == MARKER) {
                val spans = line.filterNot { it.first.isWhitespace() }.map { it.second }
                val start = spans.first().first
                val end = spans.last().last + 1
                if (start >= 3 && end + 4 <= source.length && source.substring(start - 3, end + 4) == "<p>$MARKER</p>") {
                    ranges += (start - 3 until end + 4)
                } else ranges += spans
            }
            line.clear()
        }
        for (match in tokens.findAll(source)) {
            val raw = match.value
            if (raw.startsWith("<!--")) continue
            val tag = tagName.find(raw)?.groupValues?.get(1)?.lowercase()
            if (tag != null) {
                if (tag in blocks) boundary()
                if (tag == "script" || tag == "style") {
                    ignored = if (raw.startsWith("</")) (ignored - 1).coerceAtLeast(0) else ignored + 1
                }
                continue
            }
            if (ignored > 0) continue
            val entity = raw.startsWith('&') && raw.endsWith(';')
            val text = if (entity) HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_COMPACT).toString() else raw
            for ((i, char) in text.withIndex()) {
                if (char == '\n' || char == '\r') boundary()
                else line += char to if (entity) match.range else (match.range.first + i..match.range.first + i)
            }
        }
        boundary()
        return ranges
    }

    fun parseCapture(text: String, labels: List<com.marinecircuits.doit.data.local.LabelEntity>, projects: List<com.marinecircuits.doit.data.local.ProjectEntity>, fallback: Long?): CaptureSyntax.Parsed =
        parse(text, labels, projects, fallback ?: 0, projectIds(projects))

    fun item(title: String): Pair<String, String?> {
        val match = suffix.matchEntire(title) ?: return title to null
        val name = match.groupValues[1].trimEnd()
        val quantity = match.groupValues[2].trim()
        return if (name.isEmpty() || quantity.isEmpty()) title to null else name to quantity
    }

    fun parse(text: String, labels: List<com.marinecircuits.doit.data.local.LabelEntity>, projects: List<com.marinecircuits.doit.data.local.ProjectEntity>, fallback: Long, shopping: Set<Long>): CaptureSyntax.Parsed {
        val (item, quantity) = item(text.trim())
        val prefix = CaptureSyntax.parse(item, labels, projects)
        if (quantity != null && (prefix.projectId ?: fallback) in shopping) {
            return prefix.copy(title = if (prefix.title.isEmpty()) "" else "${prefix.title} [$quantity]")
        }
        return CaptureSyntax.parse(text, labels, projects)
    }

    /** Queue required removals without overwriting pending edits, including an in-flight label add. */
    suspend fun enforce(db: DoitDatabase, settings: SettingsStore): Boolean {
        val projects = settings.shoppingProjects.first()
        if (projects.isEmpty()) return false
        return db.withTransaction {
            val label = Doing.label(db.labels().all()) ?: return@withTransaction false
            var changed = false
            for (task in db.tasks().open().filter { it.projectId in projects }) {
                val names = db.taskLabels().forTask(task.id)
                val pending = db.pendingChanges().forTask(task.id)
                val fields = pending?.let { PendingChanges.decode(it.fields) }
                val queuedDoing = fields?.get(TaskRepository.LABELS)?.jsonArray?.any {
                    it.jsonPrimitive.contentOrNull?.equals(label.title, ignoreCase = true) == true
                } == true
                val activeIntent = fields?.get(TaskRepository.DOING)?.jsonObject?.get("active")?.jsonPrimitive?.booleanOrNull == true
                if (!Doing.contains(names, label) && !queuedDoing && !activeIntent) continue
                db.taskLabels().set(task.id, names.filterNot { it.equals(label.title, ignoreCase = true) })
                val cleanup = buildJsonObject {
                    put(TaskRepository.DOING, buildJsonObject { put("id", label.id); put("active", false); put("required", true) })
                    fields?.get(TaskRepository.LABELS)?.jsonArray?.let { queued ->
                        put(TaskRepository.LABELS, JsonArray(queued.filterNot { it.jsonPrimitive.contentOrNull?.equals(label.title, ignoreCase = true) == true }))
                    }
                }
                val updated = PendingChanges.merge(pending, task.id, cleanup)
                if (pending == null) db.pendingChanges().insert(updated) else db.pendingChanges().update(updated)
                changed = true
            }
            changed
        }
    }
}
