package co.samco.mend4.desktop.helper.impl

import co.samco.mend4.desktop.helper.InputHelper
import co.samco.mend4.desktop.input.InputListener
import co.samco.mend4.desktop.input.impl.MendInputBox
import javax.inject.Inject

class InputHelperImpl @Inject constructor() : InputHelper {
    override fun createInputProviderAndRegisterListener(inputListener: InputListener) {
        //TODO can we allow different text editors somehow?
        //TODO maybe we should allow users to configure these parameters?
        val mendInputBox = MendInputBox(800, 250)
        mendInputBox.addListener(inputListener)
        mendInputBox.isVisible = true
    }
}