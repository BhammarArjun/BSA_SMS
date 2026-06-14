package com.local.smsllm.data

import androidx.room.TypeConverter
import com.local.smsllm.domain.ProcessingStatus

class Converters {
    @TypeConverter
    fun fromProcessingStatus(status: ProcessingStatus): String = status.name

    @TypeConverter
    fun toProcessingStatus(name: String): ProcessingStatus = enumValueOf(name)
}
