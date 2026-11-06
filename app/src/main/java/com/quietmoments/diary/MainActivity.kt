package com.quietmoments.diary

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
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
    universeNavigation: Bundle?, onUniverseNavigationConsumed: () -> Unit) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    val lock = remember { DiaryPinLock(context) }
    // Unlock is intentionally not Saveable: Android may restore UI state after
    // process death, but a previous PIN entry must never be restored with it.
    var unlocked by remember { mutableStateOf(!lock.isEnabled()) }
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
        DiaryUnlockScreen(lock, onUnlock = { unlocked = true })
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
                        onOpenGoogleDrive = {
                            driveBackTab = "settings"
                            currentTab = "drive"
                        },
                        onOpenGoogleAccount = {
                            cloudBackTab = "settings"
                            currentTab = "cloud"
                        },
                        onOpenProfile = { profileBackTab = "settings"; currentTab = "profile" },
                        themeId = themeId, onSelectTheme = onSelectTheme,
                        motionEnabled = motionEnabled, onMotionChange = onMotionChange)
                    else -> DiaryHomePage(
                        onOpenDiary = { currentTab = "diary" },
                        onOpenUniverse = { openUniverse() },
                        onOpenSettings = { currentTab = "settings" },
                        onOpenProfile = { profileBackTab = "home"; currentTab = "profile" },
                        onOpenCapsules = { currentTab = "capsules" },
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
    onOpenUniverse: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCapsules: () -> Unit,
    onOpenMoods: () -> Unit,
    onOpenDay: (String) -> Unit
) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    var profile by remember { mutableStateOf(DiaryProfile()) }
    var counts by remember { mutableStateOf(DiaryCounts(0, 0, null)) }
    LaunchedEffect(Unit) {
        profile = withContext(Dispatchers.IO) {
            runCatching { DiaryProfileRepository(context).read() }.getOrDefault(DiaryProfile())
        }
    }
    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) {
            runCatching {
                val media = LocalMediaRepository(context, "offline").list().filter { it.deletedAt == 0L }
                val journalEntries = LocalJournal(context, "offline").all()
                val today = LocalDate.now()
                val matches = media.filter {
                    runCatching {
                        val date = LocalDate.parse(it.memoryDate)
                        date.year < today.year &&
                            date.monthValue == today.monthValue &&
                            date.dayOfMonth == today.dayOfMonth
                    }.getOrDefault(false)
                }.sortedByDescending { it.memoryDate }.take(4)
                val reflected = journalEntries.filter {
                    runCatching {
                        val date = LocalDate.parse(it.date)
                        date.year < today.year &&
                            date.monthValue == today.monthValue &&
                            date.dayOfMonth == today.dayOfMonth
                    }.getOrDefault(false)
                }.sortedByDescending { it.date }.take(4)
                val sealedCount = TimeCapsuleRepository(context, "offline").all()
                    .count { !it.ready(today) }
                DiaryCounts(media.size, journalEntries.size,
                    media.maxByOrNull { it.createdAt }?.title,
                    matches, reflected, sealedCount)
            }.getOrDefault(DiaryCounts(0, 0, null))
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = "Иконка «Тихие моменты»",
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(17.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("ТИХИЕ МОМЕНТЫ", color = palette.primary, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                Text(if (profile.nickname.isNotBlank()) "Мир " + profile.nickname
                     else "Моё пространство",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = palette.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalIconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }

        Box(
            Modifier.fillMaxWidth().height(236.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(palette.heroStart, palette.heroEnd)))
        ) {
            IllustratedThemeBackground(palette, modifier = Modifier.fillMaxSize(), alpha = 1f)
            LivingThemeParticles(palette, Modifier.fillMaxSize(), strength = 1f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x52221836), Color(0xB6211537)))
                )
            )
            DecorativeFrame(palette, Modifier.matchParentSize())
            Column(
                Modifier.align(Alignment.BottomStart).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("✦  ТВОЯ ИСТОРИЯ", color = palette.accent,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Сохрани красоту\nкаждого дня", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onOpenDiary,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color(0xFF241D2A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Записать воспоминание", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardNumber("Фото и видео", counts.media.toString(), Icons.Default.PhotoLibrary, Modifier.weight(1f))
            DashboardNumber("Записей дня", counts.days.toString(), Icons.Default.CalendarMonth, Modifier.weight(1f))
        }

        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null,
                    modifier = Modifier.size(32.dp), tint = palette.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (profile.hasContent) "Моя анкета" else "Давай познакомимся",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = palette.text)
                    Text(if (profile.hasContent && profile.diaryWish.isNotBlank())
                            profile.diaryWish
                         else "Имя, дорогие воспоминания, мечты и цели дневника",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2)
                }
                FilledTonalButton(onClick = onOpenProfile) {
                    Text(if (profile.hasContent) "Открыть" else "Заполнить")
                }
            }
        }
        Text("Мои разделы", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = palette.text)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile(
                "Мой дневник", "Альбомы, видео и календарь", Icons.Default.AutoStories,
                palette.secondarySoft, palette.primary, onOpenDiary, Modifier.weight(1f)
            )
            DashboardTile(
                "Моя вселенная", "Гороскопы, Луна и карты", Icons.Default.NightsStay,
                palette.soft, palette.primary, onOpenUniverse, Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile(
                "Капсула времени", "Письма и видео в будущее",
                Icons.Default.MarkEmailUnread,
                palette.secondarySoft, palette.primary,
                onOpenCapsules, Modifier.weight(1f)
            )
            DashboardTile(
                "Моё настроение", "Календарь и история эмоций",
                Icons.Default.Favorite,
                palette.soft, palette.primary,
                onOpenMoods, Modifier.weight(1f)
            )
        }
        DiarySectionCard {
            Text("В этот день", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            if (counts.anniversaries.isEmpty() &&
                counts.anniversaryReflections.isEmpty()) {
                Text("Здесь появятся воспоминания, записанные в этот же день в прошлые годы.",
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                counts.anniversaries.forEach { memory ->
                    TextButton(onClick = { onOpenDay(memory.memoryDate) }) {
                        Column {
                            Text(memory.title, style = MaterialTheme.typography.titleSmall)
                            Text(memory.memoryDate, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                counts.anniversaryReflections.forEach { reflection ->
                    TextButton(onClick = { onOpenDay(reflection.date) }) {
                        Text("Запись " + reflection.date + " · " + reflection.mood)
                    }
                }
            }
        }
        UniverseBanner(onOpen = onOpenUniverse)
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Мой сегодняшний вопрос", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(LocalJournal.questionFor(LocalDate.now(), profile.questionTheme),
                style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onOpenDiary) {
                Text("Ответить в дневнике")
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }
        if (!counts.newest.isNullOrBlank()) {
            DiarySectionCard {
                Text("Последнее воспоминание", style = MaterialTheme.typography.titleMedium)
                Text(counts.newest.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onOpenDiary) { Text("Открыть альбом") }
            }
        }
        Spacer(Modifier.height(12.dp))
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
    onOpenTransfer: () -> Unit, onOpenGoogleDrive: () -> Unit,
    onOpenGoogleAccount: () -> Unit, onOpenProfile: () -> Unit,
    themeId: String, onSelectTheme: (String) -> Unit,
    motionEnabled: Boolean, onMotionChange: (Boolean) -> Unit) {
    val palette = LocalDiaryPalette.current
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
        ThemeGallery(selectedId = themeId, onSelect = onSelectTheme)
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
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Мой Google аккаунт", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Text("Войти через Google, сохранять копии в личном облаке " +
                "и восстанавливать их на другом устройстве.")
            Button(onClick = onOpenGoogleAccount, modifier = Modifier.fillMaxWidth()) {
                Text("Войти и подключить синхронизацию")
            }
            Text("Автоматическое сохранение включается отдельно с твоего согласия.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Google Диск", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
            Text("Храни проверенные ZIP-копии в своём Google Диске и восстанавливай их на другом устройстве.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenGoogleDrive,
                modifier = Modifier.fillMaxWidth()) {
                Text("Облачное хранилище Google")
            }
            Text("Ручное копирование через системный выбор файлов. Автоматической синхронизации пока нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Резервные копии", style = MaterialTheme.typography.titleMedium)
            }
            Text("Проверка архивов, резервное копирование и перенос из предыдущих версий.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenTransfer, shape = RoundedCornerShape(15.dp)) {
                Text("Открыть перенос данных")
            }
            Text("Новая стабильная установка не может читать файлы старых preview-приложений напрямую.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        DiarySectionCard {
            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            Text("Тихие моменты · Моя вселенная", style = MaterialTheme.typography.bodyLarge)
            Text("v2.4 · безопасный перенос и резервные копии", style = MaterialTheme.typography.bodySmall)
        }
    }
}
