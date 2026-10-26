package com.marinecircuits.doit.ui.setup

import com.marinecircuits.doit.data.remote.ApiException
import com.marinecircuits.doit.data.remote.VikunjaApi
import java.io.IOException
import okhttp3.HttpUrl

/** Checks a server address and token before they are saved. */
class ConnectionChecker(private val apiFor: (HttpUrl, String?) -> VikunjaApi) {
    sealed interface Result {
        data object Ok : Result
        data object Unreachable : Result
        data object TokenRejected : Result
        data object MissingPermission : Result
        data class Failed(val message: String) : Result
    }

    suspend fun check(apiBase: HttpUrl, token: String): Result {
        // /info only exists on API v2 servers, i.e. Vikunja 2.4 and newer.
        try {
            apiFor(apiBase, null).info()
        } catch (e: IOException) {
            return Result.Unreachable
        } catch (e: ApiException) {
            return Result.Unreachable
        }

        val api = apiFor(apiBase, token)
        return try {
            try {
                api.tokenTest()
            } catch (e: ApiException) {
                return if (e.isUnauthorized) Result.TokenRejected else Result.Failed(e.message.orEmpty())
            }
            try {
                api.firstProjects()
            } catch (e: ApiException) {
                return if (e.isUnauthorized || e.isForbidden) Result.MissingPermission else Result.Failed(e.message.orEmpty())
            }
            Result.Ok
        } catch (e: IOException) {
            Result.Unreachable
        }
    }
}
