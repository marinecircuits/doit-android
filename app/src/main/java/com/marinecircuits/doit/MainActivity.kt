package com.marinecircuits.doit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.ui.setup.ConnectionChecker
import com.marinecircuits.doit.ui.setup.SetupScreen
import com.marinecircuits.doit.ui.setup.SetupViewModel
import com.marinecircuits.doit.ui.tasks.TasksScreen
import com.marinecircuits.doit.ui.tasks.TasksViewModel
import com.marinecircuits.doit.ui.theme.DoitTheme

class MainActivity : ComponentActivity() {
    /** Grows each time something (the widget's + button) asks for the capture box to be focused. */
    private var focusCaptureRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            DoitTheme {
                DoitRoot(container, focusCaptureRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_CAPTURE) focusCaptureRequests++
    }

    companion object {
        /** Opens the app with the capture box focused (the widget's + button). */
        const val ACTION_CAPTURE = "com.marinecircuits.doit.CAPTURE"
    }
}

/** Wraps a loaded value so "not loaded yet" and "loaded, but null" can be told apart. */
private class Loaded<T>(val value: T)

@Composable
private fun DoitRoot(container: AppContainer, focusCaptureRequests: Int) {
    val credentials by produceState<Loaded<Credentials?>?>(null) {
        container.settings.credentials.collect { value = Loaded(it) }
    }
    val tokenRejected by container.settings.tokenRejected.collectAsStateWithLifecycle(initialValue = false)
    var replacingToken by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tokenRejected) { if (!tokenRejected) replacingToken = false }
    BackHandler(enabled = replacingToken) { replacingToken = false }

    val checker = ConnectionChecker { base, token -> VikunjaApi(base, token) }
    val saved = credentials
    when {
        saved == null -> Surface(Modifier.fillMaxSize()) {}
        saved.value == null -> SetupScreen(
            viewModel(key = "setup") { SetupViewModel(checker, container.settings, fixedApiBase = null) },
        )
        replacingToken -> SetupScreen(
            viewModel(key = "reconnect") { SetupViewModel(checker, container.settings, fixedApiBase = saved.value.apiBase) },
        )
        else -> TasksScreen(
            viewModel = viewModel {
                TasksViewModel(
                    repository = container.repository,
                    settings = container.settings,
                    syncResults = container.syncEngine.lastResult,
                    sync = container.syncEngine::sync,
                    signOut = container::signOut,
                    clock = DeviceClock,
                )
            },
            focusCaptureRequests = focusCaptureRequests,
            onEnterNewToken = { replacingToken = true },
        )
    }
}
