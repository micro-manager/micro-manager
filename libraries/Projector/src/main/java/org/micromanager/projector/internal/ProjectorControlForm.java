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
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.text.DefaultFormatter;
import mmcorej.CMMCore;
import mmcorej.DeviceType;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.data.Datastore;
import org.micromanager.display.DataViewer;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DisplayMouseEvent;
import org.micromanager.events.SLMExposureChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.SliderPanel;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.projector.Mapping;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.projector.ProjectorActions;
import org.micromanager.projector.internal.devices.SLM;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * The main window for the Projector plugin. Contains logic for calibration, and control for SLMs
 * and Galvos.
 */
public class ProjectorControlForm extends JFrame {

   private static ProjectorControlForm formSingleton_;
   private final ProjectorControlExecution projectorControlExecution_;
   private final Object studioEventHandler_;
   private final Object displayEventHandler_;
   private final ProjectionDevice dev_;
   private final AtomicBoolean pointAndShooteModeOn_ = new AtomicBoolean(false);
   private final BlockingQueue<PointAndShootInfo> pointAndShootQueue_;
   private Thread pointAndShootThread_;
   private DataViewer pointAndShootViewer_;
   private final CMMCore core_;
   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private final boolean isSLM_;
   private final boolean isASIScanner_;
   private Roi[] individualRois_ = {};
   private Mapping mapping_;
   private String targetingChannel_;
   private MosaicSequencingFrame mosaicSequencingFrame_;
   private String targetingShutter_;
   private boolean targetingShutterOriginallyOpen_;
   private Boolean disposing_ = false;
   private Calibrator calibrator_;
   private BufferedWriter logFileWriter_;
   private String logFile_;
   private BufferedWriter mdaLogFileWriter_;
   private String mdaLogFile_;
   private double xCenter_ = 0.0;
   private double yCenter_ = 0.0;


   private static final SimpleDateFormat LOGFILEDATE_FORMATTER =
         new SimpleDateFormat("yyyyMMdd");
   private static final SimpleDateFormat LOGTIME_FORMATTER =
         new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");


   public static final FileType PROJECTOR_LOG_FILE = new FileType("PROJECTOR_LOG_FILE",
         "Projector Log File", "./MyProjector.log", true, "log");


   // GUI element variables declaration
   private javax.swing.JButton allPixelsButton_;
   private javax.swing.JTabbedPane attachToMdaTabbedPane_;
   private javax.swing.JButton calibrateButton_;
   private javax.swing.JComboBox<String> channelComboBox_;
   private javax.swing.JButton checkerBoardButton_;
   private javax.swing.JTextField delayField_;
   private javax.swing.JTextField logDirectoryTextField_;
   private javax.swing.JSpinner pointAndShootIntervalSpinner_;  // mis-named, actually is the exposure time
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
   private javax.swing.JSpinner roiIntervalSpinner_;
   private javax.swing.JLabel roiStatusLabel_;
   private javax.swing.JButton exposeROIsButton_;
   private javax.swing.JButton sequencingButton_;
   private javax.swing.JComboBox<String> shutterComboBox_;
   private javax.swing.JLabel startFrameLabel_;
   private javax.swing.JSpinner startFrameSpinner_;
   private javax.swing.JLabel startTimeLabel_;
   private javax.swing.JSpinner startTimeSpinner_;
   private javax.swing.JLabel startTimeUnitLabel_;
   private javax.swing.JPanel syncRoiPanel_;
   private javax.swing.JCheckBox useInMDAcheckBox;

   /**
    * Constructor. Creates the main window for the Projector plugin.
    */
   private ProjectorControlForm(CMMCore core, Studio app) {
      studio_ = app;
      core_ = app.getCMMCore();
      settings_ = studio_.profile().getSettings(this.getClass());
      dev_ = ProjectorActions.getProjectionDevice(studio_);
      xCenter_ = settings_.getDouble(Terms.XCENTER, dev_.getXMinimum() + dev_.getXRange() / 2);
      yCenter_ = settings_.getDouble(Terms.YCENTER, dev_.getYMinimum() + dev_.getYRange() / 2);
      mapping_ = MappingStorage.loadMapping(core_, dev_, settings_.toPropertyMap());
      pointAndShootQueue_ = new LinkedBlockingQueue<>();
      projectorControlExecution_ = new ProjectorControlExecution(studio_);
      studio_.events().registerForEvents(projectorControlExecution_);

      isSLM_ = dev_ instanceof SLM;
      // Only an SLM (not a galvo) has pixels.

      boolean temp = false;
      try {
         temp = !isSLM_ && core_.getDeviceLibrary(dev_.getName()).equals("ASITiger");
      }  catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      isASIScanner_ = temp;

      // Create GUI
      initComponents();

      allPixelsButton_.setVisible(isSLM_);
      checkerBoardButton_.setVisible(isSLM_);
      // No point in looping ROIs on an SLM.
      roiLoopSpinner_.setVisible(!isSLM_);
      roiLoopLabel_.setVisible(!isSLM_);
      roiLoopTimesLabel_.setVisible(!isSLM_);
      roiIntervalSpinner_.setVisible(isASIScanner_);
      pointAndShootOffButton_.setSelected(true);
      populateChannelComboBox(settings_.getString(Terms.PTCHANNEL, ""));
      populateShutterComboBox(settings_.getString(Terms.PTSHUTTER, ""));
      super.addWindowFocusListener(new WindowAdapter() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            if (!disposing_) {
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
      commitSpinnerOnValidEdit(roiIntervalSpinner_);
      pointAndShootIntervalSpinner_.setValue(dev_.getExposure() / 1000);
      roiIntervalSpinner_.setValue(dev_.getRoiInterval() / 1000);
      sequencingButton_.setVisible(MosaicSequencingFrame.getMosaicDevices(core).size() > 0);

      studioEventHandler_ = new Object() {
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
            if (!sce.isCanceled()) {
               dispose();
            }
         }

         @Subscribe
         public void onAcquisitionStart(AcquisitionStartedEvent ae) {
            Datastore store = ae.getDatastore();
            String savePath = store.getSavePath();
            mdaLogFile_ = savePath + File.separator + "PointAndShoot.log";
         }

         @Subscribe
         public void onAcquisitionEnd(AcquisitionEndedEvent ae) {
            try {
               if (mdaLogFileWriter_ != null) {
                  mdaLogFileWriter_.flush();
                  mdaLogFileWriter_.close();
               }
            } catch (IOException ioe) {
               studio_.logs().logError(ioe, "Failed to close PAS MDA Log file");
            }
            mdaLogFileWriter_ = null;
            mdaLogFile_ = null;
         }
      };
      studio_.events().registerForEvents(studioEventHandler_);

      displayEventHandler_ = new Object() {
         @Subscribe
         public void onDataViewerBecameActiveEvent(DataViewerDidBecomeActiveEvent dve) {
            enablePointAndShootMode(pointAndShooteModeOn_.get());
         }
      };
      studio_.displays().registerForEvents(displayEventHandler_);

      delayField_.setText(settings_.getString(Terms.DELAY, "0"));
      logDirectoryTextField_.setText(settings_.getString(Terms.LOGDIRECTORY, ""));

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setLocation(500, 300);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
      updateROISettings();
   }

   /**
    * Shows the singleton form.
    */
   public static ProjectorControlForm showSingleton(CMMCore core, Studio app) {
      if (formSingleton_ == null) {
         formSingleton_ = new ProjectorControlForm(core, app);
      }
      formSingleton_.setVisible(true);
      return formSingleton_;
   }

   /**
    * The ProjectorControlExecution object carries out the "business" side of the projector. Use it
    * for scripting, etc..
    *
    * @return the ProjectorControlExecution object
    */
   public ProjectorControlExecution exec() {
      return projectorControlExecution_;
   }

   // ## Methods for handling targeting channel and shutter

   /**
    * Reads the available channels from Micro-Manager Channel Group and populates the targeting
    * channel drop-down menu.
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
    * Reads the available shutters from Micro-Manager and lists them in the targeting shutter
    * drop-down menu.
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
    * Sets the targeting channel. channelName should be a channel from the current ChannelGroup.
    */
   void setTargetingChannel(String channelName) {
      targetingChannel_ = channelName;
      if (channelName != null) {
         settings_.putString("channel", channelName);
      }
   }

   /**
    * Sets the targeting shutter. Should be the name of a loaded Shutter device.
    */
   void setTargetingShutter(String shutterName) {
      targetingShutter_ = shutterName;
      settings_.putString("shutter", shutterName);
      dev_.setExternalShutter(shutterName);
   }

   /**
    * Runs the full calibration. First generates a linear mapping (a first approximation) and then
    * generates a second piece-wise "non-linear" mapping of affine transforms. Saves the mapping to
    * Java Preferences.
    */
   public void runCalibration() {
      runCalibration(false);
   }

   /**
    * Runs the full calibration. First generates a linear mapping (a first approximation) and then
    * generates a second piece-wise "non-linear" mapping of affine transforms. Saves the mapping to
    * Java Preferences.
    */
   public void runCalibration(boolean blocking) {
      settings_.putString(Terms.DELAY, delayField_.getText());
      settings_.putString(Terms.PTCHANNEL, (String) channelComboBox_.getSelectedItem());
      final String desiredShutter = (String) shutterComboBox_.getSelectedItem();
      settings_.putString(Terms.PTSHUTTER, desiredShutter);
      if (calibrator_ != null && calibrator_.isCalibrating()) {
         return;
      }
      calibrator_ = new Calibrator(studio_, dev_, settings_);
      final String originalChannel = projectorControlExecution_.prepareChannel(
              settings_.getString(Terms.PTCHANNEL, ""));
      String originalShutterT = studio_.core().getShutterDevice();
      if (!desiredShutter.isEmpty()) {
         try {
            studio_.core().setShutterDevice(desiredShutter);
         } catch (Exception ex) {
            originalShutterT = "";
         }
      }
      final String originalShutter = originalShutterT;
      Future<Boolean> runCalibration = calibrator_.runCalibration();
      Thread t = new Thread() {
         @Override
         public void run() {
            Boolean success;
            try {
               success = runCalibration.get();
            } catch (InterruptedException | ExecutionException ex) {
               success = false;
            }
            if (success) {
               mapping_ = MappingStorage.loadMapping(core_, dev_, settings_.toPropertyMap());
            }
            if (!originalShutter.isEmpty()) {
               try {
                  studio_.core().setShutterDevice(originalShutter);
               } catch (Exception ex) {
                  studio_.logs().logError("Failed to reset shutter in Projector calibration");
               }
            }
            if (originalChannel != null && !originalChannel.isEmpty()) {
               projectorControlExecution_.returnChannel(originalChannel);
            }
            studio_.alerts().postAlert("Projector Calibration", this.getClass(),
                  "Calibration " + (success ? "succeeded." : "failed."));
            SwingUtilities.invokeLater(() -> calibrateButton_.setText("Calibrate"));
            calibrator_ = null;
         }
      };
      if (blocking) {
         t.run();
      } else {
         t.start();
      }
   }

   /**
    * Returns true if the calibration is currently running.
    *
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


   /**
    * Flips a point if the image was mirrored. TODO: also correct for rotation..
    *
    * <p>private Point mirrorIfNecessary(DataViewer dv, Point pOffscreen) { boolean
    * isImageMirrored = false; int imageWidth = 0; DataProvider dp = dv.getDataProvider();
    * if (dp != null) { try {
    * Image lastImage = dp.getImage(dp.getMaxIndices()); if (lastImage != null) { PropertyMap
    * userData = lastImage.getMetadata().getUserData(); if (userData.containsString
    * ("ImageFlipper-Mirror"))
    * { String value = userData.getString("ImageFlipper-Mirror", ""); if (value.equals("On")) {
    * isImageMirrored = true; imageWidth = lastImage.getWidth(); } } } } catch (IOException ioe) {
    * studio_.logs().logError(ioe); } } if (isImageMirrored) { return new Point(imageWidth -
    * pOffscreen.x, pOffscreen.y); } else { return pOffscreen; } }
    */


   @Subscribe
   public void onDisplayMouseEvent(DisplayMouseEvent dme) {
      // only take action when the Hand tool is selected
      if (dme.getToolId() != ij.gui.Toolbar.HAND) {
         return;
      }
      if ((dme.getEvent().getID() == MouseEvent.MOUSE_PRESSED)
                && dme.getEvent().isShiftDown()
                && dme.getEvent().getButton() == 1) {
         Point2D p2D = dme.getCenterLocation();
         addPointToPointAndShootQueue(p2D);
      }
   }

   /**
    * Adds a point in image space to the point and shoot queue.
    *
    * @param p2D 2D point on an image produced with the current camera settings Point will be mapped
    *            to corresponding point in Projector coordinates
    */
   public void addPointToPointAndShootQueue(Point2D p2D) {
      Point p = new Point((int) Math.round(p2D.getX()), (int) Math.round(p2D.getY()));
      // Is this needed?
      // p = mirrorIfNecessary(pointAndShootViewer_, p);
      Integer binning = null;
      Rectangle roi = null;
      try {
         binning = Utils.getBinning(core_);
         roi = core_.getROI();
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      final Point2D.Double devP = ProjectorActions.transformPoint(
            MappingStorage.loadMapping(core_, dev_, settings_.toPropertyMap()),
            new Point2D.Double(p.getX(), p.getY()), roi, binning);
      PointAndShootInfo.Builder psiBuilder = new PointAndShootInfo.Builder();
      PointAndShootInfo psi = psiBuilder.projectionDevice(dev_)
            .devPoint(devP)
            .canvasPoint(new Point((int) p2D.getX(), (int) p2D.getY()))
            .build();
      pointAndShootQueue_.add(psi);
   }

   /**
    * Ensures that the Point and Shoot thread is running.
    * The thread will take anything in the queue and execure those points.
    */
   public void enableShootMode() {
      // restart this thread if it is not running?
      if (pointAndShootThread_ == null || !pointAndShootThread_.isAlive()) {
         pointAndShootThread_ = new Thread(() -> {
            while (true) {
               try {
                  PointAndShootInfo psi = pointAndShootQueue_.take();
                  if (psi.isStopped()) {
                     return;
                  }
                  try {
                     final String originalConfig
                             = projectorControlExecution_.prepareChannel(targetingChannel_);
                     while (psi != null) {
                        if (psi.getDevice() != null) {
                           ProjectorActions.displaySpot(
                                   psi.getDevice(),
                                   psi.getDevPoint().x,
                                   psi.getDevPoint().y);
                           logPoint(psi.getCanvasPoint());
                        }
                        psi = pointAndShootQueue_.poll();
                     }
                     projectorControlExecution_.returnChannel(originalConfig);
                  } catch (Exception e) {
                     studio_.logs().showError(e);
                  }
               } catch (InterruptedException ex) {
                  studio_.logs().showError(ex);
               }
            }
         });
         pointAndShootThread_.setName("Point and Shoot Thread");
         pointAndShootThread_.setPriority(6);
         pointAndShootThread_.start();
      }
   }


   /**
    * Turn on/off point and shoot mode.
    *
    * @param on on/off flag
    */
   public void enablePointAndShootMode(boolean on) {
      if (on && (mapping_ == null)) {
         final String errorS =
               "Please calibrate the phototargeting device first, using the Setup tab.";
         studio_.logs().logError(errorS);
         return;
      }
      pointAndShooteModeOn_.set(on);
      enableShootMode();

      if (!on && pointAndShootViewer_ != null) {
         pointAndShootViewer_.unregisterForEvents(this);
         pointAndShootViewer_ = null;
      }
      if (on
            && pointAndShootViewer_ != null
            && pointAndShootViewer_ != studio_.displays().getActiveDataViewer()) {
         pointAndShootViewer_.unregisterForEvents(this);
         pointAndShootViewer_ = null;
      }
      if (on && pointAndShootViewer_ == null) {
         pointAndShootViewer_ = studio_.displays().getActiveDataViewer();
         if (pointAndShootViewer_ != null) {
            pointAndShootViewer_.registerForEvents(this);
         }
      }
   }


   /**
    * Creates the log file - names with the current date - if it did not yet exist.
    *
    * @return Writer to the log file
    */
   private BufferedWriter checkLogFile() {
      if (logFileWriter_ == null || logFile_ == null || !(new File(logFile_)).exists()) {
         if (logDirectoryTextField_.getText().isEmpty()) {
            return null;
         }
         String currentDate = LOGFILEDATE_FORMATTER.format(new Date());
         logFile_ = logDirectoryTextField_.getText()
               + File.separator
               + currentDate
               + ".log";
         try {
            OutputStreamWriter writer = new OutputStreamWriter(
                  new FileOutputStream(logFile_), StandardCharsets.UTF_8);
            // not sure if buffering is useful
            logFileWriter_ = new BufferedWriter(writer, 128);
         } catch (FileNotFoundException ex) {
            studio_.alerts().postAlert("Error opening logfile", this.getClass(),
                  "Failed to open log file");
         }
      }
      return logFileWriter_;
   }

   /**
    * Creates logfile in the acquisition directory of current MDA.
    *
    * @return Writer to the log file
    */
   private BufferedWriter checkMDALogFile() {
      if (mdaLogFile_ == null) {
         return null;
      }
      if (mdaLogFileWriter_ == null) {
         try {
            OutputStreamWriter writer = new OutputStreamWriter(
                  new FileOutputStream(mdaLogFile_), StandardCharsets.UTF_8);
            mdaLogFileWriter_ = new BufferedWriter(writer, 128);
         } catch (FileNotFoundException ex) {
            studio_.alerts().postAlert("Error opening MDA logfile", this.getClass(),
                  "Failed to open MDA log file");
         }
      }
      return mdaLogFileWriter_;
   }

   /**
    * Writes a point (screen coordinates) to the logfile, preceded by the current date and time in
    * ms.
    *
    * @param p Point to be written to the logfile.
    */
   private void logPoint(Point p) {
      BufferedWriter logFileWriter = checkLogFile();
      BufferedWriter mdaLogFileWriter = checkMDALogFile();
      if (logFileWriter == null && mdaLogFileWriter == null) {
         return;
      }
      String logLine = LOGTIME_FORMATTER.format(new Date())
            + "\t" + p.x + "\t" + p.y;  // could use nanoseconds instead...
      try {
         if (logFileWriter != null) {
            logFileWriter.write(logLine);
            logFileWriter.newLine();
            logFileWriter.flush();
         }
         if (mdaLogFileWriter != null) {
            mdaLogFileWriter.write(logLine);
            mdaLogFileWriter.newLine();
            mdaLogFileWriter.flush();
         }
      } catch (IOException ioe) {
         studio_.alerts().postAlert("Projector logfile error", this.getClass(),
               "Failed to open write to Projector log file");
      }
   }

   // ## Manipulating ROIs


   /** Returns the current ROIs for a given ImageWindow. If selectedOnly
    * is true, then returns only those ROIs selected in the ROI Manager.
    * If no ROIs are selected, then all ROIs are returned.
    */
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


   /**
    * Upload current Window's ROIs, transformed, to the phototargeting device. Polygons store camera
    * coordinates in integers, and we use the ImageJ class FloatPolygon for corresponding scanner
    * coordinates
    */
   public void sendCurrentImageWindowRois() {
      if (mapping_ == null) {
         throw new RuntimeException(
               "Please calibrate the phototargeting device first, using the Setup tab.");
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
      Integer binning = null;
      Rectangle roi = null;
      try {
         binning = Utils.getBinning(core_);
         roi = core_.getROI();
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      List<FloatPolygon> transformedRois = ProjectorActions.transformROIs(
            rois, mapping_, roi, binning);
      dev_.loadRois(transformedRois);
      individualRois_ = rois;
   }


   /**
    * Sets an roi.
    *
    * @param rois Roi to be applied to the device
    */
   public void setROIs(Roi[] rois) {
      if (mapping_ == null) {
         throw new RuntimeException(
               "Please calibrate the phototargeting device first, using the Setup tab.");
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
      Integer binning = null;
      Rectangle roi = null;
      try {
         binning = Utils.getBinning(core_);
         roi = core_.getROI();
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      List<FloatPolygon> transformedRois = ProjectorActions.transformROIs(
            rois, mapping_, roi, binning);
      dev_.loadRois(transformedRois);
      individualRois_ = rois;
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
    *
    * @param turnedOn true = Point and Shoot is ON
    */
   public void updatePointAndShoot(boolean turnedOn) {
      if (turnedOn && (mapping_ == null)) {
         final String errorS =
               "Please calibrate the phototargeting device first, using the Setup tab.";
         studio_.logs().showError(errorS);
         throw new RuntimeException(errorS);
      }
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


   /**
    * Runs runnable starting at firstTimeMs after this function is called, and then, if repeat is
    * true, every intervalTimeMs thereafter until shouldContinue.call() returns false.
    *
    * @deprecated - Use the {@link ProjectorControlExecution#runAtIntervals(long, boolean, long,
    * java.lang.Runnable, java.util.concurrent.Callable)} method instead
    */
   @Deprecated
   private Runnable runAtIntervals(final long firstTimeMs,
         boolean repeat,
         final long intervalTimeMs,
         final Runnable runnable,
         final Callable<Boolean> shouldContinue) {

      return projectorControlExecution_.runAtIntervals(firstTimeMs, true,
            intervalTimeMs, runnable, shouldContinue);

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
      roiIntervalSpinner_.setEnabled(isASIScanner_ && roisSubmitted);
      exposeROIsButton_.setEnabled(roisSubmitted);
      useInMDAcheckBox.setEnabled(roisSubmitted);

      int nrRepetitions = 0;
      if (roiLoopSpinner_.isEnabled()) {
         nrRepetitions = getSpinnerIntegerValue(roiLoopSpinner_);
         settings_.putInteger(Terms.NRROIREPETITIONS, nrRepetitions);
      }
      dev_.setPolygonRepetitions(nrRepetitions);

      double roiIntervalMs = 0;
      if (roiIntervalSpinner_.isEnabled()) {
         roiIntervalMs = getSpinnerDoubleValue(roiIntervalSpinner_);
         settings_.putDouble(Terms.ROIINTERVAL, roiIntervalMs);
      }
      ProjectorActions.setRoiIntervalUs(dev_, 1000 * roiIntervalMs);

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
         studio_.acquisitions().clearRunnables();
         if (synchronous) {
            final String threadName = "Synchronous phototargeting of ROIs";
            projectorControlExecution_
                  .attachRoisToMDA(getSpinnerIntegerValue(startFrameSpinner_) - 1,
                        repeatCheckBox_.isSelected(),
                        getSpinnerIntegerValue(repeatEveryFrameSpinner_),
                        projectorControlExecution_.phototargetROIsRunnable(threadName,
                              dev_, targetingChannel_, targetingShutter_, individualRois_));
         } else {
            final Callable<Boolean> mdaRunning = studio_.acquisitions()::isAcquisitionRunning;
            final String threadName = "Asynchronous phototargeting of ROIs";
            projectorControlExecution_.attachRoisToMDA(1, false, 0,
                  new Thread(
                        projectorControlExecution_.runAtIntervals(
                              (long) (1000. * getSpinnerDoubleValue(startTimeSpinner_)),
                              repeatCheckBoxTime_.isSelected(),
                              (long) (1000 * getSpinnerDoubleValue(repeatEveryIntervalSpinner_)),
                              projectorControlExecution_.phototargetROIsRunnable(threadName,
                                    dev_, targetingChannel_, targetingShutter_, individualRois_),
                              mdaRunning)));
         }
      } else {
         studio_.acquisitions().clearRunnables();
      }
   }

   // Set the exposure to whatever value is currently in the Exposure field.
   private void updateExposure() {
      double exposureMs = Double.parseDouble(
            pointAndShootIntervalSpinner_.getValue().toString());
      ProjectorActions.setExposure(dev_, 1000 * exposureMs);
      settings_.putDouble(Terms.EXPOSURE, exposureMs);
   }

   // Set the ROI interval to whatever value is currently in the Interval field.
   private void updateRoiInterval() {
      double intervalMs = Double.parseDouble(
              roiIntervalSpinner_.getValue().toString());
      ProjectorActions.setRoiIntervalUs(dev_, 1000 * intervalMs);
      settings_.putDouble(Terms.ROIINTERVAL, intervalMs);
   }

   private void updateX(String text) {
      try {
         xCenter_ = NumberUtils.displayStringToDouble(text);
         settings_.putDouble(Terms.XCENTER, xCenter_);
         ProjectorActions.displaySpot(dev_, xCenter_, yCenter_);
      } catch (ParseException e) {
         studio_.logs().logError(e);
      }
   }

   private void updateY(String text) {
      try {
         yCenter_ = NumberUtils.displayStringToDouble(text);
         settings_.putDouble(Terms.YCENTER, yCenter_);
         ProjectorActions.displaySpot(dev_, xCenter_, yCenter_);
      } catch (ParseException e) {
         studio_.logs().logError(e);
      }
   }

   /**
    * Show the Mosaic Sequencing window (a JFrame). Should only be called if we already know the
    * Mosaic is attached.
    */
   void showMosaicSequencingWindow() {
      if (mosaicSequencingFrame_ == null) {
         mosaicSequencingFrame_ = new MosaicSequencingFrame(studio_, core_, this, (SLM) dev_);
      }
      mosaicSequencingFrame_.setVisible(true);
   }


   /**
    * Returns singleton instance if it exists, null otherwise.
    *
    * @return singleton instance if it exists, null otherwise
    */
   public static ProjectorControlForm getSingleton() {
      return formSingleton_;
   }

   @Override
   public void dispose() {
      formSingleton_ = null;
      disposing_ = true;
      studio_.events().unregisterForEvents(projectorControlExecution_);
      studio_.events().unregisterForEvents(studioEventHandler_);
      studio_.displays().unregisterForEvents(displayEventHandler_);
      enablePointAndShootMode(false);
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
            if (mdaLogFileWriter_ != null) {
               mdaLogFileWriter_.close();
            }
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

   public Mapping getMapping() {
      return mapping_;
   }


   /**
    * This method is called from within the constructor to initialize the form.
    */
   @SuppressWarnings("unchecked")
   private void initComponents() {
      pointAndShootOnButton = new JToggleButton();
      pointAndShootOffButton_ = new JToggleButton();
      roiLoopLabel_ = new JLabel();
      roiLoopTimesLabel_ = new JLabel();
      exposeROIsButton_ = new JButton();
      roiLoopSpinner_ = new JSpinner();
      roiIntervalSpinner_ = new JSpinner();
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
      channelComboBox_ = new JComboBox<>();
      shutterComboBox_ = new JComboBox<>();
      delayField_ = new JTextField();
      checkerBoardButton_ = new JButton();
      pointAndShootIntervalSpinner_ = new JSpinner();
      logDirectoryTextField_ = new JTextField();

      final JPanel asyncRoiPanel = new JPanel();
      final JButton centerButton = new JButton();
      final JButton clearLogDirButton = new JButton();
      final JButton openLogDirButton = new JButton();
      final JButton onButton = new JButton();
      final JButton offButton = new JButton();
      final JLabel photoTargetInstructionsLabel = new JLabel();
      final JButton setRoiButton = new JButton();
      final JButton logDirectoryChooserButton = new JButton();
      final JTabbedPane mainTabbedPane = new JTabbedPane();
      final JPanel pointAndShootTab = new JPanel();
      final JButton roiManagerButton = new JButton();
      final JPanel roisTab = new JPanel();
      final JPanel setupTab = new JPanel();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Projector Controls");
      setResizable(false);

      // On/Off buttons in the always visible first row
      onButton.setText("On");
      onButton.addActionListener((ActionEvent evt) -> {
         targetingShutterOriginallyOpen_ =
               projectorControlExecution_.prepareShutter(targetingShutter_);
         dev_.turnOn();
         updatePointAndShoot(false);
         onButton.setSelected(true);
         offButton.setSelected(false);
      });

      offButton.setText("Off");
      offButton.setSelected(true);
      offButton.addActionListener((ActionEvent evt) -> {
         projectorControlExecution_.returnShutter(targetingShutter_,
               targetingShutterOriginallyOpen_);
         dev_.turnOff();
         onButton.setSelected(false);
         offButton.setSelected(true);
      });

      mainTabbedPane.addChangeListener((ChangeEvent evt) -> updatePointAndShoot(false));

      // On/Off button in the point and shoot pane
      pointAndShootOnButton.setText("On");
      pointAndShootOnButton.setMaximumSize(new Dimension(75, 23));
      pointAndShootOnButton.setMinimumSize(new Dimension(75, 23));
      pointAndShootOnButton.setPreferredSize(new Dimension(75, 23));
      pointAndShootOnButton.addActionListener((ActionEvent evt) -> {
         try {
            updatePointAndShoot(true);
            IJ.setTool(Toolbar.HAND);
         } catch (RuntimeException e) {
            studio_.logs().showError(e);
         }
      });
      pointAndShootOffButton_.setText("Off");
      pointAndShootOffButton_.setPreferredSize(new Dimension(75, 23));
      pointAndShootOffButton_.addActionListener((ActionEvent evt) -> updatePointAndShoot(false));

      photoTargetInstructionsLabel.setText(
            "(To phototarget, Shift + click on the image, use ImageJ hand-tool)");

      logDirectoryChooserButton.setText("...");
      logDirectoryChooserButton.addActionListener((ActionEvent evt) -> {
         File openDir = FileDialogs.openDir(ProjectorControlForm.getSingleton(),
               "Select location for Files logging Point and Shoot locations",
               PROJECTOR_LOG_FILE);
         if (openDir != null) {
            logDirectoryTextField_.setText(openDir.getAbsolutePath());
            settings_.putString(Terms.LOGDIRECTORY, openDir.getAbsolutePath());
         }
      });

      clearLogDirButton.setText("Clear Log Directory");
      clearLogDirButton.addActionListener((ActionEvent evt) -> {
         if (logFileWriter_ != null) {
            try {
               logFileWriter_.flush();
               logFileWriter_.close();
            } catch (IOException ioe) {
               studio_.logs().logError(ioe, "Failed to close Projector log file");
            } finally {
               logFileWriter_ = null;
            }
         }
         String logDirectory = logDirectoryTextField_.getText();
         File logDir = new File(logDirectory);
         if (logDir.isDirectory()) {
            java.io.File[] files = logDir.listFiles();
            if (files != null) {
               for (File logFile : files) {
                  logFile.delete();
               }
            }
         }
      });

      openLogDirButton.setText("Open Log Directory");
      openLogDirButton.addActionListener((ActionEvent evt) -> {
         if (logDirectoryTextField_.getText() != null) {
            try {
               Desktop.getDesktop().open(
                     new File(logDirectoryTextField_.getText()));
            } catch (IOException ioe) {
               studio_.logs().showMessage("Invalid directory");
            }
         }
      });

      pointAndShootTab.setLayout(new MigLayout("", "", "[40]"));

      pointAndShootTab.add(new JLabel("Point and shoot mode:"));
      pointAndShootTab.add(pointAndShootOnButton);
      pointAndShootTab.add(pointAndShootOffButton_, "wrap");

      pointAndShootTab.add(photoTargetInstructionsLabel, "span 3, wrap");

      pointAndShootTab.add(new JLabel("Log Directory"), "span3, split 3");
      pointAndShootTab.add(logDirectoryTextField_, "grow");
      pointAndShootTab.add(logDirectoryChooserButton, "wrap");

      pointAndShootTab.add(clearLogDirButton, "span 4, split 2");
      pointAndShootTab.add(openLogDirButton, "wrap");

      mainTabbedPane.addTab("Point and Shoot", pointAndShootTab);

      roiLoopLabel_.setText("Loop:");

      roiLoopTimesLabel_.setText("times");

      setRoiButton.setText("Set ROI(s)");
      setRoiButton.setToolTipText(
            "Specify an ROI you wish to be phototargeted by using the ImageJ ROI tools "
            + "(point, rectangle, oval, polygon). Then press Set ROI(s) to send the ROIs "
            + "to the phototargeting device. To initiate phototargeting, press Go!");
      setRoiButton.addActionListener((ActionEvent evt) -> {
         try {
            sendCurrentImageWindowRois();
            updateROISettings();
         } catch (RuntimeException e) {
            studio_.logs().showError(e);
         }
      });

      exposeROIsButton_.setText("Expose ROIs now!");
      exposeROIsButton_.addActionListener((ActionEvent evt) ->
            new Thread(() -> projectorControlExecution_.exposeRois(
                dev_, targetingChannel_, targetingShutter_, individualRois_)).start());

      roiLoopSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      roiLoopSpinner_.addChangeListener((ChangeEvent evt) -> updateROISettings());
      roiLoopSpinner_.setValue(settings_.getInteger(Terms.NRROIREPETITIONS, 1));

      roiIntervalSpinner_.setModel(new SpinnerNumberModel(1, 0, 1000000000, 1));
      roiIntervalSpinner_.addChangeListener((ChangeEvent evt) -> updateRoiInterval());
      roiIntervalSpinner_.setValue(settings_.getDouble(Terms.ROIINTERVAL, 1.0));

      useInMDAcheckBox.setText("Run ROIs in Multi-Dimensional Acquisition");
      useInMDAcheckBox.addActionListener((ActionEvent evt) -> updateROISettings());

      roiStatusLabel_.setText("No ROIs submitted yet");

      roiManagerButton.setText("ROI Manager >>");
      roiManagerButton.addActionListener((ActionEvent evt) -> Utils.showRoiManager());

      sequencingButton_.setText("Sequencing...");
      sequencingButton_.addActionListener((ActionEvent evt) -> showMosaicSequencingWindow());

      startTimeLabel_.setText("Start Time");

      startFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startTimeSpinner_.addChangeListener((ChangeEvent evt) -> updateROISettings());

      repeatCheckBoxTime_.setText("Repeat every");
      repeatCheckBoxTime_.addActionListener((ActionEvent evt) -> updateROISettings());

      repeatEveryFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryIntervalSpinner_.addChangeListener((ChangeEvent evt) -> updateROISettings());

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
      repeatCheckBox_.addActionListener((ActionEvent evt) -> updateROISettings());

      startFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      startFrameSpinner_.addChangeListener((ChangeEvent evt) -> updateROISettings());

      repeatEveryFrameSpinner_.setModel(new SpinnerNumberModel(1, 1, 1000000000, 1));
      repeatEveryFrameSpinner_.addChangeListener((ChangeEvent evt) -> updateROISettings());

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

      if (isASIScanner_) {
         roisTab.add(roiLoopTimesLabel_);
         roisTab.add(new JLabel("Interval"), "align center, split 3");
         roisTab.add(roiIntervalSpinner_, "wmin 60, wmax 60");
         roisTab.add(new JLabel("ms"), "wrap");
      } else {
         roisTab.add(roiLoopTimesLabel_, "wrap");
      }

      roisTab.add(exposeROIsButton_, "align center");
      roisTab.add(useInMDAcheckBox, "wrap");

      roisTab.add(attachToMdaTabbedPane_, "skip 1, wrap");

      mainTabbedPane.addTab("ROIs", roisTab);

      calibrateButton_.setText("Calibrate!");
      calibrateButton_.addActionListener((ActionEvent evt) -> {
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
            studio_.logs().showError(e);
         }
      });

      allPixelsButton_.setText("All Pixels");
      allPixelsButton_.addActionListener((ActionEvent evt) -> dev_.activateAllPixels());

      centerButton.setText("Show Center Spot");
      centerButton.addActionListener((ActionEvent evt) -> {
         ProjectorActions.displaySpot(dev_, xCenter_, yCenter_);
      });
      final JLabel xLabel = new JLabel("Center-X ");
      final SliderPanel xSlider = new SliderPanel();
      xSlider.setLimits(dev_.getXMinimum(), dev_.getXMinimum() + dev_.getXRange());
      final JLabel yLabel = new JLabel("Center-Y ");
      final SliderPanel ySlider = new SliderPanel();
      ySlider.setLimits(dev_.getYMinimum(), dev_.getYMinimum() + dev_.getYRange());
      try {
         xSlider.setText(NumberUtils.doubleToDisplayString(xCenter_));
         ySlider.setText(NumberUtils.doubleToDisplayString(yCenter_));
      } catch (ParseException e) {
         studio_.logs().logError(e);
      }
      xSlider.addEditActionListener((evt) -> updateX(xSlider.getText()));
      xSlider.addSliderMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            updateX(xSlider.getText());
         }
      });

      ySlider.addEditActionListener((evt) -> updateY(ySlider.getText()));
      ySlider.addSliderMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            updateY(ySlider.getText());
         }
      });

      channelComboBox_.setModel(new DefaultComboBoxModel<>(
            new String[]{"Item 1", "Item 2", "Item 3", "Item 4"}));
      channelComboBox_.addActionListener((ActionEvent evt) -> {
         final String channel = (String) channelComboBox_.getSelectedItem();
         setTargetingChannel(channel);
         settings_.putString(Terms.PTCHANNEL, channel);
      });

      shutterComboBox_.setModel(new DefaultComboBoxModel<>(
            new String[]{"Item 1", "Item 2", "Item 3", "Item 4"}));
      shutterComboBox_.addActionListener((ActionEvent evt) -> {
         final String shutter = (String) shutterComboBox_.getSelectedItem();
         setTargetingShutter(shutter);
         settings_.putString(Terms.PTSHUTTER, shutter);
      });

      delayField_.setText("0");

      checkerBoardButton_.setText("CheckerBoard");
      checkerBoardButton_.addActionListener((ActionEvent evt) -> dev_.showCheckerBoard(16, 16));

      setupTab.setLayout(new MigLayout("", "", "[40]"));

      setupTab.add(centerButton);
      setupTab.add(allPixelsButton_);
      setupTab.add(checkerBoardButton_, "wrap");

      setupTab.add(xLabel, "span 3, split");
      setupTab.add(xSlider, "wrap");
      setupTab.add(yLabel, "span 3, split");
      setupTab.add(ySlider, "wrap");

      setupTab.add(calibrateButton_);
      setupTab.add(new JLabel("Delay(ms):"), "span 2, split 2");
      setupTab.add(delayField_, "wmin 30, w 50!, wrap");

      setupTab.add(new JLabel("Phototargeting shutter:"), "span 3, split 2, w 180!");
      setupTab.add(shutterComboBox_, "wmin 130, wrap");

      setupTab.add(new JLabel("Phototargeting channel:"), "span 3, split 2, w 180!");
      setupTab.add(channelComboBox_, "wmin 130, wrap");

      mainTabbedPane.addTab("Setup", setupTab);

      pointAndShootIntervalSpinner_.setModel(new SpinnerNumberModel(500, 1, 1000000000, 1));
      pointAndShootIntervalSpinner_.setMaximumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.setMinimumSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.setPreferredSize(new Dimension(75, 20));
      pointAndShootIntervalSpinner_.addChangeListener((ChangeEvent evt) -> updateExposure());
      pointAndShootIntervalSpinner_.addVetoableChangeListener((PropertyChangeEvent evt) ->
            updateExposure());
      pointAndShootIntervalSpinner_.setValue(settings_.getDouble(Terms.EXPOSURE, 0.0));

      this.getContentPane().setLayout(new MigLayout());

      this.getContentPane().add(new JLabel("Exposure time:"));
      this.getContentPane().add(pointAndShootIntervalSpinner_, "w 75");
      this.getContentPane().add(new JLabel("ms"), "gapx 18px 18px");
      this.getContentPane().add(onButton, "gapx 80px 6px");
      this.getContentPane().add(offButton, "gapx 6px 6px, wrap");

      this.getContentPane().add(mainTabbedPane, "span 5, wrap");

      pack();
   }


   // *****************  Deprecated functions ****************** //


   /**
    * Illuminate the polygons ROIs that have been previously uploaded to phototargeter.
    *
    * @deprecated Use
    *     {@link ProjectorControlExecution#exposeRois(org.micromanager.projector.ProjectionDevice,
    *     java.lang.String, java.lang.String, Roi[]) } instead
    */
   @Deprecated
   public void runRois() {
      projectorControlExecution_
            .exposeRois(dev_, targetingChannel_, targetingShutter_, individualRois_);
   }

   // ## Attach/detach MDA

   /**
    * Attaches phototargeting ROIs to a multi-dimensional acquisition, so that they will run on a
    * particular firstFrame and, if repeat is true, thereafter again every frameRepeatInterval
    * frames.
    *
    * @param firstFrame frame number where photo targeting should start
    * @param repeat how oftem the targeting should be repeated
    * @param frameRepeatInveral Interbal (in frame numbers) between repeats
    * @param runPolygons patterns to be photo-targetted.
    * @deprecated - User ProjectorControlExecution.attachRoisToMDA instead
    */
   @Deprecated
   public void attachRoisToMDA(int firstFrame, boolean repeat,
         int frameRepeatInveral, Runnable runPolygons) {
      projectorControlExecution_.attachRoisToMDA(
            firstFrame, true, frameRepeatInveral, runPolygons);
   }

   /**
    * Sets the Channel Group to the targeting channel, if it exists.
    *
    * @return the channel group in effect before calling this function
    * @deprecated - Use ProjectorControlExecution.prepareChannel() instead
    */
   @Deprecated
   public String prepareChannel() {
      return projectorControlExecution_.prepareChannel(targetingChannel_);
   }

   /**
    * Should be called with the value returned by prepareChannel. Returns Channel Group to its
    * original settings, if needed.
    *
    * @param originalConfig Configuration to return to
    * @deprecated - Use ProjectorControlExecution.returnChannel() instead
    */
   @Deprecated
   public void returnChannel(String originalConfig) {
      projectorControlExecution_.returnChannel(originalConfig);
   }

   /**
    * Opens the targeting shutter, if it has been specified.
    *
    * @return true if it was already open
    * @deprecated - Use ProjectorControlExecution.prepareShutter() instead
    */
   @Deprecated
   public boolean prepareShutter() {
      return projectorControlExecution_.prepareShutter(targetingShutter_);
   }

   /**
    * Closes a targeting shutter if it exists and if it was originally closed. Should be called with
    * the value returned by prepareShutter.
    *
    * @param originallyOpen - whether or not the shutter was originally open
    * @deprecated - Use ProjectorControlExecution.returnShutter() instead
    */
   @Deprecated
   public void returnShutter(boolean originallyOpen) {
      projectorControlExecution_.returnShutter(targetingShutter_, originallyOpen);
   }


}