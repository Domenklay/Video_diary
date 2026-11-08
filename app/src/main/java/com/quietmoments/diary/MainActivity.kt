package com.quietmoments.diary

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.quietmoments.universe.DiarySessionGate
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietmoments.universe.UniverseActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Plum = Color(0xFF382648)
private val Lavender = Color(0xFFEDE2F6)
private val Page = Color(0xFFFAF5FC)
private val Cream = Color(0xFFFFF9F1)
private val Gold = Color(0xFFD9BB85)
private val Ink = Color(0xFF292035)

private data class DiaryTab(val id: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    DiaryTab("home", "Главная", Icons.Default.Home),
    DiaryTab("diary", "Дневник", Icons.Default.AutoStories),
    DiaryTab("universe", "Вселенная", Icons.Default.NightsStay),
    DiaryTab("settings", "Настройки", Icons.Default.Settings)
)

class MainActivity : FragmentActivity() {
    private var foregroundEpoch by mutableIntStateOf(0)

    fun authenticateBiometric(onSuccess: () -> Unit, onError: (String) -> Unit) {
        BiometricDiaryAuth.show(this, onSuccess, onError)
    }
    /** Keep the Android status bar visible; hide only system navigation buttons. */
    private fun applySystemNavigation(darkTheme: Boolean) {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Swipe or switching activities can reveal the system navigation bar.
        if (hasFocus) applySystemNavigation(DiaryThemes.byId(DiaryThemes.load(this)).dark)
    }

    // Navigation commands are one-shot and live only in RAM, not in private diary files.
    private var universeNavigation by mutableStateOf<Bundle?>(null)

    override fun onResume() {
        super.onResume()
        foregroundEpoch++
        UniverseActivity.consumeNavigation()?.let { universeNavigation = it }
    }

    override fun onBackPressed() {
        // The Universe activity can remain behind MainActivity for instant returns.
        // Back from the main screen must exit instead of exposing it unexpectedly.
        finishAffinity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeId by rememberSaveable { mutableStateOf(DiaryThemes.load(context)) }
            val palette = DiaryThemes.byId(themeId)
            var motionEnabled by rememberSaveable { mutableStateOf(ThemeMotionPreferences.read(context)) }
            SideEffect {
                window.statusBarColor = palette.page.toArgb()
                window.navigationBarColor = palette.surface.toArgb()
                applySystemNavigation(palette.dark)
            }
            CompositionLocalProvider(LocalDiaryPalette provides palette, LocalThemeMotionEnabled provides motionEnabled) {
                MaterialTheme(colorScheme = palette.scheme()) {
                    QuietMomentsApp(themeId = themeId,
                        universeNavigation = universeNavigation,
                        foregroundEpoch = foregroundEpoch,
                        onUniverseNavigationConsumed = { universeNavigation = null },
                        onSelectTheme = { newId ->
                        DiaryThemes.save(context, newId)
                        themeId = newId
                    }, motionEnabled = motionEnabled, onMotionChange = { newValue ->
                        ThemeMotionPreferences.write(context, newValue)
                        motionEnabled = newValue
                    })
                }
            }
        }
    }
}

@Composable
private fun QuietMomentsApp(themeId: String, onSelectTheme: (String) -> Unit,
    motionEnabled: Boolean, onMotionChange: (Boolean) -> Unit,
    universeNavigation: Bundle?, foregroundEpoch: Int,
    onUniverseNavigationConsumed: () -> Unit) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    val lock = remember { DiaryPinLock(context) }
    // Unlock is intentionally not Saveable: Android may restore UI state after
    // process death, but a previous PIN entry must never be restored with it.
    var unlocked by remember { mutableStateOf(!lock.isEnabled()) }
    val activity = context as MainActivity
    LaunchedEffect(foregroundEpoch) {
        if (lock.isEnabled()) {
            if (!DiarySessionGate.isAuthorized()) unlocked = false
        } else {
            DiarySessionGate.unlock()
            unlocked = true
        }
    }
    val introPrefs = remember(context) { context.getSharedPreferences("diary_onboarding", 0) }
    val migrationPrefs = remember(context) { context.getSharedPreferences("diary_migration", 0) }
    val cloudWelcome = remember(context) { context.getSharedPreferences("qm_cloud_welcome", 0) }
    var currentTab by rememberSaveable {
        mutableStateOf(
            if (!migrationPrefs.getBoolean("transfer_screen_seen", false)) "transfer"
            else if (!cloudWelcome.getBoolean("google_welcome_seen", false)) "cloud"
            else if (introPrefs.getBoolean("questionnaire_seen", false)) "home"
            else "profile"
        )
    }
    var profileBackTab by rememberSaveable { mutableStateOf("home") }
    var driveBackTab by rememberSaveable { mutableStateOf("settings") }
    var cloudBackTab by rememberSaveable { mutableStateOf("home") }

    var chosenDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var journalOpenRequest by rememberSaveable { mutableIntStateOf(0) }
    var momentOpenRequest by rememberSaveable { mutableIntStateOf(0) }
    var momentToOpen by rememberSaveable { mutableStateOf("") }
    val tabStateHolder = rememberSaveableStateHolder()

    fun openUniverse() {
        // Move the existing UniverseActivity to the foreground. Its WebView and
        // JavaScript state remain loaded when switching back to native tabs.
        // A new instance is created only if Android has already discarded it.
        context.startActivity(Intent(context, UniverseActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("QM_THEME", themeId).putExtra("QM_MOTION", motionEnabled))
    }
    fun selectTab(tab: String) {
        if (tab == "universe") openUniverse()
        else currentTab = tab
    }

    if (!unlocked) {
        DiaryUnlockScreen(lock,
            onUnlock = { DiarySessionGate.unlock(); unlocked = true },
            onBiometric = { onError ->
                activity.authenticateBiometric(
                    onSuccess = { DiarySessionGate.unlock(); unlocked = true },
                    onError = onError
                )
            })
        return
    }
    // Receive navigation after the PIN gate, including after a restored Activity.
    // No saved PIN-unlocked state or duplicated WebView is needed.
    LaunchedEffect(universeNavigation) {
        universeNavigation?.let { navigation ->
            when (navigation.getString(UniverseActivity.RESULT_ACTION)) {
                "NEW_DIARY_ENTRY", "NAVIGATE_DIARY" -> {
                    val date = navigation.getString(UniverseActivity.RESULT_DATE).orEmpty()
                    chosenDate = runCatching { LocalDate.parse(date).toString() }
                        .getOrDefault(LocalDate.now().toString())
                    currentTab = "diary"
                }
                "NAVIGATE_SETTINGS" -> currentTab = "settings"
                "NAVIGATE_HOME" -> currentTab = "home"
            }
            onUniverseNavigationConsumed()
        }
    }
    // Opt-in Google backup runs only while the diary is unlocked and the app is active.
    LaunchedEffect(Unit) {
        while (true) {
            GoogleCloudAutoSync.ifNeeded(context)
            delay(10 * 60 * 1000L)
        }
    }
    LaunchedEffect(currentTab) {
        if (currentTab == "profile") {
            introPrefs.edit().putBoolean("questionnaire_seen", true).apply()
        }
    }

    Scaffold(
        containerColor = palette.page,
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 6.dp,
                // Never constrain Material3's intrinsic height: it must include
                // the navigation bar inset so labels are not clipped on 3-button devices.
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.id || (currentTab == "profile" && tab.id == "settings"),
                        onClick = { selectTab(tab.id) },
                        icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(23.dp)) },
                        label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = palette.soft,
                            unselectedIconColor = palette.muted,
                            unselectedTextColor = palette.muted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Crossfade(targetState = palette.id, modifier = Modifier.fillMaxSize(),
                animationSpec = tween(durationMillis = if (motionEnabled) 320 else 0), label = "Фон темы") { theme ->
                val displayed = DiaryThemes.byId(theme)
                IllustratedThemeBackground(
                    palette = displayed,
                    modifier = Modifier.fillMaxSize(),
                    alpha = if (displayed.dark) .51f else .66f,
                    alignment = Alignment.TopCenter
                )
            }
            // A translucent wash keeps text readable over detailed flowers and patterns.
            Box(Modifier.fillMaxSize().background(
                palette.page.copy(alpha = if (palette.dark) .24f else .32f)
            ))
            LivingThemeParticles(palette, Modifier.fillMaxSize(), strength = .54f)
            tabStateHolder.SaveableStateProvider(currentTab) {
                when (currentTab) {
                    "diary" -> LocalMomentsScreen(
                        uid = "offline", pinLock = lock, initialDate = chosenDate,
                        openJournalRequest = journalOpenRequest,
                        openMomentRequest = momentOpenRequest, openMomentId = momentToOpen,
                        onClose = { currentTab = "home" },
                        onOpenMoods = { currentTab = "moods" },
                        onOpenTransfer = { currentTab = "transfer" }
                    )
                    "transfer" -> DataTransferScreen(
                        onOpenGoogleAccount = {
                            cloudBackTab = "transfer"
                            currentTab = "cloud"
                        },
                        onOpenGoogleDrive = {
                            driveBackTab = "transfer"
                            currentTab = "drive"
                        },
                        onFinished = {
                            migrationPrefs.edit().putBoolean("transfer_screen_seen", true).apply()
                            currentTab = if (introPrefs.getBoolean("questionnaire_seen", false)) "home"
                                else "profile"
                        },
                        onAppearanceRestored = { id, enabled ->
                            onSelectTheme(id)
                            onMotionChange(enabled)
                        }
                    )
                    "protected" -> ProtectedBackupScreen(
                        onBack = { currentTab = "cloudHub" },
                        onAppearanceRestored = { id, enabled ->
                            onSelectTheme(id)
                            onMotionChange(enabled)
                        }
                    )
                    "cloudHub" -> CloudHubScreen(
                        onBack = { currentTab = "settings" },
                        onGoogle = {
                            cloudBackTab = "cloudHub"
                            currentTab = "cloud"
                        },
                        onYandex = { currentTab = "yandex" },
                        onManual = {
                            driveBackTab = "cloudHub"
                            currentTab = "drive"
                        },
                        onTransfer = { currentTab = "transfer" },
                        onProtected = { currentTab = "protected" }
                    )
                    "cloud" -> GoogleCloudAccountScreen(
                        onBack = {
                            cloudWelcome.edit().putBoolean("google_welcome_seen", true).apply()
                            currentTab = cloudBackTab
                        },
                        onAppearanceRestored = { id, enabled ->
                            onSelectTheme(id)
                            onMotionChange(enabled)
                        },
                        onOpenManualDrive = {
                            driveBackTab = "cloud"
                            currentTab = "drive"
                        }
                    )
                    "yandex" -> YandexCloudAccountScreen(
                        onBack = { currentTab = "cloudHub" },
                        onAppearanceRestored = { id, enabled ->
                            onSelectTheme(id)
                            onMotionChange(enabled)
                        }
                    )
                    "drive" -> GoogleDriveBackupScreen(
                        onBack = { currentTab = driveBackTab },
                        onAppearanceRestored = { id, enabled ->
                            onSelectTheme(id)
                            onMotionChange(enabled)
                        }
                    )
                    "capsules" -> TimeCapsuleScreen(onBack = { currentTab = "home" })
                    "profile" -> AboutMeQuestionnaire(onBack = { currentTab = profileBackTab })
                    "moods" -> MoodInsightsScreen(
                        onBack = { currentTab = "home" },
                        onOpenDay = { selected ->
                            chosenDate = selected
                            currentTab = "diary"
                        }
                    )
                    "settings" -> DiarySettingsPage(lock,
                        onOpenDiary = { currentTab = "diary" },
                        onOpenTransfer = { currentTab = "transfer" },
                        onOpenCloudHub = { currentTab = "cloudHub" },
                        onOpenProfile = { profileBackTab = "settings"; currentTab = "profile" },
                        themeId = themeId, onSelectTheme = onSelectTheme,
                        motionEnabled = motionEnabled, onMotionChange = onMotionChange)
                    else -> DiaryHomePage(
                        onOpenDiary = { currentTab = "diary" },
                        onOpenQuestion = {
                            chosenDate = LocalDate.now().toString()
                            journalOpenRequest++
                            currentTab = "diary"
                        },
                        onOpenProfile = { profileBackTab = "home"; currentTab = "profile" },
                        onOpenCapsules = { currentTab = "capsules" },
                        onOpenMoment = { id ->
                            momentToOpen = id
                            momentOpenRequest++
                            currentTab = "diary"
                        },
                        onOpenMoods = { currentTab = "moods" },
                        onOpenDay = { selected ->
                            chosenDate = selected
                            currentTab = "diary"
                        }
                    )
                }
            }
        }
    }
}

private data class DiaryCounts(val media: Int, val days: Int, val newest: String?,
    val anniversaries: List<LocalMoment> = emptyList(),
    val anniversaryReflections: List<DailyReflection> = emptyList(),
    val sealedCapsules: Int = 0)

@Composable
private fun DiaryHomePage(
    onOpenDiary: () -> Unit,
    onOpenQuestion: () -> Unit,
    onOpenMoment: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenMoods: () -> Unit,
    onOpenDay: (String) -> Unit
) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    val repo = remember(context) { LocalMediaRepository(context, "offline") }
    var profile by remember { mutableStateOf(DiaryProfile()) }
    var counts by remember { mutableStateOf(DiaryCounts(0, 0, null)) }
    var recent by remember { mutableStateOf(emptyList<LocalMoment>()) }
    LaunchedEffect(Unit) {
        profile = withContext(Dispatchers.IO) {
            runCatching { DiaryProfileRepository(context).read() }.getOrDefault(DiaryProfile())
        }
        val snapshot = withContext(Dispatchers.IO) {
            runCatching {
                val media = repo.list().filter { it.deletedAt == 0L }
                val journalEntries = LocalJournal(context, "offline").all()
                val today = LocalDate.now()
                val oldMedia = media.filter {
                    runCatching {
                        val date = LocalDate.parse(it.memoryDate)
                        date.year < today.year && date.monthValue == today.monthValue &&
                            date.dayOfMonth == today.dayOfMonth
                    }.getOrDefault(false)
                }.sortedByDescending { it.memoryDate }.take(4)
                val oldJournal = journalEntries.filter {
                    runCatching {
                        val date = LocalDate.parse(it.date)
                        date.year < today.year && date.monthValue == today.monthValue &&
                            date.dayOfMonth == today.dayOfMonth
                    }.getOrDefault(false)
                }.sortedByDescending { it.date }.take(4)
                val count = DiaryCounts(
                    media.size, journalEntries.size,
                    media.maxByOrNull { it.createdAt }?.title,
                    oldMedia, oldJournal,
                    TimeCapsuleRepository(context, "offline").all().count { !it.ready(today) }
                )
                count to media.sortedByDescending { it.createdAt }.take(4)
            }.getOrElse { DiaryCounts(0, 0, null) to emptyList() }
        }
        counts = snapshot.first
        recent = snapshot.second
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = "Иконка «Тихие моменты»",
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("ТИХИЕ МОМЕНТЫ", color = palette.primary,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (profile.nickname.isNotBlank()) "Мир " + profile.nickname
                    else "Моё пространство",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold, color = palette.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LocalDate.now().format(
                    DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalIconButton(onClick = onOpenProfile) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Моя анкета")
            }
        }

        Box(
            Modifier.fillMaxWidth().height(208.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
        ) {
            IllustratedThemeBackground(palette, Modifier.matchParentSize(), alpha = 1f)
            LivingThemeParticles(palette, Modifier.matchParentSize(), strength = .56f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                listOf(Color(0x30221836), Color(0xC5211537)))))
            DecorativeFrame(palette, Modifier.matchParentSize(), subtle = true)
            Column(
                Modifier.align(Alignment.BottomStart).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("✦  ТВОЯ ИСТОРИЯ", color = palette.accent,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Каждый день —\nчасть твоей истории", color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onOpenDiary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent, contentColor = Color(0xFF241D2A)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Create, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Записать момент", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Последние воспоминания",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = palette.text)
                Text("${counts.media} фото и видео · ${counts.days} записей дня",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            TextButton(onClick = onOpenDiary) { Text("Все") }
        }

        if (recent.isEmpty()) {
            DiarySectionCard {
                Text("Здесь появятся твои фотографии и видео",
                    style = MaterialTheme.typography.titleMedium, color = palette.text)
                Text("Сохрани первый момент — и главная страница станет твоей личной галереей.",
                    style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                FilledTonalButton(onClick = onOpenDiary) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить воспоминание")
                }
            }
        } else {
            recent.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { moment ->
                        PersonalMomentCard(
                            repo = repo, moment = moment,
                            onClick = { onOpenMoment(moment.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = palette.primary)
                Text("Вопрос дня", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = palette.text)
            }
            Text(LocalJournal.questionFor(LocalDate.now(), profile.questionTheme),
                style = MaterialTheme.typography.bodyLarge, color = palette.text)
            Button(onClick = onOpenQuestion, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)) {
                Text("Ответить в дневнике")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        if (counts.anniversaries.isNotEmpty() ||
            counts.anniversaryReflections.isNotEmpty()) {
            DiarySectionCard {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = palette.primary)
                    Text("В этот день", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = palette.text)
                }
                counts.anniversaries.forEach { memory ->
                    TextButton(onClick = { onOpenMoment(memory.id) },
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(memory.title, style = MaterialTheme.typography.titleSmall)
                            Text(memory.memoryDate, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                counts.anniversaryReflections.forEach { reflection ->
                    TextButton(onClick = { onOpenDay(reflection.date) },
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Запись ${reflection.date} · ${reflection.mood}",
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        Text("Ещё для тебя", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, color = palette.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedCard(onClick = onOpenCapsules, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = palette.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.MarkEmailUnread, contentDescription = null,
                        tint = palette.primary)
                    Text("Капсула времени", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = palette.text)
                }
            }
            OutlinedCard(onClick = onOpenMoods, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = palette.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null,
                        tint = palette.primary)
                    Text("Моё настроение", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = palette.text)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PersonalMomentCard(
    repo: LocalMediaRepository,
    moment: LocalMoment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalDiaryPalette.current
    Card(onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface)) {
        Column(Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center) {
                val pictureSize = minOf(maxWidth, 142.dp)
                MomentThumbnail(repo, moment, size = pictureSize)
            }
            Text(moment.title, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = palette.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()) {
                Text(moment.memoryDate, color = palette.muted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (moment.kind == "video") {
                    Icon(Icons.Default.PlayCircleOutline, contentDescription = "Видео",
                        tint = palette.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DashboardNumber(title: String, number: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val palette = LocalDiaryPalette.current
    Card(modifier, colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(21.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(palette.soft),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = palette.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(number, style = MaterialTheme.typography.titleLarge, color = palette.text,
                    fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
        }
    }
}

@Composable
private fun DashboardTile(
    title: String, subtitle: String, icon: ImageVector,
    backgroundColor: Color, accent: Color,
    onOpen: () -> Unit, modifier: Modifier = Modifier
) {
    val palette = LocalDiaryPalette.current
    Card(onClick = onOpen, modifier = modifier.height(162.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = accent)
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = palette.text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = palette.muted, maxLines = 2)
        }
    }
}

@Composable
private fun DiarySettingsPage(lock: DiaryPinLock, onOpenDiary: () -> Unit,
    onOpenTransfer: () -> Unit, onOpenCloudHub: () -> Unit,
    onOpenProfile: () -> Unit,
    themeId: String, onSelectTheme: (String) -> Unit,
    motionEnabled: Boolean, onMotionChange: (Boolean) -> Unit) {
    val palette = LocalDiaryPalette.current
    var themesExpanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LocalDiaryHeading("Настройки", "Персонализация и защита твоих воспоминаний")
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonOutline, contentDescription = null,
                    tint = palette.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Анкета «Обо мне»", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text("Личная история, мечты и любимые темы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть мою анкету")
            }
        }
        DiarySectionCard {
            Text("Оформление", style = MaterialTheme.typography.titleMedium)
            Text("Выбрана тема: " + DiaryThemes.byId(themeId).name,
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { themesExpanded = !themesExpanded },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (themesExpanded) "Скрыть коллекцию тем" else "Выбрать из 12 тем")
            }
        }
        if (themesExpanded) ThemeGallery(selectedId = themeId, onSelect = onSelectTheme)
        DiarySectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Живые темы", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("Медленное движение звёзд, цветов, листьев и пузырьков",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = motionEnabled, onCheckedChange = onMotionChange)
            }
            Text("Можно отключить, чтобы снизить расход батареи.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Защита PIN-кодом", style = MaterialTheme.typography.titleMedium)
            }
            DiaryPinSettings(lock)
            Text("PIN защищает вход, но не шифрует фото и видео.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Облако и резервные копии",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text("Google и Яндекс Диск, дата последнего сохранения, " +
                "резервные ZIP-копии и перенос данных.")
            Button(onClick = onOpenCloudHub, modifier = Modifier.fillMaxWidth()) {
                Text("Управлять резервными копиями")
            }
        }
        DiarySectionCard {
            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            Text("Тихие моменты · Моя вселенная", style = MaterialTheme.typography.bodyLarge)
            Text("v2.8.4 · смена Яндекс-аккаунта", style = MaterialTheme.typography.bodySmall)
        }
    }
}
