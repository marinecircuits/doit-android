package com.marinecircuits.doit.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class Credentials(val apiBase: HttpUrl, val token: String)
data class ProjectMarkerChange(val active: Boolean, val revision: Long)
data class NextActionsChoice(val configured: Boolean, val labelId: Long?)

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

    /** Display-only server address; this flow never exposes or decrypts the token. */
    val serverAddress: Flow<String?> = dataStore.data.map { prefs ->
        prefs[API_BASE]?.toHttpUrlOrNull()?.let(ServerUrl::display)
    }

    /** Set when the server rejected the stored token; sync stays paused until a new token is saved. */
    val tokenRejected: Flow<Boolean> = dataStore.data.map { it[TOKEN_REJECTED] ?: false }

    val defaultProjectId: Flow<Long?> = dataStore.data.map { it[DEFAULT_PROJECT] }

    /** Null preference means use @next if present; zero means the user explicitly chose None. */
    val nextActionsChoice: Flow<NextActionsChoice> = dataStore.data.map { prefs ->
        val value = prefs[NEXT_ACTIONS_LABEL]
        NextActionsChoice(value != null, value?.takeIf { it > 0 })
    }

    suspend fun setNextActionsLabel(id: Long?) {
        dataStore.edit { it[NEXT_ACTIONS_LABEL] = id ?: 0L }
    }

    /** Three ordered label screens. Slot one keeps the original context-label preference on upgrade. */
    val contextLabelIds: Flow<List<Long?>> = dataStore.data.map { prefs -> CONTEXT_LABELS.map { prefs[it] } }

    suspend fun setContextLabelId(slot: Int, id: Long?) {
        require(slot in CONTEXT_LABELS.indices)
        val key = CONTEXT_LABELS[slot]
        dataStore.edit { prefs -> if (id == null) prefs.remove(key) else prefs[key] = id }
    }

    /** Server markers with offline changes overlaid; the legacy local choice is kept until migrated. */
    val shoppingProjects: Flow<Set<Long>> = dataStore.data.map { prefs ->
        val ids = (prefs[SYNCED_SHOPPING_PROJECTS].orEmpty() + prefs[SHOPPING_PROJECTS].orEmpty())
            .mapNotNull { it.toLongOrNull() }.toMutableSet()
        for ((id, change) in decodeProjectMarkers(prefs[PROJECT_MARKERS])) {
            if (change.active) ids.add(id) else ids.remove(id)
        }
        ids
    }

    val pendingProjectMarkers: Flow<Map<Long, ProjectMarkerChange>> = dataStore.data.map { decodeProjectMarkers(it[PROJECT_MARKERS]) }

    suspend fun migrateShoppingProjects() { dataStore.edit { migrateShoppingProjects(it) } }

    private fun migrateShoppingProjects(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val pending = decodeProjectMarkers(prefs[PROJECT_MARKERS]).toMutableMap()
        for (id in prefs[SHOPPING_PROJECTS].orEmpty().mapNotNull { it.toLongOrNull() }) {
            if (id !in pending) {
                val revision = (prefs[PROJECT_MARKER_REVISION] ?: 0) + 1
                prefs[PROJECT_MARKER_REVISION] = revision
                pending[id] = ProjectMarkerChange(true, revision)
            }
        }
        prefs.remove(SHOPPING_PROJECTS)
        prefs[PROJECT_MARKERS] = encodeProjectMarkers(pending)
    }

    suspend fun setSyncedShoppingProjects(ids: Set<Long>) {
        dataStore.edit { it[SYNCED_SHOPPING_PROJECTS] = ids.map { id -> id.toString() }.toSet() }
    }

    suspend fun setShoppingProject(id: Long, active: Boolean) {
        dataStore.edit { prefs ->
            migrateShoppingProjects(prefs)
            val pending = decodeProjectMarkers(prefs[PROJECT_MARKERS]).toMutableMap()
            val revision = (prefs[PROJECT_MARKER_REVISION] ?: 0) + 1
            prefs[PROJECT_MARKER_REVISION] = revision
            pending[id] = ProjectMarkerChange(active, revision)
            prefs[PROJECT_MARKERS] = encodeProjectMarkers(pending)
        }
    }

    suspend fun acknowledgeProjectMarker(id: Long, change: ProjectMarkerChange) {
        dataStore.edit { prefs ->
            val pending = decodeProjectMarkers(prefs[PROJECT_MARKERS]).toMutableMap()
            if (pending[id] == change) pending.remove(id)
            prefs[PROJECT_MARKERS] = encodeProjectMarkers(pending)
        }
    }

    private fun decodeProjectMarkers(raw: String?): Map<Long, ProjectMarkerChange> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val id = line.substringBefore('=', "").toLongOrNull() ?: return@mapNotNull null
            val value = line.substringAfter('=')
            val revision = value.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            id to ProjectMarkerChange(value.substringBefore(':') == "1", revision)
        }.toMap()

    private fun encodeProjectMarkers(changes: Map<Long, ProjectMarkerChange>) =
        changes.entries.joinToString("\n") { "${it.key}=${if (it.value.active) 1 else 0}:${it.value.revision}" }

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
        val NEXT_ACTIONS_LABEL = longPreferencesKey("next_actions_label_id")
        val CONTEXT_LABELS = listOf(
            longPreferencesKey("context_label_id"),
            longPreferencesKey("context_label_id_2"),
            longPreferencesKey("context_label_id_3"),
        )
        val NOTICES = stringPreferencesKey("notices")
        val SHOPPING_PROJECTS = stringSetPreferencesKey("shopping_projects")
        val SYNCED_SHOPPING_PROJECTS = stringSetPreferencesKey("synced_shopping_projects")
        val PROJECT_MARKERS = stringPreferencesKey("pending_project_markers")
        val PROJECT_MARKER_REVISION = longPreferencesKey("project_marker_revision")
        val PROJECT_COLORS = stringPreferencesKey("project_colors")
    }
}
