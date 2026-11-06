package com.quietmoments.diary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Optional portable encrypted backup, separate from existing compatible ZIP. */
@Composable
fun ProtectedBackupScreen(
    onBack: () -> Unit,
    onAppearanceRestored: (String, Boolean) -> Unit = { _, _ -> }
) {
    val ctx = LocalContext.current
    val repo = remember(ctx) { LocalMediaRepository(ctx, "offline") }
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var checkSecret by remember { mutableStateOf("") }
    var restoreSecret by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Uri?>(null) }
    var decoded by remember { mutableStateOf<File?>(null) }
    var report by remember { mutableStateOf<BackupSummary?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    fun temp() = File.createTempFile("qm_secure_staging_", ".zip",
        File(ctx.cacheDir, "cloud_backups").apply { mkdirs() })
    fun uri(f: File) = FileProvider.getUriForFile(
        ctx, ctx.packageName + ".fileprovider", f)

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")) { dest ->
        if (dest != null) {
            val pwd = secret.toCharArray()
            secret = ""; checkSecret = ""; busy = true; error = ""
            scope.launch {
                val stage = temp()
                try {
                    val r = withContext(Dispatchers.IO) {
                        val summary = repo.exportBackup(uri(stage))
                        ctx.contentResolver.openOutputStream(dest, "w").use { output ->
                            requireNotNull(output) { "Не удалось создать файл" }
                            stage.inputStream().use { input ->
                                EncryptedArchiveCodec.seal(input, output, pwd)
                            }
                        }
                        summary
                    }
                    note = "Защищённая копия сохранена: ${r.photos} фото, ${r.videos} видео."
                } catch (t: Throwable) {
                    error = "Не удалось сохранить: " + (t.localizedMessage ?: "ошибка")
                    note = "Файл мог записаться частично; старые копии не удаляй."
                } finally { stage.delete(); pwd.fill('\u0000'); busy = false }
            }
        }
    }
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) {
            chosen = it; report = null; decoded?.delete(); decoded = null
            restoreSecret = ""; error = ""; note = "Введи пароль выбранного файла .qmb."
        }
    }
    fun inspect() {
        val source = chosen ?: return
        if (busy) return
        val pwd = restoreSecret.toCharArray()
        restoreSecret = ""; busy = true; error = ""
        scope.launch {
            val f = temp()
            try {
                val checked = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(source).use { input ->
                        requireNotNull(input) { "Архив недоступен" }
                        f.outputStream().use { output ->
                            EncryptedArchiveCodec.open(input, output, pwd)
                        }
                    }
                    BackupArchive.inspect(ctx, uri(f))
                }
                decoded = f; report = checked; chosen = null
                note = "Пароль принят, контроль архива пройден."
            } catch (t: Throwable) {
                f.delete()
                error = "Неверный пароль или архив повреждён: " +
                    (t.localizedMessage ?: "ошибка проверки")
            } finally { pwd.fill('\u0000'); busy = false }
        }
    }
    DisposableEffect(decoded) {
        val old = decoded
        onDispose { old?.delete() }
    }
    if (confirm && decoded != null && report != null) {
        val info = report!!
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Восстановить воспоминания?") },
            text = { Text("Фото: ${info.photos}, видео: ${info.videos}, " +
                "дней: ${info.diaryDays}. Существующие записи не заменяются.") },
            confirmButton = {
                Button(onClick = {
                    confirm = false
                    val file = decoded ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val n = withContext(Dispatchers.IO) { repo.importBackup(uri(file)) }
                            if (info.hasAppearance) onAppearanceRestored(
                                DiaryThemes.load(ctx), ThemeMotionPreferences.read(ctx))
                            note = "Восстановлено: добавлено $n медиафайлов."
                            decoded = null; report = null; file.delete()
                        } catch (t: Throwable) {
                            error = "Ошибка восстановления: " + t.localizedMessage.orEmpty()
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Восстановить") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } }
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Назад к резервным копиям") }
        LocalDiaryHeading("Защищённый архив", "AES-256-GCM · парольный формат .qmb")
        DiarySectionCard {
            Text("Создать копию с паролем", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(secret, { secret = it.take(128) },
                label = { Text("Пароль (от 12 символов)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(checkSecret, { checkSecret = it.take(128) },
                label = { Text("Повтор пароля") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && secret.isNotEmpty(),
                onClick = {
                    error = when {
                        secret.length < 12 -> "Пароль должен быть не короче 12 символов."
                        secret != checkSecret -> "Пароли не совпадают."
                        else -> ""
                    }
                    if (error.isBlank()) save.launch(
                        "QuietMoments_Secure_" + LocalDate.now() + ".qmb")
                }, modifier = Modifier.fillMaxWidth()) {
                Text("Создать зашифрованную копию")
            }
        }
        DiarySectionCard {
            Text("Проверить и восстановить .qmb",
                style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { choose.launch(arrayOf("*/*")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Выбрать защищённый архив")
            }
            if (chosen != null) {
                OutlinedTextField(restoreSecret, { restoreSecret = it.take(128) },
                    label = { Text("Пароль архива") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Button(onClick = { inspect() }, enabled = !busy && restoreSecret.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()) { Text("Проверить пароль и архив") }
            }
            if (report != null && decoded != null) {
                Text("Проверено: ${report!!.photos} фото, ${report!!.videos} видео.")
                Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth(),
                    enabled = !busy) { Text("Восстановить") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (note.isNotBlank()) Text(note)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        DiarySectionCard {
            Text("Пароль не сохраняется и не восстанавливается. Старые ZIP " +
                "можно импортировать через обычный перенос. Автоматические копии " +
                "Google и Яндекса пока остаются без отдельного шифрования.")
        }
    }
}
