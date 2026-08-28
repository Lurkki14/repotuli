package com.lurkki14.repotuli

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object NotificationSettingsSerializer : Serializer<NotificationSettings> {
    override val defaultValue: NotificationSettings = NotificationSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): NotificationSettings {
        return try {
            NotificationSettings.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto", exception)
        }
    }

    override suspend fun writeTo(
        t: NotificationSettings,
        output: OutputStream,
    ) = t.writeTo(output)
}
