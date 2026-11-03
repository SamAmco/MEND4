package co.samco.mendroid.model

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalLogDirectoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var folder: File
    private lateinit var directory: LocalLogDirectory
    private lateinit var properties: PropertyManagerImpl
    private lateinit var manager: LogFileManagerImpl

    @Before fun setup() {
        folder = temporary.newFolder()
        directory = LocalLogDirectory { DocumentFile.fromFile(folder) }
        properties = PropertyManagerImpl(RuntimeEnvironment.getApplication())
        properties.setLogDirUri(folder.toURI().toString())
        properties.setCurrentLogUri(null)
        manager = LogFileManagerImpl(directory, properties)
    }
    @After fun teardown() { properties.setCurrentLogUri(null) }

    @Test fun directoryListingIncludesOnlyDirectMendFilesAndReflectsExternalChanges() = runBlocking {
        File(folder, "z.mend").writeText("encrypted")
        File(folder, "a.mend").writeText("encrypted")
        File(folder, "image.enc").writeText("attachment")
        File(folder, "config.xml").writeText("config")
        File(folder, "nested.mend").mkdir()
        File(folder, "nested.mend/child.mend").writeText("nested")
        manager.refresh()
        assertEquals(listOf("a.mend", "z.mend"), manager.logs.first().map { it.name })
        File(folder, "a.mend").delete()
        File(folder, "new.mend").writeText("encrypted")
        manager.refresh()
        assertEquals(listOf("new.mend", "z.mend"), manager.logs.first().map { it.name })
    }

    @Test fun creationAddsExtensionAndRemembersSelectionAcrossManagerInstances() = runBlocking {
        manager.create("phone")
        val created = requireNotNull(manager.currentForWrite())
        assertEquals("phone.mend", created.name)
        assertTrue(File(folder, "phone.mend").isFile)
        val restarted = LogFileManagerImpl(directory, properties)
        assertEquals(created, restarted.currentForWrite())
    }

    @Test fun existingExtensionIsNotDuplicatedAndDuplicateCreationNeverOverwrites() = runBlocking {
        manager.create("phone.mend")
        val original = File(folder, "phone.mend").apply { writeText("encrypted contents") }
        try { manager.create("phone"); fail("Expected duplicate rejection") }
        catch (_: FileAlreadyExistsException) { }
        assertEquals("encrypted contents", original.readText())
        assertEquals(listOf("phone.mend"), directory.files().map { it.name })
    }

    @Test fun invalidNamesCannotEscapeTheDirectoryOrCreateHiddenEmptyNames() = runBlocking {
        for (name in listOf("", " ", ".", "..", ".mend", "../escape", "a/b", "a\\b", "line\nfeed", "nul\u0000")) {
            try { manager.create(name); fail("Expected invalid name rejection") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test fun deletingSelectedFileInvalidatesItBeforeAWriteAndDoesNotRecreateIt() = runBlocking {
        manager.create("phone")
        File(folder, "phone.mend").delete()
        assertNull(manager.currentForWrite())
        assertNull(properties.getCurrentLogUri())
        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test fun staleSelectionCannotSelectAFileMovedOutsideTheDirectory() = runBlocking {
        manager.create("phone")
        val selected = requireNotNull(manager.currentForWrite())
        File(folder, "phone.mend").renameTo(File(temporary.root, "moved.mend"))
        try { manager.select(selected); fail("Expected stale selection rejection") }
        catch (_: IOException) { }
        assertNull(manager.currentLog.first())
    }

    @Test fun changingDirectoryClearsSelectionEvenWhenTheNewDirectoryContainsTheSameName() = runBlocking {
        manager.create("phone")
        folder = temporary.newFolder()
        File(folder, "phone.mend").writeText("another log")
        properties.setLogDirUri(folder.toURI().toString())
        assertNull(manager.currentForWrite())
        assertEquals(listOf("phone.mend"), manager.logs.first().map { it.name })
    }

    @Test fun inaccessibleDirectoryClearsTheVisibleSnapshotAndReportsFailure() = runBlocking {
        manager.create("phone")
        folder.deleteRecursively()
        try { manager.refresh(); fail("Expected inaccessible directory failure") }
        catch (_: IOException) { }
        assertTrue(manager.logs.first().isEmpty())
        assertNull(manager.currentLog.first())
    }

    @Test fun localOnlySelectionRejectsCloudAndArbitraryDocumentProviders() {
        assertTrue(LocalLogDirectory.isLocalTree(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMEND")))
        assertTrue(LocalLogDirectory.isLocalTree(Uri.parse("content://com.android.externalstorage.documents/tree/ABCD-1234%3AMEND")))
        assertFalse(LocalLogDirectory.isLocalTree(Uri.parse("content://org.nextcloud.documents/tree/folder")))
        assertFalse(LocalLogDirectory.isLocalTree(Uri.parse("content://another.provider/tree/folder")))
        assertFalse(LocalLogDirectory.isLocalTree(Uri.parse("file:///storage/emulated/0/MEND")))
        assertFalse(LocalLogDirectory.isLocalTree(Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMEND")))
    }
}
