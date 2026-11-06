package com.quietmoments.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Compact month view with a mark on dates containing a reflection or a media item. */
@Composable
fun DiaryMonthCalendar(selected: LocalDate, datesWithContent: Set<LocalDate>, onDate: (LocalDate) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    DiarySectionCard {

        Text("Календарь воспоминаний", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text("${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))} ${month.year}",
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        val weeks = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        Row(Modifier.fillMaxWidth()) {
            weeks.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall) }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val count = month.lengthOfMonth()
        val total = ((offset + count + 6) / 7) * 7
        repeat(total / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val dayNumber = week * 7 + weekday - offset + 1
                    if (dayNumber in 1..count) {
                        val date = month.atDay(dayNumber)
                        val isSelected = date == selected
                        val marked = date in datesWithContent
                        Surface(onClick = { onDate(date) }, modifier = Modifier.weight(1f).height(42.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Text(dayNumber.toString(), style = MaterialTheme.typography.bodyMedium)
                                Text(if (marked) "•" else " ", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else Spacer(Modifier.weight(1f).height(42.dp))
                }
            }
        }
    }
}
