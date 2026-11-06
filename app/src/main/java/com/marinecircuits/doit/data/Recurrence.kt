package com.marinecircuits.doit.data

/** Only explicit !shortcuts set recurrence. Plain words, quotes and quantities remain literal. */
object Recurrence {
    const val DAY = 86400L
    const val WEEK = 7 * DAY
    const val MONTHLY = 1
    const val AFTER_COMPLETION = 2
    private val tokens = Regex("\"[^\"]*(?:\"|$)|\\[[^\\]]*(?:\\]|$)|(?<!\\S)!([^\\s\"]*)")
    private val custom = Regex("([1-9][0-9]{0,3})([dw])", RegexOption.IGNORE_CASE)
    private val names = listOf("daily", "weekly", "monthly")

    data class Rule(val after: Long = 0, val mode: Int = 0) {
        val active: Boolean get() = after > 0 || mode == MONTHLY
        val label: String get() = when {
            mode == MONTHLY -> "monthly"
            after == DAY -> "daily"
            after == WEEK -> "weekly"
            after > 0 && after % WEEK == 0L -> "every ${after / WEEK} weeks"
            after > 0 && after % DAY == 0L -> "every ${after / DAY} days"
            after > 0 && after % 3600 == 0L -> "every ${after / 3600} hours"
            else -> "every $after seconds"
        }
        val summary: String get() = "Repeats $label" + if (mode == AFTER_COMPLETION) " after completion" else ""
    }
    data class Parsed(val text: String, val rule: Rule?)
    data class Typing(val start: Int, val word: String)
    data class Suggestion(val title: String, val token: String)

    fun fromToken(word: String): Rule? = when (word.lowercase()) {
        "daily" -> Rule(DAY)
        "weekly" -> Rule(WEEK)
        "monthly" -> Rule(0, MONTHLY)
        else -> custom.matchEntire(word)?.let { Rule(it.groupValues[1].toLong() * if (it.groupValues[2].equals("d", true)) DAY else WEEK) }
    }

    fun parse(text: String): Parsed {
        var rule: Rule? = null
        val cleaned = tokens.replace(text) { match ->
            val found = match.groups[1]?.value?.let(::fromToken)
            if (found == null) match.value else { rule = found; " " }
        }
        return Parsed(if (rule == null) text else cleaned.trim().replace(Regex("\\s+"), " "), rule)
    }

    fun typing(text: String, cursor: Int): Typing? = tokens.findAll(text.take(cursor)).lastOrNull()
        ?.takeIf { it.range.last + 1 == cursor && it.groups[1] != null }
        ?.let { Typing(it.range.first, it.groupValues[1]) }

    fun suggestions(word: String): List<Suggestion> {
        if (word.isEmpty()) return listOf(Suggestion("Choose date…", "date")) + names.map { Suggestion(it.replaceFirstChar(Char::uppercase), it) }
        fromToken(word)?.let { return listOf(Suggestion(it.summary, word.lowercase())) }
        val key = word.lowercase()
        val matches = names.filter { it.startsWith(key) }
        if (matches.isNotEmpty()) return matches.map { Suggestion("Repeats $it", it) }
        return names.filter { distance(key, it) <= 2 }.take(1).map { Suggestion("Did you mean !$it?", it) }
    }

    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, ca ->
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            b.forEachIndexed { j, cb -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (ca == cb) 0 else 1) }
            previous = current
        }
        return previous[b.length]
    }
}
