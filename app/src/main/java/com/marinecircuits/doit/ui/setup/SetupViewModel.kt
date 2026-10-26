package com.marinecircuits.doit.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.ServerUrl
import com.marinecircuits.doit.data.SettingsStore
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

sealed interface SetupError {
    data object BlankUrl : SetupError
    data object InsecureHttp : SetupError
    data object InvalidUrl : SetupError
    data object BlankToken : SetupError
    data object Unreachable : SetupError
    data object TokenRejected : SetupError
    data object MissingPermission : SetupError
    data class Other(val message: String) : SetupError
}

/**
 * First-run setup, or entering a new token for the saved server ([fixedApiBase] set). Saving credentials is what
 * switches the app to the task list; the list's first sync then loads the tasks.
 */
class SetupViewModel(
    private val checker: ConnectionChecker,
    private val settings: SettingsStore,
    val fixedApiBase: HttpUrl?,
) : ViewModel() {
    var url by mutableStateOf(fixedApiBase?.toString()?.removeSuffix("api/v2/")?.removeSuffix("/") ?: "")
    var token by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<SetupError?>(null)
        private set

    fun connect() {
        if (busy) return
        val apiBase = when (val normalized = ServerUrl.normalize(url)) {
            is ServerUrl.Result.Valid -> normalized.apiBase
            ServerUrl.Result.Blank -> return fail(SetupError.BlankUrl)
            ServerUrl.Result.InsecureHttp -> return fail(SetupError.InsecureHttp)
            ServerUrl.Result.Invalid -> return fail(SetupError.InvalidUrl)
        }
        val token = token.trim()
        if (token.isEmpty()) return fail(SetupError.BlankToken)

        busy = true
        error = null
        viewModelScope.launch {
            when (val result = checker.check(apiBase, token)) {
                ConnectionChecker.Result.Ok -> settings.saveCredentials(Credentials(apiBase, token))
                ConnectionChecker.Result.Unreachable -> error = SetupError.Unreachable
                ConnectionChecker.Result.TokenRejected -> error = SetupError.TokenRejected
                ConnectionChecker.Result.MissingPermission -> error = SetupError.MissingPermission
                is ConnectionChecker.Result.Failed -> error = SetupError.Other(result.message)
            }
            busy = false
        }
    }

    private fun fail(reason: SetupError) {
        error = reason
    }
}
