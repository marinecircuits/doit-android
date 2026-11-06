package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity

object NextActions {
    fun labelId(choice: NextActionsChoice, labels: List<LabelEntity>): Long? =
        if (choice.configured) choice.labelId else labels.firstOrNull { it.title.equals("next", ignoreCase = true) }?.id
}
