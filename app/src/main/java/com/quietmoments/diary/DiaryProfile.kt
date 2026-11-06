package com.quietmoments.diary

import android.content.Context
import android.util.AtomicFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
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
    val questionTheme: String = "Разные темы",
    val birth: String = "",
    val sign: Int = 6
) {
    val hasContent get() = nickname.isNotBlank() || about.isNotBlank() ||
        preciousThings.isNotBlank() || happiestMemory.isNotBlank() ||
        diaryWish.isNotBlank() || futureSelf.isNotBlank()

    companion object {
        val moods = listOf("Радость", "Спокойно", "Грусть", "Тревога", "Вдохновение", "Нежность", "Мечтательно")
        val signs = listOf("Овен", "Телец", "Близнецы", "Рак", "Лев", "Дева", "Весы", "Скорпион", "Стрелец", "Козерог", "Водолей", "Рыбы")
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
                questionTheme = data.optString("questionTheme", "Разные темы"),
                birth = data.optString("birth", ""),
                sign = data.optInt("sign", 6)
            )
            require((profile.birth.isBlank() || runCatching { LocalDate.parse(profile.birth) }.isSuccess) && profile.sign in 0..11) { "Неверная дата рождения или знак" }
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
            put("birth", profile.birth)
            put("sign", profile.sign)
        }.toString()
    }
}

class DiaryProfileRepository(context: Context, uid: String = "offline") {
    private val root = File(context.filesDir, "local_moments/" +
        uid.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }
    val file = File(root, "profile.json")
    private val draft = File(root, "profile_draft.json")
    @Synchronized fun readDraft(): DiaryProfile? {
        if (!draft.exists() && !File(draft.absolutePath + ".bak").exists()) return null
        val text = AtomicFile(draft).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        return DiaryProfile.fromJson(text)
    }
    @Synchronized fun saveDraft(profile: DiaryProfile) {
        val bytes = DiaryProfile.toJson(profile).toByteArray(Charsets.UTF_8)
        require(bytes.size <= 32_000)
        val atomic = AtomicFile(draft)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Throwable) { atomic.failWrite(stream); throw e }
    }
    @Synchronized fun clearDraft() {
        draft.delete()
        File(draft.absolutePath + ".bak").delete()
    }
    @Synchronized fun read(): DiaryProfile {
        if (!file.exists() && !File(file.absolutePath + ".bak").exists()) return DiaryProfile()
        val text = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        return DiaryProfile.fromJson(text)
    }
    @Synchronized fun save(profile: DiaryProfile) {
        require(profile.favoriteMood in DiaryProfile.moods && profile.questionTheme in DiaryProfile.themes)
        require(profile.sign in 0..11 && (profile.birth.isBlank() || runCatching { LocalDate.parse(profile.birth) }.isSuccess))
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

/** Simple calendar boundaries matching the zodiac choices in «Моей вселенной». */
private fun diarySignForDate(date: LocalDate): Int {
    val code = date.monthValue * 100 + date.dayOfMonth
    return when (code) {
        in 321..419 -> 0
        in 420..520 -> 1
        in 521..620 -> 2
        in 621..722 -> 3
        in 723..822 -> 4
        in 823..922 -> 5
        in 923..1022 -> 6
        in 1023..1121 -> 7
        in 1122..1221 -> 8
        in 120..218 -> 10
        in 219..320 -> 11
        else -> 9
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutMeQuestionnaire(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { DiaryProfileRepository(context) }
    val original = remember(repo) {
        runCatching { repo.read() }.getOrDefault(DiaryProfile())
    }
    val initial = remember(repo) {
        runCatching { repo.readDraft() ?: original }.getOrDefault(original)
    }
    var committed by remember { mutableStateOf(original) }

    var nickname by rememberSaveable { mutableStateOf(initial.nickname) }
    var about by rememberSaveable { mutableStateOf(initial.about) }
    var precious by rememberSaveable { mutableStateOf(initial.preciousThings) }
    var memory by rememberSaveable { mutableStateOf(initial.happiestMemory) }
    var wish by rememberSaveable { mutableStateOf(initial.diaryWish) }
    var future by rememberSaveable { mutableStateOf(initial.futureSelf) }
    var mood by rememberSaveable { mutableStateOf(initial.favoriteMood) }
    var topic by rememberSaveable { mutableStateOf(initial.questionTheme) }
    var birth by rememberSaveable { mutableStateOf(initial.birth) }
    var sign by rememberSaveable { mutableIntStateOf(initial.sign) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var signMenu by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    val palette = LocalDiaryPalette.current
    val current = DiaryProfile(nickname.trim().take(80), about.trim().take(600),
        precious.trim().take(600), memory.trim().take(600),
        wish.trim().take(600), future.trim().take(600),
        mood, topic, birth, sign)
    val latestCurrent by rememberUpdatedState(current)
    val latestCommitted by rememberUpdatedState(committed)

    // A separate, atomic draft prevents a half-filled form from replacing saved answers.
    LaunchedEffect(current, committed) {
        delay(450)
        runCatching {
            withContext(Dispatchers.IO) {
                if (current == committed) repo.clearDraft()
                else repo.saveDraft(current)
            }
        }.onFailure { notice = "Не удалось сохранить черновик: " + it.localizedMessage }
    }
    DisposableEffect(repo) {
        onDispose {
            runCatching {
                if (latestCurrent == latestCommitted) repo.clearDraft()
                else repo.saveDraft(latestCurrent)
            }
        }
    }
    fun saveAndExit(exit: Boolean) {
        runCatching {
            repo.save(current)
            repo.clearDraft()
            committed = current
        }.onSuccess {
            notice = "Анкета сохранена"
            if (exit) onBack()
        }.onFailure { notice = "Ошибка сохранения: " + it.localizedMessage }
    }
    fun returnBack() {
        if (step > 0) step -= 1
        else onBack()
    }

    if (showDatePicker) {
        val initialMillis = birth.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it).toEpochDay() * 86_400_000L }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        val chosen = LocalDate.ofEpochDay(it / 86_400_000L)
                        if (!chosen.isAfter(LocalDate.now())) {
                            birth = chosen.toString()
                            sign = diarySignForDate(chosen)
                        } else notice = "Дата рождения не может быть в будущем"
                    }
                    showDatePicker = false
                }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }
        ) { DatePicker(state = picker) }
    }
    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("Очистить анкету?") },
            text = { Text("Удалятся только ответы анкеты и её черновик. Фото, видео и записи дневника останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        repo.save(DiaryProfile())
                        repo.clearDraft()
                    }.onSuccess {
                        nickname = ""; about = ""; precious = ""; memory = ""
                        wish = ""; future = ""; mood = "Спокойно"
                        topic = "Разные темы"; birth = ""; sign = 6
                        committed = DiaryProfile()
                        step = 0
                        notice = "Анкета очищена"
                    }.onFailure { notice = "Ошибка: " + it.localizedMessage }
                    pendingClear = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("Отмена") }
            }
        )
    }

    val names = listOf("Знакомство", "То, что дорого", "Мечты", "Мои предпочтения")
    val descriptions = listOf(
        "Каждая история начинается со знакомства.",
        "Люди и мгновения, которые хочется сохранить.",
        "Воспоминания сегодня — письма себе в будущем.",
        "Каким будет твоё личное пространство?"
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { returnBack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text(if (step == 0) "Главная" else "Назад")
            }
            Text("Шаг " + (step + 1) + " из 4",
                style = MaterialTheme.typography.labelLarge, color = palette.text,
                fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { (step + 1) / 4f },
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = palette.soft
        )
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(palette.heroStart, palette.heroEnd)))) {
            IllustratedThemeBackground(palette, Modifier.matchParentSize(), preview = true,
                alpha = 1f)
            Box(Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(Color(0xEB161124), Color(0x88161124)))
            ))
            DecorativeFrame(palette, Modifier.matchParentSize())
            Column(
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("МОЙ ЛИЧНЫЙ МИР", color = palette.accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Text(names[step], color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(descriptions[step], color = Color(0xFFF5EDF7),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Crossfade(targetState = step,
            animationSpec = tween(if (LocalThemeMotionEnabled.current) 220 else 0),
            label = "Личная анкета") { page ->
            DiarySectionCard {
                when (page) {
                    0 -> {
                        Text("Расскажи о себе", color = palette.text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Все поля необязательные. Можно использовать псевдоним.",
                            color = palette.muted,
                            style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(nickname, { nickname = it.take(80) },
                            label = { Text("Как к тебе обращаться?") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(about, { about = it.take(600) },
                            label = { Text("Как бы ты описала себя?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    1 -> {
                        Text("Самое важное", color = palette.text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        OutlinedTextField(precious, { precious = it.take(600) },
                            label = { Text("Что особенно дорого твоему сердцу?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                        OutlinedTextField(memory, { memory = it.take(600) },
                            label = { Text("Какой момент хочется пережить снова?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    2 -> {
                        Text("Для будущего", color = palette.text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        OutlinedTextField(wish, { wish = it.take(600) },
                            label = { Text("Зачем тебе видеодневник?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                        OutlinedTextField(future, { future = it.take(600) },
                            label = { Text("Что скажешь себе через несколько лет?") },
                            modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                    else -> {
                        Text("Настрой дневник под себя", color = palette.text,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Какое настроение тебе ближе?",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text)
                        DiaryProfile.moods.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                pair.forEach { option ->
                                    FilterChip(
                                        selected = mood == option, onClick = { mood = option },
                                        label = { Text(option, maxLines = 1) },
                                        modifier = Modifier.weight(1f))
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Text("О чём хочется писать чаще?",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.text)
                        DiaryProfile.themes.forEach { option ->
                            FilterChip(
                                selected = topic == option,
                                onClick = { topic = option },
                                label = { Text(option) })
                        }
                        HorizontalDivider()
                        Text("Для «Моей вселенной» (необязательно)",
                            style = MaterialTheme.typography.titleSmall, color = palette.text)
                        OutlinedButton(onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (birth.isBlank()) "Указать дату рождения"
                                else "Дата рождения: " + birth)
                        }
                        if (birth.isNotBlank()) {
                            TextButton(onClick = { birth = "" }) { Text("Убрать дату") }
                        }
                        Box {
                            OutlinedButton(onClick = { signMenu = true }) {
                                Text("Знак зодиака: " + DiaryProfile.signs[sign])
                            }
                            DropdownMenu(expanded = signMenu,
                                onDismissRequest = { signMenu = false }) {
                                DiaryProfile.signs.forEachIndexed { index, name ->
                                    DropdownMenuItem(text = { Text(name) }, onClick = {
                                        sign = index; signMenu = false
                                    })
                                }
                            }
                        }
                        Text("Имя, дата рождения и знак синхронизируются с «Моей вселенной».",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.muted)
                    }
                }
            }
        }
        if (current != committed) {
            Text("Изменения автоматически сохраняются как черновик.",
                color = palette.muted, style = MaterialTheme.typography.labelMedium)
        }
        if (notice.isNotBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = palette.soft) {
                Text(notice, Modifier.fillMaxWidth().padding(12.dp),
                    color = palette.text, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { saveAndExit(true) },
                modifier = Modifier.weight(1f)) {
                Text("Сохранить")
            }
            Button(
                onClick = {
                    if (step == 3) saveAndExit(true)
                    else { step += 1; notice = "" }
                },
                modifier = Modifier.weight(1f)) {
                Text(if (step == 3) "Завершить" else "Далее")
                Spacer(Modifier.width(4.dp))
                Icon(if (step == 3) Icons.Default.CheckCircle
                    else Icons.Default.ChevronRight,
                    contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        TextButton(onClick = { pendingClear = true },
            modifier = Modifier.fillMaxWidth()) {
            Text("Очистить мои ответы")
        }
        Text("Анкета хранится на устройстве. PIN не шифрует данные; ZIP-копии также не зашифрованы.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
}
