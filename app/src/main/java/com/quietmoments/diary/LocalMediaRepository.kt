package com.quietmoments.diary

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Local-only media library, separate from existing Firebase documents. */
data class LocalMoment(
    val id: String, val album: String, val title: String,
    val kind: String, val fileName: String, val createdAt: Long, val deletedAt: Long = 0,
    val memoryDate: String = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
    val note: String = "", val pageStyle: String = "moon"
)

class LocalMediaRepository(private val context: Context, uid: String) {
    private val uidKey = uid.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private val root = File(context.filesDir, "local_moments/$uidKey").apply { mkdirs() }
    private val media = File(root, "media").apply { mkdirs() }
    private val index = File(root, "index.json")
    private val albumCovers = File(root, "album_covers.json")

    /** Album art is metadata, not a copy or mutation of the user's photos. */
    @Synchronized fun albumCoverStyle(album: String, defaultStyle: String): String {
        if (!albumCovers.exists() && !File(albumCovers.absolutePath + ".bak").exists()) return defaultStyle
        val json = JSONObject(AtomicFile(albumCovers).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        val style = json.optString(album, defaultStyle)
        return if (DiaryThemes.all.any { it.id == style }) style else defaultStyle
    }

    @Synchronized fun setAlbumCoverStyle(album: String, style: String) {
        require(album.isNotBlank() && album.length <= 120)
        require(DiaryThemes.all.any { it.id == style }) { "Неизвестный стиль обложки" }
        val json = if (albumCovers.exists() || File(albumCovers.absolutePath + ".bak").exists())
            JSONObject(AtomicFile(albumCovers).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        else JSONObject()
        json.put(album, style)
        writeAtomic(albumCovers, json.toString().toByteArray(Charsets.UTF_8))
    }

    @Synchronized fun list(): List<LocalMoment> {
        if (!index.exists() && !File(index.absolutePath + ".bak").exists()) return emptyList()
        return runCatching {
            val text = AtomicFile(index).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                LocalMoment(item.getString("id"), item.getString("album"), item.getString("title"),
                    item.getString("kind"), item.getString("fileName"), item.getLong("createdAt"), item.optLong("deletedAt", 0),
                    item.optString("memoryDate", "").ifBlank {
                        Instant.ofEpochMilli(item.getLong("createdAt")).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    },
                    item.optString("note", "").take(10000), item.optString("pageStyle", "moon"))
            }
        }.getOrElse { throw IllegalStateException("Не удалось прочитать локальный каталог", it) }
    }

    @Synchronized private fun store(items: List<LocalMoment>) {
        val array = JSONArray()
        items.forEach { m -> array.put(JSONObject().apply {
            put("id", m.id); put("album", m.album); put("title", m.title)
            put("kind", m.kind); put("fileName", m.fileName); put("createdAt", m.createdAt); put("deletedAt", m.deletedAt); put("memoryDate", m.memoryDate); put("note", m.note); put("pageStyle", m.pageStyle); put("note", m.note); put("pageStyle", m.pageStyle)
        }) }
        writeAtomic(index, array.toString().toByteArray(Charsets.UTF_8))
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) }
        catch (t: Throwable) { atomic.failWrite(output); throw t }
    }

    @Synchronized fun add(uri: Uri, title: String, album: String, mime: String?, memoryDate: String = LocalDate.now().toString()): LocalMoment {
        LocalDate.parse(memoryDate)
        val kind = if (mime?.startsWith("video/") == true) "video" else "photo"
        val id = UUID.randomUUID().toString()
        val target = File(media, "$id.${if (kind == "video") "mp4" else "img"}")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Не удалось открыть выбранный файл" }
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            val item = LocalMoment(id, album.trim().ifBlank { "Без альбома" }.take(120),
                title.trim().ifBlank { "Воспоминание" }.take(180), kind,
                target.name, System.currentTimeMillis(), memoryDate = memoryDate)
            store(list() + item)
            return item
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
    }

    fun contentUri(item: LocalMoment): Uri {
        // Used with ACTION_VIEW and the app's FileProvider; never expose raw file paths.
        val file = File(media, item.fileName)
        require(file.canonicalFile.parentFile == media.canonicalFile)
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file)
    }

    /** Edit only metadata; original media bytes and IDs remain unchanged. */
    @Synchronized fun updateDetails(id: String, title: String, album: String, memoryDate: String,
        note: String? = null, pageStyle: String? = null) {
        require(pageStyle == null || DiaryThemes.all.any { it.id == pageStyle })
        LocalDate.parse(memoryDate)
        require(title.isNotBlank()) { "Название не может быть пустым" }
        store(list().map { item -> if (item.id == id && item.deletedAt == 0L)
            item.copy(title = title.trim().take(180), album = album.trim().ifBlank { "Без альбома" }.take(120), memoryDate = memoryDate,
                note = note?.take(10000) ?: item.note, pageStyle = pageStyle ?: item.pageStyle)
            else item })
    }

    /** Internal immutable media file for read-only thumbnail extraction. */
    fun thumbnailFile(item: LocalMoment): File {
        val file = File(media, item.fileName)
        require(file.canonicalFile.parentFile == media.canonicalFile) { "Invalid media path" }
        return file
    }

    @Synchronized fun renameAlbum(id: String, newAlbum: String) {
        store(list().map { if (it.id == id) it.copy(album = newAlbum.trim().ifBlank { "Без альбома" }.take(120)) else it })
    }

    @Synchronized fun moveToTrash(id: String) {
        store(list().map { if (it.id == id && it.deletedAt == 0L)
            it.copy(deletedAt = System.currentTimeMillis()) else it })
    }

    @Synchronized fun restoreFromTrash(id: String) {
        store(list().map { if (it.id == id) it.copy(deletedAt = 0L) else it })
    }

    @Synchronized fun deleteForever(id: String) {
        val items = list()
        val item = items.firstOrNull { it.id == id && it.deletedAt != 0L } ?: return
        // Store the new index before deleting the file so a failed write cannot lose the reference.
        store(items.filterNot { it.id == id })
        File(media, item.fileName).delete()
    }

    @Synchronized fun exportBackup(uri: Uri) {
        val items = list()
        context.contentResolver.openOutputStream(uri, "w").use { stream ->
            requireNotNull(stream) { "Не удалось создать файл резервной копии" }
            ZipOutputStream(stream).use { zip ->
                val payload = JSONArray()
                items.forEach { m ->
                    payload.put(JSONObject().apply {
                        put("id", m.id); put("title", m.title); put("album", m.album)
                        put("kind", m.kind); put("fileName", m.fileName); put("createdAt", m.createdAt); put("deletedAt", m.deletedAt); put("memoryDate", m.memoryDate)
                    })
                    val f = File(media, m.fileName)
                    if (!f.isFile) error("Не найден локальный файл: ${m.title}")
                    zip.putNextEntry(ZipEntry("media/${m.fileName}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                // Date-locked capsules are backed up with their private attachments.
                TimeCapsuleRepository(context, uidKey).writeBackup(zip)
                // Local journal: daily questions and moods, stored next to media index.
                val reflections = File(root, "reflections.json")
                if (reflections.exists()) {
                    JSONArray(reflections.readText(Charsets.UTF_8))
                    zip.putNextEntry(ZipEntry("journal/reflections.json"))
                    reflections.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                // Mirror of the Universe WebView localStorage (latest persisted state).
                val universe = File(context.filesDir, "universe_backup/snapshot.json")
                if (universe.isFile && universe.length() in 1L..2_000_000L) {
                    JSONObject(universe.readText(Charsets.UTF_8)) // fail closed on invalid state
                    zip.putNextEntry(ZipEntry("universe/state.json"))
                    universe.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                // Optional visual personalization; older backups without this entry still work.
                if (albumCovers.exists() || File(albumCovers.absolutePath + ".bak").exists()) {
                    val bytes = AtomicFile(albumCovers).openRead().use { it.readBytes() }
                    require(bytes.size <= 100_000) { "Слишком много обложек" }
                    JSONObject(bytes.toString(Charsets.UTF_8))
                    zip.putNextEntry(ZipEntry("album_covers.json"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("backup-version.txt"))
                zip.write("quietmoments-v1.8-local\n".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("moments.json"))
                zip.write(payload.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    /**
     * Merge-only import. Existing entries and files are never overwritten.
     * Failed or incomplete imports leave old data unchanged.
     */
    @Synchronized fun importBackup(uri: Uri): Int {
        val staging = File(root, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            var metadata: String? = null
            var universeJson: String? = null
            var journalJson: String? = null
            var albumCoversJson: String? = null
            var capsuleJson: String? = null
            val seenZipPaths = mutableSetOf<String>()
            var totalMediaSize = 0L
            context.contentResolver.openInputStream(uri).use { stream ->
                requireNotNull(stream)
                ZipInputStream(stream).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (!entry.isDirectory) {
                            require(seenZipPaths.add(entry.name)) { "Повторяющийся путь ZIP" }
                            if (entry.name == "journal/reflections.json") {
                                journalJson = zip.readBytesLimited(2_000_000).toString(Charsets.UTF_8)
                                val j = JSONArray(journalJson!!)
                                require(j.length() <= 50000)
                            } else if (entry.name == "universe/state.json") {
                                universeJson = zip.readBytesLimited(2_000_000).toString(Charsets.UTF_8)
                                JSONObject(universeJson!!) // reject invalid JSON before changing local index
                            } else if (entry.name == "album_covers.json") {
                                albumCoversJson = zip.readBytesLimited(100_000).toString(Charsets.UTF_8)
                                val covers = JSONObject(albumCoversJson!!)
                                require(covers.length() <= 2000) { "Слишком много обложек" }
                                val keys = covers.keys()
                                while (keys.hasNext()) {
                                    val name = keys.next()
                                    require(name.isNotBlank() && name.length <= 120)
                                    require(DiaryThemes.all.any { it.id == covers.getString(name) })
                                }
                            } else if (entry.name == "capsules/manifest.json") {
                                capsuleJson = zip.readBytesLimited(TimeCapsuleRepository.MAX_INDEX_BYTES)
                                    .toString(Charsets.UTF_8)
                                TimeCapsuleRepository.decode(capsuleJson!!)
                            } else if (entry.name.matches(Regex("capsules/media/[a-f0-9-]{36}\\.(mp4|img)"))) {
                                val capsuleStage = File(staging, "capsules").apply { mkdirs() }
                                val target = File(capsuleStage, entry.name.substringAfterLast('/'))
                                target.outputStream().use { output ->
                                    zip.copyToLimited(output, TimeCapsuleRepository.MAX_MEDIA_BYTES)
                                }
                                totalMediaSize += target.length()
                                require(totalMediaSize <= 2L * 1024 * 1024 * 1024) { "Слишком большой архив" }
                            } else if (entry.name == "moments.json") {
                                metadata = zip.readBytesLimited(2_000_000).toString(Charsets.UTF_8)
                            } else if (entry.name.matches(Regex("media/[A-Za-z0-9_-]+\\.(mp4|img)"))) {
                                val target = File(staging, entry.name.substringAfterLast('/'))
                                target.outputStream().use { output -> zip.copyToLimited(output, 500L * 1024 * 1024) }
                                totalMediaSize += target.length()
                                require(totalMediaSize <= 2L * 1024 * 1024 * 1024) { "Слишком большой архив" }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val capsuleRepo = TimeCapsuleRepository(context, uidKey)
            val incomingCapsules = capsuleJson?.let { json ->
                capsuleRepo.stageImport(json, File(staging, "capsules"))
            } ?: emptyList()
            val array = JSONArray(requireNotNull(metadata) { "В архиве нет moments.json" })
            require(array.length() <= 50000) { "Слишком много записей" }
            val current = list()
            val existing = current.map { it.id }.toMutableSet()
            val incomingIds = mutableSetOf<String>()
            // Validate the entire manifest and all media before changing any existing record.
            for (i in 0 until array.length()) {
                val record = array.getJSONObject(i)
                val id = record.getString("id")
                require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && incomingIds.add(id)) { "Повторяющийся или неверный ID" }
                val file = record.getString("fileName")
                require(file.matches(Regex("[A-Za-z0-9_-]+\\.(mp4|img)"))) { "Неверное имя файла" }
                val kind = record.getString("kind")
                require((kind == "video" && file.endsWith(".mp4")) || (kind == "photo" && file.endsWith(".img"))) { "Неверный тип файла" }
                require(File(staging, file).isFile) { "В архиве отсутствует медиафайл" }
                record.optString("memoryDate", "").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
            }
            val journalTarget = File(root, "reflections.json")
            val oldJournal = if (journalJson != null && journalTarget.exists()) journalTarget.readBytes() else null
            val universeTarget = File(context.filesDir, "universe_backup/restore.json")
            val oldUniverse = if (universeJson != null && universeTarget.exists()) universeTarget.readBytes() else null
            val oldCovers = if (albumCoversJson != null && albumCovers.exists()) albumCovers.readBytes() else null
            var changedJournal = false
            var changedUniverse = false
            var changedCovers = false
            val oldCapsules = if (capsuleJson != null && (capsuleRepo.index.exists() ||
                File(capsuleRepo.index.absolutePath + ".bak").exists()))
                AtomicFile(capsuleRepo.index).openRead().use { it.readBytes() } else null
            var changedCapsules = false
            val movedCapsules = mutableListOf<File>()
            val accepted = mutableListOf<LocalMoment>()
            val moved = mutableListOf<File>()
            try {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val fileName = item.getString("fileName")
                    require(fileName.matches(Regex("[A-Za-z0-9_-]+\\.(mp4|img)"))) { "Неверное имя файла" }
                    val input = File(staging, fileName)
                    require(input.isFile) { "В архиве отсутствует медиафайл" }
                    val oldId = item.getString("id")
                    if (oldId in existing) continue
                    require(oldId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Неверный идентификатор" }
                    val id = oldId
                    existing += id
                    val ext = fileName.substringAfterLast('.')
                    val destination = File(media, "$id.$ext")
                    input.copyTo(destination)
                    moved += destination
                    accepted += LocalMoment(id, item.optString("album", "Без альбома").take(120),
                        item.optString("title", "Воспоминание").take(180),
                        item.optString("kind", "photo"), destination.name,
                        item.optLong("createdAt", System.currentTimeMillis()), item.optLong("deletedAt", 0),
                        item.optString("memoryDate", "").ifBlank {
                            Instant.ofEpochMilli(item.optLong("createdAt", System.currentTimeMillis()))
                                .atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        }, item.optString("note", "").take(10000),
                        item.optString("pageStyle", "moon"))
                }
                // Journal merge: existing dates win; imported entries fill gaps only.
                journalJson?.let { json ->
                    val journal = LocalJournal(context, uidKey)
                    val seen = journal.all().map { it.date }.toSet()
                    val imported = JSONArray(json)
                    for (i in 0 until imported.length()) {
                        val record = imported.getJSONObject(i)
                        val date = record.getString("date")
                        if (date !in seen) { changedJournal = true; journal.save(DailyReflection(
                            date, record.optString("question").take(500),
                            record.optString("answer").take(10000), record.optString("mood").take(50),
                            record.optString("reason").take(2000))) }
                    }
                }
                // Opt-in import restores Universe data on the next opening of its WebView.
                // Preserve current universe snapshot as a separate recovery file.
                universeJson?.let { json ->
                    val universeDir = File(context.filesDir, "universe_backup").apply { mkdirs() }
                    val currentUniverse = File(universeDir, "snapshot.json")
                    if (currentUniverse.exists()) currentUniverse.copyTo(File(universeDir, "before_import.json"), overwrite = true)
                    val pending = File(universeDir, "restore.pending")
                    changedUniverse = true
                    pending.writeText(json, Charsets.UTF_8)
                    if (!pending.renameTo(File(universeDir, "restore.json"))) {
                        pending.copyTo(File(universeDir, "restore.json"), overwrite = true)
                        pending.delete()
                    }
                    changedUniverse = true
                }
                albumCoversJson?.let { data ->
                    // Existing album choices win; imported choices fill missing names.
                    val existingCovers = if (albumCovers.exists())
                        JSONObject(albumCovers.readText(Charsets.UTF_8)) else JSONObject()
                    val importedCovers = JSONObject(data)
                    val keys = importedCovers.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        if (!existingCovers.has(name)) {
                            existingCovers.put(name, importedCovers.getString(name))
                        }
                    }
                    writeAtomic(albumCovers, existingCovers.toString().toByteArray(Charsets.UTF_8))
                    changedCovers = true
                }
                if (capsuleJson != null) {
                    val currentCapsules = capsuleRepo.all()
                    val existingIds = currentCapsules.map { it.id }.toSet()
                    val addCapsules = incomingCapsules.filterNot { it.id in existingIds }
                    for (capsule in addCapsules) {
                        if (capsule.attachment.isNotEmpty()) {
                            val source = File(staging, "capsules/" + capsule.attachment)
                            val destination = File(capsuleRepo.directory, capsule.attachment)
                            require(!destination.exists()) { "Конфликт имени вложения капсулы" }
                            source.copyTo(destination, overwrite = false)
                            movedCapsules += destination
                        }
                    }
                    changedCapsules = true
                    capsuleRepo.save(currentCapsules + addCapsules)
                }
                // Publish the media catalog only after auxiliary data has been written.
                store(current + accepted)
            } catch (t: Throwable) {
                // Restore old auxiliary files before removing newly copied media.
                if (changedJournal) {
                    if (oldJournal == null) journalTarget.delete() else writeAtomic(journalTarget, oldJournal)
                }
                if (changedUniverse) {
                    if (oldUniverse == null) universeTarget.delete() else writeAtomic(universeTarget, oldUniverse)
                }
                if (changedCovers) {
                    if (oldCovers == null) albumCovers.delete() else writeAtomic(albumCovers, oldCovers)
                }
                if (changedCapsules) {
                    if (oldCapsules == null) capsuleRepo.index.delete()
                    else writeAtomic(capsuleRepo.index, oldCapsules)
                }
                movedCapsules.forEach { it.delete() }
                moved.forEach { it.delete() }
                throw t
            }
            return accepted.size
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun ZipInputStream.readBytesLimited(limit: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        copyToLimited(buf, limit.toLong())
        return buf.toByteArray()
    }

    private fun ZipInputStream.copyToLimited(output: java.io.OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            count += n
            require(count <= maxBytes) { "Превышен допустимый размер файла" }
            output.write(buffer, 0, n)
        }
    }
}
