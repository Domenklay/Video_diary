package com.quietmoments.diary

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.LoginType
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Manual private app-folder backups. Yandex OAuth tokens are never persisted. */
@Composable
fun YandexCloudAccountScreen(
    onBack: () -> Unit,
    uid: String = "offline",
    onAppearanceRestored: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val repository = remember(context, uid) { LocalMediaRepository(context, uid) }
    val prefs = remember(context) {
        context.getSharedPreferences("qm_yandex_backup", Context.MODE_PRIVATE)
    }
    val sdk = remember(context) { YandexAuthSdk.create(YandexAuthOptions(context)) }
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf(emptyList<YandexDiskBackup>()) }
    var selected by remember { mutableStateOf<YandexDiskBackup?>(null) }
    var restoredArchive by remember { mutableStateOf<File?>(null) }
    var inspection by remember { mutableStateOf<BackupSummary?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var showAccountSwitchDialog by remember { mutableStateOf(false) }
    var switchingAccount by remember { mutableStateOf(false) }
    var accountVerified by remember { mutableStateOf(true) }
    var lastSuccess by remember { mutableLongStateOf(prefs.getLong("last_success", 0L)) }

    // Account-specific state MUST be cleared after selecting a different
    // account. ZIPs already stored on the old Disk are never deleted.
    fun clearSessionState(clearLastSuccess: Boolean) {
        backups = emptyList()
        selected = null
        inspection = null
        confirmRestore = false
        restoredArchive?.delete()
        restoredArchive = null
        if (clearLastSuccess) {
            lastSuccess = 0L
            prefs.edit().remove("last_success").apply()
        }
    }

    fun refresh(currentToken: String) {
        busy = true
        scope.launch {
            try {
                backups = withContext(Dispatchers.IO) {
                    YandexDiskCloud(currentToken).backups()
                }
                message = if (backups.isEmpty()) "В папке приложения пока нет резервных копий."
                    else "Список резервных копий обновлён."
                error = ""
            } catch (t: Throwable) {
                error = "Не удалось получить список Яндекс Диска: " +
                    (t.localizedMessage ?: "проверь подключение")
            } finally { busy = false }
        }
    }

    val launcher = rememberLauncherForActivityResult(sdk.contract) { result ->
        when (result) {
            is YandexAuthResult.Success -> {
                val value = result.token.value
                if (value.isBlank()) {
                    busy = false
                    error = "Яндекс не предоставил токен для доступа к папке приложения."
                } else {
                    // Keep the previous account and its archives untouched
                    // until the new authorization succeeds. A cancelled
                    // account change must not disconnect the user.
                    val changed = switchingAccount
                    if (changed) clearSessionState(clearLastSuccess = true)
                    token = value
                    accountVerified = !changed
                    switchingAccount = false
                    message = if (changed) {
                        "Авторизация выполнена. Перед загрузкой убедись, что в Яндекс ID " +
                            "выбран нужный аккаунт; прежние копии остались на старом Диске."
                    } else {
                        "Вход через Яндекс выполнен. Проверяем доступ к резервным копиям."
                    }
                    error = ""
                    refresh(value)
                }
            }
            is YandexAuthResult.Failure -> {
                switchingAccount = false
                busy = false
                error = "Не удалось войти через Яндекс: " +
                    (result.exception.localizedMessage ?: "проверь интернет и регистрацию APK")
            }
            YandexAuthResult.Cancelled -> {
                switchingAccount = false
                busy = false
                message = "Вход отменён. Предыдущий аккаунт и копии не изменились."
            }
        }
    }

    fun uploadNow() {
        if (token.isBlank() || busy || !accountVerified) return
        busy = true
        error = ""
        message = "Подготавливаем и проверяем резервную ZIP-копию…"
        val currentToken = token
        scope.launch {
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    val before = GoogleCloudFiles.fingerprint(context)
                    val temp = GoogleCloudFiles.newFile(context)
                    try {
                        repository.exportBackup(GoogleCloudFiles.uri(context, temp))
                        val afterExport = GoogleCloudFiles.fingerprint(context)
                        val remote = YandexDiskCloud(currentToken).upload(temp)
                        val afterUpload = GoogleCloudFiles.fingerprint(context)
                        remote to CloudSnapshotFingerprint.unchangedDuringUpload(
                            before, afterExport, afterUpload)
                    } finally { temp.delete() }
                }
                lastSuccess = System.currentTimeMillis()
                prefs.edit().putLong("last_success", lastSuccess).apply()
                message = if (uploaded.second) "Копия сохранена на Яндекс Диск: " + uploaded.first.name
                    else "Копия сохранена, но во время загрузки записи изменились. " +
                        "Повтори сохранение, чтобы включить последние изменения."
                try {
                    backups = withContext(Dispatchers.IO) { YandexDiskCloud(currentToken).backups() }
                } catch (t: Throwable) {
                    error = "Загрузка подтверждена, но список не обновился: " +
                        (t.localizedMessage ?: "проверь интернет")
                }
            } catch (t: Throwable) {
                error = "Копия не подтверждена Яндексом: " +
                    (t.localizedMessage ?: "проверь место на Диске и доступ к интернету")
                message = "Локальные записи сохранены; существующие копии не удалены."
            } finally { busy = false }
        }
    }

    fun inspectRemote(backup: YandexDiskBackup) {
        if (busy || token.isBlank()) return
        busy = true
        error = ""
        message = "Скачиваем и проверяем выбранную ZIP-копию…"
        selected = backup
        val currentToken = token
        scope.launch {
            val temp = GoogleCloudFiles.newFile(context)
            try {
                val checked = withContext(Dispatchers.IO) {
                    YandexDiskCloud(currentToken).download(backup, temp)
                    BackupArchive.inspect(context, GoogleCloudFiles.uri(context, temp))
                }
                restoredArchive?.delete()
                restoredArchive = temp
                inspection = checked
                message = "Архив проверен. Можно восстановить данные."
            } catch (t: Throwable) {
                temp.delete()
                inspection = null
                selected = null
                error = "Не удалось проверить копию: " +
                    (t.localizedMessage ?: "архив недоступен")
            } finally { busy = false }
        }
    }

    DisposableEffect(restoredArchive) {
        val temp = restoredArchive
        onDispose { temp?.delete() }
    }

    if (showAccountSwitchDialog) {
        AlertDialog(
            onDismissRequest = { showAccountSwitchDialog = false },
            title = { Text("Сменить Яндекс-аккаунт?") },
            text = {
                Text("QuietMoments откроет вход в Яндекс через браузер. " +
                    "В Яндекс ID выбери другой аккаунт или нажми «Войти под другим логином». " +
                    "Если браузер сразу выбрал прежний аккаунт, переключи его вручную. " +
                    "Старые облачные копии не удалятся, а отмена сохранит текущее подключение.")
            },
            confirmButton = {
                Button(onClick = {
                    showAccountSwitchDialog = false
                    busy = true
                    switchingAccount = true
                    error = ""
                    message = "Выбери нужный Яндекс-аккаунт в браузере."
                    runCatching {
                        launcher.launch(YandexAuthLoginOptions(LoginType.CHROME_TAB))
                    }.onFailure {
                        switchingAccount = false
                        busy = false
                        error = "Не удалось открыть выбор аккаунта: " +
                            (it.localizedMessage ?: "повтори попытку")
                    }
                }) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { showAccountSwitchDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (confirmRestore && restoredArchive != null && inspection != null) {
        val summary = inspection!!
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Восстановить воспоминания?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Фото: ${summary.photos}, видео: ${summary.videos}, " +
                        "дни дневника: ${summary.diaryDays}, капсулы: ${summary.capsules}.")
                    Text("Архив проверен. Существующие записи не заменяются.")
                    Text("«Моя вселенная» восстановится только при отсутствии " +
                        "своего сохранения. Исходная копия останется на Яндекс Диске.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val file = restoredArchive ?: return@Button
                    confirmRestore = false
                    busy = true
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) {
                                repository.importBackup(GoogleCloudFiles.uri(context, file))
                            }
                            if (summary.hasAppearance) {
                                onAppearanceRestored(DiaryThemes.load(context),
                                    ThemeMotionPreferences.read(context))
                            }
                            message = "Восстановление завершено. Добавлено медиафайлов: $count. " +
                                "Проверь дневник, капсулы и «Мою вселенную»."
                            inspection = null
                            selected = null
                            restoredArchive = null
                            file.delete()
                        } catch (t: Throwable) {
                            error = "Ошибка восстановления: " +
                                (t.localizedMessage ?: "исходный архив не изменён")
                        } finally { busy = false }
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Отмена") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TextButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text("Настройки")
            }
            IllustratedSectionHeader("Мой Яндекс Диск", "Отдельное облако для воспоминаний")
        }
        item {
            DiarySectionCard {
                Text("Вход через Яндекс ID", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Приложение получает доступ только к своей папке на Яндекс Диске. " +
                    "Другие файлы Диска недоступны.")
                if (token.isBlank()) {
                    Button(onClick = {
                        if (!busy) {
                            busy = true
                            error = ""
                            runCatching { launcher.launch(YandexAuthLoginOptions()) }
                                .onFailure {
                                    busy = false
                                    error = it.localizedMessage ?: "Не удалось открыть Яндекс ID"
                                }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Войти через Яндекс")
                    }
                } else {
                    if (!accountVerified) {
                        Text("Перед сохранением проверь, что при входе выбран нужный " +
                            "Яндекс-аккаунт. Браузер может автоматически открыть старый.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = accountVerified,
                                onCheckedChange = { accountVerified = it }
                            )
                            Text("Подтверждаю: я выбрала нужный аккаунт Яндекса.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(onClick = { uploadNow() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && accountVerified) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить воспоминания в Яндекс")
                    }
                    OutlinedButton(
                        onClick = { showAccountSwitchDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сменить Яндекс-аккаунт")
                    }
                    TextButton(onClick = {
                        token = ""
                        accountVerified = true
                        clearSessionState(clearLastSuccess = true)
                        message = "Яндекс отключён в этом сеансе. " +
                            "Облачные копии не удалены. В Яндекс ID авторизация может сохраниться."
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text("Отключить Яндекс")
                    }
                }
                if (lastSuccess > 0) {
                    val date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(lastSuccess))
                    Text("Последняя подтверждённая копия: $date",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Сохранение пока выполняется вручную. Автосинхронизация Яндекса " +
                    "будет отдельной функцией после проверки на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (token.isNotBlank()) {
            item {
                DiarySectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Мои копии на Яндекс Диске",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh(token) }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                    Text("Папка приложения защищена отдельным разрешением. " +
                        "Выбери копию для проверки и восстановления.",
                        style = MaterialTheme.typography.bodySmall)
                    if (backups.isEmpty()) Text("Пока нет облачных сохранений.")
                }
            }
            items(backups, key = { it.path }) { backup ->
                ElevatedCard(onClick = { inspectRemote(backup) }, enabled = !busy) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null,
                            tint = palette.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(backup.name, maxLines = 2, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${backup.size / 1024} КиБ · " + backup.modified.take(10),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (inspection != null && restoredArchive != null) {
                item {
                    val details = inspection!!
                    DiarySectionCard {
                        Text("Проверенный архив", style = MaterialTheme.typography.titleLarge)
                        Text("Фото: ${details.photos} · Видео: ${details.videos}")
                        Text("Дни дневника: ${details.diaryDays} · Капсулы: ${details.capsules}")
                        Text(if (details.verified) "Контрольные суммы совпадают"
                            else "Проверены CRC и структура старого архива")
                        Button(onClick = { confirmRestore = true },
                            enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Восстановить выбранную копию")
                        }
                    }
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotBlank()) item { Text(message) }
        if (error.isNotBlank()) item {
            DiarySectionCard {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            DiarySectionCard {
                Text("Безопасность", style = MaterialTheme.typography.titleMedium)
                Text("ZIP-копии пока не зашифрованы отдельным паролем. " +
                    "Не сохраняй чувствительные файлы без дополнительной защиты.")
                Text("Яндекс ID открывается через официальный LoginSDK. " +
                    "Пароль и Client secret приложение не запрашивает и не хранит.")
            }
        }
    }
}
