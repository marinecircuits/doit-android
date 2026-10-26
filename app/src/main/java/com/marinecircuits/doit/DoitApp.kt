package com.marinecircuits.doit

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.glance.appwidget.updateAll
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.KeystoreCipher
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.data.sync.SyncEngine
import com.marinecircuits.doit.data.sync.SyncScheduler
import com.marinecircuits.doit.widget.DoitWidget
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DoitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as DoitApp).container

/** The app's object graph, built once per process. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = DoitDatabase.build(context)
    val settings = SettingsStore(
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") }),
        KeystoreCipher(),
    )
    private val idRemap = IdRemap()

    val repository = TaskRepository(database, settings, idRemap, DeviceClock) {
        SyncScheduler.requestSync(context)
        refreshWidgets()
    }

    val syncEngine = SyncEngine(database, settings, idRemap, ::apiFor) { refreshWidgets() }

    fun apiFor(credentials: Credentials) = VikunjaApi(credentials.apiBase, credentials.token)

    /** Forgets the server, the token and every cached or unsent change. */
    suspend fun signOut() {
        SyncScheduler.cancelPending(context)
        withContext(Dispatchers.IO) { database.clearAllTables() }
        settings.clear()
        refreshWidgets()
    }

    /** Redraws home-screen widgets after local data changed. */
    fun refreshWidgets() {
        appScope.launch { DoitWidget().updateAll(context) }
    }
}

/** Follows the device's current time zone, even if it changes while the app runs. */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
