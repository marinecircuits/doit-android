package com.marinecircuits.doit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.marinecircuits.doit.PlainCipher
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ContextLabelSettingsTest {
    @Test
    fun existingContextLabelBecomesFirstSlotAndAllSlotsPersistIndependently() = runTest {
        val file = File.createTempFile("label-screens", ".preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        store.edit { it[longPreferencesKey("context_label_id")] = 7L }
        val settings = SettingsStore(store, PlainCipher)
        assertEquals(listOf(7L, null, null), settings.contextLabelIds.first())
        settings.setContextLabelId(1, 8)
        settings.setContextLabelId(2, 9)
        assertEquals(listOf(7L, 8L, 9L), settings.contextLabelIds.first())
        settings.setContextLabelId(1, null)
        assertEquals(listOf(7L, null, 9L), settings.contextLabelIds.first())
    }
}
