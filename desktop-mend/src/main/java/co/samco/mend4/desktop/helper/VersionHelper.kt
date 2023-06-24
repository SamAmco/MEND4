package co.samco.mend4.desktop.helper

import javax.inject.Inject

class VersionHelper @Inject constructor() {
    val version: String
        get() = javaClass.getPackage().implementationVersion
}