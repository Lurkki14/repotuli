package com.lurkki14.repotuli

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object NotificationHandler {
    val Context.notificationSettingsStore: DataStore<NotificationSettings> by dataStore(
        fileName = "notification_settings.pb",
        serializer = NotificationSettingsSerializer,
    )

    init {
        val ctx = RepotuliApp.context()
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            ctx.notificationSettingsStore.data.collect { settings ->
                Log.d(CLASS_NAME, "Loaded settings for ${settings.stationsCount} stations")
                if (settings.stationsCount > 0)
                    WorkManagerUpdater.schedulePeriodicUpdate(ctx)
                else
                    WorkManagerUpdater.cancelWork(ctx)
            }
        }

        scope.launch {
            MeasurementCollector.allMeasurementsFlow.collect { allMeasurements ->
                val settings = ctx.notificationSettingsStore.data.first()
                settings.stationsList.forEach { setting ->
                    val latestMeasurement = allMeasurements[setting.code]?.lastOrNull()
                    if (latestMeasurement != null && latestMeasurement.value >= setting.threshold) {
                        sendTestNotification(ctx, setting.code)
                    }
                }
            }
        }
    }

    private const val CHANNEL_ID = "station_notifications"
    private const val CLASS_NAME = "NotificationHandler"

    suspend fun addStation(context: Context, setting: StationSetting) {
        Log.d(CLASS_NAME, "Enabling notifications for ${setting.code}")
        createNotificationChannel(context)

        context.notificationSettingsStore.updateData { settings ->
            val builder = settings.toBuilder()
            val existingIdx = builder.stationsList.indexOfFirst { it.code == setting.code }
            if (existingIdx != -1) {
                builder.setStations(existingIdx, setting)
            } else {
                builder.addStations(setting)
            }
            builder.build()
        }
        //sendTestNotification(context, setting.code)
    }

    suspend fun removeStation(context: Context, setting: Station) {
        Log.d(CLASS_NAME, "Disabling notifications for ${setting.code}")
        context.notificationSettingsStore.updateData { settings ->
            val builder = settings.toBuilder()
            val existingIdx = builder.stationsList.indexOfFirst { it.code == setting.code }
            if (existingIdx != -1) {
                builder.removeStations(existingIdx)
            }
            builder.build()
        }
    }

    private fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManagerCompat.IMPORTANCE_HIGH

        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, importance)
            .setName(name)
            .setDescription(descriptionText)
            .build()

        NotificationManagerCompat.from(context).createNotificationChannel(channel)
        Log.d(CLASS_NAME, "Notification channel created/updated")
    }

    private fun sendTestNotification(context: Context, code: StationCode) {

        Log.d(CLASS_NAME, "Sending test notification for $code")
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notification_title, code))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(code.hashCode(), builder.build())
            Log.d(CLASS_NAME, "Notification sent to NotificationManagerCompat")
        } catch (e: SecurityException) {
            Log.e(CLASS_NAME, "SecurityException: lack of POST_NOTIFICATIONS permission", e)
        }
    }
}
