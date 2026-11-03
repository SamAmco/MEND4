package co.samco.mendroid.nextcloud

import android.app.Application
import co.samco.mendroid.upload.MemoryCredentials
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NextcloudSettingsTest {
    @Test fun newAccountCannotReusePreviousAccountsFolderSelections() {
        val context = RuntimeEnvironment.getApplication()
        val credentials = MemoryCredentials()
        val settings = NextcloudSettings(context, credentials)
        settings.connect(NextcloudSession(NextcloudAccount("https://cloud.example", "one"), "login", "secret"))
        settings.selectLogFolder(RemotePath.of("/one"))
        // Simulate process death after storing the new credential but before changing preferences.
        credentials.save(NextcloudSession(NextcloudAccount("https://cloud.example", "two"), "login", "secret"))
        assertNull(NextcloudSettings(context, credentials).config.value.logs)
    }
    @Test fun reconnectingSameAccountPreservesFoldersWithoutExposingCredentials() {
        val context = RuntimeEnvironment.getApplication()
        val credentials = MemoryCredentials()
        val settings = NextcloudSettings(context, credentials)
        val session = NextcloudSession(NextcloudAccount("https://cloud.example", "user"), "login", "secret")
        settings.connect(session)
        settings.selectLogFolder(RemotePath.of("/logs"))
        settings.disconnect()
        settings.connect(session)
        assertEquals(RemotePath.of("/logs"), settings.config.value.logs)
        assertFalse(settings.config.value.toString().contains("secret"))
    }
    @Test fun additionalFoldersAreUniquePersistedAndBoundToTheirAccount() {
        val context = RuntimeEnvironment.getApplication()
        val credentials = MemoryCredentials()
        val settings = NextcloudSettings(context, credentials)
        val session = NextcloudSession(NextcloudAccount("https://cloud.example", "user"), "login", "secret")
        settings.connect(session)
        settings.addAdditionalFolder(RemotePath.of("/read"))
        settings.addAdditionalFolder(RemotePath.of("/read"))
        assertEquals(listOf(RemotePath.of("/read")), NextcloudSettings(context, credentials).config.value.additionalFolders)
        settings.disconnect()
        settings.connect(session)
        assertEquals(listOf(RemotePath.of("/read")), settings.config.value.additionalFolders)
        settings.removeAdditionalFolder(RemotePath.of("/read"))
        assertTrue(settings.config.value.additionalFolders.isEmpty())
        settings.addAdditionalFolder(RemotePath.of("/read"))
        credentials.save(NextcloudSession(NextcloudAccount("https://cloud.example", "other"), "login", "secret"))
        assertTrue(NextcloudSettings(context, credentials).config.value.additionalFolders.isEmpty())
    }

    @Test fun unreadableCredentialsBecomeExplicitSetupState() {
        val credentials = MemoryCredentials().apply { value = CredentialRead.Unavailable }
        val config = NextcloudSettings(RuntimeEnvironment.getApplication(), credentials).config.value
        assertTrue(config.credentialError)
        assertNull(config.account)
    }
}
