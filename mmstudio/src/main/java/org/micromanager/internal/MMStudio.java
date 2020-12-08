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

import com.google.common.eventbus.Subscribe;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.Toolbar;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import org.micromanager.Album;
import org.micromanager.Application;
import org.micromanager.ApplicationSkin;
import org.micromanager.AutofocusManager;
import org.micromanager.CompatibilityInterface;
import org.micromanager.LogManager;
import org.micromanager.PluginManager;
import org.micromanager.PositionList;
import org.micromanager.PositionListManager;
import org.micromanager.PropertyMap;
import org.micromanager.ScriptController;
import org.micromanager.ShutterManager;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.acquisition.AcquisitionManager;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.acquisition.internal.DefaultAcquisitionManager;
import org.micromanager.acquisition.internal.IAcquisitionEngine2010;
import org.micromanager.alerts.AlertManager;
import org.micromanager.alerts.internal.DefaultAlertManager;
import org.micromanager.data.DataManager;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultDataManager;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.events.AutofocusPluginShouldInitializeEvent;
import org.micromanager.events.ChannelExposureEvent;
import org.micromanager.events.EventManager;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.PropertiesChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.events.SystemConfigurationLoadedEvent;
import org.micromanager.events.internal.CoreEventCallback;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.internal.diagnostics.EDTHangLogger;
import org.micromanager.internal.diagnostics.ThreadExceptionLogger;
import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.dialogs.CalibrationListDlg;
import org.micromanager.internal.dialogs.IJVersionCheckDlg;
import org.micromanager.internal.dialogs.IntroDlg;
import org.micromanager.internal.dialogs.OptionsDlg;
import org.micromanager.internal.dialogs.RegistrationDlg;
import org.micromanager.internal.hcwizard.MMConfigFileException;
import org.micromanager.internal.hcwizard.MicroscopeModel;
import org.micromanager.internal.logging.LogFileManager;
import org.micromanager.internal.menus.MMMenuBar;
import org.micromanager.internal.navigation.UiMovesStageManager;
import org.micromanager.internal.pipelineinterface.PipelineFrame;
import org.micromanager.internal.pluginmanagement.DefaultPluginManager;
import org.micromanager.internal.positionlist.MMPositionListDlg;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultAutofocusManager;
import org.micromanager.internal.utils.HotKeys;
import org.micromanager.internal.utils.UserProfileManager;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.UIMonitor;
import org.micromanager.internal.utils.WaitDialog;
import org.micromanager.internal.zmq.ZMQServer;
import org.micromanager.profile.internal.DefaultUserProfile;
import org.micromanager.profile.internal.UserProfileAdmin;
import org.micromanager.profile.internal.gui.HardwareConfigurationManager;
import org.micromanager.quickaccess.QuickAccessManager;
import org.micromanager.quickaccess.internal.DefaultQuickAccessManager;


/*
 * Implements the Studio (i.e. primary API) and does various other
 * tasks that should probably be refactored out at some point.
 */
public final class MMStudio implements Studio, CompatibilityInterface, PositionListManager, Application {

   private static final long serialVersionUID = 3556500289598574541L;
   
   private static final String AUTOFOCUS_DEVICE = "autofocus_device";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;
   // Note that this property is set by one of the launcher scripts.
   private static final String SHOULD_DELETE_OLD_CORE_LOGS = "whether or not to delete old MMCore log files";
   private static final String SHOULD_RUN_ZMQ_SERVER = "run ZQM server";
   private static final String CORE_LOG_LIFETIME_DAYS = "how many days to keep MMCore log files, before they get deleted";
   private static final String CIRCULAR_BUFFER_SIZE = "size, in megabytes of the circular buffer used to temporarily store images before they are written to disk";
   private static final String AFFINE_TRANSFORM_LEGACY = "affine transform for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   private static final String AFFINE_TRANSFORM = "affine transform parameters for mapping camera coordinates to stage coordinates for a specific pixel size config: ";
   private static final String EXPOSURE_KEY = "Exposure_";
   
   // GUI components
   private boolean wasStartedAsImageJPlugin_;
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;
   
   
   // Managers
   private AcquisitionManager acquisitionManager_;
   private DataManager dataManager_;
   private DisplayManager displayManager_;
   private DefaultPluginManager pluginManager_;
   private SnapLiveManager snapLiveManager_;
   private DefaultAutofocusManager afMgr_;
   private String sysConfigFile_;
   private ShutterManager shutterManager_;
   private Album albumInstance_;
   private DefaultQuickAccessManager quickAccess_;
   private DefaultAlertManager alertManager_;
   private DefaultEventManager eventManager_;
   private ApplicationSkin daytimeNighttimeManager_;
   private UserProfileManager userProfileManager_;
   private UiMovesStageManager uiMovesStageManager_;
   
   // MMcore
   private CMMCore core_;
   private AcquisitionWrapperEngine acqEngine_;
   private PositionList posList_;
   private MMPositionListDlg posListDlg_;
   private boolean isProgramRunning_;
   private boolean configChanged_ = false;
   private boolean isClickToMoveEnabled_ = false;

   private ScriptPanel scriptPanel_;
   private ZMQServer zmqServer_;
   private PipelineFrame pipelineFrame_;
   private org.micromanager.internal.utils.HotKeys hotKeys_;

   // Our instance
   // TODO: make this non-static
   private static MMStudio studio_;
   // Our menubar
   private MMMenuBar mmMenuBar_;
   // Our primary window.
   private static MainFrame frame_;
   // Callback
   private CoreEventCallback coreCallback_;
   // Lock invoked while shutting down
   private final Object shutdownLock_ = new Object();

   private Thread acquisitionEngine2010LoadingThread_ = null;
   private Class<?> acquisitionEngine2010Class_ = null;
   private IAcquisitionEngine2010 acquisitionEngine2010_ = null;
   private StaticInfo staticInfo_;
   
   
   /**
    * Main procedure for stand alone operation.
    * @param args
    */
   public static void main(String args[]) {
      String profileNameAutoStart = null; //The name of the user profile that Micro-Manager should start up with. In the case that this is left as null then a splash screen will request that the user select a profile before startup.
      for (int i=0; i<args.length; i++) { // a library for the parsing of arguments such as apache commons - cli would make this more robust if needed.
          if (args[i].equals("-profile")) {
              if (i < args.length-1) {
                  i++;
                  profileNameAutoStart = args[i];
              } else {
                  ReportingUtils.showError("Micro-Manager received no value for the `-profile` startup argument.");
              }
          } else {
              ReportingUtils.showError("Micro-Manager received unknown startup argument: " + args[i]);
          }
      }
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         MMStudio mmStudio = new MMStudio(false, profileNameAutoStart);
      } catch (ClassNotFoundException | IllegalAccessException | 
              InstantiationException | UnsupportedLookAndFeelException e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      }
   }

   /**
    * MMStudio constructor
    * @param startAsImageJPlugin Indicates if we're running from "within"
    * ImageJ, which governs our behavior when we are closed.
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MMStudio(boolean startAsImageJPlugin) {
       this(startAsImageJPlugin, null);
   }
   
   /**
    * MMStudio constructor
    * @param startAsImageJPlugin Indicates if we're running from "within"
    * ImageJ, which governs our behavior when we are closed.
    * @param profileNameAutoStart The name of a user profile. This profile and
    * its most recently used hardware configuration will be to automatically loaded. 
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MMStudio(boolean startAsImageJPlugin, String profileNameAutoStart) {
      wasStartedAsImageJPlugin_ = startAsImageJPlugin;

      // TODO Of course it is crazy to do all of the following in the
      // constructor.

      // Bad Things will happen if two are instantiated (a lot of legacy
      // internal code assumes a single instance, and some internal services
      // are singletons), so just prevent that for now.
      // Note that studio_ will remain non-null if this constructor fails,
      // preventing subsequent instantiation. This is not ideal but
      // intentional, because we do not currently have a way to cleanly exit a
      // partial initialization.
      
      // TODO Management of the singleton instance has not been done in a clean
      // manner. In fact, there should be an API method to instantiate Studio,
      // rather than calling the constructor directly.
      if (studio_ != null) {
         throw new RuntimeException("Creating more than one instance of MMStudio is not supported");
      }
      studio_ = this;
      isProgramRunning_ = true;

      ThreadExceptionLogger.setUp();

      // The Core is created as early as possible, so that we can make use of
      // the CoreLog (and also to fail early if the MMCoreJ is not available)
      try {
         core_ = new CMMCore();
      } catch(UnsatisfiedLinkError ex) {
         ReportingUtils.showError(ex, 
               "Failed to load the MMCoreJ_wrap native library");
      } catch(NoSuchMethodError ex) {
         ReportingUtils.showError(ex, 
               "Incompatible version of MMCoreJ_wrap native library");
      }
      
      // Start up multiple managers.  
      
      userProfileManager_ = new UserProfileManager();       
      
      // Essential GUI settings in preparation of the intro dialog
      daytimeNighttimeManager_ = DaytimeNighttime.create(studio_);
      
      // Start loading plugins in the background
      // Note: plugin constructors should not expect a fully constructed Studio!
      pluginManager_ = new DefaultPluginManager(studio_);
      
      // Lots of places use this. instantiate it first.
      eventManager_ = new DefaultEventManager();

      // used by Snap/Live Manager and StageControlFrame
      uiMovesStageManager_ = new UiMovesStageManager(this);
      events().registerForEvents(uiMovesStageManager_);
      
      snapLiveManager_ = new SnapLiveManager(this, core_);
      events().registerForEvents(snapLiveManager_);

      shutterManager_ = new DefaultShutterManager(studio_);
      // DisplayManager needs to be created before Pipelineframe and albumInstance
      displayManager_ = new DefaultDisplayManager(this);
      albumInstance_ = new DefaultAlbum(studio_);

      // The tools menu depends on the Quick-Access Manager.
      quickAccess_ = new DefaultQuickAccessManager(studio_);    

      acqEngine_ = new AcquisitionWrapperEngine();
      acqEngine_.setParentGUI(this);
      acqEngine_.setZStageDevice(core_.getFocusDevice());


      
      // Load, but do not show, image pipeline panel.
      // Note: pipelineFrame is used in the dataManager, however, pipelineFrame 
      // needs the dataManager.  Let's hope for the best....
      dataManager_ = new DefaultDataManager(studio_);
      if (pipelineFrame_ == null) { //Create the pipelineframe if it hasn't already been done.
         pipelineFrame_ = new PipelineFrame(studio_);
      }

      alertManager_ = new DefaultAlertManager(studio_);
      
      afMgr_ = new DefaultAutofocusManager(studio_);
      afMgr_.refresh();
      String afDevice = profile().getSettings(MMStudio.class).
              getString(AUTOFOCUS_DEVICE, "");
      if (afMgr_.hasDevice(afDevice)) {
         afMgr_.setAutofocusMethodByName(afDevice);
      }

      posList_ = new PositionList();
      acqEngine_.setPositionList(posList_);

      
      // Tell Core to start logging
      initializeLogging(core_);
      
      // We need to be subscribed to the global event bus for plugin loading
      events().registerForEvents(this);

      // Start loading acqEngine in the background
      prepAcquisitionEngine();

      RegistrationDlg.showIfNecessary(this);
      
      // We wait for plugin loading to finish now, since IntroPlugins may be
      // needed to display the intro dialog. Fortunately, plugin loading is
      // fast in 2.0 (it used to be very slow in 1.4, so we loaded plugins in
      // parallel with the intro dialog).
      // TODO Remove time out (With the current loading mechanism, the only
      // case where the plugin loading thread will hang due to individual
      // plugins is if a plugin constructor hangs, which is a case where we
      // should just hang rather than pretend nothing is wrong.)
      try {
         pluginManager_.waitForInitialization(15000);
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      }
      if (!pluginManager_.isInitializationComplete()) {
         ReportingUtils.logMessage("Warning: Plugin loading did not finish within 15 seconds; continuing anyway");
      }
      else {
         ReportingUtils.logMessage("Finished waiting for plugins to load");
      }

      ToolTipManager ttManager = ToolTipManager.sharedInstance();
      ttManager.setDismissDelay(TOOLTIP_DISPLAY_DURATION_MILLISECONDS);
      ttManager.setInitialDelay(TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS);


      UserProfileAdmin profileAdmin = userProfileManager_.getAdmin();
      UUID profileUUID = profileAdmin.getUUIDOfDefaultProfile();
      try {
          if (profileNameAutoStart != null) {
            for (Map.Entry<UUID,String> entry : profileAdmin.getProfileUUIDsAndNames().entrySet()){
                String name = entry.getValue();
                if (name.equals(profileNameAutoStart)){
                    UserProfile profile = profileAdmin.getNonSavingProfile(entry.getKey());
                    profileAdmin.setCurrentUserProfile(entry.getKey());
                    daytimeNighttimeManager_.setSkin(daytimeNighttimeManager_.getSkin());
                    sysConfigFile_ = HardwareConfigurationManager.getRecentlyUsedConfigFilesFromProfile(profile).get(0);
                    break;
                }
            }
            if (sysConfigFile_ == null) {
                ReportingUtils.showMessage("A hardware configuration for a profile matching name: " + profileNameAutoStart + " could not be found");
            }
          }
          else if (StartupSettings.create(profileAdmin.getNonSavingProfile(profileUUID)).
               shouldSkipUserInteractionWithSplashScreen()) {
            List<String> recentConfigs = HardwareConfigurationManager.
                  getRecentlyUsedConfigFilesFromProfile(
                        profile());
            sysConfigFile_ = recentConfigs.isEmpty() ? null : recentConfigs.get(0);
         }
         else {
            IntroDlg introDlg = new IntroDlg(this, MMVersion.VERSION_STRING);
            if (!introDlg.okChosen()) {
               closeSequence(false);
               return;
            }

            profileUUID = introDlg.getSelectedProfileUUID();
            profileAdmin.setCurrentUserProfile(profileUUID);

            sysConfigFile_ = introDlg.getSelectedConfigFilePath();
         }
      }
      catch (IOException ex) {
         // TODO We should fall back to virtual profile
         ReportingUtils.showError(ex, "Error accessing user profiles");
      }

      // Profile may have been switched in Intro Dialog, so reflect its setting
      core_.enableDebugLog(OptionsDlg.getIsDebugLogEnabled(studio_));

      IJVersionCheckDlg.execute(studio_);

      org.micromanager.internal.diagnostics.gui.ProblemReportController.startIfInterruptedOnExit();

      // This entity is a class property to avoid garbage collection.
      coreCallback_ = new CoreEventCallback(studio_, acqEngine_);

      // Load hardware configuration
      // Note that this also initializes Autofocus plugins.
      // TODO: This should probably be run on a background thread, while we set
      // up GUI elements (but various managers will need to be aware of this)
      if (sysConfigFile_ != null) {  // we do allow running Micro-Manager without 
         // a config file!
         if (!loadSystemConfiguration()) {
            // TODO Do we still need to turn errors off to prevent spurious error messages?
            ReportingUtils.showErrorOn(false);
         }
      }
      
      // Create Multi-D window here but do not show it.
      // This window needs to be created in order to properly set the 
      // "ChannelGroup" based on the Multi-D parameters
      acqControlWin_ = new AcqControlDlg(acqEngine_, studio_);

      acquisitionManager_ = new DefaultAcquisitionManager(this, acqEngine_,
            acqControlWin_);

      try {
         core_.setCircularBufferMemoryFootprint(getCircularBufferSize());
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      
      
      // Arrange to log stack traces when the EDT hangs.
      // Use parameters that ensure a stack trace dump within 10 seconds of an
      // EDT hang (and _no_ dump on hangs under 5.5 seconds)
      EDTHangLogger.startDefault(core_, 4500, 1000);

      // Move ImageJ window to place where it last was if possible or else
      // (150,150) if not
      if (IJ.getInstance() != null) {
         Point ijWinLoc = IJ.getInstance().getLocation();
         if (GUIUtils.getGraphicsConfigurationContaining(ijWinLoc.x, ijWinLoc.y) == null) {
            // only reach this code if the pref coordinates are off screen
            IJ.getInstance().setLocation(150, 150);
         }
      }
      
      // Load (but do no show) the scriptPanel
      createScriptPanel();
      
      // Now create and show the main window
      mmMenuBar_ = MMMenuBar.createMenuBar(studio_);
      frame_ = new MainFrame(this, core_);
      staticInfo_ = new StaticInfo(studio_, frame_);
      events().registerForEvents(staticInfo_);
      frame_.toFront();
      frame_.setVisible(true);
      ReportingUtils.SetContainingFrame(frame_);
      frame_.initializeConfigPad();

      // We wait until after showing the main window to enable hot keys
      hotKeys_ = new HotKeys();
      hotKeys_.loadSettings(userProfileManager_.getProfile());


      // Switch error reporting back on TODO See above where it's turned off
      ReportingUtils.showErrorOn(true);
      
      events().registerForEvents(displayManager_);
      
      // Tell the GUI to reflect the hardware configuration. (The config was
      // loaded before creating the GUI, so we need to reissue the event.)
      events().post(new SystemConfigurationLoadedEvent());

      executeStartupScript();

      updateGUI(true);
      
      // Give plugins a chance to initialize their state
      events().post(new StartupCompleteEvent());
      
      // start zmq server if so desired
      if (getShouldRunZMQServer()) {
         runZMQServer();
      }
      
   }

   private void initializeLogging(CMMCore core) {
      core.enableStderrLog(true);
      core.enableDebugLog(OptionsDlg.getIsDebugLogEnabled(studio_));
      ReportingUtils.setCore(core);

      // Set up logging to CoreLog file
      String logFileName = LogFileManager.makeLogFileNameForCurrentSession();
      new File(logFileName).getParentFile().mkdirs();
      try {
         core.setPrimaryLogFile(logFileName);
      }
      catch (Exception ignore) {
         // The Core will have logged the error to stderr, so do nothing.
      }

      if (getShouldDeleteOldCoreLogs()) {
         LogFileManager.deleteLogFilesDaysOld(
               getCoreLogLifetimeDays(), logFileName);
      }

      logStartupProperties();

      // Although our general rule is to perform identical logging regardless
      // of the current log level, we make an exception for UIMonitor, which we
      // enable only when debug logging is turned on (from the GUI).
      UIMonitor.enable(OptionsDlg.getIsDebugLogEnabled(studio_));
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

   /**
    * Spawn a new thread to load the acquisition engine jar, because this
    * takes significant time (TODO: Does it really, not that it is
    * AOT-compiled?).
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

   @Override
   public void setExposure(final double exposureTime) {
      // Avoid redundantly setting the exposure time.
      boolean shouldSetInCore = true;
      try {
         if (core_ != null && core_.getExposure() == exposureTime) {
            shouldSetInCore = false;
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error getting core exposure time");
      }
      // This is synchronized with the shutdown lock primarily so that
      // the exposure-time field in MainFrame won't cause issues when it loses
      // focus during shutdown.
      synchronized(shutdownLock_) {
         if (core_ == null) {
            // Just give up.
            return;
         }
         // Do this prior to updating the Core, so that if the Core posts a
         // callback resulting in a GUI refresh, we don't have the old
         // exposure time override the new one (since GUI refreshes result in
         // resetting the exposure to the old, stored-in-profile exposure time).
         String channelGroup = "";
         String channel = "";
         try {
            channelGroup = core_.getChannelGroup();
            channel = core_.getCurrentConfigFromCache(channelGroup);
            storeChannelExposureTime(channelGroup, channel, exposureTime);
         }
         catch (Exception e) {
            studio_.logs().logError("Unable to determine channel group");
         }

         if (!core_.getCameraDevice().equals("") && shouldSetInCore) {
            live().setSuspended(true);
            try {
               core_.setExposure(exposureTime);
               core_.waitForDevice(core_.getCameraDevice());
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Failed to set core exposure time.");
            }
            live().setSuspended(false);
         }

         // Display the new exposure time
         double exposure;
         try {
            exposure = core_.getExposure();
            events().post(new ChannelExposureEvent(exposure,
                     channelGroup, channel, true));
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Couldn't set exposure time.");
         }
      } // End synchronization check
   }

   public boolean getHideMDADisplayOption() {
      return AcqControlDlg.getShouldHideMDADisplay();
   }

   public void setCenterQuad() {
      ImagePlus curImage = WindowManager.getCurrentImage();
      if (curImage == null) {
         return;
      }

      Rectangle r = curImage.getProcessor().getRoi();
      int width = r.width / 2;
      int height = r.height / 2;
      int xOffset = r.x + width / 2;
      int yOffset = r.y + height / 2;

      curImage.setRoi(xOffset, yOffset, width, height);
      Roi roi = curImage.getRoi();
      try {
         setROI(updateROI(roi));
      }
      catch (Exception e) {
         // Core failed to set new ROI.
         logs().logError(e, "Unable to set new ROI");
      }
   }

   public void setROI() {
      ImagePlus curImage = WindowManager.getCurrentImage();
      if (curImage == null) {
         logs().showError("There is no open image window.");
         return;
      }

      Roi roi = curImage.getRoi();
      if (roi == null) {
         // Nothing to be done.
         logs().showError("There is no selection in the image window.\nUse the ImageJ rectangle tool to draw the ROI.");
         return;
      }
      if (roi.getType() == Roi.RECTANGLE) {
         try {
            setROI(updateROI(roi));
         }
         catch (Exception e) {
            // Core failed to set new ROI.
            logs().logError(e, "Unable to set new ROI");
         }
         return;
      }
      // Dealing with multiple ROIs; this may not be supported.
      try {
         if (!(roi instanceof ShapeRoi && core_.isMultiROISupported())) {
            handleError("ROI must be a rectangle.\nUse the ImageJ rectangle tool to draw the ROI.");
            return;
         }
      }
      catch (Exception e) {
         handleError("Unable to determine if multiple ROIs is supported");
         return;
      }
      // Generate list of rectangles for the ROIs.
      ArrayList<Rectangle> rois = new ArrayList<>();
      for (Roi subRoi : ((ShapeRoi) roi).getRois()) {
         // HACK: just use the bounding box of each sub-ROI. Determining if
         // sub-ROIs are rectangles is difficult (they "decompose" to Polygons
         // once there's more than one at a time, so as far as I can tell we
         // would have to test each angle of each polygon to see if it's
         // 90 degrees and has the correct handedness), and this provides a
         // good- enough solution for now.
         rois.add(updateROI(subRoi));
      }
      try {
         setMultiROI(rois);
      }
      catch (Exception e) {
         // Core failed to set new ROI.
         logs().logError(e, "Unable to set new ROI");
      }
   }

   /**
    * Adjust the provided rectangular ROI based on any current ROI that may be
    * in use.
    */
   private Rectangle updateROI(Roi roi) {
      Rectangle r = roi.getBounds();

      // If the image has ROI info attached to it, correct for the offsets.
      // Otherwise, assume the image was taken with the current camera ROI
      // (which is a horrendously buggy way to do things, but that was the
      // old behavior and I'm leaving it in case there are cases where it is
      // necessary).
      Rectangle originalROI = null;

      DataViewer viewer = displays().getActiveDataViewer();
      if (viewer != null) {
         try {
            List<Image> images = viewer.getDisplayedImages();
            // Just take the first one.
            originalROI = images.get(0).getMetadata().getROI();
         }
         catch (IOException e) {
            ReportingUtils.showError(e, "There was an error determining the selected ROI");
         }
      }

      if (originalROI == null) {
         try {
            originalROI = core().getROI();
         }
         catch (Exception e) {
            // Core failed to provide an ROI.
            logs().logError(e, "Unable to get core ROI");
            return null;
         }
      }

      r.x += originalROI.x;
      r.y += originalROI.y;
      return r;
   }

   public void clearROI() {
      live().setSuspended(true);
      try {
         core_.clearROI();
         staticInfo_.refreshValues();

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      live().setSuspended(false);
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
    * Returns singleton instance of MainFrame.
    * @return singleton instance of the mainFrame
    */
   public static MainFrame getFrame() {
      return frame_;
   }

   @Override
   public JFrame getMainWindow() {
      return frame_;
   }
   
   public MMMenuBar getMMMenubar() {
      return mmMenuBar_;
   }

   public void promptToSaveConfigPresets() {
      File f = FileDialogs.save(frame_,
            "Save the configuration file", FileDialogs.MM_CONFIG_FILE);
      if (f != null) {
         try {
            saveConfigPresets(f.getAbsolutePath(), true);
         }
         catch (IOException e) {
            // This should be impossible as we set shouldOverwrite to true.
            logs().logError(e, "Error saving config presets");
         }
      }
   }

   @Override
   public void saveConfigPresets(String path, boolean allowOverwrite) throws IOException {
      if (!allowOverwrite && new File(path).exists()) {
         throw new IOException("Cannot overwrite existing file at " + path);
      }
      MicroscopeModel model = new MicroscopeModel();
      try {
         model.loadFromFile(sysConfigFile_);
         model.createSetupConfigsFromHardware(core_);
         model.createResolutionsFromHardware(core_);
         model.saveToFile(path);
         sysConfigFile_ = path;
         configChanged_ = false;
         frame_.setConfigSaveButtonStatus(configChanged_);
         frame_.setConfigText(sysConfigFile_);
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

   protected void changeBinning() {
      String mode = frame_.getBinMode();
      live().setSuspended(true);
      try {
         if (!isCameraAvailable() || mode == null) {
            // No valid option.
            live().setSuspended(false);
            return;
         }
         if (core_.getProperty(StaticInfo.cameraLabel_,
                 MMCoreJ.getG_Keyword_Binning()).equals(mode)) {
            // No change in binning mode.
            live().setSuspended(false);
            return;
         }
         core_.setProperty(StaticInfo.cameraLabel_, MMCoreJ.getG_Keyword_Binning(), mode);
         staticInfo_.refreshValues();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      live().setSuspended(false);
   }

   public void createPropertyEditor() {
      if (propertyBrowser_ != null) {
         propertyBrowser_.dispose();
      }

      propertyBrowser_ = new PropertyEditor(studio_);
      this.events().registerForEvents(propertyBrowser_);
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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
      scriptPanel_ = new ScriptPanel(studio_);
   }
   
  public void runZMQServer() {
      if (zmqServer_ == null) {
         //Make a function that passes existing instances of core and studio,
         //rather than constructing them
         Function<Class, Object> instanceGrabberFunction = new Function<Class, Object>() {
            @Override
            public Object apply(Class baseClass) {
               //return instances of existing objects
               if (baseClass.equals(Studio.class)) {
                  return studio_;
               } else if (baseClass.equals(CMMCore.class)) {
                  return studio_.getCMMCore();
               }
               return null;
            }
         };
         try {
            //It appears that every plugin has its own ClassLoader. Need to extract all of these and pass to
            //ZMQServer, so that knows where to search for classes to load. If we don't do this, and just create
            //new ClassLoaders to instantiate objects, static varibles will not be shared across instances
            //created by the two objects, leading to confusing behavior
            Collection<ClassLoader> classLoaders = new HashSet<ClassLoader>();
            for (Object plugin : plugins().getMenuPlugins().values()) {
               classLoaders.add(plugin.getClass().getClassLoader());
            }


            zmqServer_ = new ZMQServer(classLoaders, instanceGrabberFunction, new String[]{"org.micromanager.internal"});
            logs().logMessage("Initialized ZMQ Server on port: " + ZMQServer.DEFAULT_MASTER_PORT_NUMBER);
         } catch (URISyntaxException | UnsupportedEncodingException e) {
            studio_.logs().logError("Failed to initialize ZMQ Server");
            studio_.logs().logError(e);
         }

      }
   }

   public void stopZMQServer() {
      if (zmqServer_ != null) {
         zmqServer_.close();
         logs().logMessage("Stopped ZMQ Server");
         zmqServer_ = null;
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

   public void updateCenterAndDragListener(boolean isEnabled) {
      isClickToMoveEnabled_ = isEnabled;
      if (isEnabled) {
         IJ.setTool(Toolbar.HAND);
      }
      mmMenuBar_.getToolsMenu().setMouseMovesStage(isEnabled);
      events().post(new MouseMovesStageStateChangeEvent(isEnabled));
   }

   public boolean isClickToMoveEnabled() {
      return isClickToMoveEnabled_;
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
         showPositionList();
      }
      if (posListDlg_ != null) {
         posListDlg_.markPosition(false);
      }
   }

   @Override
   public boolean versionLessThan(String version) throws NumberFormatException {
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
      if (v2.length < 2 || v2[1].equals("") ) {
         return false;
      }
      if (v.length < 2 ) {
         ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
         return true;
      }
      if (Integer.parseInt(v[1]) < Integer.parseInt(v2[1])) {
         ReportingUtils.showError("This code needs Micro-Manager version " + version + " or greater");
         return false;
      }
      return true;
   }

   private void configureBinningCombo() throws Exception {
      if (StaticInfo.cameraLabel_.length() > 0) {
         frame_.configureBinningComboForCamera(StaticInfo.cameraLabel_);
      }
   }

   // TODO: This method should be renamed!
   // resetGUIForNewHardwareConfig or something like that.
   // TODO: this method should be automatically invoked when
   // SystemConfigurationLoaded event occurs, and in no other way.
   // Better: each of these entities should listen for
   // SystemConfigurationLoaded itself and handle its own updates.
   public void initializeGUI() {
      try {
         if (staticInfo_ != null) {
            staticInfo_.refreshValues();
            if (acqEngine_ != null) {
               acqEngine_.setZStageDevice(StaticInfo.zStageLabel_);  
            }
         }

         // Rebuild stage list in XY PositinList
         if (posListDlg_ != null) {
            posListDlg_.rebuildAxisList();
         }

         if (frame_ != null) {
            configureBinningCombo();
            frame_.updateAutofocusButtons(afMgr_.getAutofocusMethod() != null);
            updateGUI(true);
         }
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

         frame_.updateAutofocusButtons(afMgr_.getAutofocusMethod() != null);

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
            propertyBrowser_.refresh(fromCache);
         }

         ReportingUtils.logMessage("Finished updating GUI");
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      frame_.setConfigText(sysConfigFile_);
      events().post(new GUIRefreshEvent());
   }

   /**
    * Cleans up resources while shutting down 
    * 
    * @param quitInitiatedByImageJ
    * @return Whether or not cleanup was successful. Shutdown should abort
    *         on failure.
    */
   private boolean cleanupOnClose(boolean quitInitiatedByImageJ) {
      // Save config presets if they were changed.
      if (configChanged_) {
         Object[] options = {"Yes", "No"};
         int n = JOptionPane.showOptionDialog(null,
               "Save Changed Configuration?", "Micro-Manager",
               JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
               null, options, options[0]);
         if (n == JOptionPane.YES_OPTION) {
            promptToSaveConfigPresets();
            // if the configChanged_ flag did not become false, the user 
            // must have cancelled the configuration saving and we should cancel
            // quitting as well
            if (configChanged_) {
               return false;
            }
         }
      }

      // check needed to avoid deadlock
      if (!quitInitiatedByImageJ) {
         if (!WindowManager.closeAllWindows()) {
            core_.logMessage("Failed to close some windows");
         }
      }
  
      if (scriptPanel_ != null) {
         scriptPanel_.closePanel();
         scriptPanel_ = null;
      }
      
      if (zmqServer_ != null) {
         zmqServer_.close();
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
      
      if (acqEngine_ != null) {
         acqEngine_.shutdown();
      }

      synchronized (shutdownLock_) {
         EDTHangLogger.stopDefault();

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
      // TODO All of the following should be taken care of by specific modules

      if (frame_ != null) {
         frame_.savePrefs();
      }

      // NOTE: do not save auto shutter state
      if (afMgr_ != null && afMgr_.getAutofocusMethod() != null) {
         profile().getSettings(MMStudio.class).putString(
               AUTOFOCUS_DEVICE, afMgr_.getAutofocusMethod().getName());
      }
   }

   public synchronized boolean closeSequence(boolean quitInitiatedByImageJ) {
      if (!getIsProgramRunning()) {
         if (core_ != null) {
            core_.logMessage("MMStudio::closeSequence called while isProgramRunning_ is false");
         }
         return true;
      }
      // Send two shutdown events: one that our internal logic consumes, and
      // one that's accessible in the API.
      InternalShutdownCommencingEvent internalEvent = new InternalShutdownCommencingEvent();
      events().post(internalEvent);
      if (internalEvent.isCanceled()) {
         // Shutdown cancelled by user.
         return false;
      }
      ShutdownCommencingEvent externalEvent = new ShutdownCommencingEvent();
      events().post(externalEvent);
      if (externalEvent.isCanceled()) {
         // Shutdown cancelled by user.
         return false;
      }

      if (!cleanupOnClose(quitInitiatedByImageJ)) {
         return false;
      }

      isProgramRunning_ = false;

      saveSettings();
      try {
         frame_.getConfigPad().saveSettings();
         hotKeys_.saveSettings(userProfileManager_.getProfile());
      } catch (NullPointerException e) {
         if (core_ != null) {
            ReportingUtils.logError(e);
         }
      }
      try {
         userProfileManager_.shutdown();
      }
      catch (InterruptedException notExpected) {
         Thread.currentThread().interrupt();
      }

      if (frame_ != null) {
         frame_.dispose();
         frame_ = null;
      }

      try {
         ((DefaultUserProfile) profile()).close();
      }
      catch (InterruptedException notUsedByUs) {
         Thread.currentThread().interrupt();
      }
      userProfileManager_.getAdmin().shutdownAutosaves();

      boolean shouldCloseWholeApp = OptionsDlg.getShouldCloseOnExit(studio_);
      if (shouldCloseWholeApp && !quitInitiatedByImageJ) {
         if (wasStartedAsImageJPlugin_) {
            // Let ImageJ do the quitting
            ImageJ ij = IJ.getInstance();
            if (ij != null) {
               ij.quit();
            }
         }
         else {
            // We are on our own to actually exit
            System.exit(0);
         }
      }

      studio_ = null;

      return true;
   }

   public boolean getIsProgramRunning() {
      return isProgramRunning_;
   }

   private void executeStartupScript() {
      String filename = ScriptPanel.getStartupScript(this);
      if (filename == null || filename.length() <= 0) {
         logs().logMessage("No startup script to run");
         return;
      }

      File f = new File(filename);
      if (!f.exists()) {
         logs().logMessage("Startup script (" +
               f.getAbsolutePath() + ") not present");
         return;
      }

      ReportingUtils.logMessage("Running startup script (" +
               f.getAbsolutePath() + ")...");
      WaitDialog waitDlg = new WaitDialog(
            "Executing startup script, please wait...");
      waitDlg.showDialog();
      try {
         scriptPanel_.runFile(f);
      }
      finally {
         waitDlg.closeDialog();
      }
      ReportingUtils.logMessage("Finished running startup script");
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
      if (frame_ != null) {
         frame_.setEnabled(false);
      }

      try {
         if (sysConfigFile_ != null && sysConfigFile_.length() > 0) {
            GUIUtils.preventDisplayAdapterChangeExceptions();
            core_.waitForSystem();
            coreCallback_.setIgnoring(true);
            HardwareConfigurationManager.
                  create(profile(), core_).
                  loadHardwareConfiguration(sysConfigFile_);
            coreCallback_.setIgnoring(false);
            GUIUtils.preventDisplayAdapterChangeExceptions();
            events().post(new AutofocusPluginShouldInitializeEvent());
            FileDialogs.storePath(FileDialogs.MM_CONFIG_FILE, new File(sysConfigFile_));
         }
      } catch (final Exception err) {
         GUIUtils.preventDisplayAdapterChangeExceptions();

         waitDlg.closeDialog(); // Prevent from obscuring error alert
         ReportingUtils.showError(err,
               "Failed to load hardware configuration",
               null);
         result = false;
      } finally {
         waitDlg.closeDialog();
         if (frame_ != null) {
            frame_.setEnabled(true);
         }

      }

      initializeGUI();

      return result;
   }

   public void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(acqEngine_, studio_);
         }
         if (acqControlWin_.isActive()) {
            acqControlWin_.setTopPosition();
         }

         acqControlWin_.setVisible(true);
         
         acqControlWin_.repaint();

      } catch (Exception exc) {
         ReportingUtils.showError(exc,
               "\nAcquisition window failed to open due to invalid or corrupted settings.\n"
               + "Try resetting registry settings to factory defaults (Menu Tools|Options).");
      }
   }

   public void updateChannelCombos() {
      if (acqControlWin_ != null) {
         acqControlWin_.updateChannelAndGroupCombo();
      }
   }

   public void autofocusNow() {
      if (afMgr_.getAutofocusMethod() != null) {
         new Thread() {
            @Override
            public void run() {
               live().setSuspended(true);
               try {
                  afMgr_.getAutofocusMethod().fullFocus();
               }
               catch (Exception ex) {
                  ReportingUtils.showError(ex, "An error occurred during autofocus");
               }
               live().setSuspended(false);
            }
         }.start();
      }
      else {
         ReportingUtils.showError("No autofocus device is selected.");
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
   public void logStartupProperties() {
      core_.logMessage("User: " + System.getProperty("user.name"));
      String hostname;
      try {
         hostname = java.net.InetAddress.getLocalHost().getHostName();
      }
      catch (UnknownHostException e) {
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
   public void showPositionList() {
      if (posListDlg_ == null) {
         posListDlg_ = new MMPositionListDlg(studio_, posList_, 
                 acqControlWin_);
         posListDlg_.addListeners();
      }
      posListDlg_.setVisible(true);
   }

   public void setConfigChanged(boolean status) {
      configChanged_ = status;
      frame_.setConfigSaveButtonStatus(configChanged_);
   }

   public boolean hasConfigChanged() {
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
      return this.profile().getSettings(MMStudio.class).getDouble(
              EXPOSURE_KEY + channelGroup + "_" + channel, defaultExp);
   }

   public void storeChannelExposureTime(String channelGroup, String channel,
                                      double exposure) {
      this.profile().getSettings(MMStudio.class).putDouble(
              EXPOSURE_KEY + channelGroup + "_" + channel, exposure);
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
         storeChannelExposureTime(channelGroup, channel, exposure);
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

   public void enableRoiButtons(final boolean enabled) {
      frame_.enableRoiButtons(enabled);
   }

   @Override
   public void setPositionList(PositionList pl) {
      // use serialization to clone the PositionList object
      posList_ = pl; // PositionList.newInstance(pl);
      SwingUtilities.invokeLater(() -> {
         if (posListDlg_ != null) {
            posListDlg_.setPositionList(posList_);
         }
         if (acqEngine_ != null) {
            acqEngine_.setPositionList(posList_);
         }
         if (acqControlWin_ != null) {
            acqControlWin_.updateGUIContents();
         }
      });
   }

   @Override
   public PositionList getPositionList() {
      return posList_;
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
      return acqEngine_;
   }

   public CMMCore getCore() {
      return core_;
   }

   public IAcquisitionEngine2010 getAcquisitionEngine2010() {
      try {
         acquisitionEngine2010LoadingThread_.join();
         if (acquisitionEngine2010_ == null) {
            acquisitionEngine2010_ = 
                    (IAcquisitionEngine2010) 
                    acquisitionEngine2010Class_.getConstructor(Studio.class).newInstance(studio_);
         }
         return acquisitionEngine2010_;
      } catch (IllegalAccessException | 
              IllegalArgumentException | 
              InstantiationException | 
              InterruptedException | 
              NoSuchMethodException | 
              SecurityException | 
              InvocationTargetException e) {
         ReportingUtils.logError(e);
         return null;
      }
   }

   @Override
   public void setROI(Rectangle r) throws Exception {
      live().setSuspended(true);
      core_.setROI(r.x, r.y, r.width, r.height);
      staticInfo_.refreshValues();
      live().setSuspended(false);
   }

   public void setMultiROI(List<Rectangle> rois) throws Exception {
      live().setSuspended(true);
      core_.setMultiROI(rois);
      staticInfo_.refreshValues();
      live().setSuspended(false);
   }

   public void setAcquisitionEngine(AcquisitionWrapperEngine eng) {
      acqEngine_ = eng;
   }

   @Override
   public void showAutofocusDialog() {
      afMgr_.showOptionsDialog();
   }

   @Override
   public AutofocusManager getAutofocusManager() {
      return afMgr_;
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
      return userProfileManager_.getProfile();
   }
   @Override
   public UserProfile getUserProfile() {
      return profile();
   }
   
   public UserProfileAdmin profileAdmin() {
       return userProfileManager_.getAdmin();
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
      return albumInstance_;
   }

   @Override
   public Album getAlbum() {
      return album();
   }

   @Override
   public EventManager events() {
      return eventManager_;
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
   public QuickAccessManager quickAccess() {
      return quickAccess_;
   }

   @Override
   public QuickAccessManager getQuickAccessManager() {
      return quickAccess();
   }

   @Override
   public ShutterManager shutter() {
      return shutterManager_;
   }

   @Override
   public ShutterManager getShutterManager() {
      return shutter();
   }

   @Override
   public AcquisitionManager acquisitions() {
      return acquisitionManager_;
   }

   @Override
   public AcquisitionManager getAcquisitionManager() {
      return acquisitions();
   }

   @Override
   public PositionListManager positions() {
      return this;
   }

   @Override
   public PositionListManager getPositionListManager() {
      return positions();
   }

   @Override
   public Application app() {
      return this;
   }

   @Override
   public Application getApplication() {
      return app();
   }

   @Override
   public ApplicationSkin skin() {
      return daytimeNighttimeManager_;
   }

   @Override
   public ApplicationSkin getApplicationSkin() {
      return skin();
   }

   @Override
   public AlertManager alerts() {
      return alertManager_;
   }

   @Override
   public AlertManager getAlertManager() {
      return alerts();
   }


   public UiMovesStageManager getUiMovesStageManager () {
      return uiMovesStageManager_;
   }

   @Override
   @Deprecated
   public AffineTransform getCameraTransform(String config) {
      // Try the modern way first
      double[] defaultParams = new double[0];
      double[] params = profile().getSettings(MMStudio.class).
              getDoubleList(AFFINE_TRANSFORM + config, defaultParams);
      if (params != null && params.length == 6) {
         return new AffineTransform(params);
      }

      // The early 2.0-beta way of storing as a serialized object.
      PropertyMap studioSettings = profile().
            getSettings(MMStudio.class).toPropertyMap();
      AffineTransform result = (AffineTransform)
         ((DefaultPropertyMap) studioSettings).getLegacySerializedObject(
               AFFINE_TRANSFORM_LEGACY + config, null);
      if (result != null) {
         // Save it the new way
         setCameraTransform(result, config);
         return result;
      }

      // For backwards compatibility, try retrieving it from the 1.4
      // Preferences instead.
      AffineTransform tfm = org.micromanager.internal.utils.UnpleasantLegacyCode.
              legacyRetrieveTransformFromPrefs("affine_transform_" + config);
      if (tfm != null) {
         // Save it the new way.
         setCameraTransform(tfm, config);
      }
      return tfm;
   }

   @Override
   @Deprecated
   public void setCameraTransform(AffineTransform transform, String config) {
      double[] params = new double[6];
      transform.getMatrix(params);
      profile().getSettings(MMStudio.class).putDoubleList(AFFINE_TRANSFORM + config, params);
   }

   public double getCachedXPosition() {
      return staticInfo_.getStageX();
   }

   public double getCachedYPosition() {
      return staticInfo_.getStageY();
   }

   public double getCachedZPosition() {
      return staticInfo_.getStageZ();
   }

   public int getCachedBitDepth() {
      return staticInfo_.getImageBitDepth();
   }

   public double getCachedPixelSizeUm() {
      return staticInfo_.getPixelSizeUm();
   }
   
   public AffineTransform getCachedPixelSizeAffine() {
      return staticInfo_.getPixelSizeAffine();
   }

   public boolean getShouldDeleteOldCoreLogs() {
      return profile().getSettings(MMStudio.class).getBoolean(
            SHOULD_DELETE_OLD_CORE_LOGS, false);
   }

   public void setShouldDeleteOldCoreLogs(boolean shouldDelete) {
      profile().getSettings(MMStudio.class).putBoolean(
            SHOULD_DELETE_OLD_CORE_LOGS, shouldDelete);
   }
   
   public boolean getShouldRunZMQServer() {
      return profile().getSettings(MMStudio.class).getBoolean(
              SHOULD_RUN_ZMQ_SERVER, false);
   }
   
   public void setShouldRunZMQServer(boolean shouldRun) {
      profile().getSettings(MMStudio.class).putBoolean(
              SHOULD_RUN_ZMQ_SERVER, shouldRun);
   }

   public int getCoreLogLifetimeDays() {
      return profile().getSettings(MMStudio.class).getInteger(
            CORE_LOG_LIFETIME_DAYS, 7);
   }

   public void setCoreLogLifetimeDays(int days) {
      profile().getSettings(MMStudio.class).putInteger(
            CORE_LOG_LIFETIME_DAYS, days);
   }

   public int getCircularBufferSize() {
      // Default to more MB for 64-bit systems.
      int defaultVal = System.getProperty("sun.arch.data.model", "32").equals("64") ? 250 : 25;
      return profile().getSettings(MMStudio.class).getInteger(
            CIRCULAR_BUFFER_SIZE, defaultVal);
   }

   public void setCircularBufferSize(int newSize) {
      profile().getSettings(MMStudio.class).putInteger(
            CIRCULAR_BUFFER_SIZE, newSize);
   }
}
