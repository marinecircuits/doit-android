package com.marinecircuits.doit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.marinecircuits.doit.PlainCipher
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShoppingSettingsTest {
    @Test
    fun legacyChoicesMigrateOnceAndAcknowledgementsPreserveNewerToggles() = runTest {
        val file = File.createTempFile("shopping-settings", ".preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val key = stringSetPreferencesKey("shopping_projects")
        store.edit { it[key] = setOf("1", "2") }
        val settings = SettingsStore(store, PlainCipher)
        assertEquals(setOf(1L, 2L), settings.shoppingProjects.first())
        settings.migrateShoppingProjects()
        val old = settings.pendingProjectMarkers.first().getValue(1)
        assertEquals(setOf(1L, 2L), settings.pendingProjectMarkers.first().keys)
        assertFalse(store.data.first().contains(key))
        settings.setShoppingProject(1, false)
        settings.acknowledgeProjectMarker(1, old)
        assertFalse(settings.pendingProjectMarkers.first().getValue(1).active)
        settings.migrateShoppingProjects()
        settings.setSyncedShoppingProjects(setOf(1, 3))
        assertEquals(setOf(2L, 3L), settings.shoppingProjects.first())
        settings.clear()
        assertEquals(emptySet<Long>(), settings.shoppingProjects.first())
        assertEquals(emptyMap<Long, ProjectMarkerChange>(), settings.pendingProjectMarkers.first())
    }
}
