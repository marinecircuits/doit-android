package com.marinecircuits.doit.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns whatever the user typed into the API v2 base URL. */
object ServerUrl {
    sealed interface Result {
        /** [apiBase] always ends in `/api/v2/`. */
        data class Valid(val apiBase: HttpUrl) : Result
        data object Blank : Result
        data object InsecureHttp : Result
        data object Invalid : Result
    }

    fun normalize(input: String): Result {
        val text = input.trim()
        if (text.isEmpty()) return Result.Blank
        if (text.startsWith("http://", ignoreCase = true)) return Result.InsecureHttp
        val withScheme = if ("://" in text) text else "https://$text"
        if (!withScheme.startsWith("https://", ignoreCase = true)) return Result.Invalid
        val url = withScheme.toHttpUrlOrNull() ?: return Result.Invalid

        var segments = url.encodedPathSegments.filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[segments.size - 2] == "api" && segments.last() in setOf("v1", "v2")) {
            segments = segments.dropLast(2)
        }

        val apiBase = url.newBuilder()
            .query(null)
            .fragment(null)
            .encodedPath((segments + listOf("api", "v2")).joinToString("/", prefix = "/", postfix = "/"))
            .build()
        return Result.Valid(apiBase)
    }
}
