package com.seasons.app.data

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToDate(epochDay: Long?): LocalDate? = epochDay?.let { LocalDate.ofEpochDay(it) }
}
