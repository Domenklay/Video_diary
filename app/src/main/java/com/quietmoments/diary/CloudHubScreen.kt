package com.quietmoments.diary

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One place for backup providers. Does not attempt OAuth or silently
 * enable automatic syncing. Existing provider preferences stay unchanged.
 */
@Composable
fun CloudHubScreen(
    onBack: () -> Unit,
    onGoogle: () -> Unit,
    onYandex: () -> Unit,
    onManual: () -> Unit,
    onTransfer: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val google = context.getSharedPreferences("qm_cloud_account", Context.MODE_PRIVATE)
    val yandex = context.getSharedPreferences("qm_yandex_backup", Context.MODE_PRIVATE)
    val googleEmail = google.getString("email", "").orEmpty()
    val googleBackup = google.getLong("last_success", 0L)
    val yandexBackup = yandex.getLong("last_success", 0L)
    val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Настройки")
        }
        LocalDiaryHeading("Облако и копии",
            "Выбери удобный способ хранения воспоминаний")
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Google Диск", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(if (googleEmail.isBlank()) "В этой установке аккаунт пока не подключён."
                else googleEmail,
                style = MaterialTheme.typography.bodySmall)
            Text(if (googleBackup > 0) "Последняя копия: " +
                format.format(Instant.ofEpochMilli(googleBackup))
                else "Новых копий здесь ещё не создавали.",
                style = MaterialTheme.typography.bodySmall)
            Text(if (google.getBoolean("auto", false))
                "Автокопирование включено" else "Автокопирование выключено",
                style = MaterialTheme.typography.labelSmall,
                color = palette.text)
            Button(onClick = onGoogle, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть Google Диск")
            }
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Яндекс Диск", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(if (yandexBackup > 0) "Последняя копия: " +
                format.format(Instant.ofEpochMilli(yandexBackup))
                else "Новых копий здесь ещё не создавали.",
                style = MaterialTheme.typography.bodySmall)
            Text("Ручное сохранение",
                style = MaterialTheme.typography.labelSmall,
                color = palette.text)
            Button(onClick = onYandex, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть Яндекс Диск")
            }
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("Локальный ZIP-архив", style = MaterialTheme.typography.titleMedium)
            }
            Text("Экспорт, импорт и проверка ZIP-архива.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить или открыть ZIP")
            }
            TextButton(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                Text("Перенос из старой версии")
            }
        }
        DiarySectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = palette.primary)
                Spacer(Modifier.width(9.dp))
                Text("О защите данных", style = MaterialTheme.typography.titleMedium)
            }
            Text("Оригиналы остаются на телефоне. Облачные ZIP-копии " +
                "ещё не зашифрованы отдельным паролем.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
