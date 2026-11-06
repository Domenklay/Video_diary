package com.quietmoments.diary

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.AuthorizationRequest.Prompt
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object CloudConsent {
    fun request(switchAccount: Boolean = false): AuthorizationRequest {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("openid"), Scope("email"), Scope("profile")
        ))
        if (switchAccount) builder.setPrompt(Prompt.SELECT_ACCOUNT)
        return builder.build()
    }
}

@Composable
fun GoogleCloudAccountScreen(onBack: () -> Unit, uid: String = "offline",
    onAppearanceRestored: (String, Boolean) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val client = remember(context) { Identity.getAuthorizationClient(context) }
    val repo = remember(context, uid) { LocalMediaRepository(context, uid) }
    val prefs = remember(context) {
        context.getSharedPreferences("qm_cloud_account", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(prefs.getString("email", "").orEmpty()) }
    var displayName by remember { mutableStateOf(prefs.getString("name", "").orEmpty()) }
    var activeToken by remember { mutableStateOf("") }
    var backups by remember { mutableStateOf(emptyList<GoogleCloudBackup>()) }
    var selected by remember { mutableStateOf<GoogleCloudBackup?>(null) }
    var downloadFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<BackupSummary?>(null) }
    var autoEnabled by remember { mutableStateOf(prefs.getBoolean("auto", false)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }

    fun authReady(result: AuthorizationResult) {
        val token = result.accessToken.orEmpty()
        if (token.isBlank()) { error = "Google не предоставил доступ"; busy = false; return }
        scope.launch {
            try {
                val pair = withContext(Dispatchers.IO) {
                    val api = GoogleCloudDrive(token)
                    api.account() to api.backups()
                }
                val account = pair.first
                val savedId = prefs.getString("subject", "").orEmpty()
                if (savedId.isNotEmpty() && savedId != account.id) {
                    error = "Выбран другой аккаунт Google. Сначала отключи прежний аккаунт. " +
                        "Его резервные копии останутся в Google Диске."
                    activeToken = ""
                } else {
                    prefs.edit().putString("subject", account.id)
                        .putString("email", account.email)
                        .putString("name", account.name).apply()
                    email = account.email
                    displayName = account.name
                    backups = pair.second
                    activeToken = token
                    status = "Аккаунт Google подключён. Копии доступны только этому приложению."
                    error = ""
                }
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Не удалось подключиться к Google"
            } finally { busy = false }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            if (result.resultCode != Activity.RESULT_OK) {
                error = "Вход через Google отменён"
                busy = false
            } else {
                val intent = result.data ?: error("Ответ Google отсутствует")
                authReady(client.getAuthorizationResultFromIntent(intent))
            }
        } catch (t: Throwable) {
            error = t.localizedMessage ?: "Google отказал в авторизации"
            busy = false
        }
    }
    fun authorize(switch: Boolean = false) {
        if (busy) return
        busy = true
        error = ""
        client.authorize(CloudConsent.request(switch))
            .addOnSuccessListener { auth ->
                try {
                    if (auth.hasResolution()) {
                        val pending = auth.pendingIntent ?: error("Не удалось выбрать аккаунт")
                        authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else authReady(auth)
                } catch (t: Throwable) {
                    error = t.localizedMessage ?: "Ошибка входа через Google"
                    busy = false
                }
            }
            .addOnFailureListener {
                error = "Ошибка Google. Проверь Google Play, интернет и OAuth: " +
                    (it.localizedMessage ?: "неизвестная ошибка")
                busy = false
            }
    }

    fun refresh() {
        if (activeToken.isBlank()) { authorize(); return }
        busy = true
        scope.launch {
            try {
                backups = withContext(Dispatchers.IO) {
                    GoogleCloudDrive(activeToken).backups()
                }
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Не удалось обновить список"
            } finally { busy = false }
        }
    }
    fun uploadNow() {
        if (activeToken.isBlank()) { authorize(); return }
        busy = true
        error = ""
        status = "Создаём и передаём проверенную ZIP-копию…"
        val token = activeToken
        scope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val temp = GoogleCloudFiles.newFile(context)
                    try {
                        repo.exportBackup(GoogleCloudFiles.uri(context, temp))
                        val uploaded = GoogleCloudDrive(token).upload(temp)
                        uploaded to GoogleCloudFiles.fingerprint(context)
                    } finally { temp.delete() }
                }
                prefs.edit().putString("fingerprint", backup.second)
                    .putLong("last_success", System.currentTimeMillis()).apply()
                status = "Сохранено в Google: " + backup.first.name
                backups = withContext(Dispatchers.IO) { GoogleCloudDrive(token).backups() }
            } catch (t: Throwable) {
                error = t.localizedMessage ?: "Ошибка облачного сохранения"
                status = "Копия не подтверждена Google. Локальные записи сохранены."
            } finally { busy = false }
        }
    }
    fun inspectRemote(backup: GoogleCloudBackup) {
        if (activeToken.isBlank() || busy) return
        busy = true
        selected = backup
        error = ""
        status = "Скачиваем и проверяем выбранную копию…"
        val token = activeToken
        scope.launch {
            val temp = GoogleCloudFiles.newFile(context)
            try {
                val result = withContext(Dispatchers.IO) {
                    GoogleCloudDrive(token).download(backup.id, temp)
                    BackupArchive.inspect(context, GoogleCloudFiles.uri(context, temp))
                }
                downloadFile?.delete()
                downloadFile = temp
                preview = result
                status = "Архив проверен. Для восстановления нажми «Восстановить»."
            } catch (t: Throwable) {
                temp.delete()
                preview = null
                error = t.localizedMessage ?: "Не удалось проверить облачную копию"
            } finally { busy = false }
        }
    }

    DisposableEffect(downloadFile) {
        val file = downloadFile
        onDispose { file?.delete() }
    }

    LaunchedEffect(Unit) {
        // Obtain a fresh short-lived token; no OAuth token is stored across launches.
        if (prefs.getString("subject", "").orEmpty().isNotBlank()) authorize()
    }

    if (confirmRestore && preview != null && downloadFile != null) {
        val summary = preview!!
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Восстановить воспоминания?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("В копии " + summary.photos + " фотографий, " +
                        summary.videos + " видео и " + summary.capsules + " капсул.")
                    Text("Существующие записи по ID не перезаписываются. " +
                        "Сначала лучше сохранить текущую резервную копию.")
                    Text("Данные «Моей вселенной» применятся при её открытии.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmRestore = false
                    busy = true
                    val local = downloadFile!!
                    scope.launch {
                        try {
                            val added = withContext(Dispatchers.IO) {
                                repo.importBackup(GoogleCloudFiles.uri(context, local))
                            }
                            if (summary.hasAppearance) {
                                onAppearanceRestored(DiaryThemes.load(context),
                                    ThemeMotionPreferences.read(context))
                            }
                            status = "Восстановлено. Добавлено фото и видео: " + added +
                                ". Проверь дневник, капсулы и анкету."
                            preview = null
                            selected = null
                            downloadFile = null
                            local.delete()
                        } catch (t: Throwable) {
                            error = t.localizedMessage ?: "Ошибка восстановления"
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            TextButton(onClick = onBack, enabled = !busy) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Text("Настройки")
            }
            IllustratedSectionHeader("Мой Google аккаунт",
                "Сохраняй воспоминания в закрытой папке Google Диска")
        }
        item {
            DiarySectionCard {
                Text(if (email.isBlank()) "Вход через Google" else
                    "Подключено: " + email, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                if (displayName.isNotBlank()) Text(displayName)
                Text("Выбери аккаунт Google и предоставь доступ только к скрытой папке " +
                    "этого приложения. Другие файлы на Google Диске недоступны.")
                if (email.isBlank() || activeToken.isBlank()) {
                    Button(onClick = { authorize() }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Войти через Google")
                    }
                } else {
                    Button(onClick = { uploadNow() }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Сохранить воспоминания в Google")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoEnabled, enabled = email.isNotBlank() && !busy,
                        onCheckedChange = {
                            autoEnabled = it
                            prefs.edit().putBoolean("auto", it).apply()
                        })
                    Text("Автосохранение изменений при работе приложения",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                }
                Text("Автосохранение требует согласия, интернета и разрешения Google. " +
                    "Оно выполняется при открытом приложении, а не постоянно в фоне.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (email.isNotBlank()) {
                    OutlinedButton(onClick = {
                        prefs.edit().clear().apply()
                        email = ""; displayName = ""; activeToken = ""
                        backups = emptyList(); autoEnabled = false
                        preview = null; selected = null
                        downloadFile?.delete(); downloadFile = null
                        status = "Аккаунт отключён только в приложении. Копии на Google сохранены."
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Отключить аккаунт")
                    }
                }
            }
        }
        if (email.isNotBlank()) {
            item {
                DiarySectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Мои облачные копии",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh() }, enabled = !busy) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                    Text("Копии доступны только через приложение и не видны " +
                        "в обычном списке файлов Google Диска.",
                        style = MaterialTheme.typography.bodySmall)
                    if (backups.isEmpty()) {
                        Text("Пока нет облачных сохранений.")
                    }
                }
            }
            items(backups, key = { it.id }) { backup ->
                ElevatedCard(onClick = { inspectRemote(backup) }, enabled = !busy,
                    shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null,
                            tint = palette.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(backup.name, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold, maxLines = 2)
                            Text((backup.size / (1024 * 1024)).toString() + " МиБ · " +
                                backup.modified.take(10),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (preview != null) {
                item {
                    val s = preview!!
                    DiarySectionCard {
                        Text("Проверенный архив",
                            style = MaterialTheme.typography.titleLarge)
                        Text("Фото: " + s.photos + " · Видео: " + s.videos)
                        Text("Дни дневника: " + s.diaryDays + " · Капсулы: " + s.capsules)
                        Text("Анкета: " + if (s.hasProfile) "есть" else "нет")
                        Text("Моя вселенная: " + if (s.hasUniverse) "есть" else "нет")
                        Text(if (s.verified) "Контрольные суммы совпадают" else
                            "Старый архив: проверены CRC и файлы")
                        Button(onClick = { confirmRestore = true },
                            enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Восстановить выбранную копию")
                        }
                    }
                }
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (status.isNotBlank()) item { Text(status) }
        if (error.isNotBlank()) item {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        item {
            DiarySectionCard {
                Text("Безопасность", style = MaterialTheme.typography.titleMedium)
                Text("Загружаемые ZIP-копии пока не зашифрованы. " +
                    "Для чувствительных воспоминаний потребуется парольное шифрование.")
                Text("Первое подключение требует Google Play Services, включённого Drive API " +
                    "и зарегистрированного сертификата подписи в Google Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Фото и видео всегда остаются на телефоне; Google нужен только " +
                    "для хранения копий и восстановления на другом устройстве.")
            }
        }
    }
}
