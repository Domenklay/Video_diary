package com.quietmoments.diary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Offline, always-accessible copy of the bilingual privacy information.
 * The web copy is in docs/privacy.html. Update them together whenever
 * the app's data practices change.
 */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    var english by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val palette = LocalDiaryPalette.current
    val sections = if (english) listOf(
        "What the app stores" to
            "QuietMoments (Тихие моменты) is a personal photo and video diary. " +
            "Diary entries, photos, videos, albums, profile answers, moods, time capsules " +
            "and Universe content are stored on your device. The application does not " +
            "require the developer to create a server-side diary account.",
        "Optional cloud backups" to
            "If you choose to connect Google Drive or Yandex Disk, the app uses the " +
            "provider's authorization flow. Google account identifiers, email and " +
            "display name are stored on the device for account selection. OAuth access " +
            "tokens are kept in memory and are not saved as permanent credentials. " +
            "Only when you request a backup (or enable Google automatic backup) " +
            "the app uploads a ZIP containing your selected diary data into the " +
            "provider's application-specific storage. Cloud providers process this " +
            "data under their own privacy policies. Ordinary cloud ZIP files are not " +
            "password-encrypted by QuietMoments.",
        "Security" to
            "Optional PIN and device biometric authentication protect entry to the " +
            "app. They do not independently encrypt the local media files. " +
            "A separate password-protected .qmb export uses AES-256-GCM encryption. " +
            "The app uses only the Android biometric result and does not receive " +
            "or store fingerprint images.",
        "Sharing and retention" to
            "The app does not include an advertising SDK. Personal diary content is " +
            "not intentionally sent to the developer. It stays on the device until " +
            "you remove it or clear app data. Files saved to Google or Yandex remain " +
            "there until deleted through the respective provider; deleting an entry " +
            "from the app does not automatically remove older backups.",
        "Your choices" to
            "You can delete individual diary entries and media in the app, disconnect " +
            "optional cloud providers, and remove all local app data through Android " +
            "Settings. You can manage cloud backups through your provider account. " +
            "Existing exports remain wherever you saved them.",
        "Contact and changes" to
            "Use the developer contact listed on the app's Google Play page for privacy " +
            "requests. The public GitHub issue tracker accepts general questions only; " +
            "do not post photos, PINs or other sensitive personal data in public issues. " +
            "This notice may be updated when the app's functionality changes."
    ) else listOf(
        "Какие данные хранятся" to
            "«Тихие моменты» (QuietMoments) — личный фото- и видеодневник. " +
            "Записи, фотографии, видео, альбомы, ответы анкеты, настроение, капсулы " +
            "времени и содержимое «Моей вселенной» хранятся на устройстве. " +
            "Для основного дневника не требуется серверная учётная запись у разработчика.",
        "Необязательные облачные копии" to
            "Если ты подключаешь Google Диск или Яндекс Диск, приложение использует " +
            "авторизацию выбранного сервиса. Идентификатор Google-аккаунта, адрес " +
            "электронной почты и имя отображения сохраняются на устройстве для выбора " +
            "аккаунта. OAuth-токены доступа находятся только в памяти приложения. " +
            "По запросу пользователя либо после включения автокопирования Google " +
            "приложение передаёт ZIP-архив дневника в папку приложения выбранного " +
            "облачного сервиса. Провайдер обрабатывает данные по собственной политике. " +
            "Обычные облачные ZIP-копии не шифруются отдельным паролем QuietMoments.",
        "Защита данных" to
            "Необязательный PIN и системная биометрия защищают вход в приложение, " +
            "но не шифруют отдельно локальные фото и видео. Защищённый архив .qmb " +
            "создаётся по отдельному паролю с AES-256-GCM. Отпечатки пальцев и " +
            "биометрические изображения приложение не получает и не хранит.",
        "Передача и хранение" to
            "В приложение не встроен рекламный SDK. Содержимое дневника не " +
            "передаётся разработчику намеренно. Локальные записи остаются на " +
            "устройстве до удаления пользователем или очистки данных приложения. " +
            "Облачные архивы остаются у выбранного провайдера, пока пользователь " +
            "не удалит их отдельно. Удаление записи не удаляет старые копии автоматически.",
        "Управление данными" to
            "Можно удалять записи и медиа в приложении, отключать необязательные " +
            "облачные сервисы и удалять все локальные данные через настройки Android. " +
            "Облачными копиями можно управлять в аккаунте соответствующего сервиса. " +
            "Ранее экспортированные архивы сохраняются там, куда ты их поместила.",
        "Связь и изменения" to
            "По вопросам конфиденциальности используй контакт разработчика со " +
            "страницы приложения в Google Play. В публичном GitHub Issues можно " +
            "задавать только общие вопросы: не публикуй там фото, PIN и личные данные. " +
            "Политика может обновляться вместе с изменениями приложения."
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(if (english) "Back to settings" else "К настройкам")
        }
        LocalDiaryHeading(
            if (english) "Privacy policy" else "Политика конфиденциальности",
            if (english) "QuietMoments · Updated 14 November 2026"
            else "Тихие моменты · Обновлено 14 ноября 2026"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !english, onClick = { english = false },
                label = { Text("Русский") })
            FilterChip(selected = english, onClick = { english = true },
                label = { Text("English") })
        }
        sections.forEach { (heading, content) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(heading, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text(content, style = MaterialTheme.typography.bodyMedium,
                        color = palette.text)
                }
            }
        }
        OutlinedButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Domenklay/Video_diary/issues"))
            runCatching { context.startActivity(intent) }
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (english) "Public issue tracker (general questions)"
                else "Публичные обращения (общие вопросы)")
        }
        Spacer(Modifier.height(12.dp))
    }
}
