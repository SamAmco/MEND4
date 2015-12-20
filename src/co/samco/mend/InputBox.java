package co.samco.mend;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.text.JTextComponent;

public class InputBox extends JFrame implements KeyListener
{
	private static final long serialVersionUID = -7214084221385969252L;
	
	JTextComponent textInput;
	List<InputBoxListener> listeners = new ArrayList<InputBoxListener>();
	
	boolean shiftDown = false;
	boolean ctrlDown = false;
	
	public InputBox(boolean decorated, boolean password, int width, int height)
	{
		setSize(width,height);
		setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		if (!decorated)
			setUndecorated(true);
		
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(dim.width/2-this.getSize().width/2, dim.height/2-this.getSize().height/2);
		
		if (password)
			textInput = new JPasswordField();
		else
			textInput = new JTextArea();
		
		textInput.setPreferredSize(new Dimension(width, height));
		textInput.setBackground(ColorSchemeData.getColor2());
		textInput.setFont(ColorSchemeData.getConsoleFont());
		textInput.setBorder(BorderFactory.createLineBorder(ColorSchemeData.getColor1()));
		textInput.addKeyListener(this);
		this.add(textInput);
		
	}
	
	public void addListener(InputBoxListener l)
	{
		listeners.add(l);
	}

	@Override
	public void keyPressed(KeyEvent e) 
	{
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftDown = true;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL)
		{
			ctrlDown = true;
		}
	}

	@Override
	public void keyReleased(KeyEvent e) 
	{
		if (e.getKeyCode() == KeyEvent.VK_SHIFT)
		{
			shiftDown = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_CONTROL)
		{
			ctrlDown = false;
		}
		if (e.getKeyCode() == KeyEvent.VK_ENTER)
		{
			char[] text = (textInput instanceof JPasswordField) 
					? ((JPasswordField)textInput).getPassword() 
					: textInput.getText().toCharArray();
					
			for (InputBoxListener l : listeners)
			{
				l.OnEnter(text);
				if (shiftDown)
					l.OnShiftEnter(text);
				if (ctrlDown)
					l.OnCtrlEnter(text);
			}
		}
	}

	@Override
	public void keyTyped(KeyEvent e) {}
	
	public void clear()
	{
		textInput.setText("");
	}
	
	public void close()
	{
		setVisible(false);
		dispose();
	}
}