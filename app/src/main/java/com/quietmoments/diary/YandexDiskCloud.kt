package com.quietmoments.diary

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class YandexDiskBackup(val path: String, val name: String, val size: Long, val modified: String)

/**
 * Uses only the Yandex Disk application folder (app:/), never disk:/.
 * The app-folder scope alone does not authorize other parts of the user's Disk.
 * Credentials stay in memory and are never sent to upload/download presigned URLs.
 */
class YandexDiskCloud(private val accessToken: String) {
    init { require(accessToken.isNotBlank()) }
    private val prefix = "https://cloud-api.yandex.net/v1/disk"
    private val backupName = Regex("QuietMoments_Cloud_[A-Za-z0-9._-]{5,100}\\.zip")
    private val maxArchiveSize = 2L * 1024L * 1024L * 1024L

    private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun route(path: String) = prefix + "/resources?path=" + encode(path)

    private fun apiConnection(url: String, method: String = "GET"): HttpURLConnection {
        require(url.startsWith(prefix + "/")) { "Неизвестный адрес API Яндекса" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "OAuth $accessToken")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun check(conn: HttpURLConnection, permitted: Set<Int>, label: String) {
        if (conn.responseCode in permitted) return
        val raw = conn.errorStream?.bufferedReader()?.use { it.readText().take(1400) }.orEmpty()
        val description = runCatching {
            val json = JSONObject(raw)
            json.optString("description").ifBlank { json.optString("message") }
        }.getOrDefault("")
        val reason = description.ifBlank { conn.responseMessage ?: "ошибка сервера" }.take(230)
        error("$label (HTTP ${conn.responseCode}): $reason")
    }

    private fun getJson(url: String): JSONObject {
        val conn = apiConnection(url)
        return try {
            check(conn, setOf(200), "Яндекс Диск отклонил запрос")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    /** Cloud provider pre-signed locations must never receive the OAuth token. */
    private fun transferConnection(href: String, method: String): HttpURLConnection {
        val url = URL(href)
        val host = url.host.lowercase(java.util.Locale.ROOT)
        require(url.protocol == "https" &&
            (host == "disk.yandex.net" || host.endsWith(".disk.yandex.net") ||
                host == "disk.yandex.ru" || host.endsWith(".disk.yandex.ru"))) {
            "Яндекс вернул неподдерживаемый адрес передачи файла"
        }
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = false
        }
    }

    /** Enumerate only backup ZIPs in the private application's own folder. */
    fun backups(): List<YandexDiskBackup> {
        val result = mutableListOf<YandexDiskBackup>()
        for (page in 0..3) {
            val data = getJson(route("app:/") + "&limit=100&offset=" + page * 100)
            val items = data.optJSONObject("_embedded")?.optJSONArray("items") ?: return result
            for (i in 0 until items.length()) {
                val row = items.optJSONObject(i) ?: continue
                val name = row.optString("name")
                if (row.optString("type") == "file" && backupName.matches(name)) {
                    result.add(YandexDiskBackup(
                        "app:/" + name, name, row.optLong("size", 0L),
                        row.optString("modified")))
                }
            }
            if (items.length() < 100) break
        }
        return result.sortedByDescending { it.modified }
    }

    private fun remoteMetadata(path: String): JSONObject =
        getJson(route(path))

    fun upload(file: File): YandexDiskBackup {
        require(file.isFile && file.length() in 1..maxArchiveSize) { "Некорректный размер копии" }
        val name = "QuietMoments_Cloud_" +
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
                .withZone(ZoneId.systemDefault()).format(Instant.now()) + "_" +
            UUID.randomUUID().toString().take(8) + ".zip"
        require(backupName.matches(name))
        val path = "app:/" + name
        val location = getJson(prefix + "/resources/upload?path=" + encode(path) + "&overwrite=false")
        val href = location.getString("href")
        val upload = transferConnection(href, "PUT")
        upload.doOutput = true
        upload.setRequestProperty("Content-Type", "application/zip")
        upload.setFixedLengthStreamingMode(file.length())
        try {
            upload.outputStream.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            }
            check(upload, setOf(200, 201, 202), "Не удалось передать архив в Яндекс")
        } finally { upload.disconnect() }
        // A 201 response confirms only transfer acceptance. Ensure the object
        // is actually visible and has the exact length before showing success.
        repeat(4) { attempt ->
            val remote = runCatching { remoteMetadata(path) }.getOrNull()
            if (remote?.optString("type") == "file" && remote.optLong("size", -1L) == file.length()) {
                return YandexDiskBackup(path, name, file.length(), remote.optString("modified"))
            }
            if (attempt < 3) Thread.sleep(900L)
        }
        error("Яндекс принял файл, но пока не подтвердил размер копии. " +
            "Проверь список сохранений перед повторной загрузкой.")
    }

    fun download(backup: YandexDiskBackup, destination: File) {
        require(backupName.matches(backup.name) && backup.path == "app:/" + backup.name)
        require(!destination.exists()) { "Временный файл уже существует" }
        val location = getJson(prefix + "/resources/download?path=" + encode(backup.path))
        val download = transferConnection(location.getString("href"), "GET")
        try {
            check(download, setOf(200), "Не удалось скачать ZIP из Яндекс Диска")
            var size = 0L
            destination.outputStream().use { output ->
                download.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        size += n
                        require(size <= maxArchiveSize) { "Архив слишком большой" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            require(size > 0 && (backup.size <= 0 || size == backup.size)) {
                "Загружен неполный архив: исходная копия осталась в Яндекс Диске"
            }
        } catch (t: Throwable) {
            destination.delete()
            throw t
        } finally { download.disconnect() }
    }
}
