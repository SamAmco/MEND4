package co.samco.mendroid

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import co.samco.mendroid.model.PrivateKeyManager
import co.samco.mendroid.model.ScreenEventReceiver
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class Mend4Application : Application() {

    @Inject
    lateinit var privateKeyManager: PrivateKeyManager

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

        privateKeyManager.forceCleanFiles()

        //Delete all files in all private locations
        filesDir.listFiles()?.forEach { it.deleteRecursively() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    override fun onTerminate() {
        super.onTerminate()
        unregisterReceiver(screenEventReceiver)
        //Destroy any decrypted files
        privateKeyManager.forceCleanFiles()
    }
}