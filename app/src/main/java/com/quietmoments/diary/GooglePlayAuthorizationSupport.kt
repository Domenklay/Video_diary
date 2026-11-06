package com.quietmoments.diary

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Google Identity's AuthorizationClient depends on authentic Google Play
 * services. A failing API is not an OAuth/Drive credentials error.
 */
internal object GooglePlayAuthorizationSupport {
    fun availabilityIssue(context: Context): String? {
        val status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return when (status) {
            ConnectionResult.SUCCESS -> null
            ConnectionResult.SERVICE_INVALID ->
                "Google Play Services на этом устройстве не проходят проверку подлинности " +
                    "(SERVICE_INVALID). Это бывает на некоторых эмуляторах. " +
                    "Используй устройство с официальными сервисами Google или ручную ZIP-копию."
            ConnectionResult.SERVICE_MISSING ->
                "На устройстве отсутствуют Google Play Services. " +
                    "Установи официальную версию Google Play Services или сохрани архив вручную."
            ConnectionResult.SERVICE_DISABLED ->
                "Google Play Services отключены. Включи их в настройках Android " +
                    "или используй ручную ZIP-копию."
            ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
                "Необходимо обновить Google Play Services через Play Маркет. " +
                    "Пока можно создать резервную ZIP-копию вручную."
            ConnectionResult.SERVICE_UPDATING ->
                "Google Play Services обновляются. Повтори вход после завершения обновления."
            else ->
                "Google Play Services недоступны (код $status). " +
                    "Проверь сервисы Google на устройстве или создай ZIP-копию вручную."
        }
    }

    fun loginFailure(error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .take(5).map { it.message.orEmpty() }.joinToString(" ")
        return when {
            "SERVICE_INVALID" in details ->
                "Авторизация Google недоступна: устройство сообщает SERVICE_INVALID. " +
                    "Проверь подлинность Google Play Services. На некоторых эмуляторах " +
                    "нужен другой образ Android с официальным Google Play. " +
                    "Ручной экспорт ZIP работает без Google Identity."
            "API is not available on this device" in details ||
                ("API: 17" in details && "not available" in details) ->
                "Сервис входа Google Identity отсутствует или недоступен на этом устройстве. " +
                    "Проверь Google Play Services и попробуй на устройстве с Google Play. " +
                    "Для сохранения данных используй ручную ZIP-копию."
            "DEVELOPER_ERROR" in details || "ApiException: 10" in details ->
                "Ошибка настройки OAuth (DEVELOPER_ERROR): проверь package name и SHA-1 " +
                    "сертификата именно этой сборки в Google Cloud."
            else -> "Не удалось войти в Google: " +
                (error.localizedMessage ?: "проверь подключение и настройки аккаунта").take(220)
        }
    }
}
