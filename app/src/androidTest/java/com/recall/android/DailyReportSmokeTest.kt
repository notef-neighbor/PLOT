package com.recall.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.recall.android.worker.DailyReportWorker
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReportSmokeTest {
    @Test
    fun enqueueTodayReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = OneTimeWorkRequestBuilder<DailyReportWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(DailyReportWorker.KEY_MANUAL, true)
                    .putString(DailyReportWorker.KEY_REPORT_DATE, LocalDate.now().toString())
                    .build(),
            )
            .build()
        val manager = WorkManager.getInstance(context)
        manager
            .enqueueUniqueWork("recall-daily-report-smoke", ExistingWorkPolicy.REPLACE, request)
            .result.get(10, TimeUnit.SECONDS)
        repeat(240) {
            val info = manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)
            if (info != null && info.state.isFinished) {
                assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, info.state)
                return
            }
            Thread.sleep(1_000)
        }
        fail("Daily report generation timed out")
    }
}
