package com.quietmoments.diary

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.quietmoments.universe.DiarySessionGate

/**
 * Lock the entire diary process, including Universe, when Android backgrounds it.
 * ProcessLifecycleOwner does NOT stop on ordinary in-app Activity switches.
 * Local drafts remain saved in their existing Compose saveable state.
 */
class QuietMomentsApplication : Application(), LifecycleEventObserver {
    override fun onCreate() {
        super.onCreate()
        // Remove plaintext staging files left by process death or device reboot.
        java.io.File(cacheDir, "cloud_backups").listFiles()?.filter {
            it.name.startsWith("qm_secure_staging_")
        }?.forEach { it.delete() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            DiarySessionGate.lock()
        }
    }
}
