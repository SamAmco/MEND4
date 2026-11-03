package co.samco.mendroid.viewmodel

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.os.NetworkOnMainThreadException
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelStore
import co.samco.mend4.core.Settings
import co.samco.mendroid.R
import co.samco.mendroid.model.ErrorToast
import co.samco.mendroid.model.ErrorToastManager
import co.samco.mendroid.model.LocalLogDirectory
import co.samco.mendroid.model.LogFileManagerImpl
import co.samco.mendroid.model.PropertyManager
import co.samco.mendroid.model.PropertyManagerImpl
import java.io.File
import java.io.FileNotFoundException
import java.util.Properties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val models = ViewModelStore()
    private lateinit var properties: PropertyManagerImpl
    private lateinit var provider: CloudConfigProvider
    private lateinit var model: SettingsViewModel
    private val error = CompletableDeferred<Int>()
    private val imported = CompletableDeferred<String>()
    private val uri = Uri.parse("content://test.nextcloud/config.xml")

    private class CloudConfigProvider(val source: File) : ContentProvider() {
        val openedOnMain = CompletableDeferred<Boolean>()
        var unavailable = false
        override fun onCreate() = true
        override fun getType(uri: Uri) = "application/xml"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
            val onMain = Thread.currentThread() == Looper.getMainLooper().thread
            openedOnMain.complete(onMain)
            if (onMain) throw NetworkOnMainThreadException()
            if (unavailable) throw FileNotFoundException("Cloud file unavailable")
            return AssetFileDescriptor(ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY),
                0, AssetFileDescriptor.UNKNOWN_LENGTH)
        }
    }

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = RuntimeEnvironment.getApplication()
        properties = PropertyManagerImpl(context).apply { clearSettings() }
        provider = CloudConfigProvider(temporary.newFile("config.xml"))
        provider.attachInfo(context, ProviderInfo().apply { authority = uri.authority })
        ShadowContentResolver.registerProviderInternal(requireNotNull(uri.authority), provider)
        val errors = object : ErrorToastManager {
            override val errorToast = MutableSharedFlow<ErrorToast>()
            override fun showErrorToast(messageId: Int, formatArgs: List<String>) { error.complete(messageId) }
        }
        val observed = object : PropertyManager by properties {
            override fun setConfigUriPath(path: String) {
                properties.setConfigUriPath(path)
                imported.complete(path)
            }
        }
        model = SettingsViewModel(observed, context, errors,
            LogFileManagerImpl(LocalLogDirectory { null }, properties))
        models.put("settings", model)
    }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }

    @Test fun cloudConfigIsOpenedOffTheMainThreadAndImportedFromAStream() = runBlocking {
        provider.source.writeText("<properties>" + Settings.Name.All.mapIndexed { index, name ->
            "<entry key=\"${name.encodedName}\">value-$index</entry>"
        }.joinToString("") + "</properties>")
        model.onSetConfig(uri)
        assertEquals(uri.path, withTimeout(5_000) { imported.await() })
        assertFalse(provider.openedOnMain.await())
        Settings.Name.All.forEachIndexed { index, name -> assertEquals("value-$index", properties.getValue(name)) }
        assertFalse(error.isCompleted)
    }

    @Test fun cloudOpenFailureShowsAnErrorAndKeepsThePreviousConfig() = runBlocking {
        properties.setValue(Settings.Name.PUBLIC_KEY, "existing key")
        provider.unavailable = true
        model.onSetConfig(uri)
        assertEquals(R.string.config_import_error, withTimeout(5_000) { error.await() })
        assertEquals("existing key", properties.getValue(Settings.Name.PUBLIC_KEY))
        assertFalse(provider.openedOnMain.await())
    }

    @Test fun incompleteConfigDoesNotReplaceExistingSettings() = runBlocking {
        properties.setValue(Settings.Name.PUBLIC_KEY, "existing key")
        provider.source.writeText("<properties><entry key=\"public-key\">different key</entry></properties>")
        model.onSetConfig(uri)
        assertEquals(R.string.config_import_missing, withTimeout(5_000) { error.await() })
        assertEquals("existing key", properties.getValue(Settings.Name.PUBLIC_KEY))
    }
    @Test fun standardExportedPropertiesWithDoctypeAndCommentCanBeImported() = runBlocking {
        val exported = Properties().apply {
            Settings.Name.All.forEach { setProperty(it.encodedName, "exported value") }
        }
        provider.source.outputStream().use { exported.storeToXML(it, "MEND config") }
        model.onSetConfig(uri)
        assertEquals(uri.path, withTimeout(5_000) { imported.await() })
        Settings.Name.All.forEach { assertEquals("exported value", properties.getValue(it)) }
        assertFalse(error.isCompleted)
    }

    @Test fun emptyPropertiesReturnAnErrorInsteadOfLooping() = runBlocking {
        provider.source.writeText("<properties/>")
        model.onSetConfig(uri)
        assertEquals(R.string.config_import_missing, withTimeout(5_000) { error.await() })
    }

}
