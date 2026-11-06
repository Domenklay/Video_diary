package com.quietmoments.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun DiaryUnlockScreen(lock: DiaryPinLock, onUnlock: () -> Unit) {
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
            else { failures++; error = if (failures >= 5) "Неверный PIN. Закройте приложение и повторите попытку позже." else "Неверный PIN"; code = "" }
        }, enabled = code.length >= 6 && failures < 5) { Text("Открыть") }
        Text("PIN защищает вход в приложение, но не шифрует хранящиеся файлы.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DiaryPinSettings(lock: DiaryPinLock) {
    var pin by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Защита приложения", style = MaterialTheme.typography.titleMedium)
        Text(if (lock.isEnabled()) "PIN установлен" else "PIN пока не установлен")
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(16) }, label = { Text("PIN (6–16 цифр)") },
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        if (!lock.isEnabled()) {
            OutlinedTextField(repeated, { repeated = it.filter(Char::isDigit).take(16) }, label = { Text("Повтор PIN") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Button(onClick = { if (pin == repeated) {
                runCatching { lock.enable(pin) }.onSuccess { status = "PIN установлен" }.onFailure { status = it.message.orEmpty() }
            } else status = "PIN не совпадает" }, enabled = pin.length >= 6 && repeated.length >= 6) { Text("Включить PIN") }
        } else {
            Button(onClick = { status = if (lock.disable(pin)) "PIN отключён" else "Неверный PIN" }, enabled = pin.length >= 6) { Text("Отключить PIN") }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}
