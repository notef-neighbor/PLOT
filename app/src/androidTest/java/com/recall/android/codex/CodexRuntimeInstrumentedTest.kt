package com.recall.android.codex

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodexRuntimeInstrumentedTest {
    @Test
    fun startsPackagedAppServerAndReadsAccount() = runBlocking {
        val runtime = CodexRuntimeManager(ApplicationProvider.getApplicationContext())
        try {
            runtime.start()
            val state = withTimeout(5_000) {
                runtime.state.first { it.status == CodexRuntimeStatus.LoginRequired }
            }
            assertEquals(CodexRuntimeStatus.LoginRequired, state.status)
            val login = runtime.beginDeviceLogin()
            check(login.verificationUrl.startsWith("https://"))
            check(login.userCode.isNotBlank())
        } finally {
            runtime.stop()
        }
    }
}
