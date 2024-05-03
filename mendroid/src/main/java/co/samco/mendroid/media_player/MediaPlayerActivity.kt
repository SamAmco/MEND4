package co.samco.mendroid.media_player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.ui.PlayerView
import co.samco.mendroid.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MediaPlayerActivity : ComponentActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var mediaPlayerService: MediaPlayerServiceInterface
    private var fileUri: Uri? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val mediaPlayerService = service as? MediaPlayerServiceInterface ?: return
            this@MediaPlayerActivity.mediaPlayerService = mediaPlayerService
            mediaPlayerService.getPlayerInstance().let {
                playerView.player = it
                if (fileUri != null) mediaPlayerService.setMediaUri(fileUri!!)
                else if (!mediaPlayerService.hasMediaUri()) showErrorMessage()
            }
        }

        private fun showErrorMessage() {
            Toast.makeText(
                this@MediaPlayerActivity,
                getString(R.string.failed_to_get_file_uri),
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerView.player = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        readFileUri(intent)
        setContentView(createPlayerView())
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        readFileUri(intent)
        bindToService()
    }

    private fun readFileUri(intent: Intent?) {
        fileUri = intent?.data?.toString()?.let { Uri.parse(it) }
    }

    private fun createPlayerView(): View {
        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return playerView
    }

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    private fun bindToService() {
        unbindFromServiceIfBound()
        val serviceIntent = Intent(this, MediaPlayerService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        isBound = true
    }

    private fun unbindFromServiceIfBound() {
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onStop() {
        super.onStop()
        unbindFromServiceIfBound()
    }
}