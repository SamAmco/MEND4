package co.samco.mend4.desktop.helper

import java.io.File

interface MergeHelper {
    fun mergeToFirstOrSecond(logFiles: Pair<File, File>, first: Boolean)

    fun mergeLogFilesToNew(files: Pair<File, File>, outputLog: File)
}

