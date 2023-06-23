package co.samco.mend4.desktop.helper

import co.samco.mend4.desktop.input.InputListener
import co.samco.mend4.desktop.input.impl.MendInputBox
import javax.inject.Inject

class InputHelper @Inject constructor() {
    fun createInputProviderAndRegisterListener(inputListener: InputListener) {
        //TODO can we allow different text editors somehow?
        //TODO maybe we should allow users to configure these parameters?
        val mendInputBox = MendInputBox(800, 250)
        mendInputBox.addListener(inputListener)
        mendInputBox.isVisible = true
    }
}