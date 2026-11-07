package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.TaskEntity

/** Last server result; retained while this filter is open, including when a refresh fails. */
data class SavedFilterTask(val task: TaskEntity, val labels: List<LabelEntity>)
