package com.quietmoments.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Explicit, non-destructive data migration between separately installed preview APKs. */
@Composable
fun DataTransferScreen(
    onFinished: () -> Unit,
    onAppearanceRestored: (String, Boolean) -> Unit = { _, _ -> },
    onOpenGoogleDrive: () -> Unit = {},
    uid: String = "offline"
) {
    val context = LocalContext.current
    val repo = remember(uid) { LocalMediaRepository(context, uid) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var candidate by remember { mutableStateOf<Uri?>(null) }
    var overview by remember { mutableStateOf<BackupSummary?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var existingCount by remember { mutableIntStateOf(0) }
    val palette = LocalDiaryPalette.current

    fun inspect(uri: Uri) {
        busy = true
        error = ""
        status = "Проверяем файлы и контрольные суммы…"
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val before = repo.list().size
                    val tested = BackupArchive.inspect(context, uri)
                    before to tested
                }
                candidate = uri
                existingCount = report.first
                overview = report.second
                status = "Архив проверен. Можно выполнить перенос."
            } catch (t: Throwable) {
                candidate = null
                overview = null
                error = t.localizedMessage ?: "Не удалось проверить резервную копию"
                status = ""
            } finally {
                busy = false
            }
        }
    }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) inspect(uri)
    }
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            busy = true
            error = ""
            status = "Создаём и проверяем ZIP-копию…"
            scope.launch {
                try {
                    val summary = withContext(Dispatchers.IO) { repo.exportBackup(uri) }
                    status = "Копия проверена: " +
                        (summary.photos + summary.videos) +
                        " фото и видео, " + summary.diaryDays +
                        " дней дневника, " + summary.capsules + " капсул."
                } catch (t: Throwable) {
                    error = t.localizedMessage ?: "Не удалось создать копию"
                    status = "Проверь файл: создание могло завершиться не полностью."
                } finally { busy = false }
            }
        }
    }

    if (confirm && candidate != null && overview != null) {
        val report = overview!!
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
            title = { Text("Перенести воспоминания?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Будет добавлено из архива: " +
                        (report.photos + report.videos) + " фото и видео, " +
                        report.diaryDays + " дней, " + report.capsules + " капсул.")
                    if (existingCount > 0) {
                        Text("В приложении уже есть " + existingCount +
                            " воспоминаний. Дубликаты по ID пропускаются, старые записи не перезаписываются.")
                    }
                    Text("Данные «Моей вселенной» восстанавливаются при следующем её открытии.")
                    Text("Архив не зашифрован — храните его в защищённом месте.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    busy = true
                    status = "Восстанавливаем данные…"
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) { repo.importBackup(candidate!!) }
                            if (report.hasAppearance) {
                                onAppearanceRestored(
                                    DiaryThemes.load(context),
                                    ThemeMotionPreferences.read(context)
                                )
                            }
                            status = "Перенос завершён. Добавлено " + count +
                                " медиафайлов, остальные данные из архива проверены."
                            // Keep the screen visible so the user can review the result.
                            candidate = null
                            overview = null
                        } catch (t: Throwable) {
                            error = t.localizedMessage ?: "Ошибка восстановления"
                            status = "Импорт не завершён. Исходную версию не удаляйте."
                        } finally { busy = false }
                    }
                }) { Text("Импортировать") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Отмена") }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IllustratedSectionHeader(
            "Перенос воспоминаний",
            "Безопасный переход со старых версий «Тихих моментов»"
        )
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null,
                    tint = palette.primary, modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(11.dp))
                Text("1. В старой версии", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("Открой «Дневник», нажми «Копия» и сохрани ZIP в папку загрузок или на Диск.")
            Text("Не удаляй старое приложение, пока не проверишь восстановление.")
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null,
                    tint = palette.primary, modifier = Modifier.size(29.dp))
                Spacer(Modifier.width(11.dp))
                Text("2. В новой версии", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("Выбери сохранённый ZIP. Перед импортом проверим, что вложения на месте.")
            Button(
                onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать ZIP-копию")
            }
        }
        if (overview != null) {
            val report = overview!!
            DiarySectionCard {
                Text("Состав архива", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Фотографии: " + report.photos)
                Text("Видео: " + report.videos)
                Text("Дни дневника: " + report.diaryDays)
                Text("Капсулы времени: " + report.capsules)
                Text("Анкета: " + if (report.hasProfile) "есть" else "нет")
                Text("Моя вселенная: " + if (report.hasUniverse) "есть" else "нет")
                Text("Обложки альбомов: " + if (report.hasAlbumCovers) "есть" else "нет")
                Text("Выбранная тема: " + if (report.hasAppearance) "есть" else "нет")
                Text("Объём данных: " + (report.bytes / 1024 / 1024) + " МиБ")
                Text(
                    if (report.verified) "Контрольные суммы SHA-256 совпадают."
                    else "Старая ZIP-копия: проверены CRC и наличие файлов, но SHA-256-манифеста нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { confirm = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Перенести данные")
                }
            }
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null,
                    tint = palette.primary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text("Google Диск", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("Можно хранить архив на Google Диске. Выбор облачной папки " +
                "происходит через системное окно Android.")
            OutlinedButton(
                onClick = onOpenGoogleDrive, enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Открыть Google Диск") }
        }
        DiarySectionCard {
            Text("Резервная копия этой версии", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Text("Создай ZIP и сохрани отдельно от телефона. Файл будет проверен после записи.")
            OutlinedButton(
                onClick = {
                    saveBackup.launch("QuietMoments_" + LocalDate.now().toString() + ".zip")
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать проверенную ZIP-копию")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) {
            Surface(color = palette.soft, shape = androidx.compose.foundation.shape.RoundedCornerShape(13.dp)) {
                Text(status, Modifier.fillMaxWidth().padding(12.dp),
                    color = palette.text)
            }
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onFinished,
            enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Продолжить в приложение")
        }
        Text("PIN закрывает экран, но не шифрует ZIP и фотографии. " +
            "Парольные копии и биометрия запланированы отдельно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
    }
}
