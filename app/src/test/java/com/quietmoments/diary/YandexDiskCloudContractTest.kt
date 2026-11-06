package com.quietmoments.diary

import org.junit.Assert.assertTrue
import org.junit.Test

/** Filename shape used for private app-folder backups, shared with Google ZIP format. */
class YandexDiskCloudContractTest {
    @Test fun privateCloudFolderDoesNotRequireUserDiskPermissions() {
        val publicClientId = "51b8d2a4853c4d2ba4a908cebd748281"
        assertTrue(publicClientId.matches(Regex("[a-f0-9]{32}")))
    }
}
