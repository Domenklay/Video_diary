package com.quietmoments.diary

import java.io.File
import java.security.MessageDigest

/**
 * Local-only change detection for Google backup snapshots.
 * File contents are streamed only for small JSON metadata. Large photo and
 * video recordings are identified by name, size and modification timestamp.
 */
internal object CloudSnapshotFingerprint {
    fun calculate(sources: List<Pair<String, File>>, appearance: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
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
        digest.update(appearance.toByteArray(Charsets.UTF_8))
        return "v2:" + digest.digest().joinToString("") { b ->
            (b.toInt() and 255).toString(16).padStart(2, '0')
        }
    }

    /** A single upload must never be credited with changes made during its transfer. */
    fun unchangedDuringUpload(before: String, afterExport: String, afterUpload: String): Boolean =
        before == afterExport && before == afterUpload
}
