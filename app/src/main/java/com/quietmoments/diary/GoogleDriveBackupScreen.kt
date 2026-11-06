package com.quietmoments.diary

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Manual Google Drive backups via the Android DocumentsProvider.
 * No OAuth credentials are stored by this app, and local media is never deleted.
 */
@Composable
fun GoogleDriveBackupScreen(
    onBack: () -> Unit,
    onAppearanceRestored: (String, Boolean) -> Unit = { _, _ -> },
    uid: String = "offline"
) {
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val repo = remember(context, uid) { LocalMediaRepository(context, uid) }
    val preferences = remember(context) {
        context.getSharedPreferences("quietmoments_drive_backup", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var report by remember { mutableStateOf<BackupSummary?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var lastFile by remember { mutableStateOf(preferences.getString("file", "").orEmpty()) }
    var lastProvider by remember { mutableStateOf(preferences.getString("provider", "").orEmpty()) }
    var lastReadable by remember { mutableStateOf(preferences.getString("read_uri", "").orEmpty()) }

    fun providerIsDrive(uri: Uri): Boolean =
        uri.authority.orEmpty().lowercase(Locale.ROOT).contains("google.android.apps.docs")

    fun fileName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull().orEmpty().ifBlank { "QuietMoments_cloud.zip" }.take(170)

    fun inspect(uri: Uri) {
        busy = true
        error = ""
        message = "Проверяем ZIP и все вложения…"
        scope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    BackupArchive.inspect(context, uri)
                }
                pending = uri
                report = value
                message = "Архив проверен. Ниже можно подтвердить восстановление."
            } catch (t: Throwable) {
                pending = null
                report = null
                error = t.localizedMessage ?: "Не удалось прочитать архив"
                message = ""
            } finally { busy = false }
        }
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            busy = true
            error = ""
            message = "Создаём и проверяем копию…"
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { repo.exportBackup(uri) }
                    val onDrive = providerIsDrive(uri)
                    val name = fileName(uri)
                    val persisted = runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }.isSuccess
                    lastReadable = if (persisted) uri.toString() else ""
                    lastFile = name
                    lastProvider = if (onDrive) "Google Диск" else "Другое место хранения"
                    preferences.edit()
                        .putString("file", lastFile)
                        .putString("provider", lastProvider)
                        .putString("read_uri", lastReadable)
                        .putLong("updated", System.currentTimeMillis())
                        .apply()
                    val count = result.photos + result.videos
                    message = if (onDrive)
                        "Копия записана через Google Диск и проверена: " +
                            count + " фото и видео, " + result.capsules + " капсул."
                    else
                        "Копия проверена, но место сохранения не распознано как Google Диск. " +
                            "Проверь выбранную папку."
                } catch (t: Throwable) {
                    error = t.localizedMessage ?: "Ошибка сохранения"
                    message = "Файл мог сохраниться частично. Проверь его и не удаляй старые копии."
                } finally { busy = false }
            }
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) inspect(uri)
    }

    if (confirm && pending != null && report != null) {
        val snapshot = report!!
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Восстановить облачную копию?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Фото: " + snapshot.photos + ", видео: " + snapshot.videos +
                        ", дни дневника: " + snapshot.diaryDays +
                        ", капсулы: " + snapshot.capsules + ".")
                    Text("Существующие записи не перезаписываются; дубликаты пропускаются. «Моя вселенная» восстанавливается только при отсутствии своего сохранения.")
                    Text("Перед восстановлением рекомендуется сохранить текущую копию.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = pending!!
                    confirm = false
                    busy = true
                    error = ""
                    message = "Восстанавливаем данные…"
                    scope.launch {
                        try {
                            val imported = withContext(Dispatchers.IO) {
                                repo.importBackup(target)
                            }
                            if (snapshot.hasAppearance) {
                                onAppearanceRestored(
                                    DiaryThemes.load(context),
                                    ThemeMotionPreferences.read(context)
                                )
                            }
                            message = "Восстановление завершено. Добавлено медиафайлов: " +
                                imported + ". Проверь альбомы и анкету."
                            pending = null
                            report = null
                        } catch (t: Throwable) {
                            error = t.localizedMessage ?: "Ошибка восстановления"
                            message = "Не удаляй исходный ZIP до проверки всех записей."
                        } finally { busy = false }
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Отмена") }
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack, enabled = !busy) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Настройки")
        }
        IllustratedSectionHeader("Google Диск", "Облачные копии личных воспоминаний")

        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null,
                    modifier = Modifier.size(30.dp), tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Сохранить в облако", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("В системном окне Android выбери Google Диск и нужную папку. " +
                "На телефоне должен быть установлен Google Диск с выполненным входом.")
            Button(onClick = {
                val name = "QuietMoments_Drive_" + LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".zip"
                createBackup.launch(name)
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Сделать копию на Google Диске")
            }
            Text("Каждая копия создаётся отдельно: предыдущие архивы не заменяются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (lastFile.isNotBlank()) {
            DiarySectionCard {
                Text("Последняя копия", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(lastFile)
                Text(lastProvider, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lastReadable.isNotBlank()) {
                    OutlinedButton(
                        onClick = { inspect(Uri.parse(lastReadable)) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Проверить файл повторно")
                    }
                } else {
                    Text("Для повторной проверки выбери ZIP вручную.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null,
                    modifier = Modifier.size(30.dp), tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Восстановление из облака",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
            }
            Text("Выбери ZIP-файл на Google Диске. Приложение сначала " +
                "проверит архив и покажет его состав.")
            OutlinedButton(onClick = {
                chooseBackup.launch(arrayOf("application/zip",
                    "application/octet-stream", "application/x-zip-compressed"))
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать ZIP на Google Диске")
            }
        }

        if (report != null) {
            val value = report!!
            DiarySectionCard {
                Text("Содержимое архива", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text("Фото: " + value.photos + " · Видео: " + value.videos)
                Text("Дни дневника: " + value.diaryDays +
                    " · Капсулы: " + value.capsules)
                Text("Анкета: " + (if (value.hasProfile) "есть" else "нет"))
                Text("Моя вселенная: " + (if (value.hasUniverse) "есть" else "нет"))
                Text("Оформление: " + (if (value.hasAppearance) "есть" else "нет"))
                Text("Размер данных: " + (value.bytes / 1024 / 1024) + " МиБ")
                Text(if (value.verified) "SHA-256: целостность подтверждена"
                    else "Старая копия: проверены CRC и наличие файлов",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { confirm = true }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Восстановить записи") }
                TextButton(onClick = {
                    pending = null
                    report = null
                }, enabled = !busy) { Text("Отменить") }
            }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) {
            Surface(color = palette.soft, shape =
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Text(message, Modifier.fillMaxWidth().padding(12.dp), color = palette.text)
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null,
                    tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Как работает облако", style = MaterialTheme.typography.titleMedium)
            }
            Text("Используется безопасный системный выбор файлов Android. " +
                "Приложение не получает пароль Google и не запрашивает доступ ко всему Диску.")
            Text("Копирование запускается вручную. Оригиналы остаются на телефоне; " +
                "автоматическая синхронизация пока не включена.")
            Text("ZIP-копии пока не зашифрованы. Не храни их в общедоступных папках.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Локальная проверка файла не подтверждает завершения удалённой " +
                "синхронизации Google Диска. Дождись её завершения в приложении Google Диск.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
