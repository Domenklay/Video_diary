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
        val serviceIssue = GooglePlayAuthorizationSupport.availabilityIssue(context)
        if (serviceIssue != null) {
            prefs.edit().putString("auto_status", serviceIssue).apply()
            return@withContext
        }
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
            // The fingerprint must describe the files BEFORE the backup starts.
            // Otherwise edits made during the upload could be incorrectly marked
            // as backed up and never uploaded on the next check.
            val before = GoogleCloudFiles.fingerprint(context)
            val temp = GoogleCloudFiles.newFile(context)
            try {
                LocalMediaRepository(context, "offline").exportBackup(GoogleCloudFiles.uri(context, temp))
                val afterExport = GoogleCloudFiles.fingerprint(context)
                val backup = cloud.upload(temp)
                val afterUpload = GoogleCloudFiles.fingerprint(context)
                val unchanged = CloudSnapshotFingerprint.unchangedDuringUpload(before, afterExport, afterUpload)
                prefs.edit().putString("fingerprint", if (unchanged) before else "")
                    .putLong("last_success", System.currentTimeMillis())
                    .putString("auto_status", if (unchanged) {
                        "Последняя копия: " + backup.name
                    } else {
                        "Копия отправлена, но во время сохранения появились изменения. " +
                            "Они будут загружены при следующей проверке."
                    }).apply()
            } finally { temp.delete() }
        } catch (t: Throwable) {
            prefs.edit().putString("auto_status", "Автосохранение не выполнено: " +
                (t.localizedMessage ?: "проверь интернет и аккаунт Google")).apply()
        }
    }
}
