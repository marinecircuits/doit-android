package com.marinecircuits.doit

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.marinecircuits.doit.data.SecretCipher
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.local.DoitDatabase
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Robolectric has no Android Keystore; tests store secrets as-is. */
object PlainCipher : SecretCipher {
    override fun encrypt(plain: String) = plain
    override fun decrypt(stored: String) = stored
}

/** Monday 26 October 2026, 11:00 in Oslo. */
val testZone: ZoneId = ZoneId.of("Europe/Oslo")
val testClock: Clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), testZone)

fun inMemoryDatabase(): DoitDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoitDatabase::class.java)
        .allowMainThreadQueries()
        .build()

fun testSettings(): SettingsStore {
    val file = File.createTempFile("settings", ".preferences_pb").apply { delete() }
    val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { file },
    )
    return SettingsStore(dataStore, PlainCipher)
}
