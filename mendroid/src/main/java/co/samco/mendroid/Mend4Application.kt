package co.samco.mendroid

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import co.samco.mendroid.model.ScreenEventReceiver
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class Mend4Application : Application() {

    private lateinit var screenEventReceiver: ScreenEventReceiver

    override fun onCreate() {
        super.onCreate()
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        screenEventReceiver = ScreenEventReceiver()
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenEventReceiver, intentFilter)
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(screenEventReceiver)
    }
}