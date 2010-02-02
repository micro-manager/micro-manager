import ij.plugin.*;
import ij.*;
import ij.io.*;
import com.apple.mrj.*;

/**	This Mac specific plugin handles the "About" and "Quit" items in the Apple menu and opens
	 files dropped on ImageJ and files with creator code "imgJ" that are double-clicked. It displays
	 dialogs in a separate thread to avoid hang ups on OS X. */ 
public class QuitHandler implements PlugIn, MRJAboutHandler, MRJQuitHandler, MRJOpenDocumentHandler, Runnable {
	private static final int ABOUT=0, QUIT=1, OPEN=2;
	private int command;
	private java.io.File file;

	public void run(String arg) {
		MRJApplicationUtils.registerAboutHandler(this);
		MRJApplicationUtils.registerQuitHandler(this);
		MRJApplicationUtils.registerOpenDocumentHandler(this);
	}
    
	public void handleAbout() {
		startThread(ABOUT);
	}
    
	public void handleQuit() {
		startThread(QUIT);
		throw new IllegalStateException();
	}

	public void handleOpenFile (java.io.File file) {
		this.file = file;
		startThread(OPEN);
	}
	
	void startThread(int command) {
		this.command = command;
		Thread thread = new Thread(this, "Quit Handler");
		thread.start();
	}
	
	public void run() {
		switch (command) {
			case ABOUT: IJ.run("About ImageJ..."); break;
			case QUIT: IJ.getInstance().quit(); break;
			case OPEN: new Opener().open(file.getAbsolutePath()); break;
		}
	};
}
