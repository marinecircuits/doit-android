package com.marinecircuits.doit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class Credentials(val apiBase: HttpUrl, val token: String)

/** Everything the app remembers outside the task database. The token is stored encrypted. */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    val credentials: Flow<Credentials?> = dataStore.data.map { prefs ->
        val apiBase = prefs[API_BASE]?.toHttpUrlOrNull() ?: return@map null
        val token = prefs[TOKEN]?.let(cipher::decrypt) ?: return@map null
        Credentials(apiBase, token)
    }

    /** Set when the server rejected the stored token; sync stays paused until a new token is saved. */
    val tokenRejected: Flow<Boolean> = dataStore.data.map { it[TOKEN_REJECTED] ?: false }

    val defaultProjectId: Flow<Long?> = dataStore.data.map { it[DEFAULT_PROJECT] }

    /** Messages about changes the server rejected, kept until the user has seen them. */
    val notices: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[NOTICES]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun addNotice(text: String) {
        dataStore.edit { prefs ->
            prefs[NOTICES] = listOfNotNull(prefs[NOTICES], text.replace('\n', ' ')).joinToString("\n")
        }
    }

    suspend fun clearNotices() {
        dataStore.edit { it.remove(NOTICES) }
    }

    suspend fun credentials(): Credentials? = credentials.first()

    suspend fun saveCredentials(credentials: Credentials) {
        val encrypted = cipher.encrypt(credentials.token)
        dataStore.edit {
            it[API_BASE] = credentials.apiBase.toString()
            it[TOKEN] = encrypted
            it[TOKEN_REJECTED] = false
        }
    }

    suspend fun setTokenRejected() {
        dataStore.edit { it[TOKEN_REJECTED] = true }
    }

    suspend fun setDefaultProjectId(id: Long) {
        dataStore.edit { it[DEFAULT_PROJECT] = id }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Project colours chosen locally and not yet accepted by the server. An empty value clears the colour. */
    suspend fun pendingProjectColors(): Map<Long, String> = decodeProjectColors(dataStore.data.first()[PROJECT_COLORS])

    suspend fun setPendingProjectColor(projectId: Long, hexColor: String) {
        dataStore.edit { prefs ->
            val colors = decodeProjectColors(prefs[PROJECT_COLORS]).toMutableMap()
            colors[projectId] = hexColor
            prefs[PROJECT_COLORS] = encodeProjectColors(colors)
        }
    }

    suspend fun removePendingProjectColor(projectId: Long) {
        dataStore.edit { prefs ->
            val colors = decodeProjectColors(prefs[PROJECT_COLORS]).toMutableMap()
            if (colors.remove(projectId) == null) return@edit
            if (colors.isEmpty()) prefs.remove(PROJECT_COLORS) else prefs[PROJECT_COLORS] = encodeProjectColors(colors)
        }
    }

    private fun decodeProjectColors(raw: String?): Map<Long, String> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val id = line.substringBefore('=', "").toLongOrNull() ?: return@mapNotNull null
            id to line.substringAfter('=')
        }.toMap()

    private fun encodeProjectColors(colors: Map<Long, String>): String =
        colors.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private companion object {
        val API_BASE = stringPreferencesKey("api_base")
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_REJECTED = booleanPreferencesKey("token_rejected")
        val DEFAULT_PROJECT = longPreferencesKey("default_project_id")
        val NOTICES = stringPreferencesKey("notices")
        val PROJECT_COLORS = stringPreferencesKey("project_colors")
    }
}
