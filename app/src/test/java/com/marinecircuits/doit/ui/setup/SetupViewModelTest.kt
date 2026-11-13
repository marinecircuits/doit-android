package com.marinecircuits.doit.ui.setup

import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.testSettings
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import com.marinecircuits.doit.data.Credentials
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetupViewModelTest {
    private val checker = ConnectionChecker { base, token -> VikunjaApi(base, token) }

    private fun connect(url: String, token: String = "tk_x"): SetupViewModel =
        SetupViewModel(checker, testSettings(), fixedApiBase = null).apply {
            this.url = url
            this.token = token
            connect()
        }

    @Test
    fun rejectsBadInputBeforeTouchingTheNetwork() {
        assertEquals(SetupError.BlankUrl, connect("  ").error)
        assertEquals(SetupError.InsecureHttp, connect("http://todo.example.com").error)
        assertEquals(SetupError.InvalidUrl, connect("ftp://todo.example.com").error)
        val blankToken = connect("todo.example.com", token = " ")
        assertEquals(SetupError.BlankToken, blankToken.error)
        assertFalse(blankToken.busy)
    }

    @Test
    fun reconnectingShowsTheSavedServer() {
        val viewModel = SetupViewModel(checker, testSettings(), "https://todo.example.com/api/v2/".toHttpUrl())
        assertEquals("https://todo.example.com", viewModel.url)
    }
    @Test
    fun logoutKeepsOnlyAddressAndPrefillsItWithoutAToken() = runBlocking {
        val settings = testSettings()
        settings.saveCredentials(Credentials("https://todo.example.com/api/v2/".toHttpUrl(), "tk_old"))
        settings.setDefaultProjectId(123)
        settings.setTokenRejected()
        settings.clear()
        assertNull(settings.credentials())
        assertNull(settings.defaultProjectId.first())
        assertFalse(settings.tokenRejected.first())
        assertEquals("https://todo.example.com/", settings.serverAddress.first())
        val viewModel = SetupViewModel(checker, settings, null)
        val deadline = System.currentTimeMillis() + 5_000
        while (viewModel.url.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertEquals("https://todo.example.com/", viewModel.url)
        assertEquals("", viewModel.token)
    }

}
