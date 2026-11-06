package com.quietmoments.diary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRestorePolicyTest {
    @Test fun newDiaryCanReceiveFirstUniverseBackup() {
        assertTrue(SafeRestorePolicy.canQueueUniverseRestore(false, false))
    }

    @Test fun existingUniverseIsNeverOverwrittenByImport() {
        assertFalse(SafeRestorePolicy.canQueueUniverseRestore(true, false))
    }

    @Test fun pendingUniverseRestoreIsNeverReplacedByImport() {
        assertFalse(SafeRestorePolicy.canQueueUniverseRestore(false, true))
    }

    @Test fun conflictingSnapshotAndRestoreBothPreventImport() {
        assertFalse(SafeRestorePolicy.canQueueUniverseRestore(true, true))
    }
}
