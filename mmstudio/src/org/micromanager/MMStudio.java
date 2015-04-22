///////////////////////////////////////////////////////////////////////////////
//FILE:          MMStudio.java
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

package org.micromanager;

import bsh.EvalError;
import bsh.Interpreter;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Roi;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.prefs.Preferences;

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
import org.json.JSONObject;
import org.micromanager.acquisition.*;
import org.micromanager.api.Autofocus;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.MMTags;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.SequenceSettings;
import org.micromanager.api.events.ExposureChangedEvent;
import org.micromanager.api.events.PropertiesChangedEvent;
import org.micromanager.conf2.MMConfigFileException;
import org.micromanager.conf2.MicroscopeModel;
import org.micromanager.dialogs.AcqControlDlg;
import org.micromanager.dialogs.CalibrationListDlg;
import org.micromanager.dialogs.MMIntroDlg;
import org.micromanager.dialogs.RegistrationDlg;
import org.micromanager.events.EventManager;
import org.micromanager.graph.GraphData;
import org.micromanager.graph.GraphFrame;
import org.micromanager.graph.HistogramSettings;
import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.MetadataPanel;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;
import org.micromanager.logging.LogFileManager;
import org.micromanager.menus.FileMenu;
import org.micromanager.menus.HelpMenu;
import org.micromanager.menus.ToolsMenu;
import org.micromanager.navigation.CenterAndDragListener;
import org.micromanager.navigation.XYZKeyListener;
import org.micromanager.navigation.ZWheelListener;
import org.micromanager.pipelineinterface.PipelineFrame;
import org.micromanager.pluginmanagement.PluginManager;
import org.micromanager.positionlist.PositionListDlg;
import org.micromanager.script.ScriptPanel;
import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.FileDialogs.FileType;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TextUtils;
import org.micromanager.utils.UIMonitor;
import org.micromanager.utils.WaitDialog;




/*
 * Implements the ScriptInterface (i.e. primary API) and does various other
 * tasks that should probably be refactored out at some point.
 */
public class MMStudio implements ScriptInterface {

   private static final long serialVersionUID = 3556500289598574541L;
   private static final String MAIN_SAVE_METHOD = "saveMethod";
   private static final String SYSTEM_CONFIG_FILE = "sysconfig_file";
   private static final String OPEN_ACQ_DIR = "openDataDir";
   private static final String SCRIPT_CORE_OBJECT = "mmc";
   private static final String SCRIPT_ACQENG_OBJECT = "acq";
   private static final String SCRIPT_GUI_OBJECT = "gui";
   private static final String AUTOFOCUS_DEVICE = "autofocus_device";
   private static final String EXPOSURE_SETTINGS_NODE = "MainExposureSettings";
   private static final String CONTRAST_SETTINGS_NODE = "MainContrastSettings";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;
   private static final String DEFAULT_CONFIG_FILE_NAME = "MMConfig_demo.cfg";
   private static final String DEFAULT_CONFIG_FILE_PROPERTY = "org.micromanager.default.config.file";

   // cfg file saving
   private static final String CFGFILE_ENTRY_BASE = "CFGFileEntry";
   // GUI components
   private MMOptions options_;
   private boolean amRunningAsPlugin_;
   private GUIColors guiColors_;
   private GraphFrame profileWin_;
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;
   private PluginManager pluginManager_;
   private final SnapLiveManager snapLiveManager_;
   private final ToolsMenu toolsMenu_;

   private List<Component> MMFrames_
           = Collections.synchronizedList(new ArrayList<Component>());
   private AutofocusManager afMgr_;
   private ArrayList<String> MRUConfigFiles_;
   private static final int maxMRUCfgs_ = 5;
   private String sysConfigFile_;
   private String startupScriptFile_;
   private GraphData lineProfileData_;
   // applications settings
   private Preferences mainPrefs_;
   private Preferences systemPrefs_;
   private Preferences colorPrefs_;
   private Preferences exposurePrefs_;
   private Preferences contrastPrefs_;

   // MMcore
   private CMMCore core_;
   private AcquisitionWrapperEngine engine_;
   private PositionList posList_;
   private PositionListDlg posListDlg_;
   private String openAcqDirectory_ = "";
   private boolean isProgramRunning_;
   private boolean configChanged_ = false;
   private StrVector shutters_ = null;

   private ScriptPanel scriptPanel_;
   private PipelineFrame pipelineFrame_;
   private org.micromanager.utils.HotKeys hotKeys_;
   private CenterAndDragListener centerAndDragListener_;
   private ZWheelListener zWheelListener_;
   private XYZKeyListener xyzKeyListener_;
   private AcquisitionManager acqMgr_;
   private boolean liveModeSuspended_;
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
   private final FileMenu fileMenu_;
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
      org.micromanager.diagnostics.ThreadExceptionLogger.setUp();

      // Set up event handling early, so following code can subscribe/publish
      // events as needed.
      EventManager manager = new EventManager();
      EventManager.register(this);

      prepAcquisitionEngine();

      options_ = new MMOptions();
      try {
         options_.loadSettings();
      } catch (NullPointerException ex) {
         ReportingUtils.logError(ex);
      }

      UIMonitor.enable(options_.debugLogEnabled_);
      
      guiColors_ = new GUIColors();

      studio_ = this;

      amRunningAsPlugin_ = shouldRunAsPlugin;
      isProgramRunning_ = true;

      acqMgr_ = new AcquisitionManager();
      
      sysConfigFile_ = new File(DEFAULT_CONFIG_FILE_NAME).getAbsolutePath();
      sysConfigFile_ = System.getProperty(DEFAULT_CONFIG_FILE_PROPERTY,
            sysConfigFile_);

      if (options_.startupScript_.length() > 0) {
         startupScriptFile_ = new File(options_.startupScript_).getAbsolutePath();
      } else {
         startupScriptFile_ = "";
      }

      // set the location for app preferences
      try {
         mainPrefs_ = Preferences.userNodeForPackage(getClass());
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      systemPrefs_ = mainPrefs_;
      
      colorPrefs_ = mainPrefs_.node(mainPrefs_.absolutePath() + "/" + 
              AcqControlDlg.COLOR_SETTINGS_NODE);
      exposurePrefs_ = mainPrefs_.node(mainPrefs_.absolutePath() + "/" + 
              EXPOSURE_SETTINGS_NODE);
      contrastPrefs_ = mainPrefs_.node(mainPrefs_.absolutePath() + "/" +
              CONTRAST_SETTINGS_NODE);
      
      // check system preferences
      try {
         Preferences p = Preferences.systemNodeForPackage(getClass());
         if (null != p) {
            // if we can not write to the systemPrefs, use AppPrefs instead
            if (JavaUtils.backingStoreAvailable(p)) {
               systemPrefs_ = p;
            }
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      
      showRegistrationDialogMaybe();

      try {
         core_ = new CMMCore();
      } catch(UnsatisfiedLinkError ex) {
         ReportingUtils.showError(ex, 
               "Failed to load the MMCoreJ_wrap native library");
      }

      core_.enableStderrLog(true);

      snapLiveManager_ = new SnapLiveManager(studio_, core_);

      frame_ = new MainFrame(this, core_, snapLiveManager_, mainPrefs_);
      frame_.setIconImage(SwingResourceManager.getImage(MMStudio.class,
            "icons/microscope.gif"));
      frame_.loadApplicationPrefs(mainPrefs_, options_.closeOnExit_);
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

      openAcqDirectory_ = mainPrefs_.get(OPEN_ACQ_DIR, "");
      try {
         ImageUtils.setImageStorageClass(Class.forName (mainPrefs_.get(MAIN_SAVE_METHOD,
                 ImageUtils.getImageStorageClass().getName()) ) );
      } catch (ClassNotFoundException ex) {
         ReportingUtils.logError(ex, "Class not found error.  Should never happen");
      }

      ToolTipManager ttManager = ToolTipManager.sharedInstance();
      ttManager.setDismissDelay(TOOLTIP_DISPLAY_DURATION_MILLISECONDS);
      ttManager.setInitialDelay(TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS);
      
      frame_.setBackground(getBackgroundColor());
      
      menuBar_ = new JMenuBar();

      frame_.setJMenuBar(menuBar_);

      fileMenu_ = new FileMenu(studio_);
      fileMenu_.initializeFileMenu(menuBar_);

      toolsMenu_ = new ToolsMenu(studio_, core_, options_);
      toolsMenu_.initializeToolsMenu(menuBar_, mainPrefs_);

      HelpMenu helpMenu = new HelpMenu(studio_, core_);

      initializationSequence();
           
      helpMenu.initializeHelpMenu(menuBar_, systemPrefs_);
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
      core_.enableDebugLog(options_.debugLogEnabled_);

      if (options_.deleteOldCoreLogs_) {
         LogFileManager.deleteLogFilesDaysOld(
               options_.deleteCoreLogAfterDays_, logFileName);
      }

      ReportingUtils.setCore(core_);
      logStartupProperties();
              
      engine_ = new AcquisitionWrapperEngine(acqMgr_);

      // This entity is a class property to avoid garbage collection.
      coreCallback_ = new CoreEventCallback(core_, engine_);

      try {
         core_.setCircularBufferMemoryFootprint(options_.circularBufferSizeMB_);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }

      engine_.setParentGUI(studio_);

      loadMRUConfigFiles();
      afMgr_ = new AutofocusManager(studio_);
      pluginManager_ = new PluginManager(studio_, menuBar_);
      Thread pluginInitializer = pluginManager_.initializePlugins();

      frame_.paintToFront();
      
      engine_.setCore(core_, afMgr_);
      posList_ = new PositionList();
      engine_.setPositionList(posList_);
      // load (but do no show) the scriptPanel
      createScriptPanel();
      // Ditto with the image pipeline panel.
      createPipelinePanel();

      // Create an instance of HotKeys so that they can be read in from prefs
      hotKeys_ = new org.micromanager.utils.HotKeys();
      hotKeys_.loadSettings();
      
      if (!options_.doNotAskForConfigFile_) {
         // Ask the user for a configuration file.
         MMIntroDlg introDlg = new MMIntroDlg(
               MMVersion.VERSION_STRING, MRUConfigFiles_);
         introDlg.setConfigFile(sysConfigFile_);
         introDlg.setBackground(getBackgroundColor());
         introDlg.setVisible(true);
         introDlg.toFront();
         if (!introDlg.okChosen()) {
            // User aborted; close the program down.
            closeSequence(false);
            return;
         }
         sysConfigFile_ = introDlg.getConfigFile();
      }
      saveMRUConfigFiles();

      mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
      
      // before loading the system configuration, we need to wait 
      // until the plugins are loaded
      final WaitDialog waitDlg = new WaitDialog(
              "Loading plugins, please wait...");

      waitDlg.setAlwaysOnTop(true);
      waitDlg.showDialog();
      try {
         pluginInitializer.join(15000);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex,
               "Interrupted while waiting for plugin loading thread");
      }
      if (pluginInitializer.isAlive()) {
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

      executeStartupScript();

      // Create Multi-D window here but do not show it.
      // This window needs to be created in order to properly set the 
      // "ChannelGroup" based on the Multi-D parameters
      acqControlWin_ = new AcqControlDlg(engine_, mainPrefs_, 
            studio_, options_);
      addMMBackgroundListener(acqControlWin_);

      frame_.initializeConfigPad();

      String afDevice = mainPrefs_.get(AUTOFOCUS_DEVICE, "");
      if (afMgr_.hasDevice(afDevice)) {
         try {
            afMgr_.selectDevice(afDevice);
         } catch (MMException ex) {
            // this error should never happen
            ReportingUtils.showError(ex);
         }
      }

      centerAndDragListener_ = new CenterAndDragListener(studio_);
      zWheelListener_ = new ZWheelListener(core_, studio_);
      snapLiveManager_.addLiveModeListener(zWheelListener_);
      xyzKeyListener_ = new XYZKeyListener(core_, studio_);
      snapLiveManager_.addLiveModeListener(xyzKeyListener_);

      // switch error reporting back on
      ReportingUtils.showErrorOn(true);

      org.micromanager.diagnostics.gui.ProblemReportController.startIfInterruptedOnExit();
   }

   public void showPipelinePanel() {
      pipelineFrame_.setVisible(true);
   }

   public void showScriptPanel() {
      scriptPanel_.setVisible(true);
   }

   private void handleError(String message) {
      snapLiveManager_.setLiveMode(false);
      JOptionPane.showMessageDialog(frame_, message);
      core_.logMessage(message);
   }

   public void saveChannelColor(String chName, int rgb) {
      if (colorPrefs_ != null) {
         colorPrefs_.putInt("Color_" + chName, rgb);      
      }          
   }
   
   public Color getChannelColor(String chName, int defaultColor) {  
      if (colorPrefs_ != null) {
         defaultColor = colorPrefs_.getInt("Color_" + chName, defaultColor);
      }
      return new Color(defaultColor);
   }

   public void copyFromLiveModeToAlbum(VirtualAcquisitionDisplay display) throws MMScriptException, JSONException {
      ImageCache ic = display.getImageCache();
      int channels = ic.getSummaryMetadata().getInt("Channels");
      for (int i = 0; i < channels; i++) {
         // Make a copy of the image we get, so we don't interfere with
         // the Live mode version.
         TaggedImage image = ic.getImage(i, 0, 0, 0);
         addToAlbum(ImageUtils.copyMetadata(image),
               ic.getDisplayAndComments());
      }
   }

   private void showRegistrationDialogMaybe() {
      // show registration dialog if not already registered
      // first check user preferences (for legacy compatibility reasons)
      boolean userReg = mainPrefs_.getBoolean(RegistrationDlg.REGISTRATION,
            false) || mainPrefs_.getBoolean(RegistrationDlg.REGISTRATION_NEVER, false);

      if (!userReg) {
         boolean systemReg = systemPrefs_.getBoolean(
               RegistrationDlg.REGISTRATION, false) || systemPrefs_.getBoolean(RegistrationDlg.REGISTRATION_NEVER, false);
         if (!systemReg) {
            // prompt for registration info
            RegistrationDlg dlg = new RegistrationDlg(systemPrefs_);
            dlg.setVisible(true);
         }
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
               acquisitionEngine2010Class_  = Class.forName("org.micromanager.AcquisitionEngine2010");
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

   /**
    * Shows images as they appear in the default display window. Uses
    * the default processor stack to process images as they arrive on
    * the rawImageQueue.
    * @param rawImageQueue
    * @param displayImageRoutine
    */
   public void runDisplayThread(BlockingQueue<TaggedImage> rawImageQueue, 
            final DisplayImageRoutine displayImageRoutine) {
      final BlockingQueue<TaggedImage> processedImageQueue = 
            ProcessorStack.run(rawImageQueue, 
            getAcquisitionEngine().getImageProcessors());
        
      new Thread("Display thread") {
       @Override
         public void run() {
            try {
               TaggedImage image;
               do {
                  image = processedImageQueue.take();
                  if (image != TaggedImageQueue.POISON) {
                     displayImageRoutine.show(image);
                  }
               } while (image != TaggedImageQueue.POISON);
            } catch (InterruptedException ex) {
               ReportingUtils.logError(ex);
            }
         }
      }.start();
   }

   public interface DisplayImageRoutine {
      public void show(TaggedImage image);
   }
   
   /**
    * used to store contrast settings to be later used for initialization of contrast of new windows.
    *  Shouldn't be called by loaded data sets, only
    * ones that have been acquired
    * @param channelGroup
    * @param channel
    * @param mda
    * @param settings
    */
   public void saveChannelHistogramSettings(String channelGroup, String channel, boolean mda, 
           HistogramSettings settings) {
      String type = mda ? "MDA_" : "SnapLive_";
      if (options_.syncExposureMainAndMDA_) {
         type = "";  //only one group of contrast settings
      }
      contrastPrefs_.putInt("ContrastMin_" + channelGroup + "_" + type + channel, settings.min_);
      contrastPrefs_.putInt("ContrastMax_" + channelGroup + "_" + type + channel, settings.max_);
      contrastPrefs_.putDouble("ContrastGamma_" + channelGroup + "_" + type + channel, settings.gamma_);
      contrastPrefs_.putInt("ContrastHistMax_" + channelGroup + "_" + type + channel, settings.histMax_);
      contrastPrefs_.putInt("ContrastHistDisplayMode_" + channelGroup + "_" + type + channel, settings.displayMode_);
   }

   public HistogramSettings loadStoredChannelHistogramSettings(String channelGroup, String channel, boolean mda) {
      String type = mda ? "MDA_" : "SnapLive_";
      if (options_.syncExposureMainAndMDA_) {
         type = "";  //only one group of contrast settings
      }
      return new HistogramSettings(
      contrastPrefs_.getInt("ContrastMin_" + channelGroup + "_" + type + channel,0),
      contrastPrefs_.getInt("ContrastMax_" + channelGroup + "_" + type + channel, 65536),
      contrastPrefs_.getDouble("ContrastGamma_" + channelGroup + "_" + type + channel, 1.0),
      contrastPrefs_.getInt("ContrastHistMax_" + channelGroup + "_" + type + channel, -1),
      contrastPrefs_.getInt("ContrastHistDisplayMode_" + channelGroup + "_" + type + channel, 1) );
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
         snapLiveManager_.safeSetCoreExposure(exposureTime);
         // Display the new exposure time
         double exposure;
         try {
            exposure = core_.getExposure();
            frame_.setDisplayedExposureTime(exposure);
            
            // update current channel in MDA window with this exposure
            String channelGroup = core_.getChannelGroup();
            String channel = core_.getCurrentConfigFromCache(channelGroup);
            if (!channel.equals("") ) {
               exposurePrefs_.putDouble("Exposure_" + channelGroup + "_"
                    + channel, exposure);
               if (options_.syncExposureMainAndMDA_) {
                  getAcqDlg().setChannelExposureTime(channelGroup, channel, exposure);
               }
            }
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Couldn't set exposure time.");
         }
      } // End synchronization check
   }

   public double getPreferredWindowMag() {
      return options_.windowMag_;
   }

   public boolean getMetadataFileWithMultipageTiff() {
      return options_.mpTiffMetadataFile_;
   }

   public boolean getSeparateFilesForPositionsMPTiff() {
      return options_.mpTiffSeparateFilesForPositions_;
   }
   
   @Override
   public boolean getHideMDADisplayOption() {
      return options_.hideMDADisplay_;
   }

   public void updateLineProfile() {
      if (WindowManager.getCurrentWindow() == null || profileWin_ == null
            || !profileWin_.isShowing()) {
         return;
      }

      calculateLineProfileData(WindowManager.getCurrentImage());
      profileWin_.setData(lineProfileData_);
   }

   public void openLineProfileWindow() {
      if (WindowManager.getCurrentWindow() == null || 
              WindowManager.getCurrentWindow().isClosed()) {
         return;
      }
      calculateLineProfileData(WindowManager.getCurrentImage());
      if (lineProfileData_ == null) {
         return;
      }
      profileWin_ = new GraphFrame();
      profileWin_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      profileWin_.setData(lineProfileData_);
      profileWin_.setAutoScale();
      profileWin_.setTitle("Live line profile");
      profileWin_.setBackground(getBackgroundColor());
      addMMBackgroundListener(profileWin_);
      profileWin_.setVisible(true);
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

   private void calculateLineProfileData(ImagePlus imp) {
      // generate line profile
      Roi roi = imp.getRoi();
      if (roi == null || !roi.isLine()) {

         // if there is no line ROI, create one
         Rectangle r = imp.getProcessor().getRoi();
         int iWidth = r.width;
         int iHeight = r.height;
         int iXROI = r.x;
         int iYROI = r.y;
         if (roi == null) {
            iXROI += iWidth / 2;
            iYROI += iHeight / 2;
         }

         roi = new Line(iXROI - iWidth / 4, iYROI - iWidth / 4, iXROI
               + iWidth / 4, iYROI + iHeight / 4);
         imp.setRoi(roi);
         roi = imp.getRoi();
      }

      ImageProcessor ip = imp.getProcessor();
      ip.setInterpolate(true);
      Line line = (Line) roi;

      if (lineProfileData_ == null) {
         lineProfileData_ = new GraphData();
      }
      lineProfileData_.setData(line.getPixels());
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

         VirtualAcquisitionDisplay virtAcq =
            VirtualAcquisitionDisplay.getDisplay(curImage);
         JSONObject tags = virtAcq.getCurrentMetadata();
         try {
            originalROI = MDUtils.getROI(tags);
         }
         catch (JSONException e) {
         }
         catch (MMScriptException e) {
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
         if (isLiveModeOn()) {
            liveRunning = true;
            enableLiveMode(false);
         }
         core_.clearROI();
         staticInfo_.refreshValues();
         if (liveRunning) {
            enableLiveMode(true);
         }

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   /**
    * Returns instance of the core uManager object;
    */
   @Override
   public CMMCore getMMCore() {
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

   public MetadataPanel getMetadataPanel() {
      return frame_.getMetadataPanel();
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
            mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
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
      mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
      loadSystemConfiguration();
   }

   public void setAcqDirectory(String dir) {
      openAcqDirectory_ = dir;
   }

   /**
    * Open an existing acquisition directory and build viewer window.
    * @param inRAM whether or not to keep data in RAM 
    * @return selected File object 
    */
   public File promptForAcquisitionToOpen(boolean inRAM) {
      File f = FileDialogs.openDir(frame_,
            "Please select an image data set", MM_DATA_SET);
      if (f == null) {
         return null;
      }
      String path = f.getParent();
      if (f.isDirectory()) {
         path = f.getAbsolutePath();
      }

      try {
         openAcquisitionData(path, inRAM);
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex);
      }
      return f;
   }

   @Override
   public String openAcquisitionData(String dir, boolean inRAM, boolean show) 
           throws MMScriptException {
      File f = new File(dir);
      String rootDir = f.getAbsolutePath();
      String name = f.getName();
      rootDir = rootDir.substring(0, rootDir.length() - (name.length() + 1));
      name = acqMgr_.getUniqueAcquisitionName(name);
      acqMgr_.openAcquisition(name, rootDir, show, !inRAM, true);
      try {
         getAcquisition(name).initialize();
         fileMenu_.addFileToRecentlyOpenedMenu(f);
      } catch (MMScriptException mex) {
         acqMgr_.closeAcquisition(name);
         throw (mex);
      }
      return name;
   }

   /**
    * Opens an existing data set. Shows the acquisition in a window.
    * @param dir location of data set
    * @param inRam whether not to open completely in RAM
    * @return The acquisition object.
    * @throws org.micromanager.utils.MMScriptException
    */
   @Override
   public String openAcquisitionData(String dir, boolean inRam) throws MMScriptException {
      return openAcquisitionData(dir, inRam, true);
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

         boolean isLiveOn = false;
         if (isLiveModeOn()) {
            isLiveOn = true;
            enableLiveMode(false);
         }
         core_.setProperty(StaticInfo.cameraLabel_, MMCoreJ.getG_Keyword_Binning(), mode);
         staticInfo_.refreshValues();
         if (isLiveOn) {
            enableLiveMode(true);
         }
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
      if (scriptPanel_ == null) {
         scriptPanel_ = new ScriptPanel(core_, studio_);
         scriptPanel_.insertScriptingObject(SCRIPT_CORE_OBJECT, core_);
         scriptPanel_.insertScriptingObject(SCRIPT_ACQENG_OBJECT, engine_);
         scriptPanel_.setParentGUI(studio_);
         scriptPanel_.setBackground(getBackgroundColor());
         addMMBackgroundListener(scriptPanel_);
      }
   }

   private void createPipelinePanel() {
      if (pipelineFrame_ == null) {
         pipelineFrame_ = new PipelineFrame(studio_, engine_);
         pipelineFrame_.setBackground(getBackgroundColor());
         addMMBackgroundListener(pipelineFrame_);
      }
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

   public void updateCenterAndDragListener() {
      if (toolsMenu_.getIsCenterAndDragChecked()) {
         centerAndDragListener_.start();
      } else {
         centerAndDragListener_.stop();
      }
   }
   
   // Ensure that the "XY list..." dialog exists.
   private void checkPosListDlg() {
      if (posListDlg_ == null) {
         posListDlg_ = new PositionListDlg(core_, studio_, posList_, 
                 acqControlWin_,options_);
         GUIUtils.recallPosition(posListDlg_);
         posListDlg_.setBackground(getBackgroundColor());
         studio_.addMMBackgroundListener(posListDlg_);
         posListDlg_.addListeners();
      }
   }
   

   // //////////////////////////////////////////////////////////////////////////
   // public interface available for scripting access
   // //////////////////////////////////////////////////////////////////////////
   @Override
   public void snapSingleImage() {
      doSnap();
   }

   private boolean isCameraAvailable() {
      return StaticInfo.cameraLabel_.length() > 0;
   }

   /**
    * Part of ScriptInterface API
    * Opens the XYPositionList when it is not opened
    * Adds the current position to the list (same as pressing the "Mark" button)
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

   /**
    * Implements ScriptInterface
    */
   @Override
   @Deprecated
   public AcqControlDlg getAcqDlg() {
      return acqControlWin_;
   }
   
   @Override
   @Deprecated
   public PositionListDlg getXYPosListDlg() {
      checkPosListDlg();
      return posListDlg_;
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

   @Override
   public boolean isLiveModeOn() {
      return snapLiveManager_.getIsLiveModeOn();
   }

   public boolean displayImage(final Object pixels) {
      if (pixels instanceof TaggedImage) {
         return displayTaggedImage((TaggedImage) pixels, true);
      } else {
         return displayImage(pixels, true);
      }
   }

   public boolean displayImage(final Object pixels, boolean wait) {
      return snapLiveManager_.displayImage(pixels);
   }

   public boolean displayImageWithStatusLine(Object pixels, String statusLine) {
      boolean ret = displayImage(pixels);
      snapLiveManager_.setStatusLine(statusLine);
      return ret;
   }

   public void displayStatusLine(String statusLine) {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (!(ip.getWindow() instanceof DisplayWindow)) {
         return;
      }
      VirtualAcquisitionDisplay.getDisplay(ip).displayStatusLine(statusLine);
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

   public void doSnap() {
      doSnap(false);
   }

   public void doSnap(final boolean shouldAddToAlbum) {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured");
         return;
      }

      BlockingQueue<TaggedImage> snapImageQueue = 
              new LinkedBlockingQueue<TaggedImage>();
      
      try {
         core_.snapImage();
         long c = core_.getNumberOfCameraChannels();
         runDisplayThread(snapImageQueue, new DisplayImageRoutine() {
            @Override
            public void show(final TaggedImage image) {
                  if (shouldAddToAlbum) {
                     try {
                        addToAlbum(image);
                     } catch (MMScriptException ex) {
                        ReportingUtils.showError(ex);
                     }
                  } else {
                     displayImage(image);
                  }
            }
         });
         
         for (int i = 0; i < c; ++i) {
            TaggedImage img = core_.getTaggedImage(i);
            MDUtils.setNumChannels(img.tags, (int) c);
            snapImageQueue.put(img);
         }
         
         snapImageQueue.put(TaggedImageQueue.POISON);

         snapLiveManager_.moveDisplayToFront();
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   /**
    * Is this function still needed?  It does some magic with tags. I found 
    * it to do harmful thing with tags when a Multi-Camera device is
    * present (that issue is now fixed).
    * @param ti
    */
   public void normalizeTags(TaggedImage ti) {
      if (ti != TaggedImageQueue.POISON) {
      int channel = 0;
      try {

         if (ti.tags.has("ChannelIndex")) {
            channel = MDUtils.getChannelIndex(ti.tags);
         }
         MDUtils.setChannelIndex(ti.tags, channel);
         MDUtils.setPositionIndex(ti.tags, 0);
         MDUtils.setSliceIndex(ti.tags, 0);
         MDUtils.setFrameIndex(ti.tags, 0);
         
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      }
   }

   private boolean displayTaggedImage(TaggedImage ti, boolean update) {
      try {
         frame_.setCursor(new Cursor(Cursor.WAIT_CURSOR));
         // Ensure that the acquisition is ready before we add the image.
         snapLiveManager_.validateDisplayAndAcquisition(ti);
         MDUtils.setSummary(ti.tags, getAcquisitionWithName(SnapLiveManager.SIMPLE_ACQ).getSummaryMetadata());
         staticInfo_.addStagePositionToTags(ti);
         addImage(SnapLiveManager.SIMPLE_ACQ, ti, update, true);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return false;
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         return false;
      }
      if (update) {
         frame_.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
         updateLineProfile();
      }
      return true;
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
            
         if (snapLiveManager_.getIsLiveModeOn()) {
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
      enableLiveMode(false);
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
      snapLiveManager_.setLiveMode(false);

      // check needed to avoid deadlock
      if (!calledByImageJ) {
         if (!WindowManager.closeAllWindows()) {
            core_.logMessage("Failed to close some windows");
         }
      }
  
      if (posListDlg_ != null) {
         removeMMBackgroundListener(posListDlg_);
         posListDlg_.getToolkit().getSystemEventQueue().postEvent(
                 new WindowEvent(posListDlg_, WindowEvent.WINDOW_CLOSING));
         posListDlg_.dispose();
      }
      
      if (profileWin_ != null) {
         removeMMBackgroundListener(profileWin_);
         profileWin_.dispose();
      }

      if (scriptPanel_ != null) {
         removeMMBackgroundListener(scriptPanel_);
         scriptPanel_.closePanel();
      }

      if (pipelineFrame_ != null) {
         removeMMBackgroundListener(pipelineFrame_);
         pipelineFrame_.dispose();
      }

      if (propertyBrowser_ != null) {
         removeMMBackgroundListener(propertyBrowser_);
         propertyBrowser_.getToolkit().getSystemEventQueue().postEvent(
                 new WindowEvent(propertyBrowser_, WindowEvent.WINDOW_CLOSING));
         propertyBrowser_.dispose();
      }

      if (acqControlWin_ != null) {
         removeMMBackgroundListener(acqControlWin_);
         acqControlWin_.close();
      }

      if (afMgr_ != null) {
         afMgr_.closeOptionsDialog();
      }
      
      if (engine_ != null) {
         engine_.shutdown();
         engine_.disposeProcessors();
      }

      pluginManager_.disposePlugins();

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
      frame_.savePrefs(mainPrefs_);
      
      mainPrefs_.put(OPEN_ACQ_DIR, openAcqDirectory_);
      mainPrefs_.put(MAIN_SAVE_METHOD, 
              ImageUtils.getImageStorageClass().getName());

      // NOTE: do not save auto shutter state
      if (afMgr_ != null && afMgr_.getDevice() != null) {
         mainPrefs_.put(AUTOFOCUS_DEVICE, afMgr_.getDevice().getDeviceName());
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
      
      try {
         // Close all image windows associated with MM.  Canceling saving of 
         // any of these should abort shutdown
         if (!acqMgr_.closeAllImageWindows()) {
            return false;
         }
      } catch (MMScriptException ex) {
         // Not sure what to do here...
      }

      if (!cleanupOnClose(calledByImageJ)) {
         return false;
      }

      isProgramRunning_ = false;

      saveSettings();
      try {
         frame_.getConfigPad().saveSettings();
         options_.saveSettings();
         hotKeys_.saveSettings();
      } catch (NullPointerException e) {
         if (core_ != null)
            logError(e);
      }
      if (options_.closeOnExit_) {
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

   //TODO: Deprecated @Override
   // Last I checked, this is only used by the SlideExplorer plugin.
   public ContrastSettings getContrastSettings() {
      ImagePlus img = WindowManager.getCurrentImage();
      if (img == null || VirtualAcquisitionDisplay.getDisplay(img) == null )
         return null;
      return VirtualAcquisitionDisplay.getDisplay(img).getChannelContrastSettings(0);
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
            interp.set(SCRIPT_ACQENG_OBJECT, engine_);
            interp.set(SCRIPT_GUI_OBJECT, studio_);

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

      saveMRUConfigFiles();

      final WaitDialog waitDlg = new WaitDialog(
              "Loading system configuration, please wait...");

      waitDlg.setAlwaysOnTop(true);
      waitDlg.showDialog();
      frame_.setEnabled(false);

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

   private void saveMRUConfigFiles() {
      if (0 < sysConfigFile_.length()) {
         if (MRUConfigFiles_.contains(sysConfigFile_)) {
            MRUConfigFiles_.remove(sysConfigFile_);
         }
         if (maxMRUCfgs_ <= MRUConfigFiles_.size()) {
            MRUConfigFiles_.remove(maxMRUCfgs_ - 1);
         }
         MRUConfigFiles_.add(0, sysConfigFile_);
         // save the MRU list to the preferences
         for (Integer icfg = 0; icfg < MRUConfigFiles_.size(); ++icfg) {
            String value = "";
            if (null != MRUConfigFiles_.get(icfg)) {
               value = MRUConfigFiles_.get(icfg);
            }
            mainPrefs_.put(CFGFILE_ENTRY_BASE + icfg.toString(), value);
         }
      }
   }

   public List<String> getMRUConfigFiles() {
      return MRUConfigFiles_;
   }

   /**
    * Load the most recently used config file(s) to help the user when
    * selecting which one to use.
    */
   private void loadMRUConfigFiles() {
      sysConfigFile_ = mainPrefs_.get(SYSTEM_CONFIG_FILE, sysConfigFile_);
      MRUConfigFiles_ = new ArrayList<String>();
      for (Integer icfg = 0; icfg < maxMRUCfgs_; ++icfg) {
         String value = "";
         value = mainPrefs_.get(CFGFILE_ENTRY_BASE + icfg.toString(), value);
         if (0 < value.length()) {
            File ruFile = new File(value);
            if (ruFile.exists()) {
               if (!MRUConfigFiles_.contains(value)) {
                  MRUConfigFiles_.add(value);
               }
            }
         }
      }
      // initialize MRU list from old persistant data containing only SYSTEM_CONFIG_FILE
      if (sysConfigFile_.length() > 0) {
         if (!MRUConfigFiles_.contains(sysConfigFile_)) {
            // in case persistant data is inconsistent
            if (maxMRUCfgs_ <= MRUConfigFiles_.size()) {
               MRUConfigFiles_.remove(maxMRUCfgs_ - 1);
            }
            MRUConfigFiles_.add(0, sysConfigFile_);
         }
      }
   }

   public void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(engine_, mainPrefs_, studio_, options_);
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
                  boolean lmo = isLiveModeOn();
                  if (lmo) {
                     enableLiveMode(false);
                  }
                  afMgr_.getDevice().fullFocus();
                  if (lmo) {
                     enableLiveMode(true);
                  }
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
   
   
   @Override
   public boolean displayImage(TaggedImage ti) {
      normalizeTags(ti);
      return displayTaggedImage(ti, true);
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
    * Lets JComponents register themselves so that their background can be
    * manipulated.
    * TODO: should we set the background color here as well? Currently there's
    * a lot of duplicated code where, every time someone calls this method,
    * they also have to manually set their background color for the first time.
    * @param comp
    */
   @Override
   public void addMMBackgroundListener(Component comp) {
      if (MMFrames_.contains(comp))
         return;
      MMFrames_.add(comp);
   }

   /**
    * Lets JComponents remove themselves from the list whose background gets
    * changes
    * @param comp
    */
   @Override
   public void removeMMBackgroundListener(Component comp) {
      if (!MMFrames_.contains(comp))
         return;
      MMFrames_.remove(comp);
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
      return exposurePrefs_.getDouble("Exposure_" + channelGroup
              + "_" + channel, defaultExp);
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
         exposurePrefs_.putDouble("Exposure_" + channelGroup + "_"
                 + channel, exposure);
         if (channelGroup != null && channelGroup.equals(core_.getChannelGroup())) {
            if (channel != null && !channel.equals("") && 
                    channel.equals(core_.getCurrentConfigFromCache(channelGroup))) {
               setExposure(exposure);
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError("Failed to set Exposure prefs using Channelgroup: "
                 + channelGroup + ", channel: " + channel + ", exposure: " + exposure);
      }
   }
     
   @Override
   public void enableRoiButtons(final boolean enabled) {
      frame_.enableRoiButtons(enabled);
   }

   /**
    * Returns the current background color
    * @return current background color
    */
   @Override
   public final Color getBackgroundColor() {
      return guiColors_.background.get((options_.displayBackground_));
   }

   /*
    * Changes background color of this window and all other MM windows
    */
   @Override
   public void setBackgroundStyle(String backgroundType) {
      frame_.setBackground(guiColors_.background.get((backgroundType)));
      frame_.paint(frame_.getGraphics());
      
      // sets background of all registered Components
      for (Component comp:MMFrames_) {
         if (comp != null)
            comp.setBackground(guiColors_.background.get(backgroundType));
       }
   }

   @Override
   public String getBackgroundStyle() {
      return options_.displayBackground_;
   }

   
   @Override
   public ImageWindow getSnapLiveWin() {
      return snapLiveManager_.getSnapLiveWindow();
   }

   @Override 
   public ImageCache getCacheForWindow(ImageWindow window) throws IllegalArgumentException { 
      VirtualAcquisitionDisplay display = VirtualAcquisitionDisplay.getDisplay(window.getImagePlus()); 
      if (display == null) { 
         throw new IllegalArgumentException("No matching Micro-Manager display for this window"); 
      } 
      return display.getImageCache(); 
   }
   
   @Override
   public String runAcquisition() throws MMScriptException {
      if (SwingUtilities.isEventDispatchThread()) {
         throw new MMScriptException("Acquisition can not be run from this (EDT) thread");
      }
      testForAbortRequests();
      if (acqControlWin_ != null) {
         String name = acqControlWin_.runAcquisition();
         try {
            while (acqControlWin_.isAcquisitionRunning()) {
               Thread.sleep(50);
            }
         } catch (InterruptedException e) {
            ReportingUtils.showError(e);
         }
         return name;
      } else {
         throw new MMScriptException(
               "Acquisition setup window must be open for this command to work.");
      }
   }

   @Override
   public String runAcquisition(String name, String root)
         throws MMScriptException {
      testForAbortRequests();
      if (acqControlWin_ != null) {
         String acqName = acqControlWin_.runAcquisition(name, root);
         try {
            while (acqControlWin_.isAcquisitionRunning()) {
               Thread.sleep(100);
            }
            // ensure that the acquisition has finished.
            // This does not seem to work, needs something better
            MMAcquisition acq = acqMgr_.getAcquisition(acqName);
            boolean finished = false;
            while (!finished) {
               ImageCache imCache = acq.getImageCache();
               if (imCache != null) {
                  if (imCache.isFinished()) {
                     finished = true;
                  } else {
                     Thread.sleep(100);
                  }
               }
            }

         } catch (InterruptedException e) {
            ReportingUtils.showError(e);
         }
         return acqName;
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
   public void sleep(long ms) throws MMScriptException {
      if (scriptPanel_ != null) {
         if (scriptPanel_.stopRequestPending()) {
            throw new MMScriptException("Script interrupted by the user!");
         }
         scriptPanel_.sleep(ms);
      }
   }

   @Override
   public String getUniqueAcquisitionName(String stub) {
      return acqMgr_.getUniqueAcquisitionName(stub);
   }
   
   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, int nrPositions) throws MMScriptException {
      openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices,
            nrPositions, true, false);
   }

   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices) throws MMScriptException {
      openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 0);
   }
   
   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, int nrPositions, boolean show)
         throws MMScriptException {
      openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 
            nrPositions, show, false);
   }


   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, boolean show)
         throws MMScriptException {
      openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 0, 
            show, false);
   }   

   @Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, int nrPositions, boolean show, boolean save)
         throws MMScriptException {
      if (nrFrames <= 0) {
         nrFrames = 1;
         ReportingUtils.logError("Coercing frame count to 1");
      }
      if (nrChannels <= 0) {
         nrChannels = 1;
         ReportingUtils.logError("Coercing channel count to 1");
      }
      if (nrSlices <= 0) {
         nrSlices = 1;
         ReportingUtils.logError("Coercing slice count to 1");
      }
      if (nrPositions <= 0) {
         nrPositions = 1;
         ReportingUtils.logError("Coercing position count to 1");
      }
      acqMgr_.openAcquisition(name, rootDir, show, save);
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      acq.setDimensions(nrFrames, nrChannels, nrSlices, nrPositions);
   }

   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, boolean show, boolean virtual)
         throws MMScriptException {
      openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 0, 
            show, virtual);
   }

   @Override
   @Deprecated
   public String createAcquisition(JSONObject summaryMetadata, boolean diskCached, boolean displayOff) {
      return acqMgr_.createAcquisition(summaryMetadata, diskCached, engine_, displayOff);
   }
   
   /**
    * Call initializeAcquisition with values extracted from the provided 
    * JSONObject.
    */
   private void initializeAcquisitionFromTags(String name, JSONObject tags) throws JSONException, MMScriptException {
      int width = MDUtils.getWidth(tags);
      int height = MDUtils.getHeight(tags);
      int byteDepth = MDUtils.getDepth(tags);
      int bitDepth = byteDepth * 8;
      if (MDUtils.hasBitDepth(tags)) {
         bitDepth = MDUtils.getBitDepth(tags);
      }
      initializeAcquisition(name, width, height, byteDepth, bitDepth);
   }

   @Override
   public void initializeAcquisition(String name, int width, int height, int byteDepth, int bitDepth) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      //number of multi-cam cameras is set to 1 here for backwards compatibility
      //might want to change this later
      acq.setImagePhysicalDimensions(width, height, byteDepth, bitDepth, 1);
      acq.initialize();
   }

   @Override
   public int getAcquisitionImageWidth(String acqName) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      return acq.getWidth();
   }

   @Override
   public int getAcquisitionImageHeight(String acqName) throws MMScriptException{
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      return acq.getHeight();
   }

   @Override
   public int getAcquisitionImageBitDepth(String acqName) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      return acq.getBitDepth();
   }
   
   @Override
   public int getAcquisitionImageByteDepth(String acqName) throws MMScriptException{
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      return acq.getByteDepth();
   }

   @Override public int getAcquisitionMultiCamNumChannels(String acqName) throws MMScriptException{
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      return acq.getMultiCameraNumChannels();
   }
   
   @Override
   public Boolean acquisitionExists(String name) {
      return acqMgr_.acquisitionExists(name);
   }

   @Override
   public void closeAcquisition(String name) throws MMScriptException {
      acqMgr_.closeAcquisition(name);
   }

   @Override
   public void closeAcquisitionWindow(String acquisitionName) throws MMScriptException {
      acqMgr_.closeImageWindow(acquisitionName);
   }

   @Override
   public void refreshGUI() {
      updateGUI(true);
   }
   
   @Override
   public void refreshGUIFromCache() {
      updateGUI(true, true);
   }

   @Override
   public void setAcquisitionProperty(String acqName, String propertyName,
         String value) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      acq.setProperty(propertyName, value);
   }

   @Override
   public void setImageProperty(String acqName, int frame, int channel,
         int slice, String propName, String value) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(acqName);
      acq.setProperty(frame, channel, slice, propName, value);
   }


   @Override
   public String getCurrentAlbum() {
      return acqMgr_.getCurrentAlbum();
   }
   
   @Override
   public void enableLiveMode(boolean enable) {
      if (core_ == null) {
         return;
      }
      snapLiveManager_.setLiveMode(enable);
   }

   @Override
   public void addToAlbum(TaggedImage taggedImg) throws MMScriptException {
      addToAlbum(taggedImg, null);
   }
   
   public void addToAlbum(TaggedImage taggedImg, JSONObject displaySettings) throws MMScriptException {
      normalizeTags(taggedImg);
      acqMgr_.addToAlbum(taggedImg,displaySettings);
   }

   /**
    * The basic method for adding images to an existing data set.
    * If the acquisition was not previously initialized, it will attempt to 
    * initialize it from the available image data
    * @param frame
    * @param channel
    * @param slice
    * @param position
    * @throws org.micromanager.utils.MMScriptException
    */
   @Override
   public void addImageToAcquisition(String name,
           int frame,
           int channel,
           int slice,
           int position,
           TaggedImage taggedImg) throws MMScriptException {

      // TODO: complete the tag set and initialize the acquisition
      MMAcquisition acq = acqMgr_.getAcquisition(name);

      // check position, for multi-position data set the number of declared positions should be at least 2
      if (acq.getPositions() <= 1 && position > 0) {
         throw new MMScriptException("The acquisition was open as a single position data set.\n"
                 + "Open acqusition with two or more positions in order to crate a multi-position data set.");
      }

      // check position, for multi-position data set the number of declared
      // positions should be at least 2
      if (acq.getChannels() <= channel) {
         throw new MMScriptException("This acquisition was opened with " + 
               acq.getChannels() + " channels.\n" + 
               "The channel number must not exceed declared number of positions.");
      }

      JSONObject tags = taggedImg.tags;

      // if the acquisition was not previously initialized, set physical
      // dimensions of the image
      if (!acq.isInitialized()) {
         // automatically initialize physical dimensions of the image
         try {
            initializeAcquisitionFromTags(name, tags);
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      }

      // create required coordinate tags
      try {
         MDUtils.setFrameIndex(tags, frame);
         MDUtils.setChannelIndex(tags, channel);
         MDUtils.setSliceIndex(tags, slice);
         MDUtils.setPositionIndex(tags, position);

         if (!MDUtils.hasSlicesFirst(tags) && !MDUtils.hasTimeFirst(tags)) {
            // add default setting
            MDUtils.setSlicesFirst(tags, true);
            MDUtils.setTimeFirst(tags, false);
         }

         if (acq.getPositions() > 1) {
            // if no position name is defined we need to insert a default one
            if (!MDUtils.hasPositionName(tags)) {
               MDUtils.setPositionName(tags, "Pos" + position);
            }
         }

         // update frames if necessary
         if (acq.getFrames() <= frame) {
            acq.setProperty(MMTags.Summary.FRAMES, Integer.toString(frame + 1));
         }
      } catch (JSONException e) {
         throw new MMScriptException(e);
      }
      acq.insertImage(taggedImg);
   }

   @Override 
   public void setAcquisitionAddImageAsynchronous(String name) throws MMScriptException { 
      MMAcquisition acq = acqMgr_.getAcquisition(name); 
      acq.setAsynchronous(); 
   } 

   /**
    * A quick way to implicitly snap an image and add it to the data set. Works
    * in the same way as above.
    * @param slice
    * @throws org.micromanager.utils.MMScriptException
    */
   @Override
   public void snapAndAddImage(String name, int frame, int channel, int slice, int position) throws MMScriptException {
      TaggedImage ti;
      try {
         if (core_.isSequenceRunning()) {
            ti = core_.getLastTaggedImage();
         } else {
            core_.snapImage();
            ti = core_.getTaggedImage();
         }
         MDUtils.setChannelIndex(ti.tags, channel);
         MDUtils.setFrameIndex(ti.tags, frame);
         MDUtils.setSliceIndex(ti.tags, slice);

         MDUtils.setPositionIndex(ti.tags, position);

         MMAcquisition acq = acqMgr_.getAcquisition(name);
         if (!acq.isInitialized()) {
            long width = core_.getImageWidth();
            long height = core_.getImageHeight();
            long depth = core_.getBytesPerPixel();
            long bitDepth = core_.getImageBitDepth();
            int multiCamNumCh = (int) core_.getNumberOfCameraChannels();

            acq.setImagePhysicalDimensions((int) width, (int) height, (int) depth, (int) bitDepth, multiCamNumCh);
            acq.initialize();
         }

         if (acq.getPositions() > 1) {
            MDUtils.setPositionName(ti.tags, "Pos" + position);
         }

         addImageToAcquisition(name, frame, channel, slice, position, ti);
      } catch (Exception e) {
         throw new MMScriptException(e);
      }
   }

   public void addImage(String name, TaggedImage taggedImg,
           boolean updateDisplay,
           boolean waitForDisplay) throws MMScriptException {
      acqMgr_.getAcquisition(name).insertImage(taggedImg, updateDisplay, waitForDisplay);
   }

   @Override
   public void closeAllAcquisitions() {
      acqMgr_.closeAll();
   }

   @Override
   public String[] getAcquisitionNames()
   {
      return acqMgr_.getAcquisitionNames();
   }

   // This function shouldn't be exposed in the API since users shouldn't need
   // direct access to Acquisition objects. For internal use, we have
   // getAcquisitionWithName(), below.
   @Override
   @Deprecated
   public MMAcquisition getAcquisition(String name) throws MMScriptException {
      return acqMgr_.getAcquisition(name);
   }

   public MMAcquisition getAcquisitionWithName(String name) throws MMScriptException {
      return acqMgr_.getAcquisition(name);
   }
   
   @Override
   public ImageCache getAcquisitionImageCache(String acquisitionName) throws MMScriptException {
      return getAcquisition(acquisitionName).getImageCache();
   }

   @Override
   public void message(final String text) throws MMScriptException {
      if (scriptPanel_ != null) {
         if (scriptPanel_.stopRequestPending()) {
            throw new MMScriptException("Script interrupted by the user!");
         }

         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               if (scriptPanel_ != null) {
                  scriptPanel_.message(text);
               }
            }
         });            
      }
   }

   @Override
   public void clearMessageWindow() throws MMScriptException {
      if (scriptPanel_ != null) {
         if (scriptPanel_.stopRequestPending()) {
            throw new MMScriptException("Script interrupted by the user!");
         }
         scriptPanel_.clearOutput();
      }
   }

   @Override
   public void setChannelContrast(String title, int channel, int min, int max)
         throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(title);
      acq.setChannelContrast(channel, min, max);
   }

   @Override
   public void setChannelName(String title, int channel, String name)
         throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(title);
      acq.setChannelName(channel, name);

   }

   @Override
   public void setChannelColor(String title, int channel, Color color)
         throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(title);
      acq.setChannelColor(channel, color.getRGB());
   }
   
   @Override
   public void setContrastBasedOnFrame(String title, int frame, int slice)
         throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(title);
      acq.setContrastBasedOnFrame(frame, slice);
   }

   @Override
   public void setStagePosition(double z) throws MMScriptException {
      try {
         core_.setPosition(core_.getFocusDevice(),z);
         core_.waitForDevice(core_.getFocusDevice());
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
   }

   @Override
   public void setRelativeStagePosition(double z) throws MMScriptException {
      try {
         core_.setRelativePosition(core_.getFocusDevice(), z);
         core_.waitForDevice(core_.getFocusDevice());
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
   }


   @Override
   public void setXYStagePosition(double x, double y) throws MMScriptException {
      try {
         core_.setXYPosition(core_.getXYStageDevice(), x, y);
         core_.waitForDevice(core_.getXYStageDevice());
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
   }

   @Override
   public void setRelativeXYStagePosition(double x, double y) throws MMScriptException {
      try {
         core_.setRelativeXYPosition(core_.getXYStageDevice(), x, y);
         core_.waitForDevice(core_.getXYStageDevice());
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
   }

   @Override
   public Point2D.Double getXYStagePosition() throws MMScriptException {
      String stage = core_.getXYStageDevice();
      if (stage.length() == 0) {
         throw new MMScriptException("XY Stage device is not available");
      }

      double x[] = new double[1];
      double y[] = new double[1];
      try {
         core_.getXYPosition(stage, x, y);
         Point2D.Double pt = new Point2D.Double(x[0], y[0]);
         return pt;
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
   }

   @Override
   public String getXYStageName() {
      return core_.getXYStageDevice();
   }

   @Override
   public void setXYOrigin(double x, double y) throws MMScriptException {
      String xyStage = core_.getXYStageDevice();
      try {
         core_.setAdapterOriginXY(xyStage, x, y);
      } catch (Exception e) {
         throw new MMScriptException(e);
      }
   }

   public AcquisitionWrapperEngine getAcquisitionEngine() {
      return engine_;
   }

   public SnapLiveManager getSnapLiveManager() {
      return snapLiveManager_;
   }

   @Override
   public String installAutofocusPlugin(String className) {
      try {
         return installAutofocusPlugin(Class.forName(className));
      } catch (ClassNotFoundException e) {
         String msg = "Internal error: AF manager not instantiated.";
         ReportingUtils.logError(e, msg);
         return msg;
      }
   }

   public String installAutofocusPlugin(Class<?> autofocus) {
      String msg = autofocus.getSimpleName() + " module loaded.";
      if (afMgr_ != null) {
         afMgr_.setAFPluginClassName(autofocus.getSimpleName());
         try {
            afMgr_.refresh();
         } catch (MMException e) {
            msg = e.getMessage();
            ReportingUtils.logError(e);
         }
      } else {
         msg = "Internal error: AF manager not instantiated.";
      }
      return msg;
   }

   public CMMCore getCore() {
      return core_;
   }

   @Override
   public IAcquisitionEngine2010 getAcquisitionEngine2010() {
      try {
         acquisitionEngine2010LoadingThread_.join();
         if (acquisitionEngine2010_ == null) {
            acquisitionEngine2010_ = (IAcquisitionEngine2010) acquisitionEngine2010Class_.getConstructor(ScriptInterface.class).newInstance(studio_);
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
   public void addImageProcessor(DataProcessor<TaggedImage> processor) {
      getAcquisitionEngine().addImageProcessor(processor);
   }

   @Override
   public void removeImageProcessor(DataProcessor<TaggedImage> processor) {
      getAcquisitionEngine().removeImageProcessor(processor);
   }

   @Override
   public ArrayList<DataProcessor<TaggedImage>> getImageProcessorPipeline() {
      return getAcquisitionEngine().getImageProcessorPipeline();
   }

   @Override
   public void registerProcessorClass(Class<? extends DataProcessor<TaggedImage>> processorClass, String name) {
      getAcquisitionEngine().registerProcessorClass(processorClass, name);
   }

   // NB will need @Override tags once these functions are exposed in the 
   // ScriptInterface.
   @Override
   public void setImageProcessorPipeline(List<DataProcessor<TaggedImage>> pipeline) {
      getAcquisitionEngine().setImageProcessorPipeline(pipeline);
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

   // Deprecated; use correctly spelled version. (Used to be part of API.)
   public void setAcqusitionSettings(SequenceSettings ss) {
      setAcquisitionSettings(ss);
   }
   
   @Override
   public String getAcquisitionPath() {
	   if (engine_ == null)
		   return null;
	   return engine_.getImageCache().getDiskLocation();
   }
   
   @Override
   public void promptToSaveAcquisition(String name, boolean prompt) throws MMScriptException {
      getAcquisition(name).promptToSave(prompt);
   }

   @Override
   public void registerForEvents(Object obj) {
      EventManager.register(obj);
   }

   @Override
   public void setROI(Rectangle r) throws MMScriptException {
      boolean liveRunning = false;
      if (isLiveModeOn()) {
         liveRunning = true;
         enableLiveMode(false);
      }
      try {
         core_.setROI(r.x, r.y, r.width, r.height);
      } catch (Exception e) {
         throw new MMScriptException(e.getMessage());
      }
      staticInfo_.refreshValues();
      if (liveRunning) {
         enableLiveMode(true);
      }
   }

   public void setAcquisitionEngine(AcquisitionWrapperEngine eng) {
      engine_ = eng;
   }
   
   @Override
   public Autofocus getAutofocus() {
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

   @Override
   public void setImageSavingFormat(Class imageSavingClass) throws MMScriptException {
      if (! (imageSavingClass.equals(TaggedImageStorageDiskDefault.class) || 
              imageSavingClass.equals(TaggedImageStorageMultipageTiff.class))) {
         throw new MMScriptException("Unrecognized saving class");
      }
      ImageUtils.setImageStorageClass(imageSavingClass);
      if (acqControlWin_ != null) {
         acqControlWin_.updateSavingTypeButtons();
      }
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
   public void logMessage(String msg) {
      ReportingUtils.logMessage(msg);
   }

   @Override
   public void showMessage(String msg) {
      ReportingUtils.showMessage(msg);
   }
   
   @Override
   public void showMessage(String msg, Component parent) {
      ReportingUtils.showMessage(msg, parent);
   }

   @Override
   public void logError(Exception e, String msg) {
      ReportingUtils.logError(e, msg);
   }

   @Override
   public void logError(Exception e) {
      ReportingUtils.logError(e);
   }

   @Override
   public void logError(String msg) {
      ReportingUtils.logError(msg);
   }

   @Override
   public void showError(Exception e, String msg) {
      ReportingUtils.showError(e, msg);
   }

   @Override
   public void showError(Exception e) {
      ReportingUtils.showError(e);
   }

   @Override
   public void showError(String msg) {
      ReportingUtils.showError(msg);
   }

   @Override
   public void showError(Exception e, String msg, Component parent) {
      ReportingUtils.showError(e, msg, parent);
   }

   @Override
   public void showError(Exception e, Component parent) {
      ReportingUtils.showError(e, parent);
   }

   @Override
   public void showError(String msg, Component parent) {
      ReportingUtils.showError(msg, parent);
   }

   @Override
   public void autostretchCurrentWindow() {
      VirtualAcquisitionDisplay display = VirtualAcquisitionDisplay.getDisplay(WindowManager.getCurrentImage());
      if (display != null) {
         display.getHistograms().autoscaleAllChannels();
      }
   }
}
