package com.phuoctnb.dexauto.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phuoctnb.dexauto.R
import com.phuoctnb.dexauto.util.LunarCalendar
import com.phuoctnb.dexauto.util.VietnameseDateFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun DateTimeSection(
    nowMillis: Long,
    dateFontSize: TextUnit = 18.sp,
    dateLineHeight: TextUnit = 16.sp,
    timeFontSize: TextUnit = 20.sp,
    textColor: Color = Color.White
) {
    val now = remember(nowMillis) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
    }
    val canNames = stringArrayResource(R.array.lunar_can_names).toList()
    val chiNames = stringArrayResource(R.array.lunar_chi_names).toList()
    val lunar = remember(now.toLocalDate(), canNames, chiNames) {
        LunarCalendar.fromSolar(now.toLocalDate(), canNames, chiNames)
    }
    val weekday = stringResource(now.weekdayRes)
    val leapSuffix = stringResource(R.string.date_leap_suffix)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = VietnameseDateFormatter.solarLine(now, weekday),
            color = textColor,
            fontSize = dateFontSize,
            lineHeight = dateLineHeight,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = VietnameseDateFormatter.lunarLine(lunar, leapSuffix),
            color = textColor,
            fontSize = dateFontSize,
            lineHeight = dateLineHeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(5.dp))
        Text(
            text = VietnameseDateFormatter.time(now),
            color = textColor,
            fontSize = timeFontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val LocalDateTime.weekdayRes: Int
    get() = when (dayOfWeek.value) {
        1 -> R.string.weekday_monday
        2 -> R.string.weekday_tuesday
        3 -> R.string.weekday_wednesday
        4 -> R.string.weekday_thursday
        5 -> R.string.weekday_friday
        6 -> R.string.weekday_saturday
        else -> R.string.weekday_sunday
    }
