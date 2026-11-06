package com.quietmoments.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MoodStyle(val name: String, val color: Color, val mark: String)
private val moodStyles = listOf(
    MoodStyle("Радость", Color(0xFFDBA35A), "✦"),
    MoodStyle("Спокойно", Color(0xFF619E88), "●"),
    MoodStyle("Грусть", Color(0xFF779BD1), "☾"),
    MoodStyle("Тревога", Color(0xFFB1779E), "◇"),
    MoodStyle("Вдохновение", Color(0xFFAE85C9), "✧"),
    MoodStyle("Нежность", Color(0xFFE197AA), "♡"),
    MoodStyle("Мечтательно", Color(0xFF739CAC), "☁")
)
private fun moodStyle(name: String): MoodStyle =
    moodStyles.firstOrNull { it.name == name } ?: moodStyles[1]

@Composable
fun MoodInsightsScreen(
    onBack: () -> Unit,
    onOpenDay: (String) -> Unit,
    uid: String = "offline"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val journal = remember(uid) { LocalJournal(context, uid) }
    var entries by remember(uid) { mutableStateOf(emptyList<DailyReflection>()) }
    var period by remember { mutableIntStateOf(30) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(uid) {
        runCatching { withContext(Dispatchers.IO) { journal.all() } }
            .onSuccess { entries = it }
            .onFailure { error = it.localizedMessage ?: "Не удалось открыть дневник" }
    }
    val today = LocalDate.now()
    val filtered = remember(entries, period) {
        val start = today.minusDays(period.toLong() - 1)
        entries.filter {
            runCatching {
                val date = LocalDate.parse(it.date)
                !date.isBefore(start) && !date.isAfter(today)
            }.getOrDefault(false)
        }.sortedByDescending { it.date }
    }
    val count = filtered.size
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 15.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text("Главная")
            }
            IllustratedSectionHeader("Моё настроение",
                "Календарь эмоций, причины и воспоминания о каждом дне")
        }
        if (error.isNotBlank()) item {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        item {
            DiarySectionCard {
                Text("Период наблюдения", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(selected = period == days,
                            onClick = { period = days },
                            label = { Text(days.toString() + " дней") })
                    }
                }
                Text(count.toString() + " дней с отметками",
                    style = MaterialTheme.typography.titleMedium)
                if (count == 0) {
                    Text("Пока нет записей. Отметь настроение в дневнике, чтобы увидеть историю.",
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    moodStyles.forEach { style ->
                        val n = filtered.count { it.mood == style.name }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(style.mark + "  " + style.name)
                                Text(n.toString())
                            }
                            LinearProgressIndicator(
                                progress = { n.toFloat() / count.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().height(10.dp)
                                    .clip(RoundedCornerShape(7.dp)),
                                color = style.color,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            DiarySectionCard {
                Text("Календарь настроения", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("Нажми на день, чтобы дополнить запись.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                val window = 28
                val byDate = entries.associateBy { it.date }
                val start = today.minusDays(window.toLong() - 1)
                repeat(4) { week ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(7) { weekday ->
                            val day = start.plusDays((week * 7 + weekday).toLong())
                            val record = byDate[day.toString()]
                            val style = if (record == null) null else moodStyle(record.mood)
                            Column(Modifier.weight(1f).clickable {
                                onOpenDay(day.toString())
                            }, horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(35.dp)
                                    .clip(CircleShape)
                                    .background(style?.color?.copy(alpha = .26f)
                                        ?: MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) {
                                    Text(style?.mark ?: "·",
                                        color = style?.color
                                            ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold)
                                }
                                Text(day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("Записи и причины", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        if (filtered.isEmpty()) {
            item { DiarySectionCard { Text("Добавь первую отметку через Дневник → Календарь.") } }
        }
        items(filtered, key = { it.date }) { record ->
            val style = moodStyle(record.mood)
            ElevatedCard(onClick = { onOpenDay(record.date) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(35.dp).clip(CircleShape)
                            .background(style.color.copy(alpha = .23f)),
                            contentAlignment = Alignment.Center) {
                            Text(style.mark, color = style.color)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.mood, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text(runCatching { LocalDate.parse(record.date).format(formatter) }
                                .getOrDefault(record.date),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (record.reason.isNotBlank()) {
                        Text("Причина: " + record.reason,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (record.answer.isNotBlank()) {
                        Text(record.answer, maxLines = 3,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Text("Статистика показывает только твои собственные отметки, без медицинских выводов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
