package org.micromanager.asidispim.Data;

import javax.swing.ImageIcon;

import org.micromanager.MMStudio;

import com.swtdesigner.SwingResourceManager;

public final class Icons {

	private final static String CANCEL_PATH = "/org/micromanager/icons/cancel.png";
	private final static String CAMERA_GO_PATH = "/org/micromanager/icons/camera_go.png";
	private final static String ARROW_UP_PATH = "/org/micromanager/icons/arrow_up.png";
	private final static String ARROW_DOWN_PATH = "/org/micromanager/icons/arrow_down.png";
	private final static String ARROW_RIGHT_PATH = "/org/micromanager/icons/arrow_right.png";
	private final static String MICROSCOPE_PATH = "/org/micromanager/icons/microscope.gif";
	
	public final static ImageIcon CANCEL = SwingResourceManager.getIcon(MMStudio.class, CANCEL_PATH);
	public final static ImageIcon CAMERA_GO = SwingResourceManager.getIcon(MMStudio.class, CAMERA_GO_PATH);
	public final static ImageIcon ARROW_UP = SwingResourceManager.getIcon(MMStudio.class, ARROW_UP_PATH);
	public final static ImageIcon ARROW_DOWN = SwingResourceManager.getIcon(MMStudio.class, ARROW_DOWN_PATH);
	public final static ImageIcon ARROW_RIGHT = SwingResourceManager.getIcon(MMStudio.class, ARROW_RIGHT_PATH);
	public final static ImageIcon MICROSCOPE = SwingResourceManager.getIcon(MMStudio.class, MICROSCOPE_PATH);
	
	
}
