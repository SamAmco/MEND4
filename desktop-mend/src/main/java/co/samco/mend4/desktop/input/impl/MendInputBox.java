package co.samco.mend4.desktop.input.impl;

import co.samco.mend4.desktop.core.ColorSchemeData;
import co.samco.mend4.desktop.input.InputListener;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;

public class MendInputBox extends JFrame implements KeyListener {
    private static final long serialVersionUID = -7214084221385969252L;

    private final JTextComponent textInput;
    private List<InputListener> listeners = new ArrayList<InputListener>();

    private boolean shiftDown = false;
    private boolean ctrlDown = false;

    public MendInputBox(boolean decorated, boolean password, int width, int height) {
        setSize(width, height);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (!decorated)
            setUndecorated(true);

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(dim.width / 2 - this.getSize().width / 2, dim.height / 2 - this.getSize().height / 2);

        if (password) {
            textInput = new JPasswordField();
            textInput.setPreferredSize(new Dimension(width, height));
            this.add(textInput);
        } else {
            JTextArea textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textInput = textArea;
            JScrollPane sp = new JScrollPane(textArea);
            sp.setPreferredSize(new Dimension(width, height));
            this.add(sp);
        }

        textInput.setBackground(ColorSchemeData.getColor2());
        textInput.setFont(ColorSchemeData.getConsoleFont());
        textInput.setBorder(BorderFactory.createLineBorder(ColorSchemeData.getColor1()));
        textInput.addKeyListener(this);
    }

    public void addListener(InputListener l) {
        listeners.add(l);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftDown = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            ctrlDown = true;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        handleControlsUp(e);
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            handleSubmit();
        }
    }

    private void handleSubmit() {
        char[] text = getText();

        if (shiftDown || ctrlDown) {
            for (InputListener l : listeners) {
                l.onWrite(text);
            }
        }
        if (shiftDown) {
            close();
        }
        if (ctrlDown) {
            clear();
        }
    }

    private char[] getText() {
        return (textInput instanceof JPasswordField)
                ? ((JPasswordField) textInput).getPassword()
                : textInput.getText().toCharArray();
    }

    private void handleControlsUp(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            shiftDown = false;
        }
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            ctrlDown = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    public void clear() {
        textInput.setText("");
    }

    public void close() {
        setVisible(false);
        dispose();
    }
}