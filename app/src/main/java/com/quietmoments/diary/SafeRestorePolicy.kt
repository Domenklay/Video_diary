package com.quietmoments.diary

/**
 * Preserve an existing Universe. A restore payload replaces the WebView state
 * when consumed, so it must only be queued when neither a snapshot nor another
 * pending restore already exists.
 */
internal object SafeRestorePolicy {
    fun canQueueUniverseRestore(hasExistingSnapshot: Boolean, hasPendingRestore: Boolean): Boolean =
        !hasExistingSnapshot && !hasPendingRestore
}
