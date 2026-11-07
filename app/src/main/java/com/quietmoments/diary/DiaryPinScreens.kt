package com.quietmoments.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiaryUnlockScreen(lock: DiaryPinLock, onUnlock: () -> Unit,
    onBiometric: (onError: (String) -> Unit) -> Unit = {}) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var failures by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Тихие моменты", style = MaterialTheme.typography.headlineMedium)
        Text("Введите PIN для входа")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(16) },
            label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            if (lock.verify(code)) { onUnlock() }
            else { failures++; error = if (failures >= 5)
                "Неверный PIN. Закройте приложение и повторите попытку позже."
                else "Неверный PIN"; code = "" }
        }, enabled = code.length >= 6 && failures < 5,
            modifier = Modifier.fillMaxWidth()) { Text("Открыть по PIN") }
        if (lock.isBiometricEnabled() && BiometricDiaryAuth.available(context)) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onBiometric { error = it } },
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Войти по отпечатку")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("PIN и биометрия защищают вход, но не шифруют файлы на телефоне.",
            style = MaterialTheme.typography.bodySmall)
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
