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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
        window.statusBarColor = android.graphics.Color.rgb(250, 245, 252)
        window.navigationBarColor = android.graphics.Color.rgb(255, 249, 251)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Plum, onPrimary = Color.White,
                primaryContainer = Lavender, onPrimaryContainer = Ink,
                background = Page, surface = Cream,
                secondaryContainer = Color(0xFFF0E3F2), onSurface = Ink,
                onBackground = Ink, onSurfaceVariant = Color(0xFF665A70),
                outline = Color(0xFFAD9FB4), surfaceVariant = Color(0xFFEFE7F1)
            )) {
                QuietMomentsApp()
            }
        }
    }
}

@Composable
private fun QuietMomentsApp() {
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
        universeLauncher.launch(Intent(context, UniverseActivity::class.java))
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
        containerColor = Page,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFFFFAFE),
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
                            selectedIconColor = Plum, selectedTextColor = Plum,
                            indicatorColor = Lavender,
                            unselectedIconColor = Color(0xFF897A93),
                            unselectedTextColor = Color(0xFF897A93)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                "diary" -> LocalMomentsScreen(
                    uid = "offline", pinLock = lock, initialDate = chosenDate,
                    onClose = { currentTab = "home" }
                )
                "settings" -> DiarySettingsPage(lock, onOpenDiary = { currentTab = "diary" })
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
    val context = LocalContext.current
    val sky: ImageBitmap? = remember(context) {
        runCatching {
            context.assets.open("universe/art/home_sky.webp").use {
                BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        }.getOrNull()
    }
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
            Column(Modifier.weight(1f)) {
                Text("ТИХИЕ МОМЕНТЫ", color = Plum, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                Text("Моё пространство", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold, color = Ink)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))),
                    color = Color(0xFF82758A), style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalIconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }

        Box(
            Modifier.fillMaxWidth().height(236.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A285A), Color(0xFF79517D))))
        ) {
            if (sky != null) {
                Image(sky, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x99221836), Color(0xD8211537)))
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("✦  ТВОЯ ИСТОРИЯ", color = Gold,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Сохрани красоту\nкаждого дня", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onOpenDiary,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink),
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
            fontWeight = FontWeight.Bold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardTile(
                "Мой дневник", "Альбомы, видео и календарь", Icons.Default.AutoStories,
                Color(0xFFFFE9EF), Color(0xFF9A5479), onOpenDiary, Modifier.weight(1f)
            )
            DashboardTile(
                "Моя вселенная", "Гороскопы, Луна и карты", Icons.Default.NightsStay,
                Color(0xFFECE3FA), Plum, onOpenUniverse, Modifier.weight(1f)
            )
        }
        UniverseBanner(onOpen = onOpenUniverse)
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = Color(0xFFB97B8F))
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
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(21.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Lavender),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Plum)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(number, style = MaterialTheme.typography.titleLarge, color = Ink,
                    fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7C7084))
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
    Card(onClick = onOpen, modifier = modifier.height(162.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(Modifier.fillMaxSize().padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = accent)
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF665A70), maxLines = 2)
        }
    }
}

@Composable
private fun DiarySettingsPage(lock: DiaryPinLock, onOpenDiary: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        LocalDiaryHeading("Настройки", "Персонализация и защита твоих воспоминаний")
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Plum)
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
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Plum)
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
            Text("v1.3 · локальный видеодневник", style = MaterialTheme.typography.bodySmall)
        }
    }
}
