package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.desktop.helper.VersionHelper
import javax.inject.Inject

class VersionHelperImpl @Inject constructor() : VersionHelper {
    override val version: String
        get() = javaClass.getPackage().implementationVersion
}