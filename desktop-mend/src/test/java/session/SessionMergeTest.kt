package session

import co.samco.mend4.core.bean.LogDataBlocks
import co.samco.mend4.core.bean.LogDataBlocksAndText
import co.samco.mend4.core.crypto.CryptoProvider
import co.samco.mend4.desktop.helper.FileResolveHelper
import co.samco.mend4.desktop.session.SessionCommandContext
import co.samco.mend4.desktop.session.SessionMerge
import co.samco.mend4.desktop.session.SessionOutput
import co.samco.mend4.desktop.session.SessionRequest
import com.nhaarman.mockitokotlin2.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.security.PrivateKey

class SessionMergeTest {
    @get:Rule val directory = TemporaryFolder()
    private val crypto = mock<CryptoProvider>()
    private val files = mock<FileResolveHelper>()
    private val key = mock<PrivateKey>()
    private val merger = SessionMerge(files, crypto)

    @Before fun setup() {
        whenever(files.ensureLogNameHasFileExtension(any())).thenAnswer {
            val name = it.arguments[0] as String
            if (name.endsWith(".mend")) name else "$name.mend"
        }
        whenever(files.resolveAsLogFilePath(any())).thenAnswer {
            File(directory.root, "${it.arguments[0]}.mend")
        }
        // Characters stand for encrypted records. Decoded headers supply their dates;
        // the merger must preserve the original bytes when sorting and deduplicating.
        whenever(crypto.logHasNext(any(), any())).thenAnswer {
            val value = (it.arguments[0] as InputStream).read()
            (it.arguments[1] as ByteArray)[0] = value.toByte()
            value != -1
        }
        whenever(crypto.getNextLogTextWithDataBlocks(any(), any(), any())).thenAnswer {
            assertSame(key, it.arguments[1])
            val token = (it.arguments[2] as ByteArray)[0].toInt().toChar()
            val text = if (token in 'a'..'d') "01/01/2020 00:00:0${token - 'a'} record" else "undated record"
            LogDataBlocksAndText(LogDataBlocks(token.toString().toByteArray(), ByteArray(0),
                ByteArray(0), ByteArray(0), ByteArray(0), ByteArray(0)), text)
        }
    }

    private fun merge(arguments: List<String>) {
        merger.merge(key, SessionCommandContext(
            request = SessionRequest("merge", arguments, directory.root.path),
            output = SessionOutput(ByteArrayOutputStream(), PrintStream(ByteArrayOutputStream())),
        ))
        verify(crypto, never()).unlock(any())
    }

    private fun sources(first: String, second: String) {
        File(directory.root, "first.mend").writeText(first)
        File(directory.root, "second.mend").writeText(second)
    }

    @Test fun sortsAndDeduplicatesWithoutReloadingTheKey() {
        sources("ac", "abd")
        merge(listOf("first.mend", "second.mend", "output"))
        assertEquals("abcd", File(directory.root, "output.mend").readText())
        assertEquals("ac", File(directory.root, "first.mend").readText())
    }

    @Test fun handlesEmptyLogsAndUndatedEntries() {
        for ((first, second, expected) in listOf(
            Triple("", "", ""), Triple("ab", "", "ab"), Triple("", "cd", "cd"),
            Triple("xa", "yb", "xyab"), Triple("xx", "yy", "xxyy"),
        )) {
            sources(first, second)
            merge(listOf("first.mend", "second.mend", "output"))
            assertEquals(expected, File(directory.root, "output.mend").readText())
        }
    }

    @Test fun replacesEitherInputOnlyWhenExplicitlyRequested() {
        for (flag in listOf("-1", "-2")) {
            sources("ac", "bd")
            merge(listOf(flag, "first", "second"))
            assertEquals(if (flag == "-1") "abcd" else "ac", File(directory.root, "first.mend").readText())
            assertEquals(if (flag == "-2") "abcd" else "bd", File(directory.root, "second.mend").readText())
        }
    }

    @Test fun rejectsInvalidArgumentsBeforeCreatingOutput() {
        sources("ac", "bd")
        assertThrows(IllegalArgumentException::class.java) { merge(listOf("first")) }
        assertThrows(IllegalArgumentException::class.java) { merge(listOf("-3", "first", "second")) }
        assertThrows(IllegalArgumentException::class.java) { merge(listOf("missing", "second", "output")) }
        assertFalse(File(directory.root, "output.mend").exists())
    }
}
