package com.marinecircuits.doit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.marinecircuits.doit.MainActivity
import com.marinecircuits.doit.R
import com.marinecircuits.doit.container
import com.marinecircuits.doit.data.NextActions
import com.marinecircuits.doit.ui.tasks.DueLabels
import com.marinecircuits.doit.ui.tasks.DueWords
import com.marinecircuits.doit.ui.tasks.TaskListModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

private data class WidgetTask(val id: Long, val title: String, val dueLabel: String?, val overdue: Boolean)

/** Open tasks in list order with tap-to-complete; reads the same local data as the app. */
class DoitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.container
        val connected = container.settings.credentials() != null
        val tasks = if (connected) loadTasks(context) else emptyList()
        provideContent {
            GlanceTheme {
                WidgetContent(context, connected, tasks)
            }
        }
    }

    private suspend fun loadTasks(context: Context): List<WidgetTask> {
        val container = context.container
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val words = DueWords(
            context.getString(R.string.due_today),
            context.getString(R.string.due_tomorrow),
            context.getString(R.string.due_yesterday),
        )
        val locale = context.resources.configuration.locales[0]
        val labels = container.database.labels().all()
        val nextId = NextActions.labelId(container.settings.nextActionsChoice.first(), labels)
        val nextName = labels.firstOrNull { it.id == nextId }?.title
        val taskLabels = container.repository.taskLabels.first().groupBy({ it.taskId }, { it.title })
        val rows = TaskListModel.rows(
            container.repository.openTasks(),
            container.database.projects().all(),
            container.settings.defaultProjectId.first(),
            today,
            zone,
            taskLabels = taskLabels,
            labels = labels,
            nextActionsLabel = nextName,
        )
        return rows.map { row ->
            WidgetTask(row.task.id, row.task.title, row.dueDate?.let { DueLabels.label(it, today, words, locale) }, row.overdue)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, connected: Boolean, tasks: List<WidgetTask>) {
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(R.string.app_name),
                modifier = GlanceModifier.defaultWeight().padding(start = 8.dp).clickable(openApp),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            if (connected) {
                Image(
                    provider = ImageProvider(R.drawable.ic_add),
                    contentDescription = context.getString(R.string.widget_add),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier
                        .size(40.dp)
                        .padding(8.dp)
                        .clickable(
                            actionStartActivity(
                                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_CAPTURE),
                            ),
                        ),
                )
            }
        }

        if (!connected) {
            Box(GlanceModifier.fillMaxSize().clickable(openApp), contentAlignment = Alignment.Center) {
                Text(
                    context.getString(R.string.widget_not_connected),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            return@Column
        }

        LazyColumn(GlanceModifier.fillMaxSize()) {
            items(tasks, itemId = { it.id }) { task ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<CompleteTaskAction>(actionParametersOf(TaskIdKey to task.id)),
                    )
                    Text(
                        task.title,
                        maxLines = 2,
                        modifier = GlanceModifier.defaultWeight().clickable(openApp),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    )
                    task.dueLabel?.let { label ->
                        Text(
                            label,
                            modifier = GlanceModifier.padding(start = 8.dp, end = 8.dp),
                            style = TextStyle(
                                color = if (task.overdue) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val TaskIdKey = ActionParameters.Key<Long>("taskId")

/** Completing from the widget goes through the same repository call as the app. */
class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        context.container.repository.complete(taskId)
    }
}

class DoitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DoitWidget()
}
