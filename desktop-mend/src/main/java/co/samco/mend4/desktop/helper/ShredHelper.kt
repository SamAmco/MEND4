package co.samco.mend4.desktop.helper

interface ShredHelper {
    fun generateShredCommandArgs(fileName: String, commandString: String): Array<String>

    fun tryShredFile(absolutePath: String)

    fun shredFilesInDirectory(dir: String)
}

