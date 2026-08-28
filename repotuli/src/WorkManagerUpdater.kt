package com.lurkki14.repotuli

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.URL
import java.util.concurrent.TimeUnit

class MeasurementWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    val CLASS_NAME = "MeasurementWorker"

    override suspend fun doWork(): Result {
        Log.d(CLASS_NAME, "Starting background measurement fetch")
        return try {
            val jsonString = URL(MeasurementProxy.DATA_URL).readText(Charsets.UTF_8)
            val newMeasurements = MeasurementProxy.fromJSONString(jsonString)
            MeasurementCollector.pushNew(newMeasurements)
            Log.d(CLASS_NAME, "Background fetch successful")
            Result.success()
        } catch (e: Exception) {
            Log.e(CLASS_NAME, "Background fetch failed", e)
            Result.retry()
        }
    }
}

object WorkManagerUpdater {
    private const val WORK_NAME = "measurement_update_work"

    fun schedulePeriodicUpdate(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<MeasurementWorker>(
            15, TimeUnit.MINUTES, // Minimum allowed interval
            1, TimeUnit.MINUTES  // Flex interval
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelWork(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
    }
}
