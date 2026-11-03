package co.samco.mendroid.upload

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class UploadSettingsTest {
    @Test fun newAccountCannotReusePreviousAccountsFolderSelections() {
        val context = RuntimeEnvironment.getApplication()
        val credentials = MemoryCredentials()
        val settings = UploadSettings(context, credentials)
        settings.connect(NextcloudSession(NextcloudAccount("https://cloud.example", "one"), "login", "secret"))
        settings.selectFolder(UploadKind.LOG, RemotePath.of("/one"))
        // Simulate process death after storing the new credential but before changing preferences.
        credentials.save(NextcloudSession(NextcloudAccount("https://cloud.example", "two"), "login", "secret"))
        assertNull(UploadSettings(context, credentials).config.value.logs)
    }
    @Test fun reconnectingSameAccountPreservesFoldersWithoutExposingCredentials() {
        val context = RuntimeEnvironment.getApplication()
        val credentials = MemoryCredentials()
        val settings = UploadSettings(context, credentials)
        val session = NextcloudSession(NextcloudAccount("https://cloud.example", "user"), "login", "secret")
        settings.connect(session)
        settings.selectFolder(UploadKind.LOG, RemotePath.of("/logs"))
        settings.disconnect()
        settings.connect(session)
        assertEquals(RemotePath.of("/logs"), settings.config.value.logs)
        assertFalse(settings.config.value.toString().contains("secret"))
    }
    @Test fun unreadableCredentialsBecomeExplicitSetupState() {
        val credentials = MemoryCredentials().apply { value = CredentialRead.Unavailable }
        val config = UploadSettings(RuntimeEnvironment.getApplication(), credentials).config.value
        assertTrue(config.credentialError)
        assertNull(config.account)
    }
}
