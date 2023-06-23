package co.samco.mend4.desktop.input

interface InputListener {
    fun onWrite(text: CharArray)
    fun onClose()
}