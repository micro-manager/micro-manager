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
import javax.swing.JSeparator;

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
import org.micromanager.events.SLMExposureChangedEvent;
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
           new SimpleDateFormat("yyyyMMdd-H-m-s.SSS");
   

   public static final FileType PROJECTOR_LOG_FILE = new FileType("PROJECTOR_LOG_FILE",
      "Projector Log File", "./MyProjector.log", true, "log");
  
   // ## Methods for handling targeting channel and shutter
   
   /**
    * Reads the available channels from Micro-Manager Channel Group
    * and populates the targeting channel drop-down menu.
    */
   final void populateChannelComboBox(String initialCh) {
      String initialChannel = initialCh;
      if (initialChannel == null) {
         initialChannel = (String) channelComboBox.getSelectedItem();
      }
      channelComboBox.removeAllItems();
      channelComboBox.addItem("");
      // try to avoid crash on shutdown
      if (core_ != null) {
         for (String preset : core_.getAvailableConfigs(core_.getChannelGroup())) {
            channelComboBox.addItem(preset);
         }
         channelComboBox.setSelectedItem(initialChannel);
      }
   }

   /**
    * Reads the available shutters from Micro-Manager and
    * lists them in the targeting shutter drop-down menu.
    */
   @SuppressWarnings("AssignmentToMethodParameter")
   final void populateShutterComboBox(String initialShutter) {
      if (initialShutter == null) {
         initialShutter = (String) shutterComboBox.getSelectedItem();
      }
      shutterComboBox.removeAllItems();
      shutterComboBox.addItem("");
      // trying to avoid crashes on shutdown
      if (core_ != null) {
         for (String shutter : core_.getLoadedDevicesOfType(DeviceType.ShutterDevice)) {
            shutterComboBox.addItem(shutter);
         }
         shutterComboBox.setSelectedItem(initialShutter);
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
         public void mouseClicked(MouseEvent e) {
            if (e.isShiftDown()) {
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
      if (pointAndShootThread_ == null) {
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
         if (canvas != null) {
            if (on) {
               boolean found = false;
               for (MouseListener listener : canvas.getMouseListeners()) {
                  if (listener == pointAndShootMouseListener_) {
                     found = true;
                  }
               }
               if (!found) {
                  canvas.addMouseListener(pointAndShootMouseListener_);
               }
            } else {
               for (MouseListener listener : canvas.getMouseListeners()) {
                  if (listener == pointAndShootMouseListener_) {
                     canvas.removeMouseListener(listener);
                  }
               }
            }
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
         if (logDirectoryTextField.getText().isEmpty()) {
            studio_.alerts().postAlert("Logging disabled", this.getClass(),
                    "To enable logging, set the Log Directory");
            return null;
         }
         String currentDate = LOGFILEDATE_FORMATTER.format(new Date());
         String newLogFile = new StringBuilder().append(
                 logDirectoryTextField.getText()).append(
                 File.separator).append(
                         currentDate).append(
                         ".log").toString();
         try {
            OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(newLogFile), "UTF-8");
            // not sure if buffering is useful
            logFileWriter_ = new BufferedWriter(writer, 1024);
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
      pointAndShootOffButton.setSelected(!turnedOn);
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
      roiLoopSpinner.setValue(nr);
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
            roiStatusLabel.setText("No ROIs submitted");
            roisSubmitted = false;
            break;
         case 1:
            roiStatusLabel.setText("One ROI submitted");
            roisSubmitted = true;
            break;
         default:
            // numROIs > 1
            roiStatusLabel.setText("" + numROIs + " ROIs submitted");
            roisSubmitted = true;
            break;
      }

      roiLoopLabel.setEnabled(roisSubmitted);
      roiLoopSpinner.setEnabled(!isSLM_ && roisSubmitted);
      roiLoopTimesLabel.setEnabled(!isSLM_ && roisSubmitted);
      runROIsNowButton.setEnabled(roisSubmitted);
      useInMDAcheckBox.setEnabled(roisSubmitted);
      
      int nrRepetitions = 0;
      if (roiLoopSpinner.isEnabled()) {
         nrRepetitions = getSpinnerIntegerValue(roiLoopSpinner);
      }
      dev_.setPolygonRepetitions(nrRepetitions);

      boolean useInMDA = roisSubmitted && useInMDAcheckBox.isSelected();
      attachToMdaTabbedPane.setEnabled(useInMDA);
      startFrameLabel.setEnabled(useInMDA);
      startFrameSpinner.setEnabled(useInMDA);
      repeatCheckBox.setEnabled(useInMDA);
      startTimeLabel.setEnabled(useInMDA);
      startTimeUnitLabel.setEnabled(useInMDA);
      startTimeSpinner.setEnabled(useInMDA);
      repeatCheckBoxTime.setEnabled(useInMDA);
      
      boolean repeatInMDA = useInMDA && repeatCheckBox.isSelected();
      repeatEveryFrameSpinner.setEnabled(repeatInMDA);
      repeatEveryFrameUnitLabel.setEnabled(repeatInMDA);
      
      boolean repeatInMDATime = useInMDA && repeatCheckBoxTime.isSelected();
      repeatEveryIntervalSpinner.setEnabled(repeatInMDATime);
      repeatEveryIntervalUnitLabel.setEnabled(repeatInMDATime);
      
      boolean synchronous = attachToMdaTabbedPane.getSelectedComponent() == syncRoiPanel;

      if (useInMDAcheckBox.isSelected()) {
         removeFromMDA();
         if (synchronous) {
            attachRoisToMDA(getSpinnerIntegerValue(startFrameSpinner) - 1,
                  repeatCheckBox.isSelected(),
                  getSpinnerIntegerValue(repeatEveryFrameSpinner),
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
                        runAtIntervals((long) (1000. * getSpinnerDoubleValue(startTimeSpinner)),
                        repeatCheckBoxTime.isSelected(),
                        (long) (1000 * getSpinnerDoubleValue(repeatEveryIntervalSpinner)),
                        phototargetROIsRunnable("Asynchronous phototargeting of ROIs"),
                        mdaRunning)));
         }
      } else {
         removeFromMDA();
      }
   }
    
   // Set the exposure to whatever value is currently in the Exposure field.
   private void updateExposure() {
       ProjectorActions.setExposure(dev_,
               1000 * Double.parseDouble(
                       pointAndShootIntervalSpinner.getValue().toString()));
   }
   
   // Method called if the phototargeting device has turned on or off.
   @Override
   public void stateChanged(final boolean onState) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            onButton.setSelected(onState);
            offButton.setSelected(!onState);
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
      allPixelsButton.setVisible(isSLM_);
      checkerBoardButton_.setVisible(isSLM_);
      // No point in looping ROIs on an SLM.
      roiLoopSpinner.setVisible(!isSLM_);
      roiLoopLabel.setVisible(!isSLM_);
      roiLoopTimesLabel.setVisible(!isSLM_);
      pointAndShootOffButton.setSelected(true);
      populateChannelComboBox(settings_.getString("channel", ""));
      populateShutterComboBox(settings_.getString("shutter", ""));
      super.addWindowFocusListener(new WindowAdapter() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            if (!disposing_)
            {
               populateChannelComboBox(null);
               populateShutterComboBox(null);
            }
         }
      });
      
      commitSpinnerOnValidEdit(pointAndShootIntervalSpinner);
      commitSpinnerOnValidEdit(startFrameSpinner);
      commitSpinnerOnValidEdit(repeatEveryFrameSpinner);
      commitSpinnerOnValidEdit(repeatEveryIntervalSpinner);
      commitSpinnerOnValidEdit(roiLoopSpinner);
      pointAndShootIntervalSpinner.setValue(dev_.getExposure() / 1000);
      sequencingButton.setVisible(MosaicSequencingFrame.getMosaicDevices(core).size() > 0);
     
      studio_.events().registerForEvents(new Object() {
         @Subscribe
         public void onSlmExposureChanged(SLMExposureChangedEvent event) {
            String deviceName = event.getDeviceName();
            double exposure = event.getNewExposureTime();
            if (deviceName.equals(dev_.getName())) {
               pointAndShootIntervalSpinner.setValue(exposure);
            }
         }
      });
      
      delayField_.setText(settings_.getString( Terms.DELAY, "0"));
      logDirectoryTextField.setText(settings_.getString( Terms.LOGDIRECTORY, ""));

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
      onButton = new JButton();
      mainTabbedPane = new JTabbedPane();
      pointAndShootTab = new JPanel();
      pointAndShootModeLabel = new JLabel();
      pointAndShootOnButton = new JToggleButton();
      pointAndShootOffButton = new JToggleButton();
      phototargetInstructionsLabel = new JLabel();
      roisTab = new JPanel();
      roiLoopLabel = new JLabel();
      roiLoopTimesLabel = new JLabel();
      setRoiButton = new JButton();
      runROIsNowButton = new JButton();
      roiLoopSpinner = new JSpinner();
      jSeparator1 = new JSeparator();
      useInMDAcheckBox = new JCheckBox();
      roiStatusLabel = new JLabel();
      roiManagerButton = new JButton();
      jSeparator3 = new JSeparator();
      sequencingButton = new JButton();
      attachToMdaTabbedPane = new JTabbedPane();
      asyncRoiPanel = new JPanel();
      startTimeLabel = new JLabel();
      startTimeSpinner = new JSpinner();
      repeatCheckBoxTime = new JCheckBox();
      repeatEveryIntervalSpinner = new JSpinner();
      repeatEveryIntervalUnitLabel = new JLabel();
      startTimeUnitLabel = new JLabel();
      syncRoiPanel = new JPanel();
      startFrameLabel = new JLabel();
      repeatCheckBox = new JCheckBox();
      startFrameSpinner = new JSpinner();
      repeatEveryFrameSpinner = new JSpinner();
      repeatEveryFrameUnitLabel = new JLabel();
      setupTab = new JPanel();
      calibrateButton_ = new JButton();
      allPixelsButton = new JButton();
      centerButton = new JButton();
      channelComboBox = new JComboBox();
      phototargetingChannelDropdownLabel = new JLabel();
      shutterComboBox = new JComboBox();
      phototargetingShutterDropdownLabel = new JLabel();
      jLabel1 = new JLabel();
      delayField_ = new JTextField();
      checkerBoardButton_ = new JButton();
      offButton = new JButton();
      ExposureTimeLabel = new JLabel();
      pointAndShootIntervalSpinner = new JSpinner();
      jLabel2 = new JLabel();
      logDirectoryChooserButton = new JButton();
      logDirectoryTextField = new JTextField();
      clearLogDirButton = new JButton();
      

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Projector Controls");
      setResizable(false);

      onButton.setText("On");
      onButton.addActionListener(new ActionListener() {
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

      pointAndShootModeLabel.setText("Point and shoot mode:");

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

      pointAndShootOffButton.setText("Off");
      pointAndShootOffButton.setPreferredSize(new Dimension(75, 23));
      pointAndShootOffButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updatePointAndShoot(false);
         }
      });

      phototargetInstructionsLabel.setText(
              "(To phototarget, Shift + click on the image, use ImageJ hand-tool)");

      logDirectoryChooserButton.setText("...");
      logDirectoryChooserButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            logDirectoryChooserButtonActionPerformed(evt);
         }
      });

      clearLogDirButton.setText("Clear Log Directory");
      clearLogDirButton.addActionListener(new java.awt.event.ActionListener() {
         @Override
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            clearLogDirButtonActionPerformed(evt);
         }
      });

      pointAndShootTab.setLayout(new MigLayout("", "", "[40]"));
      
      pointAndShootTab.add(pointAndShootModeLabel);
      pointAndShootTab.add(pointAndShootOnButton);
      pointAndShootTab.add(pointAndShootOffButton, "wrap");
      
      pointAndShootTab.add(phototargetInstructionsLabel, "span 3, wrap");
      
      pointAndShootTab.add(new JLabel("Log Directory"), "span3, split 3");
      pointAndShootTab.add(logDirectoryTextField, "grow");
      pointAndShootTab.add(logDirectoryChooserButton, "wrap");
      
      pointAndShootTab.add(clearLogDirButton, "span 3, wrap");
      
      mainTabbedPane.addTab("Point and Shoot", pointAndShootTab);

      roiLoopLabel.setText("Loop:");

      roiLoopTimesLabel.setText("times");

      setRoiButton.setText("Set ROI(s)");
      setRoiButton.setToolTipText("Specify an ROI you wish to be phototargeted by using the ImageJ ROI tools (point, rectangle, oval, polygon). Then press Set ROI(s) to send the ROIs to the phototargeting device. To initiate phototargeting, press Go!");
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

      runROIsNowButton.setText("Run ROIs now!");
      runROIsNowButton.addActionListener(new ActionListener() {
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

      roiLoopSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      roiLoopSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      useInMDAcheckBox.setText("Run ROIs in Multi-Dimensional Acquisition");
      useInMDAcheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      roiStatusLabel.setText("No ROIs submitted yet");

      roiManagerButton.setText("ROI Manager >>");
      roiManagerButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            Utils.showRoiManager();
         }
      });

      jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);

      sequencingButton.setText("Sequencing...");
      sequencingButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            showMosaicSequencingWindow();
         }
      });

      startTimeLabel.setText("Start Time");

      startFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startTimeSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatCheckBoxTime.setText("Repeat every");
      repeatCheckBoxTime.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryIntervalSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryIntervalUnitLabel.setText("seconds");

      startTimeUnitLabel.setText("seconds");
      
      
      asyncRoiPanel.setLayout(new MigLayout());
      
      asyncRoiPanel.add(startTimeLabel);
      asyncRoiPanel.add(startTimeSpinner, "wmin 60, wmax 60");
      asyncRoiPanel.add(new JLabel("seconds"), "wrap");
      
      asyncRoiPanel.add(repeatCheckBoxTime);
      asyncRoiPanel.add(repeatEveryIntervalSpinner, "wmin 60, wmax 60");
      asyncRoiPanel.add(repeatEveryIntervalUnitLabel, "wrap");

      attachToMdaTabbedPane.addTab("During imaging", asyncRoiPanel);

      startFrameLabel.setText("Start Frame");
      repeatEveryFrameUnitLabel.setText("frames");
      repeatCheckBox.setText("Repeat every");
      repeatCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            updateROISettings();
         }
      });

      startFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startFrameSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      repeatEveryFrameSpinner.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryFrameSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateROISettings();
         }
      });

      syncRoiPanel.setLayout(new MigLayout());
      
      syncRoiPanel.add(startFrameLabel);
      syncRoiPanel.add(startFrameSpinner, "wmin 60, wmax 60,  wrap");
      
      syncRoiPanel.add(repeatCheckBox);
      syncRoiPanel.add(repeatEveryFrameSpinner, "wmin 60, wmax 60");
      syncRoiPanel.add(repeatEveryFrameUnitLabel, "wrap");
      
      /*

      javax.swing.GroupLayout syncRoiPanelLayout = new javax.swing.GroupLayout(syncRoiPanel);
      syncRoiPanel.setLayout(syncRoiPanelLayout);
      syncRoiPanelLayout.setHorizontalGroup(
         syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(syncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(syncRoiPanelLayout.createSequentialGroup()
                  .addComponent(startFrameLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(startFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 46, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addGroup(syncRoiPanelLayout.createSequentialGroup()
                  .addComponent(repeatCheckBox)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(repeatEveryFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(repeatEveryFrameUnitLabel)))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
      syncRoiPanelLayout.setVerticalGroup(
         syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(syncRoiPanelLayout.createSequentialGroup()
            .addContainerGap()
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(startFrameLabel)
               .addComponent(startFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(syncRoiPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(repeatCheckBox)
               .addComponent(repeatEveryFrameSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(repeatEveryFrameUnitLabel))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );
*/
      attachToMdaTabbedPane.addTab("Between images", syncRoiPanel);

      roisTab.setLayout(new MigLayout());
      
      roisTab.add(setRoiButton, "align center");
      roisTab.add(roiManagerButton, "align center, wrap");
      
      roisTab.add(roiStatusLabel);
      roisTab.add(sequencingButton, "wrap");
      
      roisTab.add(roiLoopLabel, "split 3");
      roisTab.add(roiLoopSpinner, "wmin 60, wmax 60");
      roisTab.add(roiLoopTimesLabel, "wrap");
      
      roisTab.add(runROIsNowButton, "align center");
      roisTab.add(useInMDAcheckBox, "wrap");
      
      roisTab.add(attachToMdaTabbedPane, "skip 1, wrap");
      
      
      /*
      javax.swing.GroupLayout roisTabLayout = new javax.swing.GroupLayout(roisTab);
      roisTab.setLayout(roisTabLayout);
      roisTabLayout.setHorizontalGroup(
         roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 389, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addGroup(javax.swing.GroupLayout.Alignment.LEADING, roisTabLayout.createSequentialGroup()
                  .addGap(20, 20, 20)
                  .addComponent(runROIsNowButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(15, 15, 15)
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(useInMDAcheckBox)
                     .addComponent(attachToMdaTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 247, javax.swing.GroupLayout.PREFERRED_SIZE))))
            .addGap(0, 26, Short.MAX_VALUE))
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGap(25, 25, 25)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addGroup(roisTabLayout.createSequentialGroup()
                        .addComponent(roiStatusLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(sequencingButton))
                     .addGroup(roisTabLayout.createSequentialGroup()
                        .addComponent(setRoiButton, javax.swing.GroupLayout.PREFERRED_SIZE, 108, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(roiManagerButton)))
                  .addGap(93, 93, 93))
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addComponent(roiLoopLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(roiLoopSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(roiLoopTimesLabel)
                  .addGap(115, 115, 115))))
      );
      roisTabLayout.setVerticalGroup(
         roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(roisTabLayout.createSequentialGroup()
            .addGap(21, 21, 21)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(setRoiButton)
               .addComponent(roiManagerButton))
            .addGap(18, 18, 18)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(roiStatusLabel)
               .addComponent(sequencingButton))
            .addGap(18, 18, 18)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(roiLoopLabel)
               .addComponent(roiLoopTimesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(roiLoopSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
               .addGroup(roisTabLayout.createSequentialGroup()
                  .addGroup(roisTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(runROIsNowButton)
                     .addComponent(useInMDAcheckBox))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(attachToMdaTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 113, javax.swing.GroupLayout.PREFERRED_SIZE))
               .addComponent(jSeparator3, javax.swing.GroupLayout.PREFERRED_SIZE, 148, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

*/

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

      allPixelsButton.setText("All Pixels");
      allPixelsButton.addActionListener(new ActionListener() {
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

      channelComboBox.setModel(new DefaultComboBoxModel(
              new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      channelComboBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            final String channel = (String) channelComboBox.getSelectedItem();
            setTargetingChannel(channel);
         }
      });

      phototargetingChannelDropdownLabel.setText("Phototargeting channel:");

      shutterComboBox.setModel(new DefaultComboBoxModel(
              new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
      shutterComboBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            final String shutter = (String) shutterComboBox.getSelectedItem();
            setTargetingShutter(shutter);
         }
      });

      phototargetingShutterDropdownLabel.setText("Phototargeting shutter:");

      jLabel1.setText("Delay(ms):");

      delayField_.setText("0");

      checkerBoardButton_.setText("CheckerBoard");
      checkerBoardButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            dev_.showCheckerBoard(16, 16);
         }
      });

      javax.swing.GroupLayout setupTabLayout = new javax.swing.GroupLayout(setupTab);
      setupTab.setLayout(setupTabLayout);
      setupTabLayout.setHorizontalGroup(
         setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(setupTabLayout.createSequentialGroup()
            .addGap(39, 39, 39)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(setupTabLayout.createSequentialGroup()
                  .addComponent(centerButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(allPixelsButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(checkerBoardButton_))
               .addGroup(setupTabLayout.createSequentialGroup()
                  .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(phototargetingChannelDropdownLabel)
                     .addComponent(phototargetingShutterDropdownLabel))
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                     .addComponent(shutterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)
                     .addComponent(channelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 126, javax.swing.GroupLayout.PREFERRED_SIZE)))
               .addGroup(setupTabLayout.createSequentialGroup()
                  .addComponent(calibrateButton_)
                  .addGap(18, 18, 18)
                  .addComponent(jLabel1)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(delayField_, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)))
            .addContainerGap(124, Short.MAX_VALUE))
      );
      setupTabLayout.setVerticalGroup(
         setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(setupTabLayout.createSequentialGroup()
            .addGap(27, 27, 27)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(centerButton)
               .addComponent(allPixelsButton)
               .addComponent(checkerBoardButton_))
            .addGap(18, 18, 18)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(calibrateButton_)
               .addComponent(jLabel1)
               .addComponent(delayField_, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 89, Short.MAX_VALUE)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(phototargetingChannelDropdownLabel)
               .addComponent(channelComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(setupTabLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(shutterComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(phototargetingShutterDropdownLabel))
            .addGap(83, 83, 83))
      );

      mainTabbedPane.addTab("Setup", setupTab);

      offButton.setText("Off");
      offButton.setSelected(true);
      offButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            setOnState(false);
         }
      });

      ExposureTimeLabel.setText("Exposure time:");

      pointAndShootIntervalSpinner.setModel(new SpinnerNumberModel(500, 1, 1000000000, 1));
      pointAndShootIntervalSpinner.setMaximumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner.setMinimumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner.setPreferredSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent evt) {
            updateExposure();
         }
      });
      pointAndShootIntervalSpinner.addVetoableChangeListener(new VetoableChangeListener() {
         @Override
         public void vetoableChange(PropertyChangeEvent evt)throws PropertyVetoException {
             updateExposure();
         }
      });

      jLabel2.setText("ms");

      javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
               .addGroup(layout.createSequentialGroup()
                  .addComponent(mainTabbedPane)
                  .addContainerGap())
               .addGroup(layout.createSequentialGroup()
                  .addComponent(ExposureTimeLabel)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                  .addComponent(pointAndShootIntervalSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 75, javax.swing.GroupLayout.PREFERRED_SIZE)
                  .addGap(18, 18, 18)
                  .addComponent(jLabel2)
                  .addGap(65, 65, 65)
                  .addComponent(onButton)
                  .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                  .addComponent(offButton)
                  .addGap(0, 0, Short.MAX_VALUE))))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
         .addGroup(layout.createSequentialGroup()
            .addContainerGap()
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
               .addComponent(onButton)
               .addComponent(offButton)
               .addComponent(pointAndShootIntervalSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
               .addComponent(ExposureTimeLabel)
               .addComponent(jLabel2))
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(mainTabbedPane, javax.swing.GroupLayout.PREFERRED_SIZE, 337, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }
   
   private void logDirectoryChooserButtonActionPerformed(java.awt.event.ActionEvent evt) {
      File openDir = FileDialogs.openDir(this, 
              "Select location for Files logging Point and Shoot locations", 
              PROJECTOR_LOG_FILE);
      if (openDir != null) {
         logDirectoryTextField.setText(openDir.getAbsolutePath());
         settings_.putString( Terms.LOGDIRECTORY, openDir.getAbsolutePath());
      }
   }
   

   private void clearLogDirButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearLogDirButtonActionPerformed
      String logDirectory = logDirectoryTextField.getText();
      File logDir = new File(logDirectory);
      if (logDir.isDirectory()) {
         for (File logFile : logDir.listFiles()) {
            logFile.delete();
         }
      }
   }


   // Variables declaration 
   private javax.swing.JLabel ExposureTimeLabel;
   private javax.swing.JButton allPixelsButton;
   private javax.swing.JPanel asyncRoiPanel;
   private javax.swing.JTabbedPane attachToMdaTabbedPane;
   private javax.swing.JButton calibrateButton_;
   private javax.swing.JButton centerButton;
   private javax.swing.JComboBox channelComboBox;
   private javax.swing.JButton checkerBoardButton_;
   private javax.swing.JButton clearLogDirButton;
   private javax.swing.JTextField delayField_;
   private javax.swing.JLabel jLabel1;
   private javax.swing.JLabel jLabel2;
   private javax.swing.JSeparator jSeparator1;
   private javax.swing.JSeparator jSeparator3;
   private javax.swing.JButton logDirectoryChooserButton;
   private javax.swing.JTextField logDirectoryTextField;
   private javax.swing.JTabbedPane mainTabbedPane;
   private javax.swing.JButton offButton;
   private javax.swing.JButton onButton;
   private javax.swing.JLabel phototargetInstructionsLabel;
   private javax.swing.JLabel phototargetingChannelDropdownLabel;
   private javax.swing.JLabel phototargetingShutterDropdownLabel;
   private javax.swing.JSpinner pointAndShootIntervalSpinner;
   private javax.swing.JLabel pointAndShootModeLabel;
   private javax.swing.JToggleButton pointAndShootOffButton;
   private javax.swing.JToggleButton pointAndShootOnButton;
   private javax.swing.JPanel pointAndShootTab;
   private javax.swing.JCheckBox repeatCheckBox;
   private javax.swing.JCheckBox repeatCheckBoxTime;
   private javax.swing.JSpinner repeatEveryFrameSpinner;
   private javax.swing.JLabel repeatEveryFrameUnitLabel;
   private javax.swing.JSpinner repeatEveryIntervalSpinner;
   private javax.swing.JLabel repeatEveryIntervalUnitLabel;
   private javax.swing.JLabel roiLoopLabel;
   private javax.swing.JSpinner roiLoopSpinner;
   private javax.swing.JLabel roiLoopTimesLabel;
   private javax.swing.JButton roiManagerButton;
   private javax.swing.JLabel roiStatusLabel;
   private javax.swing.JPanel roisTab;
   private javax.swing.JButton runROIsNowButton;
   private javax.swing.JButton sequencingButton;
   private javax.swing.JButton setRoiButton;
   private javax.swing.JPanel setupTab;
   private javax.swing.JComboBox shutterComboBox;
   private javax.swing.JLabel startFrameLabel;
   private javax.swing.JSpinner startFrameSpinner;
   private javax.swing.JLabel startTimeLabel;
   private javax.swing.JSpinner startTimeSpinner;
   private javax.swing.JLabel startTimeUnitLabel;
   private javax.swing.JPanel syncRoiPanel;
   private javax.swing.JCheckBox useInMDAcheckBox;

}