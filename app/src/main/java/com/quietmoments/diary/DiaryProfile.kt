package com.quietmoments.diary

import android.content.Context
import android.util.AtomicFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Optional questionnaire, stored on-device. No account, network or birthday required. */
data class DiaryProfile(
    val nickname: String = "",
    val about: String = "",
    val preciousThings: String = "",
    val happiestMemory: String = "",
    val diaryWish: String = "",
    val futureSelf: String = "",
    val favoriteMood: String = "Спокойно",
    val questionTheme: String = "Разные темы"
) {
    val hasContent get() = nickname.isNotBlank() || about.isNotBlank() ||
        preciousThings.isNotBlank() || happiestMemory.isNotBlank() ||
        diaryWish.isNotBlank() || futureSelf.isNotBlank()

    companion object {
        val moods = listOf("Радость", "Спокойно", "Вдохновение", "Нежность", "Мечтательно")
        val themes = listOf("Разные темы", "Семья и близкие", "Мечты", "Благодарность", "Воспоминания")
        fun fromJson(text: String): DiaryProfile {
            require(text.toByteArray(Charsets.UTF_8).size <= 32_000) { "Анкета слишком большая" }
            val data = JSONObject(text)
            val profile = DiaryProfile(
                nickname = data.optString("nickname", "").take(80),
                about = data.optString("about", "").take(600),
                preciousThings = data.optString("preciousThings", "").take(600),
                happiestMemory = data.optString("happiestMemory", "").take(600),
                diaryWish = data.optString("diaryWish", "").take(600),
                futureSelf = data.optString("futureSelf", "").take(600),
                favoriteMood = data.optString("favoriteMood", "Спокойно"),
                questionTheme = data.optString("questionTheme", "Разные темы")
            )
            require(profile.favoriteMood in moods && profile.questionTheme in themes) {
                "Некорректные варианты анкеты"
            }
            return profile
        }
        fun toJson(profile: DiaryProfile): String = JSONObject().apply {
            put("nickname", profile.nickname.take(80))
            put("about", profile.about.take(600))
            put("preciousThings", profile.preciousThings.take(600))
            put("happiestMemory", profile.happiestMemory.take(600))
            put("diaryWish", profile.diaryWish.take(600))
            put("futureSelf", profile.futureSelf.take(600))
            put("favoriteMood", profile.favoriteMood)
            put("questionTheme", profile.questionTheme)
        }.toString()
    }
}

class DiaryProfileRepository(context: Context, uid: String = "offline") {
    private val root = File(context.filesDir, "local_moments/" +
        uid.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }
    val file = File(root, "profile.json")
    @Synchronized fun read(): DiaryProfile {
        if (!file.exists() && !File(file.absolutePath + ".bak").exists()) return DiaryProfile()
        val text = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        return DiaryProfile.fromJson(text)
    }
    @Synchronized fun save(profile: DiaryProfile) {
        require(profile.favoriteMood in DiaryProfile.moods && profile.questionTheme in DiaryProfile.themes)
        val bytes = DiaryProfile.toJson(profile).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 32_000)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (e: Throwable) {
            atomic.failWrite(stream)
            throw e
        }
    }
    fun addToBackup(zip: ZipOutputStream) {
        if (!file.exists() && !File(file.absolutePath + ".bak").exists()) return
        val content = DiaryProfile.toJson(read()).toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry("profile/about_me.json"))
        zip.write(content)
        zip.closeEntry()
    }
}

@Composable
fun AboutMeQuestionnaire(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { DiaryProfileRepository(context) }
    var profile by remember { mutableStateOf(runCatching { repository.read() }.getOrDefault(DiaryProfile())) }
    var nickname by rememberSaveable { mutableStateOf(profile.nickname) }
    var about by rememberSaveable { mutableStateOf(profile.about) }
    var precious by rememberSaveable { mutableStateOf(profile.preciousThings) }
    var memory by rememberSaveable { mutableStateOf(profile.happiestMemory) }
    var wish by rememberSaveable { mutableStateOf(profile.diaryWish) }
    var future by rememberSaveable { mutableStateOf(profile.futureSelf) }
    var mood by rememberSaveable { mutableStateOf(profile.favoriteMood) }
    var theme by rememberSaveable { mutableStateOf(profile.questionTheme) }
    var notice by remember { mutableStateOf("") }
    var pendingClear by remember { mutableStateOf(false) }
    val colors = LocalDiaryPalette.current

    fun saveChanges() {
        runCatching {
            val updated = DiaryProfile(
                nickname.trim().take(80), about.trim().take(600),
                precious.trim().take(600), memory.trim().take(600),
                wish.trim().take(600), future.trim().take(600),
                mood, theme
            )
            repository.save(updated)
            profile = updated
        }.onSuccess { notice = "Анкета сохранена на этом устройстве" }
            .onFailure { notice = "Ошибка сохранения: " + it.localizedMessage }
    }

    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("Очистить анкету?") },
            text = { Text("Это удалит только ответы анкеты. Фото, видео и записи дневника останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        repository.save(DiaryProfile())
                    }.onSuccess {
                        profile = DiaryProfile()
                        nickname = ""; about = ""; precious = ""; memory = ""
                        wish = ""; future = ""; mood = "Спокойно"
                        theme = "Разные темы"; notice = "Анкета очищена"
                    }.onFailure { notice = it.localizedMessage ?: "Ошибка" }
                    pendingClear = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("Отмена") }
            }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Text("Назад")
        }
        IllustratedSectionHeader("Немного обо мне",
            "Личная анкета — расскажи о себе столько, сколько захочешь")
        DiarySectionCard {
            Text("Твоя история", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = colors.text)
            Text("Все поля необязательные. Ответы хранятся только на этом устройстве и входят в ZIP-копию.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(nickname, { nickname = it.take(80) },
                label = { Text("Как к тебе обращаться?") },
                placeholder = { Text("Имя или псевдоним") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(about, { about = it.take(600) },
                label = { Text("Как бы ты описала себя?") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(precious, { precious = it.take(600) },
                label = { Text("Что для тебя особенно дорого?") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(memory, { memory = it.take(600) },
                label = { Text("Какой момент хотелось бы пережить снова?") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wish, { wish = it.take(600) },
                label = { Text("Зачем тебе видеодневник?") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(future, { future = it.take(600) },
                label = { Text("Что хочешь сказать себе в будущем?") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        DiarySectionCard {
            Text("Настроение моего дневника", style = MaterialTheme.typography.titleLarge)
            Text("Что тебе ближе?", style = MaterialTheme.typography.titleSmall)
            DiaryProfile.moods.chunked(2).forEach { options ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    options.forEach { option ->
                        FilterChip(selected = mood == option,
                            onClick = { mood = option },
                            label = { Text(option) })
                    }
                }
            }
            Text("О чём чаще хочется писать?", style = MaterialTheme.typography.titleSmall)
            DiaryProfile.themes.forEach { option ->
                FilterChip(
                    selected = theme == option,
                    onClick = { theme = option },
                    label = { Text(option) }
                )
            }
        }
        if (notice.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = colors.soft)) {
                Text(notice, Modifier.padding(12.dp), color = colors.text)
            }
        }
        Button(onClick = { saveChanges() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Сохранить анкету")
        }
        TextButton(onClick = { pendingClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Очистить мои ответы")
        }
        Text("PIN защищает вход, но анкета и обычные ZIP-копии пока не зашифрованы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
}
