package com.marinecircuits.doit

import android.content.Context
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CaptureShortcutTest {
    @Test
    fun shortcutTargetsTheInstalledPackageAndCaptureAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shortcut = CaptureShortcut.create(context)
        val intent = requireNotNull(shortcut.intent)

        assertEquals("add_task_v2", shortcut.id)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(MainActivity.ACTION_CAPTURE, intent.action)

        CaptureShortcut.register(context)
        val manager = context.getSystemService(ShortcutManager::class.java)
        assertTrue(manager.dynamicShortcuts.any { it.id == shortcut.id })
    }
}
