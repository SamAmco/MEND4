package co.samco.mend4.desktop.input.impl

import co.samco.mend4.desktop.core.ColorSchemeData.color1
import co.samco.mend4.desktop.core.ColorSchemeData.color2
import co.samco.mend4.desktop.core.ColorSchemeData.consoleFont
import co.samco.mend4.desktop.input.InputListener
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.text.JTextComponent

class MendInputBox(
    width: Int,
    height: Int
) : JFrame(), KeyListener {
    private var textInput: JTextComponent
    private val listeners: MutableList<InputListener> = ArrayList()
    private var shiftDown = false
    private var ctrlDown = false

    init {
        setSize(width, height)

        defaultCloseOperation = EXIT_ON_CLOSE

        val dimens = Toolkit.getDefaultToolkit().screenSize

        this.setLocation(
            dimens.width / 2 - this.size.width / 2,
            dimens.height / 2 - this.size.height / 2
        )

        textInput = JTextArea().apply {
            lineWrap = true
            wrapStyleWord = true
            background = color2
            font = consoleFont
            border = BorderFactory.createLineBorder(color1)
            addKeyListener(this@MendInputBox)
        }

        JScrollPane(textInput).apply {
            preferredSize = Dimension(width, height)
            this@MendInputBox.add(this)
        }
    }

    fun addListener(l: InputListener) {
        listeners.add(l)
    }

    override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_SHIFT) {
            shiftDown = true
        }
        if (e.keyCode == KeyEvent.VK_CONTROL) {
            ctrlDown = true
        }
    }

    override fun keyReleased(e: KeyEvent) {
        handleControlsUp(e)
        if (e.keyCode == KeyEvent.VK_ENTER) {
            handleSubmit()
        }
    }

    private fun handleSubmit() {
        val text = text
        if (shiftDown || ctrlDown) {
            for (l in listeners) {
                l.onWrite(text)
            }
        }
        if (shiftDown) {
            close()
        }
        if (ctrlDown) {
            clear()
        }
    }

    private val text: CharArray
        get() = textInput.text.toCharArray()

    private fun handleControlsUp(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_SHIFT) {
            shiftDown = false
        }
        if (e.keyCode == KeyEvent.VK_CONTROL) {
            ctrlDown = false
        }
    }

    override fun keyTyped(e: KeyEvent) {}

    private fun clear() {
        textInput.text = ""
    }

    private fun close() {
        isVisible = false
        listeners.forEach(Consumer { l: InputListener -> l.onClose() })
        dispose()
    }

    companion object {
        private const val serialVersionUID = -7214084221385969252L
    }
}