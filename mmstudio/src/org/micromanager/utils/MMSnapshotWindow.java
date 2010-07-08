/**
 * @author OD
 * 
 */

///////////////////////////////////////////////////////////////////////////////
//FILE:          MMSnapshotWindow.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       OD
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.utils;

import ij.ImagePlus;
import ij.WindowManager;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.ColorModel;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.JButton;

import mmcorej.CMMCore;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;

import com.swtdesigner.SwingResourceManager;

/**
 * ImageJ compatible image window. Derived from the original ImageJ class.
 */
public class MMSnapshotWindow extends Image5DWindow {

	private static final long serialVersionUID = 1L;
	private static final String WINDOW_X = "mmsimg_y";
	private static final String WINDOW_Y = "mmsimg_x";
	private static final String WINDOW_WIDTH = "mmsimg_width";
	private static final String WINDOW_HEIGHT = "mmsimg_height";
	private static CMMCore core_ = null;
	private static String title_ = "Snap";
	private static ColorModel currentColorModel_ = null;
	private static Preferences prefs_ = null;
	private static ContrastSettings contrastSettings8_ = new ContrastSettings();
	private static ContrastSettings contrastSettings16_ = new ContrastSettings();;
	private static long instanceCounter_ = 0;

	private Panel buttonPanel_;
	private LUTDialog contrastDlg_;
	private ImageController contrastPanel_ = null;

	public MMSnapshotWindow(CMMCore core, ImageController contrastPanel)
			throws Exception {
		this(core, contrastPanel, createTitle(title_));
	}

	public MMSnapshotWindow(CMMCore core, ImageController contrastPanel,
			String wndTitle) throws Exception {
		super(createImage5D(core_ = core, title_ = wndTitle));
		contrastPanel_ = contrastPanel;
		core_ = core;
		Initialize();
	}

	public void setContrastSettings(ContrastSettings s8, ContrastSettings s16) {
		contrastSettings8_ = s8;
		contrastSettings16_ = s16;
	}

	public ContrastSettings getCurrentContrastSettings() {
		if (getImagePlus().getBitDepth() == 8)
			return contrastSettings8_;
		else
			return contrastSettings16_;
	}

	public static ContrastSettings getContrastSettings8() {
		return contrastSettings8_;
	}

	public static ContrastSettings getContrastSettings16() {
		return contrastSettings16_;
	}

	public void loadPosition(int x, int y) {
		if (prefs_ != null)
			setLocation(prefs_.getInt(WINDOW_X, x), prefs_.getInt(WINDOW_Y, y));
	}

	public void savePosition() {
		if (prefs_ == null)
			loadPreferences();
		Rectangle r = getBounds();
		// save window position
		prefs_.putInt(WINDOW_X, r.x);
		prefs_.putInt(WINDOW_Y, r.y);
		prefs_.putInt(WINDOW_WIDTH, r.width);
		prefs_.putInt(WINDOW_HEIGHT, r.height);
	}

	private static Image5D createImage5D(CMMCore core, String wndTitle)
			throws Exception {
	   core_ = core;
		ImageProcessor ip;
		int type = 0;
		int width_ = (int) core_.getImageWidth();
		int height_ = (int) core_.getImageHeight();
		long byteDepth = core_.getBytesPerPixel();
		long components = core_.getNumberOfComponents();
		if (byteDepth == 1 && components == 1) {
			type = ImagePlus.GRAY8;
			ip = new ByteProcessor(width_, height_);
			if (contrastSettings8_.getRange() == 0.0)
				ip.setMinAndMax(0, 255);
			else
				ip.setMinAndMax(contrastSettings8_.min, contrastSettings8_.max);
		} else if (byteDepth == 2 && components == 1) {
			type = ImagePlus.GRAY16;
			ip = new ShortProcessor(width_, height_);
			if (contrastSettings16_.getRange() == 0.0)
				ip.setMinAndMax(0, 65535);
			else
				ip.setMinAndMax(contrastSettings16_.min,
						contrastSettings16_.max);
		} else if (byteDepth == 0) {
			throw (new Exception(logError("Imaging device not initialized")));
		} else if (byteDepth == 1 && components == 4) {
			// assuming RGB32 format
			ip = new ColorProcessor(width_, height_);
			if (contrastSettings8_.getRange() == 0.0)
				ip.setMinAndMax(0, 255);
			else
				ip.setMinAndMax(contrastSettings8_.min, contrastSettings8_.max);
		} else if ( 2 == byteDepth && 4 == components){
			ip = new ColorProcessor(width_, height_);
			// todo get ADC bit depth from camera, 
			int bitdepth = 12;
			ip.setMinAndMax(0, (1<<(bitdepth-1))-1);

		} else{
			String message = "Unsupported pixel depth: "
					+ core_.getBytesPerPixel() + " byte(s) and " + components
					+ " channel(s).";
			throw (new Exception(logError(message)));
		}
		ip.setColor(Color.black);
		if (currentColorModel_ != null)
			ip.setColorModel(currentColorModel_);
		ip.fill();
		Image5D img5d = new Image5D(wndTitle, type, width_, height_, 1, 1, 1,
				false);
		@SuppressWarnings("unused")
      Image5DWindow i5dw = new Image5DWindow(img5d);
		return img5d;

	}

	public void Initialize() {

		setIJCal();
		setPreferredLocation();

		buttonPanel_ = new Panel();

		AbstractButton saveButton = new JButton("Save");
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new FileSaver(getImagePlus()).save();
			}
		});
		buttonPanel_.add(saveButton);

		AbstractButton saveAsButton = new JButton("Save As...");
		saveAsButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new FileSaver(getImagePlus()).saveAsTiff();
			}
		});
		buttonPanel_.add(saveAsButton);

		add(buttonPanel_);
		pack();

		// add window listeners
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				finalizeClosing();

				if (contrastDlg_ != null)
					contrastDlg_.dispose();
				savePosition();
				// ToDo: implement winAccesslock_;
				// remember LUT so that a new window can be opened with the
				// same LUT
				if (getImagePlus().getProcessor().isPseudoColorLut())
					currentColorModel_ = getImagePlus().getProcessor()
							.getColorModel();

				if (contrastPanel_ != null)
					contrastPanel_.setImagePlus(null, null, null);
				// remember old color model
				if (getImagePlus().getProcessor().isPseudoColorLut())
					currentColorModel_ = getImagePlus().getProcessor()
							.getColorModel();
				WindowManager.removeWindow(getImagePlus().getWindow());
			}
		});
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
			}
		});

		addWindowListener(new WindowAdapter() {
			public void windowOpened(WindowEvent e) {
				getCanvas().requestFocus();
				finalizeOpening();
			}
		});

		addWindowListener(new WindowAdapter() {
			public void windowGainedFocus(WindowEvent e) {
				updateHistogram();
			}
		});

		addWindowListener(new WindowAdapter() {
			public void windowActivated(WindowEvent e) {
				updateHistogram();
			}
		});
		setIconImage(SwingResourceManager.getImage(MMStudioMainFrame.class,
				"/org/micromanager/icons/camera.png"));

		setIJCal();
	}

	private void loadPreferences() {
		prefs_ = Preferences.userNodeForPackage(this.getClass());
	}

	public void setFirstInstanceLocation() {
		setLocationRelativeTo(getParent());
	}

	public void setPreferredLocation() {
		loadPreferences();
		Point p = getLocation();
		loadPosition(p.x, p.y);
	}

	private static String logError(String message) {
		core_.logMessage("MMImageWindow:" + message);
		return message;
	}

	private static String createTitle(String wndTitle) {
		return (wndTitle) + (new Long(instanceCounter_).toString());
	}

	private void finalizeOpening() {
		instanceCounter_++;
	}

	private void finalizeClosing() {
	}

	protected void updateHistogram() {
		if (contrastPanel_ != null) {
			contrastPanel_.setImagePlus(getImagePlus(), contrastSettings8_,
					contrastSettings16_);
		}
	}

	// public
	public void newImage(Object img) {
		getImagePlus().getProcessor().setPixels(img);
		getImagePlus().updateAndDraw();
		getCanvas().paint(getCanvas().getGraphics());
		// update coordinate and pixel info in imageJ by simulating mouse
		// move
		Point pt = getCanvas().getCursorLoc();
		getImagePlus().mouseMoved(pt.x, pt.y);
	}

	public void zoomIn() {
		Rectangle r = getCanvas().getBounds();
		getCanvas().zoomIn(r.width / 2, r.height / 2);
	}

	public void zoomOut() {
		Rectangle r = getCanvas().getBounds();
		getCanvas().zoomOut(r.width / 2, r.height / 2);
	}

	// Set ImageJ pixel calibration
	public void setIJCal() {
		double pixSizeUm = core_.getPixelSizeUm();
		Calibration cal = new Calibration();
		if (pixSizeUm > 0) {
			cal.setUnit("um");
			cal.pixelWidth = pixSizeUm;
			cal.pixelHeight = pixSizeUm;
		}
		getImagePlus().setCalibration(cal);
	}

}

/*
 * 
 * 
 * package org.micromanager.utils;
 * 
 * import ij.ImagePlus; import ij.WindowManager; import ij.io.FileSaver; import
 * mmcorej.CMMCore;
 * 
 * import java.awt.Panel; import java.awt.Point; import
 * java.awt.event.ActionEvent; import java.awt.event.ActionListener; import
 * java.awt.event.WindowAdapter; import java.awt.event.WindowEvent; import
 * java.lang.Long; import java.util.prefs.Preferences;
 * 
 * import javax.swing.AbstractButton; import javax.swing.JButton;
 * 
 * import org.micromanager.MMStudioMainFrame;
 * 
 * import com.swtdesigner.SwingResourceManager;
 * 
 * public class MMSnapshotWindow extends MMImageWindow { private static final
 * long serialVersionUID = 2L; private static long instanceCounter_ = 0; private
 * static String title_ = "Snap"; private static Preferences prefs_=null;
 * 
 * public MMSnapshotWindow(CMMCore core, ImageController contrastPanel) throws
 * Exception { super(core, contrastPanel, createTitle(title_)); }
 * 
 * public MMSnapshotWindow(CMMCore core, ImageController contrastPanel, String
 * wndTitle) throws Exception { super(core, contrastPanel, createTitle(title_ =
 * wndTitle)); }
 * 
 * private static String createTitle(String wndTitle) { return (wndTitle) + (new
 * Long(instanceCounter_).toString()); }
 * 
 * private void finalizeOpening() { instanceCounter_++; }
 * 
 * private void finalizeClosing() { }
 * 
 * public void setFirstInstanceLocation() { if (getImageWindowInstance() != null
 * && getImageWindowInstance().isVisible())
 * setLocationRelativeTo(getImageWindowInstance()); else
 * setLocationRelativeTo(getParent()); }
 * 
 * private void loadPreferences() { prefs_ =
 * Preferences.userNodeForPackage(this.getClass()); }
 * 
 * public void Initialize() {
 * 
 * setPreferredLocation();
 * 
 * buttonPanel_ = new Panel();
 * 
 * AbstractButton saveButton = new JButton("Save");
 * saveButton.addActionListener(new ActionListener() { public void
 * actionPerformed(ActionEvent e) { new FileSaver(getImagePlus()).save(); } });
 * buttonPanel_.add(saveButton);
 * 
 * AbstractButton saveAsButton = new JButton("Save As...");
 * saveAsButton.addActionListener(new ActionListener() { public void
 * actionPerformed(ActionEvent e) { new FileSaver(getImagePlus()).saveAsTiff();
 * } }); buttonPanel_.add(saveAsButton);
 * 
 * add(buttonPanel_); pack();
 * 
 * // add window listeners addWindowListener(new WindowAdapter() { public void
 * windowClosing(WindowEvent e) { finalizeClosing();
 * 
 * if (contrastDlg_ != null) contrastDlg_.dispose(); savePosition(); // ToDo:
 * implement winAccesslock_; // remember LUT so that a new window can be opened
 * with the // same LUT if (getImagePlus().getProcessor().isPseudoColorLut())
 * currentColorModel_ = getImagePlus().getProcessor() .getColorModel();
 * 
 * if(contrastPanel_ != null) contrastPanel_.setImagePlus(null); // remember old
 * color model if (getImagePlus().getProcessor().isPseudoColorLut())
 * currentColorModel_ = getImagePlus().getProcessor() .getColorModel();
 * WindowManager.removeWindow(getImagePlus().getWindow()); } });
 * addWindowListener(new WindowAdapter() { public void windowClosed(WindowEvent
 * e) { } });
 * 
 * addWindowListener(new WindowAdapter() { public void windowOpened(WindowEvent
 * e) { getCanvas().requestFocus(); finalizeOpening(); } });
 * 
 * addWindowListener(new WindowAdapter() { public void
 * windowGainedFocus(WindowEvent e) { updateHistogram(); } });
 * 
 * addWindowListener(new WindowAdapter() { public void
 * windowActivated(WindowEvent e) { updateHistogram(); } });
 * setIconImage(SwingResourceManager.getImage(MMStudioMainFrame.class,
 * "/org/micromanager/icons/camera_go.png"));
 * 
 * setIJCal(); }
 * 
 * 
 * 
 * }
 */
