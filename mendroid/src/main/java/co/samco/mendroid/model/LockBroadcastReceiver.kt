package co.samco.mendroid.model

import android.content.BroadcastReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.Context
import android.content.Intent

@AndroidEntryPoint
class LockBroadcastReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_LOCK = "ACTION_LOCK"
    }

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_LOCK) privateKeyManager.onUserLockMend()
    }
}