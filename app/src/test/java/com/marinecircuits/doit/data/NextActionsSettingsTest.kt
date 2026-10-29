package com.marinecircuits.doit.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.marinecircuits.doit.PlainCipher
import com.marinecircuits.doit.data.local.LabelEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextActionsSettingsTest {
    @Test
    fun defaultExplicitChoiceAndNoneAreLocalAndIndependent() = runTest {
        val file = File.createTempFile("next-actions", ".preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val settings = SettingsStore(store, PlainCipher)
        val labels = listOf(LabelEntity(1, "Next"), LabelEntity(2, "Later"))
        assertEquals(1L, NextActions.labelId(settings.nextActionsChoice.first(), labels))
        settings.setNextActionsLabel(2)
        assertEquals(2L, NextActions.labelId(settings.nextActionsChoice.first(), labels))
        assertEquals(2L, NextActions.labelId(settings.nextActionsChoice.first(), listOf(LabelEntity(2, "Renamed"))))
        settings.setNextActionsLabel(null)
        assertNull(NextActions.labelId(settings.nextActionsChoice.first(), labels))
        settings.clear()
        assertEquals(1L, NextActions.labelId(settings.nextActionsChoice.first(), labels))
    }
}
