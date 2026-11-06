package com.quietmoments.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudSnapshotFingerprintTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fingerprint(root: File): String =
        CloudSnapshotFingerprint.calculate(listOf("moments" to root), "theme=moon;motion=true")

    @Test fun unchangedSnapshotIsStable() {
        val root = tmp.newFolder("stable")
        File(root, "index.json").writeText("{\"day\":1}")
        File(root, "photo.img").writeBytes(byteArrayOf(2, 4, 6))
        assertEquals(fingerprint(root), fingerprint(root))
    }

    @Test fun metadataContentChangeDetectedEvenWithSameSizeAndTime() {
        val root = tmp.newFolder("metadata")
        val old = File(root, "profile.json")
        old.writeText("{\"mood\":\"calm\"}")
        val timestamp = old.lastModified()
        val first = fingerprint(root)
        old.writeText("{\"mood\":\"glad\"}")
        assertTrue(old.setLastModified(timestamp))
        assertNotEquals(first, fingerprint(root))
    }

    @Test fun changesInOlderFilesAreNotLostByNewestTimestamp() {
        val root = tmp.newFolder("older")
        val old = File(root, "old.img").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            setLastModified(1_600_000_000_000)
        }
        File(root, "newer.img").apply {
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(1_700_000_000_000)
        }
        val first = fingerprint(root)
        old.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertNotEquals(first, fingerprint(root))
    }

    @Test fun uploadMustNotClaimEditsMadeMidTransfer() {
        assertTrue(CloudSnapshotFingerprint.unchangedDuringUpload("a", "a", "a"))
        assertFalse(CloudSnapshotFingerprint.unchangedDuringUpload("a", "b", "b"))
        assertFalse(CloudSnapshotFingerprint.unchangedDuringUpload("a", "a", "c"))
    }
}
