package com.quietmoments.diary

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class GoogleCloudAccount(val id: String, val email: String, val name: String)
data class GoogleCloudBackup(val id: String, val name: String, val size: Long, val modified: String)

/** Restricted to Drive appDataFolder; OAuth access tokens are held only in memory. */
class GoogleCloudDrive(private val token: String) {
    init { require(token.isNotBlank()) }
    private fun connect(url: String, method: String = "GET"): HttpURLConnection {
        val u = URL(url)
        require(u.protocol == "https")
        require(u.host == "www.googleapis.com" ||
            u.host == "openidconnect.googleapis.com" ||
            u.host.endsWith(".googleapis.com") ||
            u.host.endsWith(".googleusercontent.com"))
        return (u.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/json")
        }
    }
    private fun body(conn: HttpURLConnection): String {
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val details = conn.errorStream?.bufferedReader()?.use { it.readText().take(400) }
                error("Google API HTTP " + code + ": " + (details ?: conn.responseMessage))
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
    fun account(): GoogleCloudAccount {
        val data = JSONObject(body(connect("https://openidconnect.googleapis.com/v1/userinfo")))
        return GoogleCloudAccount(data.getString("sub"), data.optString("email"),
            data.optString("name").ifBlank { data.optString("email") })
    }
    fun backups(): List<GoogleCloudBackup> {
        val all = mutableListOf<GoogleCloudBackup>()
        var page = ""
        repeat(4) {
            val query = "spaces=appDataFolder&pageSize=100&" +
                "fields=nextPageToken,files(id,name,size,modifiedTime)&" +
                "orderBy=modifiedTime%20desc&" +
                "q=" + URLEncoder.encode("name contains 'QuietMoments_Cloud_' and trashed = false", "UTF-8") +
                (if (page.isEmpty()) "" else "&pageToken=" + URLEncoder.encode(page, "UTF-8"))
            val data = JSONObject(body(connect("https://www.googleapis.com/drive/v3/files?" + query)))
            val records = data.optJSONArray("files")
            if (records != null) {
                for (i in 0 until records.length()) {
                    val row = records.getJSONObject(i)
                    val id = row.optString("id")
                    if (id.matches(Regex("[A-Za-z0-9_-]{5,200}"))) {
                        all.add(GoogleCloudBackup(id, row.optString("name"),
                            row.optLong("size", 0L), row.optString("modifiedTime")))
                    }
                }
            }
            page = data.optString("nextPageToken")
            if (page.isEmpty()) return all
        }
        return all
    }
    fun upload(file: File): GoogleCloudBackup {
        require(file.isFile && file.length() > 0 && file.length() <= 2L * 1024 * 1024 * 1024)
        val name = "QuietMoments_Cloud_" +
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneId.systemDefault()).format(Instant.now()) + "_" +
            UUID.randomUUID().toString().take(6) + ".zip"
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/zip")
            put("parents", JSONArray().put("appDataFolder"))
        }.toString().toByteArray(Charsets.UTF_8)
        val start = connect(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable" +
                "&fields=id,name,size,modifiedTime", "POST")
        start.doOutput = true
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        start.setRequestProperty("X-Upload-Content-Type", "application/zip")
        start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        start.setFixedLengthStreamingMode(metadata.size)
        val session: String
        try {
            start.outputStream.use { it.write(metadata) }
            require(start.responseCode == 200) {
                "Google Drive rejected upload (HTTP " + start.responseCode + ")"
            }
            session = start.getHeaderField("Location")
                ?: error("Google Drive upload session is missing")
        } finally { start.disconnect() }
        val upload = connect(session, "PUT")
        upload.doOutput = true
        upload.setRequestProperty("Content-Type", "application/zip")
        upload.setFixedLengthStreamingMode(file.length())
        try {
            upload.outputStream.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            }
            val status = upload.responseCode
            require(status == 200 || status == 201) {
                "Google Drive did not confirm complete upload (HTTP " + status + ")"
            }
            val data = JSONObject(upload.inputStream.bufferedReader().use { it.readText() })
            return GoogleCloudBackup(data.getString("id"), data.optString("name", name),
                data.optLong("size", file.length()), data.optString("modifiedTime"))
        } finally { upload.disconnect() }
    }
    fun download(id: String, destination: File) {
        require(id.matches(Regex("[A-Za-z0-9_-]{5,200}")))
        require(!destination.exists())
        val conn = connect("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media")
        try {
            require(conn.responseCode == 200) {
                "Cannot download backup (HTTP " + conn.responseCode + ")"
            }
            var count = 0L
            try {
                destination.outputStream().use { output ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            require(count <= 2L * 1024 * 1024 * 1024) { "Backup too large" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
            } catch (t: Throwable) { destination.delete(); throw t }
            require(count > 0)
        } finally { conn.disconnect() }
    }
}

/** Scoped temp files permit existing checked ZIP importer/exporter to stay unchanged. */
object GoogleCloudFiles {
    fun newFile(context: Context): File = File(
        File(context.cacheDir, "cloud_backups").apply { mkdirs() },
        UUID.randomUUID().toString() + ".zip")
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    /**
     * A stable snapshot of every local file included in a backup.
     * The old newest-timestamp + total-length checksum could miss changes in an
     * older record when the newest file remained unchanged. Never read large
     * videos/photos just to check whether an automatic upload is needed.
     */
    fun fingerprint(context: Context): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val sources = listOf(
            "moments" to File(context.filesDir, "local_moments/offline"),
            "universe" to File(context.filesDir, "universe_backup")
        )
        sources.forEach { (scope, folder) ->
            if (folder.isDirectory) {
                folder.walkTopDown()
                    .filter { file ->
                        file.isFile && !file.relativeTo(folder).invariantSeparatorsPath
                            .startsWith("restore_")
                    }
                    .sortedBy { it.relativeTo(folder).invariantSeparatorsPath }
                    .forEach { file ->
                        val relative = file.relativeTo(folder).invariantSeparatorsPath
                        val identity = "$scope/$relative:" + file.length() + ":" +
                            file.lastModified() + "\n"
                        digest.update(identity.toByteArray(Charsets.UTF_8))
                        // Metadata can change without changing length or mtime.
                        // Content-check the small JSON files, not media recordings.
                        if (file.extension == "json" && file.length() <= 2_000_000L) {
                            file.inputStream().use { input ->
                                val chunk = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(chunk)
                                    if (read < 0) break
                                    digest.update(chunk, 0, read)
                                }
                            }
                        }
                    }
            }
        }
        val appearance = "theme=" + DiaryThemes.load(context) +
            ";motion=" + ThemeMotionPreferences.read(context)
        digest.update(appearance.toByteArray(Charsets.UTF_8))
        return "v2:" + digest.digest().joinToString("") { b ->
            (b.toInt() and 255).toString(16).padStart(2, '0')
        }
    }
}
