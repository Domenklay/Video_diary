package com.quietmoments.diary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A single decorated scrapbook page with an optional note and theme. */
@Composable
fun MemoryPageDialog(
    repo: LocalMediaRepository,
    moment: LocalMoment,
    reflection: DailyReflection?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit,
    onOpenMedia: () -> Unit
) {
    var title by remember(moment.id) { mutableStateOf(moment.title) }
    var album by remember(moment.id) { mutableStateOf(moment.album) }
    var date by remember(moment.id) { mutableStateOf(moment.memoryDate) }
    var note by remember(moment.id) { mutableStateOf(moment.note) }
    var style by remember(moment.id) {
        mutableStateOf(DiaryThemes.byId(moment.pageStyle).id)
    }
    val palette = DiaryThemes.byId(style)
    val source = remember(moment.id) { repo.thumbnailFile(moment) }
    val image by produceState<Bitmap?>(initialValue = null, source, moment.kind) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (moment.kind == "video") {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(source.absolutePath)
                        if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(-1L,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 960, 540)
                        } else retriever.getFrameAtTime()
                    } finally { retriever.release() }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(source.absolutePath, bounds)
                    val size = maxOf(bounds.outWidth, bounds.outHeight)
                    val ratio = if (size > 1400) (size / 1400).coerceAtLeast(1) else 1
                    BitmapFactory.decodeFile(source.absolutePath,
                        BitmapFactory.Options().apply { inSampleSize = ratio })
                }
            }.getOrNull()
        }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.94f),
            shape = RoundedCornerShape(26.dp),
            color = palette.surface,
            tonalElevation = 4.dp
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(bottom = 18.dp)) {
                    Box(Modifier.fillMaxWidth().height(145.dp)
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(Brush.linearGradient(listOf(
                            palette.heroStart, palette.heroEnd)))) {
                        IllustratedThemeBackground(palette, Modifier.fillMaxSize(),
                            preview = true)
                        Box(Modifier.matchParentSize().background(
                            Brush.horizontalGradient(listOf(
                                Color(0xDD171124), Color(0x4B171124)))))
                        DecorativeFrame(palette, Modifier.matchParentSize())
                        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                            Text("СТРАНИЦА ВОСПОМИНАНИЙ",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.accent, fontWeight = FontWeight.Bold)
                            Text(moment.title, style = MaterialTheme.typography.titleLarge,
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Column(Modifier.padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (image != null) {
                            Image(image!!.asImageBitmap(), contentDescription = "Воспоминание",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(15.dp)))
                        } else {
                            Card(colors = CardDefaults.cardColors(
                                containerColor = palette.soft)) {
                                Row(Modifier.fillMaxWidth().padding(19.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (moment.kind == "video") Icons.Default.Videocam
                                        else Icons.Default.Image, contentDescription = null)
                                    Spacer(Modifier.width(9.dp))
                                    Text(if (moment.kind == "video") "Видео" else "Фотография")
                                }
                            }
                        }
                        OutlinedButton(onClick = onOpenMedia, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (moment.kind == "video") "Смотреть видео" else "Открыть фотографию")
                        }
                        if (reflection != null) {
                            Card(colors = CardDefaults.cardColors(
                                containerColor = palette.soft)) {
                                Column(Modifier.padding(13.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Настроение: " + reflection.mood,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = palette.text)
                                    if (reflection.answer.isNotBlank()) {
                                        Text(reflection.answer, color = palette.text,
                                            style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (reflection.reason.isNotBlank()) {
                                        Text("Почему: " + reflection.reason,
                                            color = palette.muted,
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        Text("Оформление страницы", color = palette.text,
                            style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DiaryThemes.all.forEach { theme ->
                                FilterChip(selected = style == theme.id,
                                    onClick = { style = theme.id },
                                    label = { Text(theme.name) })
                            }
                        }
                        OutlinedTextField(title, { title = it },
                            label = { Text("Название") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(album, { album = it },
                            label = { Text("Альбом") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(date, { date = it },
                            label = { Text("Дата (ГГГГ-ММ-ДД)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(note, { note = it },
                            label = { Text("Личная заметка к воспоминанию") },
                            minLines = 3, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss,
                                modifier = Modifier.weight(1f)) { Text("Закрыть") }
                            Button(enabled = title.isNotBlank() &&
                                    runCatching { LocalDate.parse(date) }.isSuccess,
                                onClick = { onSave(title, album, date, note, style) },
                                modifier = Modifier.weight(1f)) { Text("Сохранить") }
                        }
                    }
                }
            }
        }
    }
}
