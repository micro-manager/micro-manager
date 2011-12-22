///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageWindow.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006
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
// CVS:          $Id$
//
package org.micromanager.utils;

import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.CompositeImage;
import ij.ImageStack;

import java.awt.Color;
import java.awt.Graphics;
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

import com.swtdesigner.SwingResourceManager;



/**
 * ImageJ compatible image window. Derived from the original ImageJ class.
 */
public final class MMImageWindow extends ImageWindow {
	private static final long serialVersionUID = 1L;
	private static final String WINDOW_X = "mmimg_y";
	private static final String WINDOW_Y = "mmimg_x";
	private static final String WINDOW_WIDTH = "mmimg_width";
	private static final String WINDOW_HEIGHT = "mmimg_height";
	private static CMMCore core_ = null;
	private static String title_ = "Live";
	private static ColorModel currentColorLUT__ = null;
	private static Preferences prefs_ = null;
	private static MMStudioMainFrame gui_ = null;

	private Panel buttonPanel_;


	public MMImageWindow(ImagePlus imp, CMMCore core) throws Exception {
		super(imp);
		core_ = core;
		Initialize();
	}

	public MMImageWindow(CMMCore core)
			throws Exception {
		super(createImagePlus(core_ = core, title_));
		core_ = core;
		Initialize();
	}

	public MMImageWindow(CMMCore core, MMStudioMainFrame gui) throws Exception {
		super(createImagePlus(core_ = core, title_));
		gui_ = gui;
		core_ = core;
		Initialize();
	}

	public MMImageWindow(CMMCore core, String wndTitle) throws Exception {
		super(createImagePlus(core_ = core, title_ = wndTitle));
		core_ = core;
		Initialize();
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

	private static ImagePlus createImagePlus(CMMCore core, String wndTitle)
			throws Exception {
	   core_ = core;
		ImageProcessor ip = null;
		long byteDepth = core_.getBytesPerPixel();
		long components = core_.getNumberOfComponents();
		int width = (int) core_.getImageWidth();
		int height = (int) core_.getImageHeight();
		if (byteDepth == 0) {
			throw (new Exception(logError("Imaging device not initialized")));
		}
		if (byteDepth == 1 && components == 1) {
			ip = new ByteProcessor(width, height);
		} else if (byteDepth == 2 && components == 1) {
			ip = new ShortProcessor(width, height);
		} else {
			String message = "Unsupported pixel depth: "
					+ core_.getBytesPerPixel() + " byte(s) and " + components
					+ " channel(s).";
			throw (new Exception(logError(message)));
		}
		ip.setColor(Color.black);
		if (currentColorLUT__ != null && !(ip instanceof ColorProcessor )) {
			ip.setColorModel(currentColorLUT__);
			logError("Restoring color model:" + currentColorLUT__.toString());
		}
		ip.fill();
		return new ImagePlus(title_ = wndTitle, ip);
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

		AbstractButton addToSeriesButton = new JButton("Add to Series");
		addToSeriesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					gui_.addToAlbum(ImageUtils.makeTaggedImage(getImagePlus().getProcessor()));
				} catch (Exception e2) {
					ReportingUtils.showError(e2);
				}
			}
		});
		buttonPanel_.add(addToSeriesButton);

		add(buttonPanel_);
		pack();

		// add window listeners
		addWindowListener(new WindowAdapter() {
         @Override
			public void windowClosing(WindowEvent e) {
				saveAttributes();
			}
		});
		addWindowListener(new WindowAdapter() {
         @Override
			public void windowClosed(WindowEvent e) {
            if (closed)
               return;
			}
		});

		addWindowListener(new WindowAdapter() {
         @Override
			public void windowOpened(WindowEvent e) {
				getCanvas().requestFocus();
			}
		});

		addWindowListener(new WindowAdapter() {
         @Override
			public void windowGainedFocus(WindowEvent e) {
				// updateHistogram();
			}
		});

		addWindowListener(new WindowAdapter() {
         @Override
			public void windowActivated(WindowEvent e) {
				// updateHistogram();
			}
		});
		setIconImage(SwingResourceManager.getImage(MMStudioMainFrame.class,
				"/org/micromanager/icons/camera_go.png"));

		setIJCal();
	}

	public void saveAttributes() {
		try {
			savePosition();
			// ToDo: implement winAccesslock_;
			// remember LUT so that a new window can be opened with the
			// same LUT
			ImagePlus imgp = getImagePlus();
			ImageProcessor ip = imgp != null ? imgp.getProcessor() : null;
			boolean isLUT = ip != null ? ip.isPseudoColorLut() : false;
			if (isLUT) {
				currentColorLUT__ = getImagePlus().getProcessor()
						.getColorModel();
				// !!!
				core_.logMessage(
						"Storing color model:" + currentColorLUT__.toString());
			} else {
				core_.logMessage(
						"Color model was not stored successfully"
								+ (null == currentColorLUT__ ? "null" : currentColorLUT__.toString()) + "ImagePlus:"
								+ imgp == null ? "null"
								: "OK" + "ip:" + ip == null ? "null" : "OK");
			}

		} catch (Exception e) {
			ReportingUtils.showError(e);
		}

	}

   @Override
	public void windowOpened(WindowEvent e) {
		getCanvas().requestFocus();
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

	public long getImageWindowByteLength() {
		ImageProcessor ip = getImagePlus().getProcessor();
		int w = ip.getWidth();
		int h = ip.getHeight();
		int imPlusBitDepth = getImagePlus().getBitDepth();
		// ImageWindow returns bitdepth 24 when Image processor type is Color
		imPlusBitDepth = imPlusBitDepth == 24 ? 32 : imPlusBitDepth;
		return  w * h * imPlusBitDepth / 8;
	}

    // this returns the length of the raw data array
	public long imageByteLenth(Object pixels) throws IllegalArgumentException {
		int byteLength = 0;
		if (pixels instanceof byte[]) {
			byte bytePixels[] = (byte[]) pixels;
			byteLength = bytePixels.length;
		} else if (pixels instanceof short[]) {
			short bytePixels[] = (short[]) pixels;
			byteLength = bytePixels.length * 2;
		} else if (pixels instanceof int[]) {
			int bytePixels[] = (int[]) pixels;
			byteLength = bytePixels.length * 4;
		} else
			throw (new IllegalArgumentException("Unsupported pixel data type."));
		return byteLength;
	}

	public boolean windowNeedsResizing() {
		ImageProcessor ip = getImagePlus().getProcessor();
		int w = ip.getWidth();
		int h = ip.getHeight();
		int imPlusBitDepth = getImagePlus().getBitDepth();
      int nrChannels = getImagePlus().getNChannels();
		
		// ImageWindow returns bitdepth 24 when Image processor type is Color
      if( nrChannels == 3){
         nrChannels = 4;
      }

      long components = core_.getNumberOfComponents();
        
      // retrieve the image storage size per pixel,
      // 1 for 8 bit gray, 2 for 16 bit gray, 4 for 32 bit RGB, 8 for 64 bit RGB
      long coreTotalByteDepth = core_.getBytesPerPixel();

		// warn the user if image dimensions do not match the current window
		boolean ret = (w != core_.getImageWidth()) || (h != core_.getImageHeight())
				|| ( (imPlusBitDepth * nrChannels) != coreTotalByteDepth * 8);

		return ret;
	}

   public void newImage(Object img) {

      long ibd = core_.getImageBitDepth();
      long bpp = core_.getBytesPerPixel();
      long noc = core_.getNumberOfComponents();
      long ncc = core_.getNumberOfCameraChannels();

      // flag to check for color
      boolean deepColor = (1 < noc);

      ImagePlus ip = getImagePlus();
      ImageProcessor ipr = ip.getProcessor();

      ImagePlus iplus = null;

      if (null != ip) {
            ip.setTitle(title_);
            if (null != ipr) {
               ipr.setPixels(img);
            }
          

         if (ip != null)
            gui_.updateContrast(ip);

         this.getImagePlus().updateAndDraw();

         // update coordinate and pixel info in imageJ by simulating mouse
         // move
         ImageCanvas ica = getCanvas();
         if (null != ica) {
            Point pt = ica.getCursorLoc();
            ip.mouseMoved(pt.x, pt.y);
         }
      }
   }

	public void newImageWithStatusLine(Object img, String statusLine) {
      //TODO: add error handling
		if (getImageWindowByteLength() != imageByteLenth(img)) {
			throw (new RuntimeException("Image bytelength does not match"));
		}
		ImagePlus ip = getImagePlus();
		if(null != ip) {
         ip.setTitle(statusLine);
			ImageProcessor ipr = ip.getProcessor();
			if(null != ipr){
				ipr.setPixels(img);
			}
			// update coordinate and pixel info in imageJ by simulating mouse
			// move
			ImageCanvas ic = getCanvas();
			if(null != ic)
			{
				Point pt = ic.getCursorLoc();
				ip.mouseMoved(pt.x, pt.y);
			}
		}
	}
	
	public void displayStatusLine(String statusLine) {
	   ImagePlus ip = getImagePlus();
	   if (ip != null) {
	      ip.setTitle(title_ + ": " + statusLine);
	      ip.updateAndDraw();
	   }
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

	public long getRawHistogramSize() {
		long ret = 0;
      ImagePlus ip = getImagePlus();
      if (ip != null) {
         ImageProcessor pp = ip.getProcessor();
         if (pp != null)  {
            int rawHistogram[] = pp.getHistogram();
            if (rawHistogram != null) {
               ret = rawHistogram.length;
            }
         }
		}
		return ret;
	}

   @Override
    public void windowClosing(WindowEvent e) {
    	gui_.enableLiveMode(false);
    	super.windowClosing(e);
    }

    public void setSubTitle(String title) {
        title_ = title;
        ImagePlus imgp = getImagePlus();
        if (imgp != null)
            imgp.setTitle(title_);
    }

 
}
