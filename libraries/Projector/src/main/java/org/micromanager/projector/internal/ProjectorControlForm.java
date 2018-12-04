///////////////////////////////////////////////////////////////////////////////
//FILE:          ProjectionControlForm.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein
//                Contributions by Jon Daniels and Nico Stuurman
//COPYRIGHT:     University of California, San Francisco, 2010-2018
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.projector.internal;


import com.google.common.eventbus.Subscribe;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.DefaultFormatter;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;

import org.micromanager.Studio;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.events.SLMExposureChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.propertymap.MutablePropertyMapView;

import org.micromanager.projector.internal.devices.SLM;
import org.micromanager.projector.internal.devices.Galvo;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.projector.ProjectorActions;

// TODO should not depend on internal code.
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * The main window for the Projector plugin. Contains logic for calibration,
 * and control for SLMs and Galvos.
*/
public class ProjectorControlForm extends MMFrame implements OnStateListener {
   private static ProjectorControlForm formSingleton_;
   private final ProjectionDevice dev_;
   private final MouseListener pointAndShootMouseListener_;
   private final AtomicBoolean pointAndShooteModeOn_ = new AtomicBoolean(false);
   private final BlockingQueue<PointAndShootInfo> pointAndShootQueue_;
   private Thread pointAndShootThread_;
   private ImageCanvas pointAndShootCanvas_;
   private final CMMCore core_;
   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final boolean isSLM_;
   private Roi[] individualRois_ = {};
   private Map<Polygon, AffineTransform> mapping_ = null;
   private String targetingChannel_;
   private MosaicSequencingFrame mosaicSequencingFrame_;
   private String targetingShutter_;
   private Boolean disposing_ = false;
   private Calibrator calibrator_;
   private BufferedWriter logFileWriter_;
   
   
   private static final SimpleDateFormat LOGFILEDATE_FORMATTER = 
           new SimpleDateFormat("yyyyMMdd");
   private static final SimpleDateFormat LOGTIME_FORMATTER = 
           new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
   

   public static final FileType PROJECTOR_LOG_FILE = new FileType("PROJECTOR_LOG_FILE",
      "Projector Log File", "./MyProjector.log", true, "log");
  
   
    /**
    * Constructor. Creates the main window for the Projector plugin.
    */
   private ProjectorControlForm(CMMCore core, Studio app) {
      studio_ = app;
      core_ = app.getCMMCore();
      settings_ = studio_.profile().getSettings(this.getClass());
      dev_ = ProjectorActions.getProjectionDevice(studio_);
      mapping_ = Mapping.loadMapping(core_, dev_, settings_);
      pointAndShootQueue_ = new LinkedBlockingQueue<PointAndShootInfo>();
      pointAndShootMouseListener_ = createPointAndShootMouseListenerInstance();
      
      // Create GUI
      initComponents();

      // Make sure that the POint and Shoot code listens to the correct window
      Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
         @Override
         public void eventDispatched(AWTEvent e) {
            enablePointAndShootMode(pointAndShooteModeOn_.get());
         }
      }, AWTEvent.WINDOW_EVENT_MASK);
      
      isSLM_ = dev_ instanceof SLM;
      // Only an SLM (not a galvo) has pixels.
      allPixelsButton_.setVisible(isSLM_);
      checkerBoardButton_.setVisible(isSLM_);
      // No point in looping ROIs on an SLM.
      roiLoopSpinner_.setVisible(!isSLM_);
      roiLoopLabel_.setVisible(!isSLM_);
      roiLoopTimesLabel_.setVisible(!isSLM_);
      pointAndShootOffButton_.setSelected(true);
      populateChannelComboBox(settings_.getString(Terms.PTCHANNEL, ""));
      populateShutterComboBox(settings_.getString(Terms.PTSHUTTER, ""));
      super.addWindowFocusListener(new WindowAdapter() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            if (!disposing_)
            {
               populateChannelComboBox(settings_.getString(Terms.PTCHANNEL, ""));
               populateShutterComboBox(settings_.getString(Terms.PTSHUTTER, ""));
            }
         }
      });
      
      commitSpinnerOnValidEdit(pointAndShootIntervalSpinner_);
      commitSpinnerOnValidEdit(startFrameSpinner_);
      commitSpinnerOnValidEdit(repeatEveryFrameSpinner_);
      commitSpinnerOnValidEdit(repeatEveryIntervalSpinner_);
      commitSpinnerOnValidEdit(roiLoopSpinner_);
      pointAndShootIntervalSpinner_.setValue(dev_.getExposure() / 1000);
      sequencingButton_.setVisible(MosaicSequencingFrame.getMosaicDevices(core).size() > 0);
     
      studio_.events().registerForEvents(new Object() {
         @Subscribe
         public void onSlmExposureChanged(SLMExposureChangedEvent event) {
            String deviceName = event.getDeviceName();
            double exposure = event.getNewExposureTime();
            if (deviceName.equals(dev_.getName())) {
               pointAndShootIntervalSpinner_.setValue(exposure);
            }
         }
         
         @Subscribe
         public void onShutdownStarting(ShutdownCommencingEvent sce) {
            dispose();
         }
         
      });

      studio_.displays().registerForEvents(new Object() {
         @Subscribe
         public void onDataViewerBecameActiveEvent(DataViewerDidBecomeActiveEvent dve) {
            if (dve.getDataViewer() instanceof DisplayWindow) {
               Window asWindow = ((DisplayWindow) (dve.getDataViewer())).getWindow();
               if (asWindow instanceof ImageWindow) {
                  ImageCanvas canvas = ((ImageWindow) asWindow).getCanvas();
                  pointAndShootWindow(canvas, pointAndShooteModeOn_.get());
               }
            }
         }
      });
      
      delayField_.setText(settings_.getString( Terms.DELAY, "0"));
      logDirectoryTextField_.setText(settings_.getString( Terms.LOGDIRECTORY, ""));

      super.loadAndRestorePosition(500, 300);
      updateROISettings();
   }
   
   // Show the form, which is a singleton.
   public static ProjectorControlForm showSingleton(CMMCore core, Studio app) {
      if (formSingleton_ == null) {
         formSingleton_ = new ProjectorControlForm(core, app);
      }
      formSingleton_.setVisible(true);
      return formSingleton_;
   }
   
   
   // ## Methods for handling targeting channel and shutter
   
   /**
    * Reads the available channels from Micro-Manager Channel Group
    * and populates the targeting channel drop-down menu.
    */
   final void populateChannelComboBox(String initialCh) {
      String initialChannel = initialCh;
      if (initialChannel == null) {
         initialChannel = (String) channelComboBox_.getSelectedItem();
      }
      channelComboBox_.removeAllItems();
      channelComboBox_.addItem("");
      // try to avoid crash on shutdown
      if (core_ != null) {
         for (String preset : core_.getAvailableConfigs(core_.getChannelGroup())) {
            channelComboBox_.addItem(preset);
         }
         channelComboBox_.setSelectedItem(initialChannel);
      }
   }

   /**
    * Reads the available shutters from Micro-Manager and
    * lists them in the targeting shutter drop-down menu.
    */
   @SuppressWarnings("AssignmentToMethodParameter")
   final void populateShutterComboBox(String initialShutter) {
      if (initialShutter == null) {
         initialShutter = (String) shutterComboBox_.getSelectedItem();
      }
      shutterComboBox_.removeAllItems();
      shutterComboBox_.addItem("");
      // trying to avoid crashes on shutdown
      if (core_ != null) {
         for (String shutter : core_.getLoadedDevicesOfType(DeviceType.ShutterDevice)) {
            shutterComboBox_.addItem(shutter);
         }
         shutterComboBox_.setSelectedItem(initialShutter);
      }
   }
   
   /**
    * Sets the targeting channel. channelName should be
    * a channel from the current ChannelGroup.
    */
   void setTargetingChannel(String channelName) {
      targetingChannel_ = channelName;
       if (channelName != null) {
          studio_.profile().getSettings(this.getClass()).putString(
                  "channel", channelName);
       }
   }
   
   /**
    * Sets the targeting shutter. 
    * Should be the name of a loaded Shutter device.
    */
   void setTargetingShutter(String shutterName) {
      targetingShutter_ = shutterName;
      if (shutterName != null) {
         studio_.profile().getSettings(this.getClass()).putString(
                 "shutter", shutterName);
      }
   }
   
   /**
    * Sets the Channel Group to the targeting channel, if it exists.
    * @return the channel group in effect before calling this function
    */
   public Configuration prepareChannel() {
      Configuration originalConfig = null;
      String channelGroup = core_.getChannelGroup();
      try {
         if (targetingChannel_ != null && targetingChannel_.length() > 0) {
            originalConfig = core_.getConfigGroupState(channelGroup);
            if (!originalConfig.isConfigurationIncluded(core_.getConfigData(channelGroup, targetingChannel_))) {
               if (studio_.acquisitions().isAcquisitionRunning()) {
                  studio_.acquisitions().setPause(true);
               }
               core_.setConfig(channelGroup, targetingChannel_);
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return originalConfig;
   }
   
   /**
    * Should be called with the value returned by prepareChannel.
    * Returns Channel Group to its original settings, if needed.
    * @param originalConfig Configuration to return to
    */
   public void returnChannel(Configuration originalConfig) {
      if (originalConfig != null) {
         try {
            core_.setSystemState(originalConfig);
            if (studio_.acquisitions().isAcquisitionRunning() && studio_.acquisitions().isPaused()) {
               studio_.acquisitions().setPause(false);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   /**
    * Opens the targeting shutter, if it has been specified.
    * @return true if it was already open
    */
   public boolean prepareShutter() {
      try {
         if (targetingShutter_ != null && targetingShutter_.length() > 0) {
            boolean originallyOpen = core_.getShutterOpen(targetingShutter_);
            if (!originallyOpen) {
               core_.setShutterOpen(targetingShutter_, true);
               core_.waitForDevice(targetingShutter_);
            }
            return originallyOpen;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return true; // by default, say it was already open
   }

   /**
    * Closes a targeting shutter if it exists and if it was originally closed.
    * Should be called with the value returned by prepareShutter.
    * @param originallyOpen - whether or not the shutter was originally open
    */
   public void returnShutter(boolean originallyOpen) {
      try {
         if (targetingShutter_ != null &&
               (targetingShutter_.length() > 0) &&
               !originallyOpen) {
            core_.setShutterOpen(targetingShutter_, false);
            core_.waitForDevice(targetingShutter_);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
   
   // ## Simple methods for device control.
     
   
   
   /** 
    * Turns the projection device on or off.
    * @param onState on=true
    */
   private void setOnState(boolean onState) {
      if (onState) {
         dev_.turnOn();
      } else {
         dev_.turnOff();
      }
   }
      
   /**
    * Runs the full calibration. First
    * generates a linear mapping (a first approximation) and then generates
    * a second piece-wise "non-linear" mapping of affine transforms. Saves
    * the mapping to Java Preferences.
    */
   public void runCalibration() {
      settings_.putString(Terms.DELAY, delayField_.getText());
      if (calibrator_ != null && calibrator_.isCalibrating()) {
         return;
      }
      calibrator_ = new Calibrator(studio_, dev_, settings_);
      Future<Boolean> runCalibration = calibrator_.runCalibration();
      new Thread() {
         @Override
         public void run() {
            Boolean success;
            try {
                success = runCalibration.get();
            } catch (InterruptedException | ExecutionException ex) {
               success = false;
            }
            if (success) {
               mapping_ = Mapping.loadMapping(core_, dev_, settings_);
            }
            JOptionPane.showMessageDialog(IJ.getImage().getWindow(), "Calibration "
                       + (success ? "finished." : "canceled."));
            calibrateButton_.setText("Calibrate");
            calibrator_ = null;
         }
      }.start();

   }
   
   /**
    * Returns true if the calibration is currently running.
    * @return true if calibration is running
    */
   public boolean isCalibrating() {
      if (calibrator_ == null) {
         return false;
      }
      return calibrator_.isCalibrating();
   }
   
   /**
    * Requests an interruption to calibration while it is running.
    */
   public void stopCalibration() {
      if (calibrator_ != null) {
         calibrator_.requestStop();
      }
   }
     

   /** Flips a point if the image was mirrored.
    *   TODO: also correct for rotation..
   */
   private Point mirrorIfNecessary(ImageCanvas canvas, 
           Point pOffscreen) {
      boolean isImageMirrored = false;
      int imageWidth = 0;
      DataProvider dp = getDataProvider(canvas);
      if (dp != null) {
         try {
            Image lastImage = dp.getImage(dp.getMaxIndices());
            if (lastImage != null) {
               PropertyMap userData = lastImage.getMetadata().getUserData();
               if (userData.containsString("ImageFlipper-Mirror")) {
                  String value = userData.getString("ImageFlipper-Mirror", "");
                  if (value.equals("On")) {
                     isImageMirrored = true;
                     imageWidth = lastImage.getWidth();
                  }
               }
            }
         } catch (IOException ioe) {
            studio_.logs().logError(ioe);
         }
      }
      if (isImageMirrored) {
         return new Point(imageWidth - pOffscreen.x, pOffscreen.y);
      } else {
         return pOffscreen;
      }
   }
   


   /**
    * ## Point and shoot
    * Creates a MouseListener instance for future use with Point and Shoot
    * mode. When the MouseListener is attached to an ImageJ window, any
    * clicks will result in a spot being illuminated.
    */
   private MouseListener createPointAndShootMouseListenerInstance() {
      return new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            if (e.isShiftDown()) {
               if (studio_.acquisitions().isAcquisitionRunning() || studio_.live().getIsLiveModeOn()) {
                  final Point p = e.getPoint();
                  final ImageCanvas canvas = (ImageCanvas) e.getSource();
                  Point pOff = new Point(canvas.offScreenX(p.x), canvas.offScreenY(p.y));
                  final Point pOffScreen = mirrorIfNecessary(canvas, pOff);
                  final Point2D.Double devP = ProjectorActions.transformPoint(
                          Mapping.loadMapping(core_, dev_, settings_),
                          new Point2D.Double(pOffScreen.x, pOffScreen.y));
                  final Configuration originalConfig = prepareChannel();
                  final boolean originalShutterState = prepareShutter();
                  PointAndShootInfo.Builder psiBuilder = new PointAndShootInfo.Builder();
                  PointAndShootInfo psi = psiBuilder.projectionDevice(dev_).
                          devPoint(devP).
                          originalShutterState(originalShutterState).
                          originalConfig(originalConfig).
                          canvasPoint(pOffScreen).
                          build();
                  pointAndShootQueue_.add(psi);
               }
            }
         }
      };
   }

   /**
    * Turn on/off point and shoot mode.
    * @param on on/off flag
    */
   public void enablePointAndShootMode(boolean on) {
      if (on && (mapping_ == null)) {
         final String errorS = 
                 "Please calibrate the phototargeting device first, using the Setup tab.";
         ReportingUtils.showError(errorS);
         throw new RuntimeException(errorS);
      }
      pointAndShooteModeOn_.set(on);
      // restart this thread if it is not running?
      if (pointAndShootThread_ == null || !pointAndShootThread_.isAlive()) {
         pointAndShootThread_ = new Thread(
                 new Runnable() {
            @Override
            public void run() {
               while (true) {
                  PointAndShootInfo psi;
                  try {
                     psi = pointAndShootQueue_.take();
                     if (psi.isStopped()) {
                        return;
                     }
                     try {
                        if (psi.getDevice() != null) {
                           ProjectorActions.displaySpot(
                                   psi.getDevice(),
                                   psi.getDevPoint().x,
                                   psi.getDevPoint().y);
                        }
                        returnShutter(psi.getOriginalShutterState());
                        returnChannel(psi.getOriginalConfig());
                        logPoint(psi.getCanvasPoint());
                     } catch (Exception e) {
                        ReportingUtils.showError(e);
                     }
                  } catch (InterruptedException ex) {
                     ReportingUtils.showError(ex);
                  }
               }
            }
         });
         pointAndShootThread_.start();
      }
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window != null) {
         ImageCanvas canvas = window.getCanvas();
         pointAndShootWindow(canvas, on);
      }
   }

   private void pointAndShootWindow(ImageCanvas canvas, Boolean on) {
      if (canvas != null) {
         if (on) {
            boolean found = false;
            for (MouseListener listener : canvas.getMouseListeners()) {
               if (listener == pointAndShootMouseListener_) {
                  found = true;
               }
            }
            if (!found) {
               if (canvas != pointAndShootCanvas_ && pointAndShootCanvas_ != null) {  // this should be true whenever pointAndShootCanvas_ != null
                  pointAndShootCanvas_.removeMouseListener(pointAndShootMouseListener_);
               }
               canvas.addMouseListener(pointAndShootMouseListener_);
               pointAndShootCanvas_ = canvas;
            }
         } else {
            for (MouseListener listener : canvas.getMouseListeners()) {
               if (listener == pointAndShootMouseListener_) {
                  canvas.removeMouseListener(listener);
               }
            }
            pointAndShootCanvas_ = null;
         }
      }

   }

   /**
    * Creates the log file - names with the current date - if it 
    * did not yet exist.
    * @return 
    */
   private BufferedWriter checkLogFile() {
      if (logFileWriter_ == null) {
         if (logDirectoryTextField_.getText().isEmpty()) {
            studio_.alerts().postAlert("Logging disabled", this.getClass(),
                    "To enable logging, set the Log Directory");
            return null;
         }
         String currentDate = LOGFILEDATE_FORMATTER.format(new Date());
         String newLogFile = new StringBuilder().append(
                 logDirectoryTextField_.getText()).append(
                 File.separator).append(
                         currentDate).append(
                         ".log").toString();
         try {
            OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(newLogFile), "UTF-8");
            // not sure if buffering is useful
            logFileWriter_ = new BufferedWriter(writer, 128);
         } catch (UnsupportedEncodingException | FileNotFoundException ex) {
            studio_.alerts().postAlert("Error opening logfile", this.getClass(),
                    "Failed to open log file");
         }
      }
      return logFileWriter_;
   }

   /**
    * Writes a point (screen coordinates) to the logfile, preceded
    * by the current date and time in ms.
    * @param p 
    */
   private void logPoint(Point p) {
      BufferedWriter logFileWriter = checkLogFile();
      if (logFileWriter == null) {
         return;
      }
      String currentTime = LOGTIME_FORMATTER.format(new Date());  // could use nanoseconds instead...
      try {
         logFileWriter.write(currentTime + "\t" + p.x + "\t" + p.y);
         logFileWriter.newLine();
      } catch (IOException ioe) {
         studio_.alerts().postAlert("Projector logfile error", this.getClass(),
                 "Failed to open write to Projector log file");
      }
   }
   
   /**
    * Ugly internal stuff to see if this IJ IMageCanvas is the MM active window
    * @param canvas
    * @return 
    */   
   private DataProvider getDataProvider(ImageCanvas canvas) {
      if (canvas == null) {
         return null;
      }
      List<DisplayWindow> dws = studio_.displays().getAllImageWindows();
      if (dws != null) {
         for (DisplayWindow dw : dws) {
            if (dw instanceof DisplayController) {
               if ( ((DisplayController) dw).getUIController().
                       getIJImageCanvas().equals(canvas)) {
                  return dw.getDataProvider();
               }
            }
         }
      }
      return null;
   }
   
   // ## Manipulating ROIs
   
  
  
   /**
    * Gets the label of an ROI with the given index n. Borrowed from ImageJ.
    */
   private static String getROILabel(ImagePlus imp, Roi roi, int n) {
      Rectangle r = roi.getBounds();
      int xc = r.x + r.width / 2;
      int yc = r.y + r.height / 2;
      if (n >= 0) {
         xc = yc;
         yc = n;
      }
      if (xc < 0) {
         xc = 0;
      }
      if (yc < 0) {
         yc = 0;
      }
      int digits = 4;
      String xs = "" + xc;
      if (xs.length() > digits) {
         digits = xs.length();
      }
      String ys = "" + yc;
      if (ys.length() > digits) {
         digits = ys.length();
      }
      if (digits == 4 && imp != null && imp.getStackSize() >= 10000) {
         digits = 5;
      }
      xs = "000000" + xc;
      ys = "000000" + yc;
      String label = ys.substring(ys.length() - digits) + "-" + xs.substring(xs.length() - digits);
      if (imp != null && imp.getStackSize() > 1) {
         int slice = roi.getPosition();
         if (slice == 0) {
            slice = imp.getCurrentSlice();
         }
         String zs = "000000" + slice;
         label = zs.substring(zs.length() - digits) + "-" + label;
         roi.setPosition(slice);
      }
      return label;
   }
     
   /**
    * Save a list of ROIs to a given path.
    */
   private static void saveROIs(File path, Roi[] rois) {
      try {
         ImagePlus imgp = IJ.getImage();
         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
         try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos))) {
            RoiEncoder re = new RoiEncoder(out);
            for (Roi roi : rois) {
               String label = getROILabel(imgp, roi, 0);
               if (!label.endsWith(".roi")) {
                  label += ".roi";
               }
               zos.putNextEntry(new ZipEntry(label));
               re.write(roi);
               out.flush();
            }
         }
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }
   }
   
   // Returns the current ROIs for a given ImageWindow. If selectedOnly
   // is true, then returns only those ROIs selected in the ROI Manager.
   // If no ROIs are selected, then all ROIs are returned.
   public static Roi[] getRois(ImagePlus plus, boolean selectedOnly) {
      Roi[] rois = new Roi[]{};
      Roi[] roiMgrRois = {};
      Roi singleRoi = plus.getRoi();
      final RoiManager mgr = RoiManager.getInstance();
      if (mgr != null) {
         if (selectedOnly) {
            roiMgrRois = mgr.getSelectedRoisAsArray();
            if (roiMgrRois.length == 0) {
               roiMgrRois = mgr.getRoisAsArray();
            }
         } else {
            roiMgrRois = mgr.getRoisAsArray();
         }
      }
      if (roiMgrRois.length > 0) {
         rois = roiMgrRois;
      } else if (singleRoi != null) {
         rois = new Roi[]{singleRoi};
      }
      return rois;
   }
   
  
       
   // ## Saving, sending, and running ROIs.
     

   
   // Save ROIs in the acquisition path, if it exists.
   private void recordPolygons() {
      if (studio_.acquisitions().isAcquisitionRunning()) {
         // TODO: The MM2.0 refactor broke this code by removing the below
         // method.
//         String location = studio_.compat().getAcquisitionPath();
//         if (location != null) {
//            try {
//               File f = new File(location, "ProjectorROIs.zip");
//               if (!f.exists()) {
//                  saveROIs(f, individualRois_);
//               }
//            } catch (Exception ex) {
//               ReportingUtils.logError(ex);
//            }
//         }
      }
   }
   
   /**
    * Upload current Window's ROIs, transformed, to the phototargeting device.
    * Polygons store camera coordinates in integers, and we use 
    * the ImageJ class FloatPolygon for corresponding scanner coordinates
    */
   public void sendCurrentImageWindowRois() {
      if (mapping_ == null) {
         throw new RuntimeException("Please calibrate the phototargeting device first, using the Setup tab.");
      }
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window == null) {
         throw new RuntimeException("No image window with ROIs is open.");
      }
      ImagePlus imgp = window.getImagePlus();
      Roi[] rois = getRois(imgp, true);
      if (rois.length == 0) {
         throw new RuntimeException("Please first draw the desired phototargeting ROIs.");
      }
      List<FloatPolygon> transformedRois = ProjectorActions.transformROIs(
              rois, mapping_);
      dev_.loadRois(transformedRois);
      individualRois_ = rois;
   }
   

   public void setROIs(Roi[] rois) {
      if (mapping_ == null) {
         throw new RuntimeException("Please calibrate the phototargeting device first, using the Setup tab.");
      }
      if (rois.length == 0) {
         throw new RuntimeException("Please provide ROIs.");
      }
      /*
      ImageWindow window = WindowManager.getCurrentWindow();
      if (window == null) {
         throw new RuntimeException("No image window with ROIs is open.");
      }
      ImagePlus imgp = window.getImagePlus();
      */
      List<FloatPolygon> transformedRois = ProjectorActions.transformROIs(
              rois, mapping_);
      dev_.loadRois(transformedRois);
      individualRois_ = rois;
   }
   
   /**
    * Illuminate the polygons ROIs that have been previously uploaded to
    * phototargeter.
    */
   public void runRois() {
      boolean isGalvo = dev_ instanceof Galvo;
      if (isGalvo) {
         Configuration originalConfig = prepareChannel();
         boolean originalShutterState = prepareShutter();
         dev_.runPolygons();
         returnShutter(originalShutterState);
         returnChannel(originalConfig);
         recordPolygons();
      } else {
         dev_.runPolygons();
         recordPolygons();
      }
   }

   // ## Attach/detach MDA
       
   /**
    * Attaches phototargeting ROIs to a multi-dimensional acquisition, so that
    * they will run on a particular firstFrame and, if repeat is true,
    * thereafter again every frameRepeatInterval frames.
    * @param firstFrame
    * @param repeat
    * @param frameRepeatInveral
    * @param runPolygons
   */
   public void attachRoisToMDA(int firstFrame, boolean repeat, 
           int frameRepeatInveral, Runnable runPolygons) {
      studio_.acquisitions().clearRunnables();
      if (repeat) {
         for (int i = firstFrame; i < studio_.acquisitions().getAcquisitionSettings().numFrames * 10; i += frameRepeatInveral) {
            studio_.acquisitions().attachRunnable(i, -1, 0, 0, runPolygons);
         }
      } else {
         studio_.acquisitions().attachRunnable(firstFrame, -1, 0, 0, runPolygons);
      }
   }

   /**
    * Remove the attached ROIs from the multi-dimensional acquisition.
    */
   public void removeFromMDA() {
      studio_.acquisitions().clearRunnables();
   }
  
   // ## GUI
   
   // Forces a JSpinner to fire a change event reflecting the new value
   // whenver user types a valid entry.
   private static void commitSpinnerOnValidEdit(final JSpinner spinner) {
      ((DefaultFormatter) ((JSpinner.DefaultEditor) spinner.getEditor())
            .getTextField().getFormatter()).setCommitsOnValidEdit(true);
   }
   
   // Return the value of a spinner displaying an integer.
   private static int getSpinnerIntegerValue(JSpinner spinner) {
      return Integer.parseInt(spinner.getValue().toString());
   }

   // Return the value of a spinner displaying an integer.
   private static double getSpinnerDoubleValue(JSpinner spinner) {
      return Double.parseDouble(spinner.getValue().toString());
   }
   
   /**
    * Sets the Point and Shoot "On and Off" buttons to a given state.
    * @param turnedOn true = Point and Shoot is ON
    */
   public void updatePointAndShoot(boolean turnedOn) {
      if (!turnedOn && logFileWriter_ != null) {
         try {
            logFileWriter_.flush();
         } catch (IOException ioe) {
            studio_.logs().logError(ioe);
         }
      }
      pointAndShootOnButton.setSelected(turnedOn);
      pointAndShootOffButton_.setSelected(!turnedOn);
      enablePointAndShootMode(turnedOn);
   }
   
   // Generates a runnable that runs the selected ROIs.
   private Runnable phototargetROIsRunnable(final String runnableName) {
      return new Runnable() {
         @Override
         public void run() {
            runRois();
         }
         @Override
         public String toString() {
            return runnableName;
         }
      };
   }
   

   
   /**
    * Runs runnable starting at firstTimeMs after this function is called, and
    * then, if repeat is true, every intervalTimeMs thereafter until
    * shouldContinue.call() returns false.
    */
   @SuppressWarnings("AssignmentToMethodParameter")
   private Runnable runAtIntervals(final long firstTimeMs, 
           boolean repeat,
           final long intervalTimeMs, 
           final Runnable runnable, 
           final Callable<Boolean> shouldContinue) {
      // protect from actions that have bad consequences
      if (intervalTimeMs == 0) {
         repeat = false;
      }
      final boolean rep = repeat;
      return new Runnable() {
         @Override
         public void run() {
            try {
               final long startTime = System.currentTimeMillis() + firstTimeMs;
               int reps = 0;
               while (shouldContinue.call()) {
                  Utils.sleepUntil(startTime + reps * intervalTimeMs);
                  runnable.run();
                  ++reps;
                  if (!rep) {
                     break;
                  }
               }
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      };
   }
   
   public void setNrRepetitions(int nr) {
      roiLoopSpinner_.setValue(nr);
      updateROISettings();
   }
   
   /**
    * Update the GUI's roi settings so they reflect the user's current choices.
    */
   public final void updateROISettings() {
      boolean roisSubmitted;
      int numROIs = individualRois_.length;
      switch (numROIs) {
         case 0:
            roiStatusLabel_.setText("No ROIs submitted");
            roisSubmitted = false;
            break;
         case 1:
            roiStatusLabel_.setText("One ROI submitted");
            roisSubmitted = true;
            break;
         default:
            // numROIs > 1
            roiStatusLabel_.setText("" + numROIs + " ROIs submitted");
            roisSubmitted = true;
            break;
      }

      roiLoopLabel_.setEnabled(roisSubmitted);
      roiLoopSpinner_.setEnabled(!isSLM_ && roisSubmitted);
      roiLoopTimesLabel_.setEnabled(!isSLM_ && roisSubmitted);
      runROIsNowButton_.setEnabled(roisSubmitted);
      useInMDAcheckBox.setEnabled(roisSubmitted);
      
      int nrRepetitions = 0;
      if (roiLoopSpinner_.isEnabled()) {
         nrRepetitions = getSpinnerIntegerValue(roiLoopSpinner_);
         settings_.putInteger(Terms.NRROIREPETITIONS, nrRepetitions);
      }
      dev_.setPolygonRepetitions(nrRepetitions);

      boolean useInMDA = roisSubmitted && useInMDAcheckBox.isSelected();
      attachToMdaTabbedPane_.setEnabled(useInMDA);
      startFrameLabel_.setEnabled(useInMDA);
      startFrameSpinner_.setEnabled(useInMDA);
      repeatCheckBox_.setEnabled(useInMDA);
      startTimeLabel_.setEnabled(useInMDA);
      startTimeUnitLabel_.setEnabled(useInMDA);
      startTimeSpinner_.setEnabled(useInMDA);
      repeatCheckBoxTime_.setEnabled(useInMDA);
      
      boolean repeatInMDA = useInMDA && repeatCheckBox_.isSelected();
      repeatEveryFrameSpinner_.setEnabled(repeatInMDA);
      repeatEveryFrameUnitLabel_.setEnabled(repeatInMDA);
      
      boolean repeatInMDATime = useInMDA && repeatCheckBoxTime_.isSelected();
      repeatEveryIntervalSpinner_.setEnabled(repeatInMDATime);
      repeatEveryIntervalUnitLabel_.setEnabled(repeatInMDATime);
      
      boolean synchronous = attachToMdaTabbedPane_.getSelectedComponent() == syncRoiPanel_;

      if (useInMDAcheckBox.isSelected()) {
         removeFromMDA();
         if (synchronous) {
            attachRoisToMDA(getSpinnerIntegerValue(startFrameSpinner_) - 1,
                  repeatCheckBox_.isSelected(),
                  getSpinnerIntegerValue(repeatEveryFrameSpinner_),
                  phototargetROIsRunnable("Synchronous phototargeting of ROIs"));
         } else {
            final Callable<Boolean> mdaRunning = new Callable<Boolean>() {
               @Override
               public Boolean call() throws Exception {
                  return studio_.acquisitions().isAcquisitionRunning();
               }
            };
            attachRoisToMDA(1, false, 0,
                  new Thread(
                        runAtIntervals((long) (1000. * getSpinnerDoubleValue(startTimeSpinner_)),
                        repeatCheckBoxTime_.isSelected(),
                        (long) (1000 * getSpinnerDoubleValue(repeatEveryIntervalSpinner_)),
                        phototargetROIsRunnable("Asynchronous phototargeting of ROIs"),
                        mdaRunning)));
         }
      } else {
         removeFromMDA();
      }
   }
    
   // Set the exposure to whatever value is currently in the Exposure field.
   private void updateExposure() {
      double exposureMs = Double.parseDouble(
                       pointAndShootIntervalSpinner_.getValue().toString()) ;
       ProjectorActions.setExposure(dev_, 1000 * exposureMs);
       settings_.putDouble(Terms.EXPOSURE, exposureMs);
   }
   
   // Method called if the phototargeting device has turned on or off.
   @Override
   public void stateChanged(final boolean onState) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            onButton_.setSelected(onState);
            offButton_.setSelected(!onState);
         }
      });
   }
   
   /**
    * Show the Mosaic Sequencing window (a JFrame). Should only be called
    * if we already know the Mosaic is attached.
    */
   void showMosaicSequencingWindow() {
      if (mosaicSequencingFrame_ == null) {
         mosaicSequencingFrame_ = new MosaicSequencingFrame(studio_, core_, this, (SLM) dev_);
      }
      mosaicSequencingFrame_.setVisible(true);
   }
   
  
   
   /**
    * Returns singleton instance if it exists, null otherwise
    * 
    * @return singleton instance if it exists, null otherwise
    */
   public static ProjectorControlForm getSingleton() {
      return formSingleton_;
   }
   
   @Override
   public void dispose()
   {
      formSingleton_ = null;
      disposing_ = true;
      if (pointAndShootThread_ != null && pointAndShootThread_.isAlive()) {
         pointAndShootQueue_.add(
                 new PointAndShootInfo.Builder().stop().build());
         try {
            pointAndShootThread_.join(1000);
         } catch (InterruptedException ex) {
            // It may be dangerous to report stuff while exiting
         }
      }
      if (logFileWriter_ != null) {
         try {
            logFileWriter_.close();
         } catch (IOException ioe) {
            // we are trying to close this file.  silently ignoring should be OK....
         } finally {
            logFileWriter_ = null;
         }
      }
      super.dispose();
   }
   
   public ProjectionDevice getDevice() {
      return dev_;
   }
   
   public Map<Polygon, AffineTransform> getMapping() {
      return mapping_;
   }
   

   
   /**
    * This method is called from within the constructor to initialize the form.
    * 
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {
      onButton_ = new JButton();
      offButton_ = new JButton();
      pointAndShootOnButton = new JToggleButton();
      pointAndShootOffButton_ = new JToggleButton();
      phototargetInstructionsLabel = new JLabel();
      roiLoopLabel_ = new JLabel();
      roiLoopTimesLabel_ = new JLabel();
      setRoiButton = new JButton();
      runROIsNowButton_ = new JButton();
      roiLoopSpinner_ = new JSpinner();
      useInMDAcheckBox = new JCheckBox();
      roiStatusLabel_ = new JLabel();
      sequencingButton_ = new JButton();
      attachToMdaTabbedPane_ = new JTabbedPane();
      startTimeLabel_ = new JLabel();
      startTimeSpinner_ = new JSpinner();
      repeatCheckBoxTime_ = new JCheckBox();
      repeatEveryIntervalSpinner_ = new JSpinner();
      repeatEveryIntervalUnitLabel_ = new JLabel();
      startTimeUnitLabel_ = new JLabel();
      syncRoiPanel_ = new JPanel();
      startFrameLabel_ = new JLabel();
      repeatCheckBox_ = new JCheckBox();
      startFrameSpinner_ = new JSpinner();
      repeatEveryFrameSpinner_ = new JSpinner();
      repeatEveryFrameUnitLabel_ = new JLabel();
      calibrateButton_ = new JButton();
      allPixelsButton_ = new JButton();
      channelComboBox_ = new JComboBox();
      shutterComboBox_ = new JComboBox();
      delayField_ = new JTextField();
      checkerBoardButton_ = new JButton();
      pointAndShootIntervalSpinner_ = new JSpinner();
      logDirectoryTextField_ = new JTextField();
      
      JPanel asyncRoiPanel = new JPanel();
      JButton centerButton = new JButton();
      JButton clearLogDirButton = new JButton();
      JButton logDirectoryChooserButton = new JButton();
      JTabbedPane mainTabbedPane = new JTabbedPane();
      JPanel pointAndShootTab = new JPanel();
      JButton roiManagerButton = new JButton();
      JPanel roisTab = new JPanel();
      JPanel setupTab = new JPanel();
      

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Projector Controls");
      setResizable(false);

      onButton_.setText("On");
      onButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            setOnState(true);
            updatePointAndShoot(false);
         }
      });

      mainTabbedPane.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updatePointAndShoot(false);
         }
      });

      pointAndShootOnButton.setText("On");
      pointAndShootOnButton.setMaximumSize(new Dimension(75, 23));
      pointAndShootOnButton.setMinimumSize(new Dimension(75, 23));
      pointAndShootOnButton.setPreferredSize(new Dimension(75, 23));
      pointAndShootOnButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            setOnState(false);
            try {
               updatePointAndShoot(true);
            } catch (RuntimeException e) {
               ReportingUtils.showError(e);
            }
         }
      });

      pointAndShootOffButton_.setText("Off");
      pointAndShootOffButton_.setPreferredSize(new Dimension(75, 23));
      pointAndShootOffButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updatePointAndShoot(false);
         }
      });

      phototargetInstructionsLabel.setText(
              "(To phototarget, Shift + click on the image, use ImageJ hand-tool)");

      logDirectoryChooserButton.setText("...");
      logDirectoryChooserButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            File openDir = FileDialogs.openDir(ProjectorControlForm.getSingleton(),
                    "Select location for Files logging Point and Shoot locations",
                    PROJECTOR_LOG_FILE);
            if (openDir != null) {
               logDirectoryTextField_.setText(openDir.getAbsolutePath());
               settings_.putString(Terms.LOGDIRECTORY, openDir.getAbsolutePath());
            }
         }
      });

      clearLogDirButton.setText("Clear Log Directory");
      clearLogDirButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            if (logFileWriter_ != null) {
               try {
                  logFileWriter_.flush();
                  logFileWriter_.close();
               } catch (IOException ioe) {
               } finally {
                  logFileWriter_ = null;
               }
            }
            String logDirectory = logDirectoryTextField_.getText();
            File logDir = new File(logDirectory);
            if (logDir.isDirectory()) {
               for (File logFile : logDir.listFiles()) {
                  logFile.delete();
               }
            }
         }
      });

      pointAndShootTab.setLayout(new MigLayout("", "", "[40]"));
      
      pointAndShootTab.add(new JLabel("Point and shoot mode:"));
      pointAndShootTab.add(pointAndShootOnButton);
      pointAndShootTab.add(pointAndShootOffButton_, "wrap");
      
      pointAndShootTab.add(phototargetInstructionsLabel, "span 3, wrap");
      
      pointAndShootTab.add(new JLabel("Log Directory"), "span3, split 3");
      pointAndShootTab.add(logDirectoryTextField_, "grow");
      pointAndShootTab.add(logDirectoryChooserButton, "wrap");
      
      pointAndShootTab.add(clearLogDirButton, "span 3, wrap");
      
      mainTabbedPane.addTab("Point and Shoot", pointAndShootTab);

      roiLoopLabel_.setText("Loop:");

      roiLoopTimesLabel_.setText("times");

      setRoiButton.setText("Set ROI(s)");
      setRoiButton.setToolTipText(
              "Specify an ROI you wish to be phototargeted by using the ImageJ ROI tools (point, rectangle, oval, polygon). Then press Set ROI(s) to send the ROIs to the phototargeting device. To initiate phototargeting, press Go!");
      setRoiButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            try {
               sendCurrentImageWindowRois();
               updateROISettings();
            } catch (RuntimeException e) {
               ReportingUtils.showError(e);
            }
         }
      });

      runROIsNowButton_.setText("Run ROIs now!");
      runROIsNowButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            new Thread(
                    new Runnable() {
               @Override
               public void run() {
                  phototargetROIsRunnable("Asynchronous phototargeting of ROIs").run();
               }
            }
            ).start();
         }
      });

      roiLoopSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      roiLoopSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });
      roiLoopSpinner_.setValue(settings_.getInteger(Terms.NRROIREPETITIONS, 1));

      useInMDAcheckBox.setText("Run ROIs in Multi-Dimensional Acquisition");
      useInMDAcheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      roiStatusLabel_.setText("No ROIs submitted yet");

      roiManagerButton.setText("ROI Manager >>");
      roiManagerButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            Utils.showRoiManager();
         }
      });

      sequencingButton_.setText("Sequencing...");
      sequencingButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            showMosaicSequencingWindow();
         }
      });

      startTimeLabel_.setText("Start Time");

      startFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startTimeSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatCheckBoxTime_.setText("Repeat every");
      repeatCheckBoxTime_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryIntervalSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryIntervalUnitLabel_.setText("seconds");

      startTimeUnitLabel_.setText("seconds");
      
      
      asyncRoiPanel.setLayout(new MigLayout());
      
      asyncRoiPanel.add(startTimeLabel_);
      asyncRoiPanel.add(startTimeSpinner_, "wmin 60, wmax 60");
      asyncRoiPanel.add(new JLabel("seconds"), "wrap");
      
      asyncRoiPanel.add(repeatCheckBoxTime_);
      asyncRoiPanel.add(repeatEveryIntervalSpinner_, "wmin 60, wmax 60");
      asyncRoiPanel.add(repeatEveryIntervalUnitLabel_, "wrap");

      attachToMdaTabbedPane_.addTab("During imaging", asyncRoiPanel);

      startFrameLabel_.setText("Start Frame");
      repeatEveryFrameUnitLabel_.setText("frames");
      repeatCheckBox_.setText("Repeat every");
      repeatCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      startFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startFrameSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryFrameSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      syncRoiPanel_.setLayout(new MigLayout());
      
      syncRoiPanel_.add(startFrameLabel_);
      syncRoiPanel_.add(startFrameSpinner_, "wmin 60, wmax 60,  wrap");
      
      syncRoiPanel_.add(repeatCheckBox_);
      syncRoiPanel_.add(repeatEveryFrameSpinner_, "wmin 60, wmax 60");
      syncRoiPanel_.add(repeatEveryFrameUnitLabel_, "wrap");
      
      attachToMdaTabbedPane_.addTab("Between images", syncRoiPanel_);

      roisTab.setLayout(new MigLayout());
      
      roisTab.add(setRoiButton, "align center");
      roisTab.add(roiManagerButton, "align center, wrap");
      
      roisTab.add(roiStatusLabel_);
      roisTab.add(sequencingButton_, "wrap");
      
      roisTab.add(roiLoopLabel_, "split 3");
      roisTab.add(roiLoopSpinner_, "wmin 60, wmax 60");
      roisTab.add(roiLoopTimesLabel_, "wrap");
      
      roisTab.add(runROIsNowButton_, "align center");
      roisTab.add(useInMDAcheckBox, "wrap");
      
      roisTab.add(attachToMdaTabbedPane_, "skip 1, wrap");
      
      mainTabbedPane.addTab("ROIs", roisTab);

      calibrateButton_.setText("Calibrate!");
      calibrateButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            try {
               boolean running = isCalibrating();
               if (running) {
                  stopCalibration();
                  calibrateButton_.setText("Calibrate");
               } else {
                  runCalibration();
                  calibrateButton_.setText("Stop calibration");
               }
            } catch (Exception e) {
               ReportingUtils.showError(e);
            }
         }
      });

      allPixelsButton_.setText("All Pixels");
      allPixelsButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            dev_.activateAllPixels();
         }
      });

      centerButton.setText("Center spot");
      centerButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            setOnState(false);
            ProjectorActions.displayCenterSpot(dev_);
         }
      });

      channelComboBox_.setModel(new DefaultComboBoxModel(
              new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      channelComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            final String channel = (String) channelComboBox_.getSelectedItem();
            setTargetingChannel(channel);
            settings_.putString(Terms.PTCHANNEL, channel);
         }
      });

      shutterComboBox_.setModel(new DefaultComboBoxModel(
              new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      shutterComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            final String shutter = (String) shutterComboBox_.getSelectedItem();
            setTargetingShutter(shutter);
            settings_.putString(Terms.PTSHUTTER, shutter);
         }
      });

      delayField_.setText("0");

      checkerBoardButton_.setText("CheckerBoard");
      checkerBoardButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            dev_.showCheckerBoard(16, 16);
         }
      });
      
      setupTab.setLayout(new MigLayout("", "", "[40]"));
      
      setupTab.add(centerButton);
      setupTab.add(allPixelsButton_);
      setupTab.add(checkerBoardButton_, "wrap");
      
      setupTab.add(calibrateButton_);
      setupTab.add(new JLabel("Delay(ms):"), "span 2, split 2");
      setupTab.add(delayField_, "wmin 30, w 50!, wrap");
      
      setupTab.add(new JLabel("Phototargeting channel:"), "span 3, split 2, w 180!");
      setupTab.add(shutterComboBox_, "wmin 130, wrap");
      
      setupTab.add(new JLabel("Phototargeting shutter:"), "span 3, split 2, w 180!");
      setupTab.add(channelComboBox_, "wmin 130, wrap");   

      mainTabbedPane.addTab("Setup", setupTab);

      offButton_.setText("Off");
      offButton_.setSelected(true);
      offButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            setOnState(false);
         }
      });

      pointAndShootIntervalSpinner_.setModel(new SpinnerNumberModel(500, 1, 1000000000, 1));
      pointAndShootIntervalSpinner_.setMaximumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.setMinimumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.setPreferredSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateExposure();
         }
      });
      pointAndShootIntervalSpinner_.addVetoableChangeListener(new VetoableChangeListener() {
         @Override
         public void vetoableChange(PropertyChangeEvent evt)throws PropertyVetoException {
             updateExposure();
         }
      });
      pointAndShootIntervalSpinner_.setValue(settings_.getDouble(Terms.EXPOSURE, 0.0));
      
      this.getContentPane().setLayout(new MigLayout());
      
      this.getContentPane().add(new JLabel("Exposure time:"));
      this.getContentPane().add(pointAndShootIntervalSpinner_, "w 75");
      this.getContentPane().add(new JLabel("ms"), "gapx 18px 18px");
      this.getContentPane().add(onButton_, "gapx 80px 6px");
      this.getContentPane().add(offButton_, "gapx 6px 6px, wrap");
      
      this.getContentPane().add(mainTabbedPane, "span 5, wrap");
      
    
      pack();
   }


   // Variables declaration 
   private javax.swing.JButton allPixelsButton_;
   private javax.swing.JTabbedPane attachToMdaTabbedPane_;
   private javax.swing.JButton calibrateButton_;
   private javax.swing.JComboBox channelComboBox_;
   private javax.swing.JButton checkerBoardButton_;
   private javax.swing.JTextField delayField_;
   private javax.swing.JTextField logDirectoryTextField_;
   private javax.swing.JButton offButton_;
   private javax.swing.JButton onButton_;
   private javax.swing.JLabel phototargetInstructionsLabel;
   private javax.swing.JSpinner pointAndShootIntervalSpinner_;
   private javax.swing.JToggleButton pointAndShootOffButton_;
   private javax.swing.JToggleButton pointAndShootOnButton;
   private javax.swing.JCheckBox repeatCheckBox_;
   private javax.swing.JCheckBox repeatCheckBoxTime_;
   private javax.swing.JSpinner repeatEveryFrameSpinner_;
   private javax.swing.JLabel repeatEveryFrameUnitLabel_;
   private javax.swing.JSpinner repeatEveryIntervalSpinner_;
   private javax.swing.JLabel repeatEveryIntervalUnitLabel_;
   private javax.swing.JLabel roiLoopLabel_;
   private javax.swing.JSpinner roiLoopSpinner_;
   private javax.swing.JLabel roiLoopTimesLabel_;
   private javax.swing.JLabel roiStatusLabel_;
   private javax.swing.JButton runROIsNowButton_;
   private javax.swing.JButton sequencingButton_;
   private javax.swing.JButton setRoiButton;
   private javax.swing.JComboBox shutterComboBox_;
   private javax.swing.JLabel startFrameLabel_;
   private javax.swing.JSpinner startFrameSpinner_;
   private javax.swing.JLabel startTimeLabel_;
   private javax.swing.JSpinner startTimeSpinner_;
   private javax.swing.JLabel startTimeUnitLabel_;
   private javax.swing.JPanel syncRoiPanel_;
   private javax.swing.JCheckBox useInMDAcheckBox;

}