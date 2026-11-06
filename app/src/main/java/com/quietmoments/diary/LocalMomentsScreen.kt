package com.quietmoments.diary

import android.content.Intent
import java.io.File
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.FileProvider
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMomentsScreen(uid: String, onClose: () -> Unit, pinLock: DiaryPinLock, initialDate: String = LocalDate.now().toString()) {
    val context = LocalContext.current
    val repo = remember(uid) { LocalMediaRepository(context, uid) }
    val journal = remember(uid) { LocalJournal(context, uid) }
    var entries by remember(uid) { mutableStateOf(emptyList<DailyReflection>()) }
    var day by remember(initialDate) { mutableStateOf(initialDate) }
    var choosingDate by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("Спокойно") }
    var journalOpen by remember(initialDate) { mutableStateOf(false) }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    var moments by remember(uid) { mutableStateOf(emptyList<LocalMoment>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var album by remember { mutableStateOf("Мои моменты") }
    var title by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf("Все") }
    var showTrash by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LocalMoment?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var calendarVisible by remember { mutableStateOf(true) }

    fun refresh() {
        runCatching { repo.list() }.onSuccess { moments = it }.onFailure { error = it.localizedMessage.orEmpty() }
        runCatching { journal.all() }.onSuccess { entries = it }.onFailure { error = it.localizedMessage.orEmpty() }
    }
    fun selectDate(value: LocalDate) {
        day = value.toString()
        val old = entries.find { it.date == day }
        answer = old?.answer ?: ""
        mood = old?.mood ?: "Спокойно"
    }
    LaunchedEffect(uid) {
        runCatching {
            withContext(Dispatchers.IO) { repo.list() to journal.all() }
        }.onSuccess { (files, reflections) ->
            moments = files
            entries = reflections
            selectDate(runCatching { LocalDate.parse(initialDate) }.getOrDefault(LocalDate.now()))
        }.onFailure { error = it.localizedMessage ?: "Не удалось открыть локальный каталог" }
    }

    fun runTask(block: suspend () -> String) {
        scope.launch {
            busy = true
            try {
                val result = withContext(Dispatchers.IO) { block() }
                refresh()
                Toast.makeText(context, result, Toast.LENGTH_LONG).show()
            } catch (t: Throwable) { error = t.localizedMessage ?: "Ошибка операции" }
            finally { busy = false }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            runTask {
                repo.add(uri, title, album, mime, day)
                "Файл сохранён локально"
            }
        }
    }
    val captureVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        val file = captureFile
        val uri = captureUri
        if (saved && file != null && uri != null && file.exists() && file.length() > 0) {
            runTask {
                try { repo.add(uri, title.ifBlank { "Видео от $day" }, album, "video/mp4", day); "Видео сохранено" }
                finally { file.delete() }
            }
        } else { file?.delete(); error = "Запись отменена или видео не сохранено" }
        captureFile = null
        captureUri = null
    }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) runTask { repo.exportBackup(uri); "Резервная копия сохранена" } }
    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) runTask {
        val count = repo.importBackup(uri)
        "Добавлено записей: $count"
    } }

    if (choosingDate) {
        val chosenMillis = runCatching { LocalDate.parse(day).toEpochDay() * 86_400_000L }
            .getOrDefault(LocalDate.now().toEpochDay() * 86_400_000L)
        val picker = rememberDatePickerState(initialSelectedDateMillis = chosenMillis)
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        selectDate(LocalDate.ofEpochDay(millis / 86_400_000L))
                    }
                    choosingDate = false
                }) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { choosingDate = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = picker, showModeToggle = true)
        }
    }

    if (editing != null) AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text("Редактировать воспоминание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = editTitle, onValueChange = { editTitle = it },
                    label = { Text("Название") }, singleLine = true)
                OutlinedTextField(value = editAlbum, onValueChange = { editAlbum = it },
                    label = { Text("Альбом") }, singleLine = true)
                OutlinedTextField(value = editDate, onValueChange = { editDate = it },
                    label = { Text("Дата воспоминания ГГГГ-ММ-ДД") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = editTitle.isNotBlank() && runCatching { LocalDate.parse(editDate) }.isSuccess && !busy, onClick = {
            val id = editing!!.id
            editing = null
            runTask { repo.updateDetails(id, editTitle, editAlbum, editDate); "Изменения сохранены" }
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("Отмена") } }
    )
    if (deleteId != null) AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text("Удалить безвозвратно?") },
        text = { Text("Файл будет удалён с устройства. Отменить это действие нельзя.") },
        confirmButton = { TextButton(onClick = { val id = deleteId!!; deleteId = null; runTask { repo.deleteForever(id); "Удалено" } }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Отмена") } }
    )
    val albums = listOf("Все") + moments.filter { it.deletedAt == 0L }.map { it.album }.distinct().sorted()
    val shown = moments.filter {
        (it.deletedAt != 0L) == showTrash &&
            (showTrash || selectedAlbum == "Все" || it.album == selectedAlbum)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            LocalDiaryHeading("Мой видеодневник", "Фото, видео и дорогие сердцу дни",
                modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { journalOpen = !journalOpen }, modifier = Modifier.weight(1f)) {
                Text(if (journalOpen) "Скрыть календарь" else "Календарь")
            }
            Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                val file = File(context.filesDir, "captures/${UUID.randomUUID()}.mp4")
                file.parentFile?.mkdirs()
                captureFile = file
                captureUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                captureVideo.launch(captureUri!!)
            }) { Text("Записать видео") }
        }
        Text("Все воспоминания хранятся только на твоём устройстве", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (journalOpen) {
            Text("Ежедневный вопрос", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { choosingDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать дату: $day")
            }
            val selectedDate = runCatching { LocalDate.parse(day) }.getOrDefault(LocalDate.now())
            val activeDates = remember(entries, moments) {
                entries.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet() +
                    moments.filter { it.deletedAt == 0L }.map {
                        runCatching { LocalDate.parse(it.memoryDate) }.getOrDefault(LocalDate.now())
                    }
            }
            DiaryMonthCalendar(selectedDate, activeDates, onDate = { selectDate(it) })
            Text(LocalJournal.questionFor(selectedDate))
            OutlinedTextField(answer, { answer = it }, label = { Text("Ответ") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Радость","Спокойно","Грусть","Тревога").forEach { item ->
                    FilterChip(selected = mood == item, onClick = { mood = item }, label = { Text(item) })
                }
            }
            Button(enabled = !busy, onClick = { runTask {
                journal.save(DailyReflection(day, LocalJournal.questionFor(selectedDate), answer, mood))
                "Ответ сохранён"
            }}) { Text("Сохранить день") }
            Text("Статистика настроения", style = MaterialTheme.typography.titleSmall)
            entries.groupingBy { it.mood }.eachCount().forEach { (label, count) -> Text("$label — $count") }
        }
        DiarySectionCard {
            Text("Добавить воспоминание · $day", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(album, { album = it }, label = { Text("Альбом") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { pickMedia.launch(arrayOf("image/*", "video/*")) }) { Text("Добавить") }
            OutlinedButton(enabled = !busy, onClick = { createBackup.launch("QuietMoments_backup.zip") }) { Text("Копия") }
            OutlinedButton(enabled = !busy, onClick = { restoreBackup.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Импорт") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showTrash, onClick = { showTrash = false; selectedAlbum = "Все" }, label = { Text("Альбомы") })
            FilterChip(selected = showTrash, onClick = { showTrash = true; selectedAlbum = "Все" }, label = { Text("Корзина") })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        ScrollableTabRow(selectedTabIndex = albums.indexOf(selectedAlbum).coerceAtLeast(0)) {
            albums.forEach { name ->
                Tab(selected = selectedAlbum == name, onClick = { selectedAlbum = name },
                    text = { Text(name) })
            }
        }
        Text(if (showTrash) "Корзина · ${shown.size}" else "Воспоминания · ${shown.size}",
            style = MaterialTheme.typography.titleMedium)
        Text("Календарь: ${moments.count { it.deletedAt == 0L }} медиафайлов и ${entries.size} записей о настроении", style = MaterialTheme.typography.bodySmall)

            }
        }

            items(shown, key = { it.id }) { moment ->
                ElevatedCard(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = {
                    if (showTrash) return@ElevatedCard
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(repo.contentUri(moment),
                                if (moment.kind == "video") "video/mp4" else "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }.onFailure { error = it.localizedMessage ?: "Не удалось открыть файл" }
                }) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MomentThumbnail(repo, moment)
                            Column(Modifier.weight(1f)) {
                                Text(moment.title, style = MaterialTheme.typography.titleMedium)
                                Text("${moment.album} · ${if (moment.kind == "video") "Видео" else "Фото"}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(moment.memoryDate,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (showTrash) {
                            Row {
                                TextButton(enabled = !busy, onClick = { runTask { repo.restoreFromTrash(moment.id); "Восстановлено" } }) { Text("Восстановить") }
                                TextButton(enabled = !busy, onClick = { deleteId = moment.id }) { Text("Удалить навсегда") }
                            }
                        } else {
                            Row {
                                TextButton(enabled = !busy, onClick = {
                                    editing = moment
                                    editTitle = moment.title
                                    editAlbum = moment.album
                                    editDate = moment.memoryDate
                                }) { Text("Изменить") }
                                TextButton(enabled = !busy, onClick = {
                                    runTask { repo.moveToTrash(moment.id); "Перемещено в корзину" }
                                }) { Text("В корзину") }
                            }
                        }
                    }
                }
            }
    }
}
