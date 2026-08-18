package com.recall.android.data

import android.content.Context
import androidx.room.Room
import com.recall.android.codex.CodexRuntimeManager
import com.recall.android.crypto.VaultCipher
import com.recall.android.privacy.PrivacyGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: HistoryDatabase = Room.databaseBuilder(
        applicationContext,
        HistoryDatabase::class.java,
        "recall-history.db",
    ).build()

    val cipher = VaultCipher()
    val settings = ObservationSettings(applicationContext, applicationScope, cipher)
    val privacyGuard = PrivacyGuard(applicationContext.packageName)
    val codexRuntime = CodexRuntimeManager(applicationContext)
    val gatewayClient = HistoryGatewayClient()
    val historyRepository = HistoryRepository(
        context = applicationContext,
        database = database,
        cipher = cipher,
    )
}
