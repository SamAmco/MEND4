package co.samco.mend4.desktop.helper

import co.samco.mend4.desktop.input.InputListener


interface InputHelper {
    fun createInputProviderAndRegisterListener(inputListener: InputListener)
}

