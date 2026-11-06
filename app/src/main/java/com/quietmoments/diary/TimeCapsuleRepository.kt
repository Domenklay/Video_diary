package com.quietmoments.diary

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Date-locked memories. The date gate is UI-only, not cryptographic protection. */
data class TimeCapsule(
    val id: String, val title: String, val letter: String, val openDate: String,
    val createdAt: Long, val attachment: String = "", val mime: String = ""
) {
    fun ready(today: LocalDate = LocalDate.now()): Boolean =
        !LocalDate.parse(openDate).isAfter(today)
}
class TimeCapsuleRepository(private val context: Context, uid: String) {
    private val root = File(context.filesDir,
        "local_moments/" + uid.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }
    val directory = File(root, "capsule_media").apply { mkdirs() }
    val index = File(root, "capsules.json")

    companion object {
        private val validId = Regex("[a-f0-9-]{36}")
        private val validName = Regex("[a-f0-9-]{36}\\.(img|mp4)")
        const val MAX_MEDIA_BYTES = 500L * 1024L * 1024L
        const val MAX_INDEX_BYTES = 2_000_000
        fun decode(json: String): List<TimeCapsule> {
            val arr = JSONArray(json)
            require(arr.length() <= 3000) { "Слишком много капсул" }
            val ids = mutableSetOf<String>()
            return (0 until arr.length()).map { i ->
                val x = arr.getJSONObject(i)
                val id = x.getString("id")
                require(validId.matches(id) && ids.add(id)) { "Неверный ID капсулы" }
                val date = LocalDate.parse(x.getString("openDate")).toString()
                val name = x.optString("attachment", "")
                require(name.isEmpty() || validName.matches(name)) { "Неверное вложение" }
                val mime = x.optString("mime", "")
                require(name.isEmpty() || (name.endsWith(".mp4") && mime == "video/mp4") ||
                    (name.endsWith(".img") && mime.startsWith("image/"))) { "Неверный тип вложения" }
                TimeCapsule(id, x.optString("title").take(180), x.optString("letter").take(10000),
                    date, x.getLong("createdAt"), name, mime)
            }
        }
        fun encode(items: List<TimeCapsule>): String =
            JSONArray().also { arr ->
                items.forEach { c ->
                    arr.put(JSONObject().apply {
                        put("id", c.id); put("title", c.title); put("letter", c.letter)
                        put("openDate", c.openDate); put("createdAt", c.createdAt)
                        put("attachment", c.attachment); put("mime", c.mime)
                    })
                }
            }.toString()
    }

    @Synchronized fun all(): List<TimeCapsule> {
        if (!index.exists() && !File(index.absolutePath + ".bak").exists()) return emptyList()
        val json = AtomicFile(index).openRead().bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_INDEX_BYTES)
        return decode(json)
    }
    @Synchronized fun save(items: List<TimeCapsule>) {
        val bytes = encode(items).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_INDEX_BYTES)
        val atomic = AtomicFile(index)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (t: Throwable) {
            atomic.failWrite(out)
            throw t
        }
    }
    @Synchronized fun create(
        title: String, letter: String, date: LocalDate,
        source: Uri? = null, mime: String? = null
    ): TimeCapsule {
        require(title.isNotBlank())
        require(!date.isBefore(LocalDate.now())) { "Дата открытия должна быть сегодня или позже" }
        val id = UUID.randomUUID().toString()
        val kind = if (source == null) "" else when {
            mime?.startsWith("image/") == true -> "img"
            mime == "video/mp4" || mime?.startsWith("video/") == true -> "mp4"
            else -> error("Допускаются только изображения и видео")
        }
        val dest = if (kind.isBlank()) null else File(directory, id + "." + kind)
        try {
            if (source != null && dest != null) {
                context.contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Не удалось открыть вложение" }
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            size += n
                            require(size <= MAX_MEDIA_BYTES) { "Файл слишком большой" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
            val normalizedMime = if (kind == "mp4") "video/mp4" else if (kind == "img")
                (mime ?: "image/jpeg").take(100) else ""
            val capsule = TimeCapsule(id, title.take(180), letter.take(10000),
                date.toString(), System.currentTimeMillis(), dest?.name ?: "", normalizedMime)
            save(all() + capsule)
            return capsule
        } catch (t: Throwable) {
            dest?.delete()
            throw t
        }
    }
    @Synchronized fun delete(id: String) {
        val items = all()
        val old = items.firstOrNull { it.id == id } ?: return
        save(items.filterNot { it.id == id })
        if (old.attachment.isNotEmpty()) File(directory, old.attachment).delete()
    }
    fun attachmentUri(capsule: TimeCapsule): Uri {
        require(capsule.ready()) { "Капсула ещё закрыта" }
        require(validName.matches(capsule.attachment))
        val f = File(directory, capsule.attachment)
        require(f.canonicalFile.parentFile == directory.canonicalFile)
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
    }

    fun writeBackup(writer: BackupZipWriter) {
        val items = all()
        for (c in items) {
            if (c.attachment.isNotEmpty()) {
                val file = File(directory, c.attachment)
                writer.writeFile("capsules/media/" + c.attachment, file, MAX_MEDIA_BYTES)
            }
        }
        writer.writeBytes("capsules/manifest.json", encode(items).toByteArray(Charsets.UTF_8))
    }

    /** Staged, validated import; caller coordinates undo with other backup sections. */
    fun stageImport(json: String, stageDir: File): List<TimeCapsule> {
        val items = decode(json)
        for (c in items) {
            if (c.attachment.isNotEmpty()) {
                require(File(stageDir, c.attachment).isFile) {
                    "В архиве отсутствует вложение капсулы"
                }
            }
        }
        return items
    }
}
