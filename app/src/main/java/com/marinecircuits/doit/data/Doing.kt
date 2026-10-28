package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity

/** The special label is optional and is never created by the app. */
object Doing {
    fun label(labels: List<LabelEntity>): LabelEntity? = labels.firstOrNull { it.title.equals("doing", ignoreCase = true) }
    fun contains(names: List<String>, label: LabelEntity): Boolean = names.any { it.equals(label.title, ignoreCase = true) }
}
