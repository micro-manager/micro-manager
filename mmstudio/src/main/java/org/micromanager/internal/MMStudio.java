///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//AUTHOR:        Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
//               Modifications by Arthur Edelstein, Nico Stuurman, Henry Pinkard
//COPYRIGHT:     University of California, San Francisco, 2006-2013
//               100X Imaging Inc, www.100ximaging.com, 2008
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//CVS:          $Id$
//
package org.micromanager.internal;

import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.acquisition.internal.TaggedImageQueue;
import bsh.EvalError;
import bsh.Interpreter;

import com.google.common.eventbus.Subscribe;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.Toolbar;

import java.awt.Color;
import java.awt.Component;
import java.awt.geom.AffineTransform;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import org.json.JSONException;

import org.micromanager.Album;
import org.micromanager.AutofocusPlugin;
import org.micromanager.CompatibilityInterface;
import org.micromanager.data.DataManager;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.EventManager;
import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.IAcquisitionEngine2010;
import org.micromanager.LogManager;
import org.micromanager.MMListenerInterface;
import org.micromanager.internal.pluginmanagement.DefaultPluginManager;
import org.micromanager.PluginManager;
import org.micromanager.PositionList;
import org.micromanager.ScriptController;
import org.micromanager.Studio;
import org.micromanager.SequenceSettings;
import org.micromanager.UserProfile;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.internal.conf2.MMConfigFileException;
import org.micromanager.internal.conf2.MicroscopeModel;

import org.micromanager.data.internal.DefaultDataManager;

import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.dialogs.CalibrationListDlg;
import org.micromanager.internal.dialogs.IntroDlg;
import org.micromanager.internal.dialogs.OptionsDlg;
import org.micromanager.internal.dialogs.RegistrationDlg;
import org.micromanager.internal.dialogs.IJVersionCheckDlg;

import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.display.internal.DefaultDisplayManager;

import org.micromanager.internal.logging.LogFileManager;
import org.micromanager.internal.menus.FileMenu;
import org.micromanager.internal.menus.HelpMenu;
import org.micromanager.internal.menus.ToolsMenu;
import org.micromanager.internal.navigation.ClickToMoveManager;
import org.micromanager.internal.navigation.XYZKeyListener;
import org.micromanager.internal.navigation.ZWheelListener;
import org.micromanager.internal.pipelineinterface.PipelineFrame;
import org.micromanager.internal.positionlist.PositionListDlg;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.AutofocusManager;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.FileDialogs.FileType;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.internal.utils.UIMonitor;
import org.micromanager.internal.utils.WaitDialog;





/*
 * Implements the Studio (i.e. primary API) and does various other
 * tasks that should probably be refactored out at some point.
 */
public class MMStudio implements Studio, CompatibilityInterface {

   private static final long serialVersionUID = 3556500289598574541L;
   private static final String OPEN_ACQ_DIR = "openDataDir";
   private static final String SCRIPT_CORE_OBJECT = "mmc";
   private static final String AUTOFOCUS_DEVICE = "autofocus_device";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;
   // Note that this property is set by one of the launcher scripts.
   private static final String SHOULD_DELETE_OLD_CORE_LOGS = "whether or not to delete old MMCore log files";
   private static final String CORE_LOG_LIFETIME_DAYS = "how many days to keep MMCore log files, before they get deleted";
   private static final String CIRCULAR_BUFFER_SIZE = "size, in megabytes of the circular buffer used to temporarily store images before they are written to disk";
   private static final String AFFINE_TRANSFORM = "affine transform for mapping camera coordinates to stage coordinates for a specific pixel size config: ";

   // cfg file saving
   private static final String CFGFILE_ENTRY_BASE = "CFGFileEntry";
   // GUI components
   private boolean amRunningAsPlugin_;
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;
   private DataManager dataManager_;
   private DisplayManager displayManager_;
   private DefaultPluginManager pluginManager_;
   private final SnapLiveManager snapLiveManager_;
   private final ToolsMenu toolsMenu_;

   private List<Component> MMFrames_
           = Collections.synchronizedList(new ArrayList<Component>());
   private AutofocusManager afMgr_;
   private String sysConfigFile_;
   private String startupScriptFile_;

   // MMcore
   private CMMCore core_;
   private AcquisitionWrapperEngine engine_;
   private PositionList posList_;
   private PositionListDlg posListDlg_;
   private String openAcqDirectory_ = "";
   private boolean isProgramRunning_;
   private boolean configChanged_ = false;
   private boolean isClickToMoveEnabled_ = false;
   private StrVector shutters_ = null;

   private ScriptPanel scriptPanel_;
   private PipelineFrame pipelineFrame_;
   private org.micromanager.internal.utils.HotKeys hotKeys_;
   private ZWheelListener zWheelListener_;
   private XYZKeyListener xyzKeyListener_;
   public static final FileType MM_CONFIG_FILE
            = new FileType("MM_CONFIG_FILE",
                           "Micro-Manager Config File",
                           "./MyScope.cfg",
                           true, "cfg");

   // Our instance
   private static MMStudio studio_;
   // Our primary window.
   private static MainFrame frame_;
   // Callback
   private CoreEventCallback coreCallback_;
   // Lock invoked while shutting down
   private final Object shutdownLock_ = new Object();

   private final JMenuBar menuBar_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;
   public static final FileType MM_DATA_SET 
           = new FileType("MM_DATA_SET",
                 "Micro-Manager Image Location",
                 System.getProperty("user.home") + "/Untitled",
                 false, (String[]) null);
   private Thread acquisitionEngine2010LoadingThread_ = null;
   private Class<?> acquisitionEngine2010Class_ = null;
   private IAcquisitionEngine2010 acquisitionEngine2010_ = null;
   private final StaticInfo staticInfo_;
   
   
   /**
    * Main procedure for stand alone operation.
    * @param args
    */
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         MMStudio mmStudio = new MMStudio(false);
      } catch (ClassNotFoundException e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      } catch (IllegalAccessException e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      } catch (InstantiationException e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      } catch (UnsupportedLookAndFeelException e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      }
   }

   /**
    * MMStudio constructor
    * @param shouldRunAsPlugin Indicates if we're running from "within" ImageJ,
    *        which governs our behavior when we are closed.
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MMStudio(boolean shouldRunAsPlugin) {
      org.micromanager.internal.diagnostics.ThreadExceptionLogger.setUp();

      DefaultEventManager.getInstance().registerForEvents(this);

      prepAcquisitionEngine();

      UIMonitor.enable(OptionsDlg.getIsDebugLogEnabled());
      
      studio_ = this;

      amRunningAsPlugin_ = shouldRunAsPlugin;
      isProgramRunning_ = true;

      sysConfigFile_ = IntroDlg.getMostRecentlyUsedConfig();

      if (ScriptPanel.getStartupScript().length() > 0) {
         startupScriptFile_ = new File(ScriptPanel.getStartupScript()).getAbsolutePath();
      } else {
         startupScriptFile_ = "";
      }

      setBackgroundStyle(DaytimeNighttime.getBackgroundMode());

      showRegistrationDialogMaybe();

      try {
         core_ = new CMMCore();
      } catch(UnsatisfiedLinkError ex) {
         ReportingUtils.showError(ex, 
               "Failed to load the MMCoreJ_wrap native library");
      }

      core_.enableStderrLog(true);

      // The ClickToMoveManager manages itself; don't need to retain a
      // reference to it.
      new ClickToMoveManager(this, core_);
      snapLiveManager_ = new SnapLiveManager(this, core_);

      frame_ = new MainFrame(this, core_, snapLiveManager_);
      ReportingUtils.SetContainingFrame(frame_);

      // move ImageJ window to place where it last was if possible
      // or else (150,150) if not
      if (IJ.getInstance() != null) {
         Point ijWinLoc = IJ.getInstance().getLocation();
         if (GUIUtils.getGraphicsConfigurationContaining(ijWinLoc.x, ijWinLoc.y) == null) {
            // only reach this code if the pref coordinates are off screen
            IJ.getInstance().setLocation(150, 150);
         }
      }

      staticInfo_ = new StaticInfo(core_, frame_);

      openAcqDirectory_ = profile().getString(MMStudio.class,
            OPEN_ACQ_DIR, "");

      ToolTipManager ttManager = ToolTipManager.sharedInstance();
      ttManager.setDismissDelay(TOOLTIP_DISPLAY_DURATION_MILLISECONDS);
      ttManager.setInitialDelay(TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS);
      
      
      menuBar_ = new JMenuBar();

      frame_.setJMenuBar(menuBar_);

      FileMenu fileMenu = new FileMenu(studio_);
      fileMenu.initializeFileMenu(menuBar_);

      toolsMenu_ = new ToolsMenu(studio_, core_);
      toolsMenu_.initializeToolsMenu(menuBar_);

      HelpMenu helpMenu = new HelpMenu(studio_, core_);

      initializationSequence();
           
      helpMenu.initializeHelpMenu(menuBar_);
   }

   /**
    * Initialize the program.
    */
   private void initializationSequence() {
      if (core_ == null) {
         // Give up.
         return;
      }
      // Initialize hardware.
      String logFileName = LogFileManager.makeLogFileNameForCurrentSession();
      new File(logFileName).getParentFile().mkdirs();
      try {
         core_.setPrimaryLogFile(logFileName);
      }
      catch (Exception ignore) {
         // The Core will have logged the error to stderr, so do nothing.
      }
      core_.enableDebugLog(OptionsDlg.getIsDebugLogEnabled());

      if (getShouldDeleteOldCoreLogs()) {
         LogFileManager.deleteLogFilesDaysOld(
               getCoreLogLifetimeDays(), logFileName);
      }

      ReportingUtils.setCore(core_);
      logStartupProperties();
              
      engine_ = new AcquisitionWrapperEngine();

      // This entity is a class property to avoid garbage collection.
      coreCallback_ = new CoreEventCallback(core_, engine_);

      try {
         core_.setCircularBufferMemoryFootprint(getCircularBufferSize());
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }

      engine_.setParentGUI(studio_);

      dataManager_ = new DefaultDataManager();
      displayManager_ = new DefaultDisplayManager(this);

      afMgr_ = new AutofocusManager(studio_);
      pluginManager_ = new DefaultPluginManager(studio_, menuBar_);

      frame_.paintToFront();
      
      engine_.setCore(core_, afMgr_);
      posList_ = new PositionList();
      engine_.setPositionList(posList_);
      // load (but do no show) the scriptPanel
      createScriptPanel();
      // Ditto with the image pipeline panel.
      createPipelineFrame();

      // Create an instance of HotKeys so that they can be read in from prefs
      hotKeys_ = new org.micromanager.internal.utils.HotKeys();
      hotKeys_.loadSettings();

      if (IntroDlg.getShouldAskForConfigFile() ||
            !DefaultUserProfile.getShouldAlwaysUseDefaultProfile()) {
         // Ask the user for a configuration file and/or user profile.
         IntroDlg introDlg = new IntroDlg(MMVersion.VERSION_STRING);
         introDlg.setConfigFile(sysConfigFile_);
         introDlg.setVisible(true);
         introDlg.toFront();
         if (!introDlg.okChosen()) {
            // User aborted; close the program down.
            closeSequence(false);
            return;
         }
         sysConfigFile_ = introDlg.getConfigFile();
      }

      IJVersionCheckDlg.execute();

      // before loading the system configuration, we need to wait 
      // until the plugins are loaded
      final WaitDialog waitDlg = new WaitDialog(
              "Loading plugins, please wait...");

      waitDlg.setAlwaysOnTop(true);
      waitDlg.showDialog();
      try {
         pluginManager_.waitForInitialization(15000);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex,
               "Interrupted while waiting for plugin loading thread");
      }
      if (!pluginManager_.isInitializationComplete()) {
         ReportingUtils.logMessage("Warning: Plugin loading did not finish within 15 seconds; continuing anyway");
      }
      else {
         ReportingUtils.logMessage("Finished waiting for plugins to load");
      }
      waitDlg.closeDialog();
      
      // if an error occurred during config loading, do not display more 
      // errors than needed
      if (!loadSystemConfiguration()) {
         ReportingUtils.showErrorOn(false);
      }

      // Done with main startup logic; show the main frame now.
      // Reset its position as the current profile may have a different default
      // as the one that was active when the frame was created.
      frame_.resetPosition();
      frame_.setVisible(true);
      executeStartupScript();

      // Create Multi-D window here but do not show it.
      // This window needs to be created in order to properly set the 
      // "ChannelGroup" based on the Multi-D parameters
      acqControlWin_ = new AcqControlDlg(engine_, studio_);

      frame_.initializeConfigPad();

      String afDevice = profile().getString(MMStudio.class, AUTOFOCUS_DEVICE, "");
      if (afMgr_.hasDevice(afDevice)) {
         try {
            afMgr_.selectDevice(afDevice);
         } catch (MMException ex) {
            // this error should never happen
            ReportingUtils.showError(ex);
         }
      }

      zWheelListener_ = new ZWheelListener(core_, studio_);
      snapLiveManager_.addLiveModeListener(zWheelListener_);
      xyzKeyListener_ = new XYZKeyListener(core_, studio_);
      snapLiveManager_.addLiveModeListener(xyzKeyListener_);

      // switch error reporting back on
      ReportingUtils.showErrorOn(true);

      org.micromanager.internal.diagnostics.gui.ProblemReportController.startIfInterruptedOnExit();
   }

   public void showPipelineFrame() {
      pipelineFrame_.setVisible(true);
   }

   public void showScriptPanel() {
      scriptPanel_.setVisible(true);
   }

   private void handleError(String message) {
      live().setLiveMode(false);
      JOptionPane.showMessageDialog(frame_, message);
      core_.logMessage(message);
   }

   private void showRegistrationDialogMaybe() {
      // show registration dialog if not already registered
      // first check user preferences (for legacy compatibility reasons)
      boolean shouldShow = !(RegistrationDlg.getHaveRegistered() ||
            RegistrationDlg.getShouldNeverRegister());

      if (shouldShow) {
         // prompt for registration info
         RegistrationDlg dlg = new RegistrationDlg();
         dlg.setVisible(true);
      }
   }

   /**
    * Spawn a new thread to load the acquisition engine jar, because this
    * takes significant time (or so Mark claims).
    */
   private void prepAcquisitionEngine() {
      acquisitionEngine2010LoadingThread_ = new Thread("Pipeline Class loading thread") {
         @Override
         public void run() {
            try {
               acquisitionEngine2010Class_  = Class.forName("org.micromanager.internal.AcquisitionEngine2010");
            } catch (ClassNotFoundException ex) {
               ReportingUtils.logError(ex);
               acquisitionEngine2010Class_ = null;
            }
         }
      };
      acquisitionEngine2010LoadingThread_.setContextClassLoader(getClass().getClassLoader());
      acquisitionEngine2010LoadingThread_.start();
   }

   public void toggleAutoShutter() {
      try {
         if (frame_.getAutoShutterChecked()) {
            core_.setAutoShutter(true);
            core_.setShutterOpen(false);
            frame_.toggleAutoShutter(false);
         } else {
            core_.setAutoShutter(false);
            core_.setShutterOpen(false);
            frame_.toggleAutoShutter(true);
         }
      } catch (Exception exc) {
         ReportingUtils.logError(exc);
      }

   }

   public void setExposure(double exposureTime) {
      // This is synchronized with the shutdown lock primarily so that
      // the exposure-time field in MainFrame won't cause issues when it loses
      // focus during shutdown.
      synchronized(shutdownLock_) {
         if (core_ == null) {
            // Just give up.
            return;
         }
         live().setSuspended(true);
         try {
            core_.setExposure(exposureTime);
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Failed to set core exposure time.");
         }
         live().setSuspended(false);

         // Display the new exposure time
         double exposure;
         try {
            exposure = core_.getExposure();
            frame_.setDisplayedExposureTime(exposure);
            
            // update current channel in MDA window with this exposure
            String channelGroup = core_.getChannelGroup();
            String channel = core_.getCurrentConfigFromCache(channelGroup);
            if (!channel.equals("") ) {
               AcqControlDlg.setChannelExposure(channelGroup, channel,
                     exposure);
               if (AcqControlDlg.getShouldSyncExposure()) {
                  acqControlWin_.setChannelExposureTime(channelGroup,
                        channel, exposure);
               }
            }
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Couldn't set exposure time.");
         }
      } // End synchronization check
   }

   @Override
   public boolean getHideMDADisplayOption() {
      return AcqControlDlg.getShouldHideMDADisplay();
   }

   @Override
   public Rectangle getROI() throws MMScriptException {
      // ROI values are given as x,y,w,h in individual one-member arrays (pointers in C++):
      int[][] a = new int[4][1];
      try {
         core_.getROI(a[0], a[1], a[2], a[3]);
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
      // Return as a single array with x,y,w,h:
      return new Rectangle(a[0][0], a[1][0], a[2][0], a[3][0]);
   }

   public void setROI() {
      ImagePlus curImage = WindowManager.getCurrentImage();
      if (curImage == null) {
         return;
      }

      Roi roi = curImage.getRoi();
      
      try {
         if (roi == null) {
            // if there is no ROI, create one
            Rectangle r = curImage.getProcessor().getRoi();
            int iWidth = r.width;
            int iHeight = r.height;
            int iXROI = r.x;
            int iYROI = r.y;
            if (roi == null) {
               iWidth /= 2;
               iHeight /= 2;
               iXROI += iWidth / 2;
               iYROI += iHeight / 2;
            }

            curImage.setRoi(iXROI, iYROI, iWidth, iHeight);
            roi = curImage.getRoi();
         }

         if (roi.getType() != Roi.RECTANGLE) {
            handleError("ROI must be a rectangle.\nUse the ImageJ rectangle tool to draw the ROI.");
            return;
         }

         Rectangle r = roi.getBounds();

         // If the image has ROI info attached to it, correct for the offsets.
         // Otherwise, assume the image was taken with the current camera ROI
         // (which is a horrendously buggy way to do things, but that was the
         // old behavior and I'm leaving it in case there are cases where it is
         // necessary).
         Rectangle originalROI = null;

         DisplayWindow curWindow = displays().getCurrentWindow();
         if (curWindow != null) {
            List<Image> images = curWindow.getDisplayedImages();
            // Just take the first one.
            originalROI = images.get(0).getMetadata().getROI();
         }

         if (originalROI == null) {
            originalROI = getROI();
         }

         r.x += originalROI.x;
         r.y += originalROI.y;

         // Stop (and restart) live mode if it is running
         setROI(r);

      } catch (MMScriptException e) {
         ReportingUtils.showError(e);
      }
   }

   public void clearROI() {
      try {
         boolean liveRunning = false;
         live().setSuspended(true);
         core_.clearROI();
         staticInfo_.refreshValues();
         live().setSuspended(false);

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   @Override
   public CMMCore core() {
      return core_;
   }

   /**
    * Returns instance of the core uManager object;
    */
   @Override
   public CMMCore getCMMCore() {
      return core_;
   }

   /**
    * Returns singleton instance of MMStudio
    * @return singleton instance of MMStudio
    */
   public static MMStudio getInstance() {
      return studio_;
   }

   /**
    * Returns singleton instance of MainFrame. You should ideally not need
    * to use this function.
    * @return singleton instance of the mainFrame
    */
   public static MainFrame getFrame() {
      return frame_;
   }

   @Override
   public void saveConfigPresets() {
      MicroscopeModel model = new MicroscopeModel();
      try {
         model.loadFromFile(sysConfigFile_);
         model.createSetupConfigsFromHardware(core_);
         model.createResolutionsFromHardware(core_);
         File f = FileDialogs.save(frame_,
               "Save the configuration file", MM_CONFIG_FILE);
         if (f != null) {
            model.saveToFile(f.getAbsolutePath());
            sysConfigFile_ = f.getAbsolutePath();
            configChanged_ = false;
            frame_.setConfigSaveButtonStatus(configChanged_);
            frame_.updateTitle(sysConfigFile_);
         }
      } catch (MMConfigFileException e) {
         ReportingUtils.showError(e);
      }
   }

   /**
    * Get currently used configuration file
    * @return - Path to currently used configuration file
    */
   public String getSysConfigFile() {
      return sysConfigFile_;
   }

   public void setSysConfigFile(String newFile) {
      sysConfigFile_ = newFile;
      configChanged_ = false;
      frame_.setConfigSaveButtonStatus(configChanged_);
      loadSystemConfiguration();
   }

   public void setAcqDirectory(String dir) {
      openAcqDirectory_ = dir;
   }

   protected void changeBinning() {
      try {
         String mode = frame_.getBinMode();
         if (!isCameraAvailable() || mode == null) {
            // No valid option.
            return;
         }
         if (core_.getProperty(StaticInfo.cameraLabel_,
                  MMCoreJ.getG_Keyword_Binning()).equals(mode)) {
            // No change in binning mode.
            return;
         }

         live().setSuspended(true);
         core_.setProperty(StaticInfo.cameraLabel_, MMCoreJ.getG_Keyword_Binning(), mode);
         staticInfo_.refreshValues();
         live().setSuspended(false);

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   public void createPropertyEditor() {
      if (propertyBrowser_ != null) {
         propertyBrowser_.dispose();
      }

      propertyBrowser_ = new PropertyEditor();
      propertyBrowser_.setGui(studio_);
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      propertyBrowser_.setCore(core_);
   }

   public void createCalibrationListDlg() {
      if (calibrationListDlg_ != null) {
         calibrationListDlg_.dispose();
      }

      calibrationListDlg_ = new CalibrationListDlg(core_);
      calibrationListDlg_.setVisible(true);
      calibrationListDlg_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      calibrationListDlg_.setParentGUI(studio_);
   }

   public CalibrationListDlg getCalibrationListDlg() {
      if (calibrationListDlg_ == null) {
         createCalibrationListDlg();
      }
      return calibrationListDlg_;
   }

   private void createScriptPanel() {
      scriptPanel_ = new ScriptPanel(core_, studio_);
      scriptPanel_.insertScriptingObject(SCRIPT_CORE_OBJECT, core_);
      scriptPanel_.setParentGUI(studio_);
   }

   private void createPipelineFrame() {
      if (pipelineFrame_ == null) {
         pipelineFrame_ = new PipelineFrame(studio_, engine_);
      }
   }

   public PipelineFrame getPipelineFrame() {
      return pipelineFrame_;
   }

   public void updateXYPos(double x, double y) {
      staticInfo_.updateXYPos(x, y);
   }
   public void updateXYPosRelative(double x, double y) {
      staticInfo_.updateXYPosRelative(x, y);
   }

   public void updateZPos(double z) {
      staticInfo_.updateZPos(z);
   }
   public void updateZPosRelative(double z) {
      staticInfo_.updateZPosRelative(z);
   }

   public void updateXYStagePosition() {
      staticInfo_.getNewXYStagePosition();
   }

   public void toggleShutter() {
      frame_.toggleShutter();
   }

   public void updateCenterAndDragListener(boolean isEnabled) {
      isClickToMoveEnabled_ = isEnabled;
      if (isEnabled) {
         IJ.setTool(Toolbar.HAND);
         toolsMenu_.setMouseMovesStage(isEnabled);
      }
      events().post(new MouseMovesStageEvent(isEnabled));
   }

   public boolean getIsClickToMoveEnabled() {
      return isClickToMoveEnabled_;
   }
   
   // Ensure that the "XY list..." dialog exists.
   private void checkPosListDlg() {
      if (posListDlg_ == null) {
         posListDlg_ = new PositionListDlg(core_, studio_, posList_, 
                 acqControlWin_);
         posListDlg_.addListeners();
      }
   }
   

   // //////////////////////////////////////////////////////////////////////////
   // public interface available for scripting access
   // //////////////////////////////////////////////////////////////////////////

   private boolean isCameraAvailable() {
      return StaticInfo.cameraLabel_.length() > 0;
   }

   /**
    * Part of Studio API
    * Opens the XYPositionList when it is not opened
    * Adds the current position to the list (same as pressing the "Mark"
    * button)
    */
   @Override
   public void markCurrentPosition() {
      if (posListDlg_ == null) {
         showXYPositionList();
      }
      if (posListDlg_ != null) {
         posListDlg_.markPosition();
      }
   }

   @Override
   public boolean isAcquisitionRunning() {
      if (engine_ == null)
         return false;
      return engine_.isAcquisitionRunning();
   }

   @Override
   public boolean versionLessThan(String version) throws MMScriptException {
      try {
         String[] v = MMVersion.VERSION_STRING.split(" ", 2);
         String[] m = v[0].split("\\.", 3);
         String[] v2 = version.split(" ", 2);
         String[] m2 = v2[0].split("\\.", 3);
         for (int i=0; i < 3; i++) {
            if (Integer.parseInt(m[i]) < Integer.parseInt(m2[i])) {
               ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
               return true;
            }
            if (Integer.parseInt(m[i]) > Integer.parseInt(m2[i])) {
               return false;
            }
         }
         if (v2.length < 2 || v2[1].equals("") )
            return false;
         if (v.length < 2 ) {
            ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
            return true;
         }
         if (Integer.parseInt(v[1]) < Integer.parseInt(v2[1])) {
            ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
            return false;
         }
         return true;

      } catch (NumberFormatException ex) {
         throw new MMScriptException ("Format of version String should be \"a.b.c\"");
      }
   } 

   private boolean isCurrentImageFormatSupported() {
      long channels = core_.getNumberOfComponents();
      long bpp = core_.getBytesPerPixel();

      if (channels > 1 && channels != 4 && bpp != 1) {
         handleError("Unsupported image format.");
      } else {
         return true;
      }
      return false;
   }

   private void configureBinningCombo() throws Exception {
      if (StaticInfo.cameraLabel_.length() > 0) {
         frame_.configureBinningComboForCamera(StaticInfo.cameraLabel_);
      }
   }

   public void initializeGUI() {
      try {
         staticInfo_.refreshValues();
         engine_.setZStageDevice(StaticInfo.zStageLabel_);  
  
         configureBinningCombo();

         // active shutter combo
         try {
            shutters_ = core_.getLoadedDevicesOfType(DeviceType.ShutterDevice);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }

         if (shutters_ != null) {
            String items[] = new String[(int) shutters_.size()];
            for (int i = 0; i < shutters_.size(); i++) {
               items[i] = shutters_.get(i);
            }
            frame_.initializeShutterGUI(items);
         }

         // Rebuild stage list in XY PositinList
         if (posListDlg_ != null) {
            posListDlg_.rebuildAxisList();
         }

         frame_.updateAutofocusButtons(afMgr_.getDevice() != null);
         updateGUI(true);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      updateGUI(true);
   }

   @Subscribe
   public void onExposureChanged(ExposureChangedEvent event) {
      if (event.getCameraName().equals(StaticInfo.cameraLabel_)) {
         frame_.setDisplayedExposureTime(event.getNewExposureTime());
      }
   }

   public void updateGUI(boolean updateConfigPadStructure) {
      updateGUI(updateConfigPadStructure, false);
   }

   public void updateGUI(boolean updateConfigPadStructure, boolean fromCache) {
      ReportingUtils.logMessage("Updating GUI; config pad = " +
            updateConfigPadStructure + "; from cache = " + fromCache);
      try {
         staticInfo_.refreshValues();
         afMgr_.refresh();

         // camera settings
         if (isCameraAvailable()) {
            double exp = core_.getExposure();
            frame_.setDisplayedExposureTime(exp);
            configureBinningCombo();
            String binSize;
            if (fromCache) {
               binSize = core_.getPropertyFromCache(StaticInfo.cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            } else {
               binSize = core_.getProperty(StaticInfo.cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            }
            frame_.setBinSize(binSize);
         }



         // active shutter combo
         if (shutters_ != null) {
            String activeShutter = core_.getShutterDevice();
            frame_.setShutterComboSelection(
                  activeShutter != null ? activeShutter : "");
         }
         
         // Set AutoShutterCheckBox
         frame_.setAutoShutterSelected(core_.getAutoShutter());
          
         // Set Shutter button
         frame_.setShutterButton(core_.getShutterOpen());
            
         if (live().getIsLiveModeOn()) {
            frame_.setToggleShutterButtonEnabled(!core_.getAutoShutter());
         }

         ConfigGroupPad pad = frame_.getConfigPad();
         // state devices
         if (updateConfigPadStructure && (pad != null)) {
            pad.refreshStructure(fromCache);
            // Needed to update read-only properties.  May slow things down...
            if (!fromCache) {
               core_.updateSystemStateCache();
            }
         }

         // update Channel menus in Multi-dimensional acquisition dialog
         updateChannelCombos();

         // update list of pixel sizes in pixel size configuration window
         if (calibrationListDlg_ != null) {
            calibrationListDlg_.refreshCalibrations();
         }
         if (propertyBrowser_ != null) {
            propertyBrowser_.refresh();
         }

         ReportingUtils.logMessage("Finished updating GUI");
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      frame_.updateTitle(sysConfigFile_);
   }

   // Cancel acquisitions and stop live mode.
   public void stopAllActivity() {
      if (acquisitionEngine2010_ != null) {
         acquisitionEngine2010_.stop();
      }
      live().setLiveMode(false);
   }

   /**
    * Cleans up resources while shutting down 
    * 
    * @param calledByImageJ
    * @return Whether or not cleanup was successful. Shutdown should abort
    *         on failure.
    */
   private boolean cleanupOnClose(boolean calledByImageJ) {
      // Save config presets if they were changed.
      if (configChanged_) {
         Object[] options = {"Yes", "No"};
         int n = JOptionPane.showOptionDialog(null,
               "Save Changed Configuration?", "Micro-Manager",
               JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
               null, options, options[0]);
         if (n == JOptionPane.YES_OPTION) {
            saveConfigPresets();
            // if the configChanged_ flag did not become false, the user 
            // must have cancelled the configuration saving and we should cancel
            // quitting as well
            if (configChanged_) {
               return false;
            }
         }
      }
      live().setLiveMode(false);

      // check needed to avoid deadlock
      if (!calledByImageJ) {
         if (!WindowManager.closeAllWindows()) {
            core_.logMessage("Failed to close some windows");
         }
      }
  
      if (posListDlg_ != null) {
         posListDlg_.getToolkit().getSystemEventQueue().postEvent(
                 new WindowEvent(posListDlg_, WindowEvent.WINDOW_CLOSING));
         posListDlg_.dispose();
      }
      
      if (scriptPanel_ != null) {
         scriptPanel_.closePanel();
      }

      if (pipelineFrame_ != null) {
         pipelineFrame_.dispose();
      }

      if (propertyBrowser_ != null) {
         propertyBrowser_.getToolkit().getSystemEventQueue().postEvent(
                 new WindowEvent(propertyBrowser_, WindowEvent.WINDOW_CLOSING));
         propertyBrowser_.dispose();
      }

      if (acqControlWin_ != null) {
         acqControlWin_.close();
      }

      if (afMgr_ != null) {
         afMgr_.closeOptionsDialog();
      }
      
      if (engine_ != null) {
         engine_.shutdown();
      }

      synchronized (shutdownLock_) {
         try {
            if (core_ != null) {
               ReportingUtils.setCore(null);
               core_.delete();
               core_ = null;
            }
         } catch (Exception err) {
            ReportingUtils.showError(err);
         }
      }
      return true;
   }

   private void saveSettings() {
      frame_.savePrefs();
      
      profile().setString(MMStudio.class, OPEN_ACQ_DIR, openAcqDirectory_);

      // NOTE: do not save auto shutter state
      if (afMgr_ != null && afMgr_.getDevice() != null) {
         profile().setString(MMStudio.class,
               AUTOFOCUS_DEVICE, afMgr_.getDevice().getName());
      }
   }

   public synchronized boolean closeSequence(boolean calledByImageJ) {
      if (!getIsProgramRunning()) {
         if (core_ != null) {
            core_.logMessage("MMStudio::closeSequence called while isProgramRunning_ is false");
         }
         return true;
      }
      
      if (engine_ != null && engine_.isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(frame_,
               "Acquisition in progress. Are you sure you want to exit and discard all data?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION,
               JOptionPane.INFORMATION_MESSAGE);

         if (result == JOptionPane.NO_OPTION) {
            return false;
         }
      }

      stopAllActivity();

      if (!displays().closeAllDisplayWindows(true)) {
         // The user canceled out of closing a display window.
         return false;
      }

      if (!cleanupOnClose(calledByImageJ)) {
         return false;
      }

      isProgramRunning_ = false;

      saveSettings();
      try {
         frame_.getConfigPad().saveSettings();
         hotKeys_.saveSettings();
      } catch (NullPointerException e) {
         if (core_ != null)
            ReportingUtils.logError(e);
      }
      try {
         profile().syncToDisk();
      }
      catch (IOException e) {
         if (core_ != null) {
            ReportingUtils.logError(e);
         }
      }
      if (OptionsDlg.getShouldCloseOnExit()) {
         if (!amRunningAsPlugin_) {
            System.exit(0);
         } else {
            ImageJ ij = IJ.getInstance();
            if (ij != null) {
               ij.quit();
            }
         }
      } else {
         frame_.dispose();
      }
      
      return true;
   }

   public boolean getIsProgramRunning() {
      return isProgramRunning_;
   }

   /**
    * Executes the beanShell script.
    */
   private void executeStartupScript() {
      // execute startup script
      File f = new File(startupScriptFile_);

      if (startupScriptFile_.length() > 0 && f.exists()) {
         WaitDialog waitDlg = new WaitDialog(
               "Executing startup script, please wait...");
         waitDlg.showDialog();
         Interpreter interp = new Interpreter();
         try {
            interp.set(SCRIPT_CORE_OBJECT, core_);

            // read text file and evaluate
            interp.eval(TextUtils.readTextFile(startupScriptFile_));
         } catch (IOException exc) {
            ReportingUtils.logError(exc, "Unable to read the startup script (" + startupScriptFile_ + ").");
         } catch (EvalError exc) {
            ReportingUtils.logError(exc);
         } finally {
            waitDlg.closeDialog();
         }
      } else {
         if (startupScriptFile_.length() > 0)
            ReportingUtils.logMessage("Startup script file ("+startupScriptFile_+") not present.");
      }
   }

   /**
    * Loads system configuration from the cfg file.
    * @return true when successful
    */
   public boolean loadSystemConfiguration() {
      boolean result = true;

      final WaitDialog waitDlg = new WaitDialog(
              "Loading system configuration, please wait...");

      waitDlg.setAlwaysOnTop(true);
      waitDlg.showDialog();
      frame_.setEnabled(false);

      IntroDlg.addRecentlyUsedConfig(sysConfigFile_);

      try {
         if (sysConfigFile_.length() > 0) {
            GUIUtils.preventDisplayAdapterChangeExceptions();
            core_.waitForSystem();
            coreCallback_.setIgnoring(true);
            core_.loadSystemConfiguration(sysConfigFile_);
            coreCallback_.setIgnoring(false);
            GUIUtils.preventDisplayAdapterChangeExceptions();
         }
      } catch (final Exception err) {
         GUIUtils.preventDisplayAdapterChangeExceptions();

         waitDlg.closeDialog(); // Prevent from obscuring error alert
         ReportingUtils.showError(err,
               "Failed to load hardware configuation",
               null);
         result = false;
      } finally {
         waitDlg.closeDialog();
      }

      frame_.setEnabled(true);
      initializeGUI();

      toolsMenu_.updateSwitchConfigurationMenu();

      FileDialogs.storePath(MM_CONFIG_FILE, new File(sysConfigFile_));

      return result;
   }

   public void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(engine_, studio_);
         }
         if (acqControlWin_.isActive()) {
            acqControlWin_.setTopPosition();
         }

         acqControlWin_.setVisible(true);
         
         acqControlWin_.repaint();

      } catch (Exception exc) {
         ReportingUtils.showError(exc,
               "\nAcquistion window failed to open due to invalid or corrupted settings.\n"
               + "Try resetting registry settings to factory defaults (Menu Tools|Options).");
      }
   }

   public void updateChannelCombos() {
      if (acqControlWin_ != null) {
         acqControlWin_.updateChannelAndGroupCombo();
      }
   }
     
   public void autofocusNow() {
      if (afMgr_.getDevice() != null) {
         new Thread() {

            @Override
            public void run() {
               try {
                  live().setSuspended(true);
                  afMgr_.getDevice().fullFocus();
                  live().setSuspended(false);
               } catch (MMException ex) {
                  ReportingUtils.logError(ex);
               }
            }
         }.start();
      }
   }
   
   private void testForAbortRequests() throws MMScriptException {
      if (scriptPanel_ != null) {
         if (scriptPanel_.stopRequestPending()) {
            throw new MMScriptException("Script interrupted by the user!");
         }
      }
   }

      
   
   // //////////////////////////////////////////////////////////////////////////
   // Script interface
   // //////////////////////////////////////////////////////////////////////////

   @Override
   public String getVersion() {
      return MMVersion.VERSION_STRING;
   }
   
   /**
    * Inserts version info for various components in the Corelog
    */
   @Override
   public void logStartupProperties() {
      core_.logMessage("User: " + System.getProperty("user.name"));
      String hostname;
      try {
         hostname = java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (java.net.UnknownHostException e) {
         hostname = "unknown";
      }
      core_.logMessage("Host: " + hostname);
      core_.logMessage("MM Studio version: " + getVersion());
      core_.logMessage(core_.getVersionInfo());
      core_.logMessage(core_.getAPIVersionInfo());
      core_.logMessage("Operating System: " + System.getProperty("os.name") +
              " (" + System.getProperty("os.arch") + ") " + System.getProperty("os.version"));
      core_.logMessage("JVM: " + System.getProperty("java.vm.name") +
              ", version " + System.getProperty("java.version") + ", " +
              System.getProperty("sun.arch.data.model") + "-bit");
   }
   
   @Override
   public void makeActive() {
      frame_.toFront();
   }
   
   /**
    * Opens a dialog to record stage positions
    */
   @Override
   public void showXYPositionList() {
      checkPosListDlg();
      posListDlg_.setVisible(true);
   }

   
   @Override
   public void setConfigChanged(boolean status) {
      configChanged_ = status;
      frame_.setConfigSaveButtonStatus(configChanged_);
   }

   public boolean getIsConfigChanged() {
      return configChanged_;
   }

    /**
    * Returns exposure time for the desired preset in the given channelgroup
    * Acquires its info from the preferences
    * Same thing is used in MDA window, but this class keeps its own copy
    * 
    * @param channelGroup
    * @param channel - 
    * @param defaultExp - default value
    * @return exposure time
    */
   @Override
   public double getChannelExposureTime(String channelGroup, String channel,
           double defaultExp) {
      return AcqControlDlg.getChannelExposure(channelGroup, channel,
            defaultExp);
   }

   /**
    * Updates the exposure time in the given preset 
    * Will also update current exposure if it the given channel and channelgroup
    * are the current one
    * 
    * @param channelGroup - 
    * 
    * @param channel - preset for which to change exposure time
    * @param exposure - desired exposure time
    */
   @Override
   public void setChannelExposureTime(String channelGroup, String channel,
           double exposure) {
      try {
         AcqControlDlg.setChannelExposure(channelGroup, channel, exposure);
         if (channelGroup != null && channelGroup.equals(core_.getChannelGroup())) {
            if (channel != null && !channel.equals("") && 
                    channel.equals(core_.getCurrentConfigFromCache(channelGroup))) {
               setExposure(exposure);
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError("Failed to set exposure using Channelgroup: "
                 + channelGroup + ", channel: " + channel + ", exposure: " + exposure);
      }
   }
     
   @Override
   public void enableRoiButtons(final boolean enabled) {
      frame_.enableRoiButtons(enabled);
   }

   /*
    * Changes background color of this window and all other MM windows
    */
   @Override
   public final void setBackgroundStyle(String backgroundType) {
      DaytimeNighttime.setMode(backgroundType);
   }

   @Override
   public String getBackgroundStyle() {
      return DaytimeNighttime.getBackgroundMode();
   }

   
   @Override
   public Datastore runAcquisition() throws MMScriptException {
      if (SwingUtilities.isEventDispatchThread()) {
         throw new MMScriptException("Acquisition can not be run from this (EDT) thread");
      }
      testForAbortRequests();
      if (acqControlWin_ != null) {
         Datastore store = acqControlWin_.runAcquisition();
         try {
            while (acqControlWin_.isAcquisitionRunning()) {
               Thread.sleep(50);
            }
         } catch (InterruptedException e) {
            ReportingUtils.showError(e);
         }
         return store;
      } else {
         throw new MMScriptException(
               "Acquisition setup window must be open for this command to work.");
      }
   }

   @Override
   public Datastore runAcquisition(String name, String root)
         throws MMScriptException {
      testForAbortRequests();
      if (acqControlWin_ != null) {
         Datastore store = acqControlWin_.runAcquisition(name, root);
         try {
            while (!store.getIsFrozen()) {
               Thread.sleep(100);
            }
         } catch (InterruptedException e) {
            ReportingUtils.showError(e);
         }
         return store;
      } else {
         throw new MMScriptException(
               "Acquisition setup window must be open for this command to work.");
      }
   }

   /**
    * Loads acquisition settings from file
    * @param path file containing previously saved acquisition settings
    * @throws MMScriptException 
    */
   @Override
   public void loadAcquisition(String path) throws MMScriptException {
      testForAbortRequests();
      try {
         engine_.shutdown();

         // load protocol
         if (acqControlWin_ != null) {
            acqControlWin_.loadAcqSettingsFromFile(path);
         }
      } catch (MMScriptException ex) {
         throw new MMScriptException(ex.getMessage());
      }

   }

   @Override
   public void setPositionList(PositionList pl) throws MMScriptException {
      testForAbortRequests();
      // use serialization to clone the PositionList object
      posList_ = pl; // PositionList.newInstance(pl);
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (posListDlg_ != null)
               posListDlg_.setPositionList(posList_);
            
            if (engine_ != null)
               engine_.setPositionList(posList_);
            
            if (acqControlWin_ != null)
               acqControlWin_.updateGUIContents();
         }
      });
   }

   @Override
   public PositionList getPositionList() throws MMScriptException {
      testForAbortRequests();
      // use serialization to clone the PositionList object
      return posList_; //PositionList.newInstance(posList_);
   }

   @Override
   public void refreshGUI() {
      updateGUI(true);
   }
   
   @Override
   public void refreshGUIFromCache() {
      updateGUI(true, true);
   }

   public AcquisitionWrapperEngine getAcquisitionEngine() {
      return engine_;
   }

   public CMMCore getCore() {
      return core_;
   }

   @Override
   public IAcquisitionEngine2010 getAcquisitionEngine2010() {
      try {
         acquisitionEngine2010LoadingThread_.join();
         if (acquisitionEngine2010_ == null) {
            acquisitionEngine2010_ = (IAcquisitionEngine2010) acquisitionEngine2010Class_.getConstructor(Studio.class).newInstance(studio_);
         }
         return acquisitionEngine2010_;
      } catch (IllegalAccessException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (IllegalArgumentException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (InstantiationException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (InterruptedException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (NoSuchMethodException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (SecurityException e) {
         ReportingUtils.logError(e);
         return null;
      } catch (InvocationTargetException e) {
         ReportingUtils.logError(e);
         return null;
      }
   }
   
   @Override
   public void setPause(boolean state) {
	   getAcquisitionEngine().setPause(state);
   }

   @Override
   public boolean isPaused() {
	   return getAcquisitionEngine().isPaused();
   }
   
   @Override
   public void attachRunnable(int frame, int position, int channel, int slice, Runnable runnable) {
	   getAcquisitionEngine().attachRunnable(frame, position, channel, slice, runnable);
   }

   @Override
   public void clearRunnables() {
	   getAcquisitionEngine().clearRunnables();
   }
   
   @Override
   public SequenceSettings getAcquisitionSettings() {
	   if (engine_ == null)
		   return new SequenceSettings();
	   return engine_.getSequenceSettings();
   }

   @Override
   public void setAcquisitionSettings(SequenceSettings ss) {
      if (engine_ == null)
         return;
      
      engine_.setSequenceSettings(ss);
      acqControlWin_.updateGUIContents();
   }

   @Override
   public void setROI(Rectangle r) throws MMScriptException {
      live().setSuspended(true);
      try {
         core_.setROI(r.x, r.y, r.width, r.height);
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
      staticInfo_.refreshValues();
      live().setSuspended(false);
   }

   public void setAcquisitionEngine(AcquisitionWrapperEngine eng) {
      engine_ = eng;
   }
   
   @Override
   public AutofocusPlugin getAutofocus() {
      return afMgr_.getDevice();
   }

   @Override
   public void showAutofocusDialog() {
      if (afMgr_.getDevice() != null) {
         afMgr_.showOptionsDialog();
      }
   }

   @Override
   public AutofocusManager getAutofocusManager() {
      return afMgr_;
   }

   /**
    * Allows MMListeners to register themselves
    * @param newL
    */
   @Override
   public void addMMListener(MMListenerInterface newL) {
      coreCallback_.addMMListener(newL);
   }

   /**
    * Allows MMListeners to remove themselves
    * @param oldL
    */
   @Override
   public void removeMMListener(MMListenerInterface oldL) {
      coreCallback_.removeMMListener(oldL);
   }

   @Override
   public void autostretchCurrentWindow() {
      DisplayWindow display = displays().getCurrentWindow();
      DisplaySettings settings = display.getDisplaySettings();
      if (settings.getShouldAutostretch() != true) {
         // Autostretch is not currently enabled; toggle it to perform the
         // autostretching.
         // TODO: this seems like a rather hacky way to do things.
         settings = settings.copy().shouldAutostretch(true).build();
         display.setDisplaySettings(settings);
         settings = settings.copy().shouldAutostretch(false).build();
         display.setDisplaySettings(settings);
      }
   }
   
   @Override
   public DataManager data() {
      return dataManager_;
   }
   @Override
   public DataManager getDataManager() {
      return data();
   }

   @Override
   public DisplayManager displays() {
      return displayManager_;
   }
   @Override
   public DisplayManager getDisplayManager() {
      return displays();
   }

   @Override
   public UserProfile profile() {
      return DefaultUserProfile.getInstance();
   }
   @Override
   public UserProfile getUserProfile() {
      return profile();
   }

   @Override
   public LogManager logs() {
      return ReportingUtils.getWrapper();
   }
   @Override
   public LogManager getLogManager() {
      return logs();
   }

   // TODO: split methods associated with this interface out to a separate
   // object.
   @Override
   public CompatibilityInterface compat() {
      return this;
   }
   @Override
   public CompatibilityInterface getCompatibilityInterface() {
      return this;
   }

   @Override
   public ScriptController scripter() {
      return scriptPanel_;
   }

   @Override
   public ScriptController getScriptController() {
      return scriptPanel_;
   }

   @Override
   public org.micromanager.SnapLiveManager live() {
      return snapLiveManager_;
   }

   @Override
   public org.micromanager.SnapLiveManager getSnapLiveManager() {
      return snapLiveManager_;
   }

   @Override
   public Album album() {
      return DefaultAlbum.getInstance();
   }

   @Override
   public Album getAlbum() {
      return album();
   }

   @Override
   public EventManager events() {
      return DefaultEventManager.getInstance();
   }

   @Override
   public EventManager getEventManager() {
      return events();
   }

   @Override
   public PluginManager plugins() {
      return pluginManager_;
   }

   @Override
   public PluginManager getPluginManager() {
      return plugins();
   }

   @Override
   public AffineTransform getCameraTransform(String config) {
      // Look in the profile first.
      try {
         AffineTransform result = (AffineTransform)
            (DefaultUserProfile.getInstance().getObject(
               MMStudio.class, AFFINE_TRANSFORM + config, null));
         if (result != null) {
            return result;
         }
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error retrieving camera transform");
      }
      // For backwards compatibility, try retrieving it from the 1.4
      // Preferences instead.
      return org.micromanager.internal.utils.UnpleasantLegacyCode.legacyRetrieveTransformFromPrefs("affine_transform_" + config);
   }

   @Override
   public void setCameraTransform(AffineTransform transform, String config) {
      try {
         DefaultUserProfile.getInstance().setObject(MMStudio.class,
               AFFINE_TRANSFORM + config, transform);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Error setting camera transform");
      }
   }

   public static boolean getShouldDeleteOldCoreLogs() {
      return DefaultUserProfile.getInstance().getBoolean(MMStudio.class,
            SHOULD_DELETE_OLD_CORE_LOGS, false);
   }

   public static void setShouldDeleteOldCoreLogs(boolean shouldDelete) {
      DefaultUserProfile.getInstance().setBoolean(MMStudio.class,
            SHOULD_DELETE_OLD_CORE_LOGS, shouldDelete);
   }

   public static int getCoreLogLifetimeDays() {
      return DefaultUserProfile.getInstance().getInt(MMStudio.class,
            CORE_LOG_LIFETIME_DAYS, 7);
   }

   public static void setCoreLogLifetimeDays(int days) {
      DefaultUserProfile.getInstance().setInt(MMStudio.class,
            CORE_LOG_LIFETIME_DAYS, days);
   }

   public static int getCircularBufferSize() {
      // Default to more MB for 64-bit systems.
      int defaultVal = System.getProperty("sun.arch.data.model", "32").equals("64") ? 250 : 25;
      return DefaultUserProfile.getInstance().getInt(MMStudio.class,
            CIRCULAR_BUFFER_SIZE, defaultVal);
   }

   public static void setCircularBufferSize(int newSize) {
      DefaultUserProfile.getInstance().setInt(MMStudio.class,
            CIRCULAR_BUFFER_SIZE, newSize);
   }
}
