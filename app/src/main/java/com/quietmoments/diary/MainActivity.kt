package com.quietmoments.diary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quietmoments.universe.UniverseActivity

/** New, fully local entry point; legacy cloud operations are not referenced. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF634A7D), onPrimary = Color.White,
                background = Color(0xFFFBF7FC), surface = Color(0xFFFBF7FC),
                secondaryContainer = Color(0xFFECE4F4), onSurface = Color(0xFF30283D))) {
                QuietMomentsOffline()
            }
        }
    }
}

@Composable
private fun QuietMomentsOffline() {
    val context = LocalContext.current
    val lock = remember { DiaryPinLock(context) }
    var unlocked by remember { mutableStateOf(!lock.isEnabled()) }
    var currentPage by remember { mutableStateOf("home") }
    var selectedDiaryDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val universeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        if (response.resultCode == android.app.Activity.RESULT_OK &&
            response.data?.getStringExtra(UniverseActivity.RESULT_ACTION) == "NEW_DIARY_ENTRY") {
            val requested = response.data?.getStringExtra(UniverseActivity.RESULT_DATE).orEmpty()
            selectedDiaryDate = runCatching { LocalDate.parse(requested).toString() }
                .getOrDefault(LocalDate.now().toString())
            currentPage = "diary"
        }
    }
    if (!unlocked) {
        DiaryUnlockScreen(lock, onUnlock = { unlocked = true })
        return
    }
    if (currentPage == "diary") {
        LocalMomentsScreen(
            uid = "offline", onClose = { currentPage = "home" },
            pinLock = lock, initialDate = selectedDiaryDate
        )
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Тихие моменты", style = MaterialTheme.typography.headlineLarge)
        Text("Место для твоих воспоминаний", style = MaterialTheme.typography.bodyLarge)
        ElevatedCard(onClick = { currentPage = "diary" }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Мой видеодневник", style = MaterialTheme.typography.titleLarge)
                Text("Видео, фотографии, альбомы, календарь и настроение — сохраняются локально")
            }
        }
        ElevatedCard(onClick = {
            universeLauncher.launch(Intent(context, UniverseActivity::class.java))
        }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NightsStay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Моя вселенная", style = MaterialTheme.typography.titleLarge)
                Text("Гороскопы, Луна, карта дня, сны и история")
            }
        }
        HorizontalDivider()
        Text("Все новые воспоминания остаются на устройстве. Создавай резервные копии в разделе видеодневника.",
            style = MaterialTheme.typography.bodySmall)
        Text("Настройки безопасности", style = MaterialTheme.typography.titleMedium)
        DiaryPinSettings(lock)
    }
}
