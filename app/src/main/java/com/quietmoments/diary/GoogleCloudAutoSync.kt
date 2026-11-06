package com.quietmoments.diary

import android.content.Context
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Consent-based foreground syncing only. Never launches OAuth account selection
 * from a background job; if authorization is required, the user can sign in in Settings.
 */
object GoogleCloudAutoSync {
    suspend fun ifNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("qm_cloud_account", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("auto", false)) return@withContext
        val expected = prefs.getString("subject", "").orEmpty()
        if (expected.isEmpty()) return@withContext
        val fingerprint = GoogleCloudFiles.fingerprint(context)
        if (fingerprint == prefs.getString("fingerprint", "")) return@withContext
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_attempt", 0) < 15 * 60 * 1000L) {
            return@withContext
        }
        prefs.edit().putLong("last_attempt", now).apply()
        try {
            val auth = Tasks.await(Identity.getAuthorizationClient(context)
                .authorize(CloudConsent.request()))
            if (auth.hasResolution()) {
                prefs.edit().putString("auto_status",
                    "Для автоматического сохранения нужно снова открыть Google аккаунт").apply()
                return@withContext
            }
            val token = auth.accessToken.orEmpty()
            if (token.isBlank()) return@withContext
            val cloud = GoogleCloudDrive(token)
            require(cloud.account().id == expected) {
                "Аккаунт Google изменился: резервное копирование остановлено"
            }
            val temp = GoogleCloudFiles.newFile(context)
            try {
                LocalMediaRepository(context, "offline").exportBackup(GoogleCloudFiles.uri(context, temp))
                val backup = cloud.upload(temp)
                prefs.edit().putString("fingerprint", GoogleCloudFiles.fingerprint(context))
                    .putLong("last_success", System.currentTimeMillis())
                    .putString("auto_status", "Последняя копия: " + backup.name).apply()
            } finally { temp.delete() }
        } catch (t: Throwable) {
            prefs.edit().putString("auto_status", "Автосохранение не выполнено: " +
                (t.localizedMessage ?: "проверь интернет и аккаунт Google")).apply()
        }
    }
}
