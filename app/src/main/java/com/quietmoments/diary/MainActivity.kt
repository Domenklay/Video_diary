package com.quietmoments.diary

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            var themeId by rememberSaveable { mutableStateOf(DiaryThemes.load(context)) }
            val palette = DiaryThemes.byId(themeId)
            SideEffect {
                window.statusBarColor = palette.page.toArgb()
                window.navigationBarColor = palette.surface.toArgb()
                window.decorView.systemUiVisibility =
                    if (palette.dark) 0 else
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            CompositionLocalProvider(LocalDiaryPalette provides palette) {
                MaterialTheme(colorScheme = palette.scheme()) {
                    QuietMomentsApp(themeId = themeId, onSelectTheme = { newId ->
                        DiaryThemes.save(context, newId)
                        themeId = newId
                    })
                }
            }
        }
    }
}

@Composable
private fun QuietMomentsApp(themeId: String, onSelectTheme: (String) -> Unit) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    val lock = remember { DiaryPinLock(context) }
    var unlocked by rememberSaveable { mutableStateOf(!lock.isEnabled()) }
    var currentTab by rememberSaveable { mutableStateOf("home") }
    var chosenDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    val universeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { response ->
        val action = response.data?.getStringExtra(UniverseActivity.RESULT_ACTION)
        when (action) {
            "NEW_DIARY_ENTRY", "NAVIGATE_DIARY" -> {
                val date = response.data?.getStringExtra(UniverseActivity.RESULT_DATE).orEmpty()
                chosenDate = runCatching { LocalDate.parse(date).toString() }
                    .getOrDefault(LocalDate.now().toString())
                currentTab = "diary"
            }
            "NAVIGATE_SETTINGS" -> currentTab = "settings"
            "NAVIGATE_HOME" -> currentTab = "home"
        }
    }
    fun openUniverse() {
        universeLauncher.launch(Intent(context, UniverseActivity::class.java).putExtra("QM_THEME", themeId))
    }
    fun selectTab(tab: String) {
        if (tab == "universe") openUniverse()
        else currentTab = tab
    }

    if (!unlocked) {
        DiaryUnlockScreen(lock, onUnlock = { unlocked = true })
        return
    }

    Scaffold(
        containerColor = palette.page,
        bottomBar = {
            NavigationBar(
                containerColor = palette.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.height(76.dp)
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab.id,
                        onClick = { selectTab(tab.id) },
                        icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(23.dp)) },
                        label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
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
            IllustratedThemeBackground(
                palette = palette,
                modifier = Modifier.fillMaxSize(),
                alpha = if (palette.dark) 0.26f else 0.34f,
                alignment = Alignment.Center
            )
            when (currentTab) {
                "diary" -> LocalMomentsScreen(
                    uid = "offline", pinLock = lock, initialDate = chosenDate,
                    onClose = { currentTab = "home" }
                )
                "settings" -> DiarySettingsPage(lock, onOpenDiary = { currentTab = "diary" }, themeId = themeId, onSelectTheme = onSelectTheme)
                else -> DiaryHomePage(
                    onOpenDiary = { currentTab = "diary" },
                    onOpenUniverse = { openUniverse() },
                    onOpenSettings = { currentTab = "settings" }
                )
            }
        }
    }
}

private data class DiaryCounts(val media: Int, val days: Int, val newest: String?)

@Composable
private fun DiaryHomePage(
    onOpenDiary: () -> Unit,
    onOpenUniverse: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    var counts by remember { mutableStateOf(DiaryCounts(0, 0, null)) }
    LaunchedEffect(Unit) {
        counts = withContext(Dispatchers.IO) {
            runCatching {
                val media = LocalMediaRepository(context, "offline").list().filter { it.deletedAt == 0L }
                val days = LocalJournal(context, "offline").all().size
                DiaryCounts(media.size, days, media.maxByOrNull { it.createdAt }?.title)
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
                Text("Моё пространство", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = palette.text)
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x44221836), Color(0xCC211537)))
                )
            )
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
        UniverseBanner(onOpen = onOpenUniverse)
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Мой сегодняшний вопрос", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(LocalJournal.questionFor(LocalDate.now()),
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
private fun DiarySettingsPage(lock: DiaryPinLock, onOpenDiary: () -> Unit, themeId: String, onSelectTheme: (String) -> Unit) {
    val palette = LocalDiaryPalette.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LocalDiaryHeading("Настройки", "Персонализация и защита твоих воспоминаний")
        ThemeGallery(selectedId = themeId, onSelect = onSelectTheme)
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
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(10.dp))
                Text("Резервные копии", style = MaterialTheme.typography.titleMedium)
            }
            Text("Создание и восстановление ZIP находится в разделе «Дневник».",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenDiary, shape = RoundedCornerShape(15.dp)) {
                Text("Перейти к копиям")
            }
        }
        DiarySectionCard {
            Text("О приложении", style = MaterialTheme.typography.titleMedium)
            Text("Тихие моменты · Моя вселенная", style = MaterialTheme.typography.bodyLarge)
            Text("v1.5 · рисованные темы и иконка", style = MaterialTheme.typography.bodySmall)
        }
    }
}
