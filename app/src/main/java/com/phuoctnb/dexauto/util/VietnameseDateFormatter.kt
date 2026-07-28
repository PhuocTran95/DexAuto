package com.phuoctnb.dexauto.util

import com.phuoctnb.dexauto.data.LunarDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object VietnameseDateFormatter {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun solarLine(now: LocalDateTime, weekday: String): String {
        return "$weekday, ${now.dayOfMonth}/${now.monthValue}/${now.year}"
    }

    fun lunarLine(lunar: LunarDate, leapSuffix: String): String {
        val leap = if (lunar.isLeap) leapSuffix else ""
        return "(${lunar.day}/${lunar.month}$leap ${lunar.yearName})"
    }

    fun time(now: LocalDateTime): String = now.format(timeFormatter)
}
