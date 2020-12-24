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
import org.micromanager.PositionListManager;
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
import org.micromanager.internal.logging.LogFileManager;
import org.micromanager.internal.menus.MMMenuBar;
import org.micromanager.internal.navigation.UiMovesStageManager;
import org.micromanager.internal.pipelineinterface.PipelineFrame;
import org.micromanager.internal.pluginmanagement.DefaultPluginManager;
import org.micromanager.internal.positionlist.MMPositionListDlg;
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
public final class MMStudio implements Studio {

   private static final long serialVersionUID = 3556500289598574541L;
   
   private static final String AUTOFOCUS_DEVICE = "autofocus_device";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;

   private boolean wasStartedAsImageJPlugin_;

   
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
   private PositionListManager posListManager_;
   private UiMovesStageManager uiMovesStageManager_;
   private DefaultApplication defaultApplication_;
   private DefaultCompatibilityInterface compatibility_;
   
   // Local Classes
   private final MMSettings settings_ = new MMSettings();
   private MMCache cache_;
   private MMUIManager ui_;
   
   
   // MMcore
   private CMMCore core_;
   private AcquisitionWrapperEngine acqEngine_;
   private boolean isProgramRunning_;
   private boolean configChanged_ = false;
   private boolean isClickToMoveEnabled_ = false;

   private ZMQServer zmqServer_;
   private org.micromanager.internal.utils.HotKeys hotKeys_;

   // Our instance
   // TODO: make this non-static
   private static MMStudio studio_;

   // Callback
   private CoreEventCallback coreCallback_;
   // Lock invoked while shutting down
   private final Object shutdownLock_ = new Object();

   private Thread acquisitionEngine2010LoadingThread_ = null;
   private Class<?> acquisitionEngine2010Class_ = null;
   private IAcquisitionEngine2010 acquisitionEngine2010_ = null;   
   
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
      ui_ = new MMUIManager(this);
      userProfileManager_ = new UserProfileManager();       
      compatibility_ = new DefaultCompatibilityInterface(studio_);
      
      // Essential GUI settings in preparation of the intro dialog
      daytimeNighttimeManager_ = DaytimeNighttime.create(studio_);
      defaultApplication_ = new DefaultApplication(studio_, daytimeNighttimeManager_);
      
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
      ui_.createPipelineFrame();

      alertManager_ = new DefaultAlertManager(studio_);
      
      afMgr_ = new DefaultAutofocusManager(studio_);
      afMgr_.refresh();
      String afDevice = profile().getSettings(MMStudio.class).
              getString(AUTOFOCUS_DEVICE, "");
      if (afMgr_.hasDevice(afDevice)) {
         afMgr_.setAutofocusMethodByName(afDevice);
      }

      posListManager_ = new DefaultPositionListManager(this);
      acqEngine_.setPositionList(posListManager_.getPositionList());

      
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
      core_.enableDebugLog(OptionsDlg.isDebugLoggingEnabled(studio_));

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

      acquisitionManager_ = new DefaultAcquisitionManager(this, acqEngine_, ui_.getAcquisitionWindow());

      try {
         core_.setCircularBufferMemoryFootprint(settings().getCircularBufferSize());
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
      ui_.createScriptPanel();
      
      ui_.createMainWindow(); // Now create and show the main window

      
      cache_ = new MMCache(this, ui_.frame());


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

      ui_.updateGUI(true);
      
      // Give plugins a chance to initialize their state
      events().post(new StartupCompleteEvent());
      
      // start zmq server if so desired
      if (settings().getShouldRunZMQServer()) {
         runZMQServer();
      }
      
   }

   private void initializeLogging(CMMCore core) {
      core.enableStderrLog(true);
      core.enableDebugLog(OptionsDlg.isDebugLoggingEnabled(studio_));
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

      if (settings().getShouldDeleteOldCoreLogs()) {
         LogFileManager.deleteLogFilesDaysOld(
               settings().getCoreLogLifetimeDays(), logFileName);
      }

      logStartupProperties();

      // Although our general rule is to perform identical logging regardless
      // of the current log level, we make an exception for UIMonitor, which we
      // enable only when debug logging is turned on (from the GUI).
      UIMonitor.enable(OptionsDlg.isDebugLoggingEnabled(studio_));
   }

   private void handleError(String message) {
      live().setLiveModeOn(false);
      JOptionPane.showMessageDialog(ui_.frame(), message);
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
         app().setROI(updateROI(roi));
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
            app().setROI(updateROI(roi));
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
         cache().refreshValues();

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
    * Get currently used configuration file
    * @return - Path to currently used configuration file
    */
   public String getSysConfigFile() {
      return sysConfigFile_;
   }

   public void setSysConfigFile(String newFile) {
      sysConfigFile_ = newFile;
      configChanged_ = false;
      ui_.frame().setConfigSaveButtonStatus(configChanged_);
      loadSystemConfiguration();
   }

   protected void changeBinning() {
      String mode = ui_.frame().getBinMode();
      live().setSuspended(true);
      try {
         if (!isCameraAvailable() || mode == null) {
            // No valid option.
            live().setSuspended(false);
            return;
         }
         if (core_.getProperty(cache().getCameraLabel(),
                 MMCoreJ.getG_Keyword_Binning()).equals(mode)) {
            // No change in binning mode.
            live().setSuspended(false);
            return;
         }
         core_.setProperty(cache().getCameraLabel(), MMCoreJ.getG_Keyword_Binning(), mode);
         cache().refreshValues();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      live().setSuspended(false);
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

   public void updateCenterAndDragListener(boolean isEnabled) {
      isClickToMoveEnabled_ = isEnabled;
      if (isEnabled) {
         IJ.setTool(Toolbar.HAND);
      }
      ui_.menubar().getToolsMenu().setMouseMovesStage(isEnabled);
      events().post(new MouseMovesStageStateChangeEvent(isEnabled));
   }

   public boolean isClickToMoveEnabled() {
      return isClickToMoveEnabled_;
   }
   

   // //////////////////////////////////////////////////////////////////////////
   // public interface available for scripting access
   // //////////////////////////////////////////////////////////////////////////

   private boolean isCameraAvailable() {
      return cache().getCameraLabel().length() > 0;
   }

   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      ui_.updateGUI(true);
   }

   @Subscribe
   public void onExposureChanged(ExposureChangedEvent event) {
      if (event.getCameraName().equals(cache().getCameraLabel())) {
         ui_.frame().setDisplayedExposureTime(event.getNewExposureTime());
      }
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
            ui_.promptToSaveConfigPresets();
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
  
      ui_.cleanupOnClose();
      
      if (zmqServer_ != null) {
         zmqServer_.close();
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

      if (ui_.frame() != null) {
         ui_.frame().savePrefs();
      }

      // NOTE: do not save auto shutter state
      if (afMgr_ != null && afMgr_.getAutofocusMethod() != null) {
         profile().getSettings(MMStudio.class).putString(
               AUTOFOCUS_DEVICE, afMgr_.getAutofocusMethod().getName());
      }
   }

   public synchronized boolean closeSequence(boolean quitInitiatedByImageJ) {
      if (!isProgramRunning()) {
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
         ui_.frame().getConfigPad().saveSettings();
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

      ui_.close();

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

   public boolean isProgramRunning() {
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
         ui_.getScriptPanel().runFile(f);
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
      if (ui_.frame() != null) {
         ui_.frame().setEnabled(false);
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
            afMgr_.initialize();
            // in case 3rdparties use this deprecated code:
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
         if (ui_.frame() != null) {
            ui_.frame().setEnabled(true);
         }

      }

      ui_.initializeGUI();

      return result;
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
      core_.logMessage("MM Studio version: " + compat().getVersion());
      core_.logMessage(core_.getVersionInfo());
      core_.logMessage(core_.getAPIVersionInfo());
      core_.logMessage("Operating System: " + System.getProperty("os.name") +
              " (" + System.getProperty("os.arch") + ") " + System.getProperty("os.version"));
      core_.logMessage("JVM: " + System.getProperty("java.vm.name") +
              ", version " + System.getProperty("java.version") + ", " +
              System.getProperty("sun.arch.data.model") + "-bit");
   }

   public void setConfigChanged(boolean status) {
      configChanged_ = status;
      ui_.frame().setConfigSaveButtonStatus(configChanged_);
   }

   public boolean hasConfigChanged() {
      return configChanged_;
   }

   public void enableRoiButtons(final boolean enabled) {
      ui_.frame().enableRoiButtons(enabled);
   }

   public AcquisitionWrapperEngine getAcquisitionEngine() {
      return acqEngine_;
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

   public void setMultiROI(List<Rectangle> rois) throws Exception {
      live().setSuspended(true);
      core_.setMultiROI(rois);
      cache().refreshValues();
      live().setSuspended(false);
   }

   public void setAcquisitionEngine(AcquisitionWrapperEngine eng) {
      acqEngine_ = eng;
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

   @Override
   public CompatibilityInterface compat() {
      return compatibility_;
   }
   
   @Override
   public CompatibilityInterface getCompatibilityInterface() {
      return compat();
   }

   @Override
   public ScriptController scripter() {
      return ui_.getScriptPanel();
   }

   @Override
   public ScriptController getScriptController() {
      return ui_.getScriptPanel();
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
      return posListManager_;
   }

   @Override
   public PositionListManager getPositionListManager() {
      return positions();
   }

   @Override
   public Application app() {
      return defaultApplication_;
   }

   @Override
   public Application getApplication() {
      return app();
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

   
   public MMCache cache() {
      return cache_;
   }
   
   public MMSettings settings() {
      return settings_;
   }

   public class MMSettings {
      private static final String SHOULD_DELETE_OLD_CORE_LOGS = "whether or not to delete old MMCore log files";
      private static final String SHOULD_RUN_ZMQ_SERVER = "run ZQM server";
      private static final String CORE_LOG_LIFETIME_DAYS = "how many days to keep MMCore log files, before they get deleted";
      private static final String CIRCULAR_BUFFER_SIZE = "size, in megabytes of the circular buffer used to temporarily store images before they are written to disk";

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
   
   public MMUIManager uiManager() {
      return ui_;
   }
}
