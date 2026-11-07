package com.quietmoments.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-bleed theme artwork for the PIN gate. All 12 preinstalled illustrations
 * are reused locally; the gate never exposes journal data before unlocking.
 */
@Composable
fun DiaryUnlockScreen(lock: DiaryPinLock, onUnlock: () -> Unit,
    onBiometric: (onError: (String) -> Unit) -> Unit = {}) {
    val palette = LocalDiaryPalette.current
    val context = LocalContext.current
    val biometricAvailable = remember(context) {
        lock.isBiometricEnabled() && BiometricDiaryAuth.available(context)
    }
    var code by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }

    fun openDiary() {
        if (code.length < 6 || failures >= 5) return
        val valid = runCatching { lock.verify(code) }.getOrDefault(false)
        if (valid) {
            code = ""
            error = ""
            onUnlock()
        } else {
            failures++
            error = if (failures >= 5)
                "Слишком много попыток. Закрой приложение и попробуй позднее."
            else "Неверный PIN. Проверь цифры и попробуй ещё раз."
            code = ""
        }
    }

    Box(Modifier.fillMaxSize().background(palette.heroStart)) {
        // The same artwork as the selected diary theme, with a dark scrim for
        // contrast. Animation honors the user's "Живые темы" preference.
        IllustratedThemeBackground(palette, Modifier.matchParentSize(), alpha = .91f,
            alignment = Alignment.Center)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            listOf(Color(0xB5211235), Color(0x99211236), Color(0xD51B112B)))))
        LivingThemeParticles(palette, Modifier.matchParentSize(), strength = .7f)
        Column(
            modifier = Modifier.fillMaxSize().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // An ornamental medallion and illustration distinguish the lock
            // screen from the default platform PIN form.
            Box(
                modifier = Modifier.size(82.dp).clip(CircleShape)
                    .background(Brush.radialGradient(
                        listOf(palette.heroEnd, palette.heroStart)))
                    .border(1.5.dp, palette.accent.copy(alpha = .86f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(palette.symbol, fontSize = 41.sp, color = palette.accent,
                    textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            Text("Тихие моменты", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = Color.White, textAlign = TextAlign.Center)
            Text("Твои воспоминания — только для тебя",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF3EAF6), textAlign = TextAlign.Center)
            Spacer(Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp),
                shape = RoundedCornerShape(27.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surface),
                border = BorderStroke(1.5.dp, palette.accent.copy(alpha = .75f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column {
                    // Miniature illustrated frontispiece inside the lock card.
                    Box(
                        Modifier.fillMaxWidth().height(108.dp)
                            .background(Brush.horizontalGradient(
                                listOf(palette.heroStart, palette.heroEnd)))
                    ) {
                        IllustratedThemeBackground(palette,
                            modifier = Modifier.matchParentSize(), alpha = .90f,
                            alignment = Alignment.Center)
                        Box(Modifier.matchParentSize().background(
                            Brush.horizontalGradient(
                                listOf(Color(0xDC201229), Color(0x7D28143A)))))
                        DecorativeFrame(palette, Modifier.matchParentSize(), subtle = true)
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart).padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.LockOutline, contentDescription = null,
                                tint = Color(0xFFFFE9B2), modifier = Modifier.size(30.dp))
                            Column {
                                Text("ЛИЧНЫЙ ДНЕВНИК",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFE9B2))
                                Text(palette.name, style = MaterialTheme.typography.titleMedium,
                                    color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(
                            start = 20.dp, end = 20.dp, top = 19.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Text("С возвращением",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = palette.text)
                        Text("Введи PIN, чтобы открыть свой дневник",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted)
                        OutlinedTextField(
                            value = code,
                            onValueChange = {
                                code = it.filter(Char::isDigit).take(16)
                                if (error.isNotBlank() && failures < 5) error = ""
                            },
                            label = { Text("PIN-код") },
                            placeholder = { Text("6–16 цифр") },
                            leadingIcon = {
                                Icon(Icons.Default.LockOutline, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPin = !showPin }) {
                                    Icon(
                                        if (showPin) Icons.Default.VisibilityOff
                                        else Icons.Default.Visibility,
                                        contentDescription = if (showPin) "Скрыть PIN"
                                        else "Показать PIN"
                                    )
                                }
                            },
                            visualTransformation = if (showPin)
                                VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { openDiary() }),
                            isError = error.isNotBlank(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = palette.text,
                                unfocusedTextColor = palette.text,
                                focusedBorderColor = palette.primary,
                                unfocusedBorderColor = palette.muted.copy(alpha = .65f),
                                focusedLabelColor = palette.primary,
                                cursorColor = palette.primary
                            )
                        )
                        if (error.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(error, Modifier.padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = { openDiary() },
                            enabled = code.length >= 6 && failures < 5,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (palette.dark)
                                    palette.accent else palette.primary,
                                contentColor = if (palette.dark)
                                    Color(0xFF17202A) else Color.White
                            )
                        ) {
                            Text("Открыть дневник", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                        }
                        if (biometricAvailable) {
                            OutlinedButton(
                                onClick = { onBiometric { error = it } },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.2.dp,
                                    palette.primary.copy(alpha = .58f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = palette.text
                                )
                            ) {
                                Icon(Icons.Default.Fingerprint,
                                    contentDescription = null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Войти по отпечатку", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(
                            "Доступ защищён PIN. Локальные фото и видео отдельно не шифруются.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(Modifier.height(17.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = palette.accent, modifier = Modifier.size(17.dp))
                Text("Каждый момент — часть твоей истории",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF3EAF6), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun DiaryPinSettings(lock: DiaryPinLock) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var biometricToggle by remember { mutableStateOf(lock.isBiometricEnabled()) }
    // Do not query vendor biometric services on every Compose recomposition.
    val canUseBiometrics = remember(context) { BiometricDiaryAuth.available(context) }
    val scope = rememberCoroutineScope()
    var updatingBiometrics by remember { mutableStateOf(false) }
    var pinWarning by remember { mutableStateOf<String?>(null) }
    val pinFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Защита приложения", style = MaterialTheme.typography.titleMedium)
        Text(if (lock.isEnabled()) "PIN установлен" else "PIN пока не установлен")
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter(Char::isDigit).take(16)
                pinWarning = null
                if (status == "Сначала введи текущий PIN в поле выше.") status = ""
            },
            label = { Text(if (lock.isEnabled()) "Текущий PIN (6–16 цифр)"
                else "Придумай PIN (6–16 цифр)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().focusRequester(pinFocusRequester),
            isError = pinWarning != null,
            supportingText = {
                pinWarning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        )
        if (!lock.isEnabled()) {
            OutlinedTextField(repeated, { repeated = it.filter(Char::isDigit).take(16) },
                label = { Text("Повтор PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                if (pin == repeated) {
                    runCatching { lock.enable(pin) }
                        .onSuccess {
                            status = "PIN установлен. После сворачивания приложение запросит его снова."
                            pin = ""
                            repeated = ""
                        }
                        .onFailure { status = it.message.orEmpty() }
                } else status = "PIN не совпадает"
            }, enabled = pin.length >= 6 && repeated.length >= 6,
                modifier = Modifier.fillMaxWidth()) { Text("Включить PIN") }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Вход по биометрии")
                    Text(if (canUseBiometrics) "Можно использовать отпечаток или другую биометрию"
                        else "Сначала настрой биометрию в Android",
                        style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = biometricToggle,
                    // Keep the switch tappable: explain missing PIN or unavailable
                    // biometrics instead of making the control appear broken.
                    enabled = !updatingBiometrics,
                    onCheckedChange = { wanted ->
                        when {
                            pin.length < 6 -> {
                                pinWarning = "Сначала введи текущий PIN (6–16 цифр), затем нажми переключатель."
                                status = ""
                                pinFocusRequester.requestFocus()
                                keyboard?.show()
                            }
                            !canUseBiometrics && wanted -> {
                                pinWarning = null
                                status = "Биометрия недоступна. Проверь отпечаток в настройках телефона."
                            }
                            else -> {
                                pinWarning = null
                                val submittedPin = pin
                                pin = ""
                                updatingBiometrics = true
                                status = "Проверяем PIN…"
                                scope.launch {
                                    try {
                                        val valid = withContext(Dispatchers.Default) {
                                            lock.verify(submittedPin)
                                        }
                                        if (!valid) {
                                            pinWarning = "Неверный PIN. Проверь код и попробуй ещё раз."
                                            status = ""
                                            pinFocusRequester.requestFocus()
                                            updatingBiometrics = false
                                        } else if (!wanted) {
                                            val disabled = withContext(Dispatchers.Default) {
                                                lock.setBiometricEnabled(false, submittedPin)
                                            }
                                            if (disabled) biometricToggle = false
                                            status = if (disabled) "Вход по биометрии выключен."
                                                else "Не удалось сохранить изменение."
                                            updatingBiometrics = false
                                        } else {
                                            val activity = context as? FragmentActivity
                                            if (activity == null) {
                                                status = "Не удалось открыть системное окно отпечатка."
                                                updatingBiometrics = false
                                            } else {
                                                status = "Подтверди отпечаток в системном окне."
                                                BiometricDiaryAuth.show(
                                                    activity,
                                                    onSuccess = {
                                                        scope.launch {
                                                            try {
                                                                val saved = withContext(Dispatchers.Default) {
                                                                    lock.setBiometricEnabled(true, submittedPin)
                                                                }
                                                                biometricToggle = saved
                                                                status = if (saved)
                                                                    "Вход по биометрии включён."
                                                                else "Не удалось сохранить настройку."
                                                            } catch (t: Throwable) {
                                                                status = "Ошибка сохранения настройки: " +
                                                                    (t.localizedMessage ?: "попробуй снова")
                                                            } finally {
                                                                updatingBiometrics = false
                                                            }
                                                        }
                                                    },
                                                    onError = { message ->
                                                        status = message
                                                        updatingBiometrics = false
                                                    },
                                                    onCancelled = {
                                                        status = "Проверка отменена. PIN по-прежнему работает."
                                                        updatingBiometrics = false
                                                    }
                                                )
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        status = "Не удалось проверить PIN: " +
                                            (t.localizedMessage ?: "попробуй снова")
                                        updatingBiometrics = false
                                    }
                                }
                            }
                        }
                    }
                )
            }
            OutlinedButton(onClick = {
                if (lock.disable(pin)) {
                    biometricToggle = false
                    pin = ""
                    status = "PIN отключён; вход по биометрии также отключён."
                } else status = "Неверный PIN"
            }, enabled = pin.length >= 6, modifier = Modifier.fillMaxWidth()) {
                Text("Отключить PIN")
            }
            Text("При переходе в другое приложение или блокировке телефона дневник " +
                "закрывается. Переход внутри дневника между разделами не требует повторного PIN.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
