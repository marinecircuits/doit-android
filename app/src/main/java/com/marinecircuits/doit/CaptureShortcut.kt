package com.marinecircuits.doit

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/** Create the launcher shortcut with the installed package, including the .debug variant. */
internal object CaptureShortcut {
    // Use a new ID so a pinned, immutable shortcut from the old XML definition
    // cannot block registration of the corrected target.
    private const val ID = "add_task_v2"

    fun create(context: Context): ShortcutInfo = ShortcutInfo.Builder(context, ID)
        .setShortLabel(context.getString(R.string.shortcut_add_task))
        .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_add))
        .setIntent(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CAPTURE))
        .build()

    fun register(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcut = create(context)
        manager.addDynamicShortcuts(listOf(shortcut))
        // Update an already pinned shortcut with this ID after an app update.
        manager.updateShortcuts(listOf(shortcut))
    }
}
