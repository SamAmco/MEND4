package helper

import co.samco.mend4.desktop.helper.impl.ShredHelperImpl
import commands.TestBase
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShredHelperTest : TestBase() {
    private var fileNameRef: String? = null

    @Before
    override fun setup() {
        super.setup()
        fileNameRef = strings["Shred.fileName"]

        shredHelper = ShredHelperImpl(
            strings = strings,
            settings = settings,
            log = log,
            fileResolveHelper = fileResolveHelper,
            osDao = osDao
        )
    }

    private fun assertShredCommand(filename: String, command: String, result: Array<String>) {
        assertTrue(shredHelper.generateShredCommandArgs(filename, command).contentEquals(result))
    }

    @Test
    fun testGenerateShredCommandArgs() {
        val filename = "sam"
        val command = "some $fileNameRef to shred"
        val result = arrayOf("some", filename, "to", "shred")
        assertShredCommand(filename, command, result)
    }

    @Test
    fun testGenerateShredCommandArgsMultipleFilenameInstances() {
        val filename = "sam"
        val command = "some $fileNameRef to shred $fileNameRef"
        val result = arrayOf("some", filename, "to", "shred", filename)
        assertShredCommand(filename, command, result)
    }

    @Test
    fun testGenerateShredCommandRealisticExample() {
        val filename = "some-file.txt"
        val command = "shred -u $fileNameRef"
        val result = arrayOf("shred", "-u", filename)
        assertShredCommand(filename, command, result)
    }
}