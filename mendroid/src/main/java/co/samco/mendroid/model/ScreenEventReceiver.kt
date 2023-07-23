package co.samco.mendroid.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ScreenEventReceiver : BroadcastReceiver() {

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_SCREEN_OFF) {
            privateKeyManager.onScreenOff()
        }
    }
}