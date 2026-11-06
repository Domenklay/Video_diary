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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
    val canUseBiometrics = BiometricDiaryAuth.available(context)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Защита приложения", style = MaterialTheme.typography.titleMedium)
        Text(if (lock.isEnabled()) "PIN установлен" else "PIN пока не установлен")
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(16) },
            label = { Text(if (lock.isEnabled()) "Текущий PIN (6–16 цифр)"
                else "Придумай PIN (6–16 цифр)") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth())
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
                Switch(checked = biometricToggle,
                    onCheckedChange = { wanted ->
                        if (!canUseBiometrics && wanted) {
                            status = "Биометрия не настроена в системных настройках."
                        } else if (lock.setBiometricEnabled(wanted, pin)) {
                            biometricToggle = wanted
                            status = if (wanted) "Вход по биометрии включён."
                                else "Вход по биометрии выключен."
                            pin = ""
                        } else {
                            status = "Сначала введи правильный PIN выше."
                        }
                    }, enabled = canUseBiometrics || biometricToggle)
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
