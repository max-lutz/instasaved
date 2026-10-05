package com.maxlutz.instasaved.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Stores a list of names in one column: empty for none, else a JSON array, which no name can break. */
class NameListConverter {
    @TypeConverter
    fun toColumn(names: List<String>): String = if (names.isEmpty()) "" else Json.encodeToString(serializer, names)

    @TypeConverter
    fun fromColumn(column: String): List<String> = if (column.isEmpty()) emptyList() else Json.decodeFromString(serializer, column)

    private companion object {
        val serializer = ListSerializer(String.serializer())
    }
}
