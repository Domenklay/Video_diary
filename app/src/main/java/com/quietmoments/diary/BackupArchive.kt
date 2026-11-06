package com.quietmoments.diary

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** SHA-256 detects accidental damage; checksums are not encryption or authentication. */
data class BackupSummary(
    val photos: Int, val videos: Int, val diaryDays: Int,
    val capsules: Int, val hasProfile: Boolean, val hasUniverse: Boolean,
    val hasAlbumCovers: Boolean, val bytes: Long, val verified: Boolean,
    val hasAppearance: Boolean = false
)

private fun shaHex(bytes: ByteArray) =
    bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Every file included in the ZIP is covered by the final integrity manifest. */
class BackupZipWriter(private val zip: ZipOutputStream) {
    private val records = JSONObject()
    private val buffer = ByteArray(64 * 1024)
    private fun record(name: String, count: Long, digest: MessageDigest) {
        records.put(name, JSONObject().apply {
            put("bytes", count)
            put("sha256", shaHex(digest.digest()))
        })
    }
    fun writeBytes(name: String, bytes: ByteArray) {
        require(name != "backup-integrity.json")
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        record(name, bytes.size.toLong(), digest)
    }
    fun writeFile(name: String, source: File, maxBytes: Long) {
        require(name != "backup-integrity.json" && source.isFile)
        require(source.length() <= maxBytes) { "Файл слишком большой: $name" }
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        zip.putNextEntry(ZipEntry(name))
        source.inputStream().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                require(count <= maxBytes) { "Файл слишком большой: $name" }
                digest.update(buffer, 0, n)
                zip.write(buffer, 0, n)
            }
        }
        zip.closeEntry()
        record(name, count, digest)
    }
    fun finish() {
        val bytes = JSONObject().apply {
            put("format", "quietmoments-sha256-v1")
            put("files", records)
        }.toString().toByteArray(Charsets.UTF_8)
        zip.putNextEntry(ZipEntry("backup-integrity.json"))
        zip.write(bytes)
        zip.closeEntry()
    }
}

object BackupArchive {
    const val MAX_SINGLE_MEDIA = 500L * 1024L * 1024L
    private const val MAX_TOTAL = 2L * 1024L * 1024L * 1024L
    private const val MAX_FILE_COUNT = 50_000
    private val mediaPath = Regex("media/[A-Za-z0-9_-]+\\.(mp4|img)")
    private val capsulePath = Regex("capsules/media/[a-f0-9-]{36}\\.(mp4|img)")

    private fun limit(name: String): Long = when {
        mediaPath.matches(name) || capsulePath.matches(name) -> MAX_SINGLE_MEDIA
        name == "profile/about_me.json" -> 32_000L
        name == "album_covers.json" -> 100_000L
        name == "preferences/appearance.json" -> 2_048L
        name == "backup-version.txt" -> 100L
        name == "backup-integrity.json" -> 4_000_000L
        name in setOf("moments.json", "journal/reflections.json",
            "universe/state.json", "capsules/manifest.json") -> 2_000_000L
        else -> throw IllegalArgumentException("Неизвестный файл ZIP: $name")
    }

    /** Fully validate ZIP CRC, sizes, references, and optional hashes; never write user files. */
    fun inspect(context: Context, uri: Uri): BackupSummary {
        val seen = mutableSetOf<String>()
        val observed = mutableMapOf<String, Pair<Long, String>>()
        val metadata = mutableMapOf<String, String>()
        var total = 0L
        var manifest: String? = null
        var mediaFiles = 0
        var capsuleFiles = 0
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть ZIP" }
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Неожиданный каталог ZIP" }
                    val name = entry.name
                    require(name.length <= 160 && seen.add(name)) { "Повторяющийся путь ZIP" }
                    require(seen.size <= MAX_FILE_COUNT) { "Слишком много файлов ZIP" }
                    val max = limit(name)
                    val digest = MessageDigest.getInstance("SHA-256")
                    val captured = if (max <= 4_000_000L) ByteArrayOutputStream() else null
                    var count = 0L
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        count += n
                        total += n
                        require(count <= max && total <= MAX_TOTAL) { "Превышен размер ZIP" }
                        digest.update(buffer, 0, n)
                        captured?.write(buffer, 0, n)
                    }
                    zip.closeEntry()
                    if (name != "backup-integrity.json") {
                        observed[name] = count to shaHex(digest.digest())
                    }
                    if (name == "backup-integrity.json") manifest = captured!!.toString("UTF-8")
                    else if (captured != null) metadata[name] = captured.toString("UTF-8")
                    if (mediaPath.matches(name)) mediaFiles++
                    if (capsulePath.matches(name)) capsuleFiles++
                }
            }
        }
        val raw = requireNotNull(metadata["moments.json"]) { "В архиве отсутствует moments.json" }
        val moments = JSONArray(raw)
        require(moments.length() <= MAX_FILE_COUNT) { "Слишком много воспоминаний" }
        val ids = mutableSetOf<String>()
        var photos = 0
        var videos = 0
        for (i in 0 until moments.length()) {
            val record = moments.getJSONObject(i)
            val id = record.getString("id")
            val name = record.getString("fileName")
            val kind = record.getString("kind")
            require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && ids.add(id)) { "Неверный ID" }
            require(mediaPath.matches("media/" + name)) { "Неверное имя медиа" }
            require(name == id + (if (kind == "video") ".mp4" else ".img")) {
                "ID и имя медиа не совпадают"
            }
            require("media/" + name in seen) { "В архиве не хватает файла: $name" }
            when (kind) {
                "photo" -> photos++
                "video" -> videos++
                else -> error("Неизвестный тип медиа")
            }
        }
        require(mediaFiles == photos + videos) { "Посторонний медиафайл" }
        val capsuleJson = metadata["capsules/manifest.json"]
        val capsules = if (capsuleJson != null) TimeCapsuleRepository.decode(capsuleJson) else emptyList()
        for (item in capsules) {
            if (item.attachment.isNotBlank()) {
                require("capsules/media/" + item.attachment in seen) { "Отсутствует вложение капсулы" }
            }
        }
        require(capsuleFiles == capsules.count { it.attachment.isNotBlank() }) {
            "Постороннее вложение капсулы"
        }
        metadata["profile/about_me.json"]?.let { DiaryProfile.fromJson(it) }
        metadata["journal/reflections.json"]?.let { JSONArray(it) }
        metadata["universe/state.json"]?.let { JSONObject(it) }
        metadata["album_covers.json"]?.let { JSONObject(it) }
        metadata["preferences/appearance.json"]?.let {
            val appearance = JSONObject(it)
            require(DiaryThemes.all.any { theme ->
                theme.id == appearance.optString("theme")
            }) { "В архиве неизвестная тема" }
            require(appearance.has("motion")) { "Не указана настройка анимации" }
        }
        metadata["backup-version.txt"]?.let {
            require(it.startsWith("quietmoments-")) { "Неизвестная версия копии" }
        }
        if (manifest != null) {
            val root = JSONObject(manifest!!)
            require(root.optString("format") == "quietmoments-sha256-v1") {
                "Неизвестная схема проверки"
            }
            val expected = root.getJSONObject("files")
            require(expected.length() == observed.size) { "В архиве не хватает контрольных сумм" }
            for ((name, value) in observed) {
                val checksum = expected.getJSONObject(name)
                require(checksum.getLong("bytes") == value.first &&
                    checksum.getString("sha256") == value.second) {
                    "Повреждён файл: $name"
                }
            }
        }
        return BackupSummary(
            photos, videos,
            metadata["journal/reflections.json"]?.let { JSONArray(it).length() } ?: 0,
            capsules.size, metadata.containsKey("profile/about_me.json"),
            metadata.containsKey("universe/state.json"),
            metadata.containsKey("album_covers.json"),
            total, manifest != null,
            metadata.containsKey("preferences/appearance.json")
        )
    }
}
