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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocalMomentsScreen(uid: String, onClose: () -> Unit, pinLock: DiaryPinLock,
    initialDate: String = LocalDate.now().toString(),
    openJournalRequest: Int = 0,
    openMomentRequest: Int = 0, openMomentId: String = "",
    onOpenMoods: () -> Unit = {}, onOpenTransfer: () -> Unit = {}) {
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val repo = remember(uid) { LocalMediaRepository(context, uid) }
    val journal = remember(uid) { LocalJournal(context, uid) }
    val profile = remember(uid) { runCatching { DiaryProfileRepository(context, uid).read() }.getOrDefault(DiaryProfile()) }
    var entries by remember(uid) { mutableStateOf(emptyList<DailyReflection>()) }
    var day by rememberSaveable { mutableStateOf(initialDate) }
    var loadedForDate by rememberSaveable { mutableStateOf("") }
    var choosingDate by remember { mutableStateOf(false) }
    var answer by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf(profile.favoriteMood) }
    var moodReason by rememberSaveable { mutableStateOf("") }
    var journalOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var captureFile by remember { mutableStateOf<File?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()
    var moments by remember(uid) { mutableStateOf(emptyList<LocalMoment>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var album by rememberSaveable { mutableStateOf("Мои моменты") }
    var title by rememberSaveable { mutableStateOf("") }
    var selectedAlbum by rememberSaveable { mutableStateOf("Все") }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<LocalMoment?>(null) }
    var pageMoment by remember { mutableStateOf<LocalMoment?>(null) }
    var lastHandledMomentRequest by rememberSaveable { mutableIntStateOf(0) }
    var editingCover by remember { mutableStateOf<String?>(null) }
    var coverRevision by remember { mutableStateOf(0) }
    var editTitle by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var calendarVisible by rememberSaveable { mutableStateOf(false) }
    var addMomentOpen by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        runCatching { repo.list() }.onSuccess { moments = it }.onFailure { error = it.localizedMessage.orEmpty() }
        runCatching { journal.all() }.onSuccess { entries = it }.onFailure { error = it.localizedMessage.orEmpty() }
    }
    fun selectDate(value: LocalDate) {
        day = value.toString()
        val old = entries.find { it.date == day }
        answer = old?.answer ?: ""
        mood = old?.mood ?: profile.favoriteMood
        moodReason = old?.reason ?: ""
    }
    LaunchedEffect(uid, initialDate, openMomentRequest) {
        runCatching {
            withContext(Dispatchers.IO) { repo.list() to journal.all() }
        }.onSuccess { (files, reflections) ->
            moments = files
            entries = reflections
            // A tap on a recent home-page photo opens its exact memory, not
            // merely the general album list. Consume only once per request.
            if (openMomentRequest > lastHandledMomentRequest) {
                pageMoment = files.firstOrNull {
                    it.id == openMomentId && it.deletedAt == 0L
                }
                lastHandledMomentRequest = openMomentRequest
            }
            // Returning to a tab must not erase an unsaved answer or album draft.
            if (loadedForDate != initialDate) {
                selectDate(runCatching { LocalDate.parse(initialDate) }.getOrDefault(LocalDate.now()))
                loadedForDate = initialDate
            }
        }.onFailure { error = it.localizedMessage ?: "Не удалось открыть локальный каталог" }
    }

    LaunchedEffect(openJournalRequest) {
        if (openJournalRequest > 0) {
            journalOpen = true
            calendarVisible = false
        }
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
    ) { uri -> if (uri != null) runTask {
        val details = repo.exportBackup(uri)
        "ZIP проверен: " + (details.photos + details.videos) +
            " медиафайлов, " + details.diaryDays + " дней, " +
            details.capsules + " капсул"
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

    if (pageMoment != null) {
        val moment = pageMoment!!
        MemoryPageDialog(
            repo = repo, moment = moment,
            reflection = entries.firstOrNull { it.date == moment.memoryDate },
            onDismiss = { pageMoment = null },
            onSave = { name, albumName, dateValue, noteValue, styleValue ->
                pageMoment = null
                runTask {
                    repo.updateDetails(moment.id, name, albumName,
                        dateValue, noteValue, styleValue)
                    "Страница воспоминания сохранена"
                }
            },
            onOpenMedia = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(repo.contentUri(moment),
                            if (moment.kind == "video") "video/mp4" else "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }.onFailure { error = it.localizedMessage ?: "Не удалось открыть медиа" }
            }
        )
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
    if (editingCover != null) {
        val chosenAlbum = editingCover!!
        val currentCover = remember(chosenAlbum, coverRevision, palette.id) {
            repo.albumCoverStyle(chosenAlbum, palette.id)
        }
        AlbumCoverPicker(
            albumName = chosenAlbum,
            currentStyle = currentCover,
            onChoose = { style ->
                runCatching { repo.setAlbumCoverStyle(chosenAlbum, style) }
                    .onSuccess { coverRevision++; editingCover = null }
                    .onFailure { error = it.localizedMessage ?: "Не удалось сменить обложку" }
            },
            onDismiss = { editingCover = null }
        )
    }
    val liveMoments = moments.filter { it.deletedAt == 0L }
    val albums = listOf("Все") + liveMoments.map { it.album }.distinct().sorted()
    val shown = moments.filter {
        (it.deletedAt != 0L) == showTrash &&
            (showTrash || searchQuery.isNotBlank() || selectedAlbum == "Все" || it.album == selectedAlbum) &&
            MomentSearch.matches(it.title, it.album, it.note, it.memoryDate, searchQuery)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        IllustratedSectionHeader(
            "Мой видеодневник", "Фото, видео и дорогие сердцу дни", compact = true
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { journalOpen = !journalOpen },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.surface.copy(alpha = .96f),
                    contentColor = palette.text
                ),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = .6f))
            ) {
                Text(if (journalOpen) "Скрыть вопрос" else "Вопрос дня")
            }
            Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                val file = File(context.filesDir, "captures/${UUID.randomUUID()}.mp4")
                file.parentFile?.mkdirs()
                captureFile = file
                captureUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                captureVideo.launch(captureUri!!)
            }) { Text("Записать видео") }
        }
        Text(
            "Оригиналы — на телефоне, резервные копии — по твоему выбору в облаке",
            style = MaterialTheme.typography.labelSmall,
            color = palette.text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(palette.surface.copy(alpha = .9f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
        if (journalOpen && searchQuery.isBlank()) {
            val selectedDate = runCatching { LocalDate.parse(day) }.getOrDefault(LocalDate.now())
            val currentQuestion = entries.firstOrNull { it.date == day }?.question
                ?: LocalJournal.questionFor(selectedDate, profile.questionTheme)
            DiarySectionCard {
                Text("Ежедневный вопрос", style = MaterialTheme.typography.titleMedium,
                    color = palette.text)
                OutlinedButton(onClick = { choosingDate = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Дата: $day · изменить")
                }
                Text(currentQuestion, style = MaterialTheme.typography.titleMedium,
                    color = palette.text)
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Мой ответ") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 8
                )
                Text("Как ты себя чувствуешь?", style = MaterialTheme.typography.bodyMedium,
                    color = palette.text)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DiaryProfile.moods.forEach { item ->
                        FilterChip(
                            selected = mood == item,
                            onClick = { mood = item },
                            label = { Text(item, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = palette.surface,
                                labelColor = palette.text,
                                selectedContainerColor = palette.soft,
                                selectedLabelColor = palette.text
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = moodReason,
                    onValueChange = { moodReason = it },
                    label = { Text("Почему такое настроение?") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { runTask {
                        journal.save(DailyReflection(day, currentQuestion, answer, mood, moodReason))
                        "Ответ сохранён"
                    } }) { Text("Сохранить день") }
                TextButton(onClick = onOpenMoods, modifier = Modifier.fillMaxWidth()) {
                    Text("Календарь эмоций и статистика")
                }
            }
            OutlinedButton(
                onClick = { calendarVisible = !calendarVisible },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.surface.copy(alpha = .97f),
                    contentColor = palette.text
                ),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = .62f))
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null,
                    modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (calendarVisible) "Скрыть календарь месяца"
                    else "Показать календарь месяца")
            }
            if (calendarVisible) {
                val activeDates = remember(entries, moments) {
                    entries.mapNotNull {
                        runCatching { LocalDate.parse(it.date) }.getOrNull()
                    }.toSet() + moments.filter { it.deletedAt == 0L }.mapNotNull {
                        runCatching { LocalDate.parse(it.memoryDate) }.getOrNull()
                    }
                }
                DiaryMonthCalendar(selectedDate, activeDates, onDate = { selectDate(it) })
            }
        }
        if (searchQuery.isBlank()) {
            OutlinedButton(
                onClick = { addMomentOpen = !addMomentOpen },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.surface.copy(alpha = .97f),
                    contentColor = palette.text
                ),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = .62f))
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                    modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (addMomentOpen) "Свернуть добавление"
                    else "Добавить фото или видео")
            }
            if (addMomentOpen) {
                DiarySectionCard {
                    Text("Новое воспоминание · $day",
                        style = MaterialTheme.typography.titleMedium, color = palette.text)
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        value = album, onValueChange = { album = it },
                        label = { Text("Альбом") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        onClick = { pickMedia.launch(arrayOf("image/*", "video/*")) }) {
                        Text("Выбрать фото или видео")
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                    onClick = { createBackup.launch("QuietMoments_backup.zip") }) {
                    Text("ZIP-копия")
                }
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f),
                    onClick = onOpenTransfer) {
                    Text("Импорт ZIP")
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(120) },
            label = { Text("Поиск воспоминаний") },
            placeholder = { Text("Название, альбом, дата или заметка") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить поиск")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showTrash, onClick = { showTrash = false; selectedAlbum = "Все" }, label = { Text("Альбомы") })
            FilterChip(selected = showTrash, onClick = { showTrash = true; selectedAlbum = "Все" }, label = { Text("Корзина") })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        if (!showTrash && searchQuery.isBlank() && albums.size > 1) {
            Text("Иллюстрированные обложки", style = MaterialTheme.typography.titleMedium)
            Text("Нажми на альбом, чтобы открыть, или на карандаш, чтобы сменить рисунок.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 5.dp)) {
                items(albums.drop(1), key = { it }) { albumName ->
                    val cover = remember(albumName, coverRevision, palette.id) {
                        repo.albumCoverStyle(albumName, palette.id)
                    }
                    val related = remember(liveMoments, albumName) {
                        liveMoments.filter { it.album == albumName }
                    }
                    AlbumCoverCard(
                        repo = repo, name = albumName, count = related.size,
                        previewMoment = related.maxByOrNull { it.createdAt },
                        coverId = cover, selected = selectedAlbum == albumName,
                        onOpen = { selectedAlbum = albumName },
                        onEdit = { editingCover = albumName }
                    )
                }
            }
        }
        if (!showTrash && searchQuery.isBlank()) {
            ScrollableTabRow(selectedTabIndex = albums.indexOf(selectedAlbum).coerceAtLeast(0)) {
                albums.forEach { name ->
                    Tab(selected = selectedAlbum == name, onClick = { selectedAlbum = name },
                        text = { Text(name) })
                }
            }
        }
        if (searchQuery.isBlank()) {
            IllustratedSectionHeader(
                if (showTrash) "Корзина" else "Мои альбомы",
                if (showTrash) "Удалённые воспоминания: ${shown.size}"
                else "Фото и видео: ${shown.size}", compact = true
            )
        } else {
            Text("Результаты поиска", style = MaterialTheme.typography.titleMedium,
                color = palette.text)
        }
        Text(if (searchQuery.isBlank()) "Всего медиафайлов: ${moments.count { it.deletedAt == 0L }} · Записей дня: ${entries.size}"
             else "Найдено: ${shown.size}", style = MaterialTheme.typography.bodySmall)
        if (shown.isEmpty() && searchQuery.isNotBlank()) {
            Text("По этому запросу ничего не найдено. Попробуй другое слово или очисти поиск.",
                style = MaterialTheme.typography.bodySmall)
        }

            }
        }

            items(shown, key = { it.id }) { moment ->
                ElevatedCard(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    onClick = {
                    if (showTrash) return@ElevatedCard
                    pageMoment = moment
                }) {
                    Box(Modifier.fillMaxWidth()) {
                        IllustratedThemeBackground(
                            palette, Modifier.matchParentSize(), preview = true, alpha = .23f
                        )
                        Box(Modifier.matchParentSize().background(
                            palette.surface.copy(alpha = .82f)
                        ))
                        DecorativeFrame(palette, Modifier.matchParentSize(), subtle = true)
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MomentThumbnail(repo, moment)
                            Column(Modifier.weight(1f)) {
                                Text(moment.title, style = MaterialTheme.typography.titleMedium)
                                Text("${moment.album} · ${if (moment.kind == "video") "Видео" else "Фото"}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(moment.memoryDate,
                                    style = MaterialTheme.typography.labelSmall)
                                if (!showTrash) Text("Открыть страницу воспоминания",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
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
}
