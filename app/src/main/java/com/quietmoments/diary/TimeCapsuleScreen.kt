package com.quietmoments.diary

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCapsuleScreen(onBack: () -> Unit, uid: String = "offline") {
    val context = LocalContext.current
    val repo = remember(uid) { TimeCapsuleRepository(context, uid) }
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(emptyList<TimeCapsule>()) }
    var title by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(LocalDate.now().plusMonths(3)) }
    var attachment by remember { mutableStateOf<Uri?>(null) }
    var attachmentMime by remember { mutableStateOf("") }
    var captureTemp by remember { mutableStateOf<File?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    var selected by remember { mutableStateOf<TimeCapsule?>(null) }
    var toDelete by remember { mutableStateOf<TimeCapsule?>(null) }
    var dateDialog by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun refresh() {
        runCatching { repo.all() }.onSuccess { all = it.sortedByDescending { c -> c.createdAt } }
            .onFailure { error = it.localizedMessage ?: "Не удалось прочитать капсулы" }
    }
    LaunchedEffect(uid) { refresh() }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                error = "Поддерживаются фото и видео"
            } else {
                attachment = uri
                attachmentMime = mime
            }
        }
    }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = captureTemp
        if (ok && file != null && file.length() > 0L) {
            attachment = captureUri
            attachmentMime = "video/mp4"
        } else {
            file?.delete()
            error = "Запись видео не сохранена"
        }
    }

    if (dateDialog) {
        val initialMillis = opening.toEpochDay() * 86_400_000L
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { dateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        opening = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    dateDialog = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { dateDialog = false }) { Text("Отмена") } }
        ) { DatePicker(state = picker) }
    }

    if (selected != null) {
        val item = selected!!
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = { Icon(Icons.Default.MarkEmailRead, contentDescription = null) },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Открыта " + item.openDate, style = MaterialTheme.typography.labelMedium)
                    Text(item.letter.ifBlank { "Текст не добавлен" })
                    if (item.attachment.isNotBlank()) {
                        Button(onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(repo.attachmentUri(item), item.mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            }.onFailure { error = it.localizedMessage ?: "Не удалось открыть вложение" }
                        }) {
                            Icon(if (item.mime.startsWith("video/")) Icons.Default.PlayCircle else
                                Icons.Default.Photo, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("Открыть вложение")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Закрыть") } }
        )
    }
    if (toDelete != null) {
        val item = toDelete!!
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить капсулу?") },
            text = { Text("Письмо и вложение будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repo.delete(item.id) }
                            refresh()
                        } catch (t: Throwable) { error = t.localizedMessage.orEmpty() }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text("Главная")
            }
            IllustratedSectionHeader("Капсула времени",
                "Письмо, фотография или видео для себя в будущем")
        }
        item {
            DiarySectionCard {
                Text("Создать новую капсулу", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                OutlinedTextField(title, { title = it }, label = { Text("Название") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(letter, { letter = it },
                    label = { Text("Письмо себе в будущее") }, minLines = 3,
                    modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { dateDialog = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Event, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Открыть " + opening.toString())
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = {
                        pickMedia.launch(arrayOf("image/*", "video/*"))
                    }) { Text("Добавить фото / видео") }
                    OutlinedButton(onClick = {
                        val file = File(context.filesDir, "captures/" +
                            UUID.randomUUID().toString() + ".mp4")
                        file.parentFile?.mkdirs()
                        captureTemp = file
                        captureUri = FileProvider.getUriForFile(context,
                            context.packageName + ".fileprovider", file)
                        captureVideo.launch(captureUri!!)
                    }) { Text("Записать видео") }
                }
                if (attachment != null) {
                    Text("Вложение выбрано", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        attachment = null
                        attachmentMime = ""
                        captureTemp?.delete()
                        captureTemp = null
                    }) { Text("Убрать вложение") }
                }
                Button(enabled = !busy && title.isNotBlank() &&
                    !opening.isBefore(LocalDate.now()), onClick = {
                    busy = true
                    val name = title
                    val message = letter
                    val date = opening
                    val uri = attachment
                    val mime = attachmentMime
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repo.create(name, message, date, uri, mime)
                            }
                            title = ""; letter = ""; attachment = null
                            attachmentMime = ""; opening = LocalDate.now().plusMonths(3)
                            captureTemp?.delete(); captureTemp = null
                            refresh()
                            Toast.makeText(context, "Капсула сохранена", Toast.LENGTH_SHORT).show()
                        } catch (t: Throwable) {
                            error = t.localizedMessage ?: "Не удалось сохранить капсулу"
                        } finally { busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Запечатать капсулу") }
                Text("Дата ограничивает просмотр только в интерфейсе. Капсулы и резервные ZIP-копии не зашифрованы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (error.isNotBlank()) item {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        item {
            Text("Мои капсулы", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
        if (all.isEmpty()) item {
            DiarySectionCard { Text("Капсул пока нет. Создай первое письмо для будущего.") }
        }
        items(all, key = { it.id }) { item ->
            val opened = item.ready()
            ElevatedCard(shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (opened) Icons.Default.Drafts else Icons.Default.LockClock,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (opened) "Можно открыть" else "Откроется " + item.openDate,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = opened, onClick = { selected = item }) {
                            Text(if (opened) "Открыть" else "Запечатана")
                        }
                        TextButton(onClick = { toDelete = item }) { Text("Удалить") }
                    }
                }
            }
        }
    }
}
