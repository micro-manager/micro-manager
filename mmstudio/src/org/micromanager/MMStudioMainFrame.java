///////////////////////////////////////////////////////////////////////////////
//FILE:          MMStudioMainFrame.java
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
package org.micromanager;

import com.google.common.eventbus.Subscribe;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Line;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import mmcorej.CMMCore;
import mmcorej.DeviceType;
import mmcorej.MMCoreJ;
import mmcorej.MMEventCallback;
import mmcorej.StrVector;

import org.json.JSONObject;

import org.micromanager.acquisition.AcquisitionManager;

import org.micromanager.api.Autofocus;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.events.ConfigGroupChangedEvent;
import org.micromanager.api.events.ExposureChangedEvent;
import org.micromanager.api.events.PixelSizeChangedEvent;
import org.micromanager.api.events.PropertiesChangedEvent;
import org.micromanager.api.events.StagePositionChangedEvent;
import org.micromanager.api.events.XYStagePositionChangedEvent;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.MMProcessorPlugin;
import org.micromanager.api.MMTags;
import org.micromanager.api.PositionList;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.MMListenerInterface;
import org.micromanager.api.SequenceSettings;

import org.micromanager.conf2.ConfiguratorDlg2;
import org.micromanager.conf2.MMConfigFileException;
import org.micromanager.conf2.MicroscopeModel;

import org.micromanager.events.EventManager;
import org.micromanager.events.MMListenerProxy;

import org.micromanager.graph.GraphData;
import org.micromanager.graph.GraphFrame;

import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.MetadataPanel;
import org.micromanager.imagedisplay.VirtualAcquisitionDisplay;

import org.micromanager.logging.LogFileManager;

import org.micromanager.navigation.CenterAndDragListener;
import org.micromanager.navigation.XYZKeyListener;
import org.micromanager.navigation.ZWheelListener;

import org.micromanager.pipelineinterface.PipelinePanel;

import org.micromanager.positionlist.PositionListDlg;

import org.micromanager.utils.AutofocusManager;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.TextUtils;
import org.micromanager.utils.WaitDialog;

import bsh.EvalError;
import bsh.Interpreter;

import com.swtdesigner.SwingResourceManager;

import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.micromanager.acquisition.*;
import org.micromanager.api.ImageCache;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.graph.HistogramSettings;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.DragDropUtil;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.FileDialogs.FileType;
import org.micromanager.utils.HotKeysDialog;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMKeyDispatcher;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.UIMonitor;





/*
 * Main panel and application class for the MMStudio.
 */
public class MMStudioMainFrame extends JFrame implements ScriptInterface {

   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";
   private static final long serialVersionUID = 3556500289598574541L;
   private static final String MAIN_FRAME_X = "x";
   private static final String MAIN_FRAME_Y = "y";
   private static final String MAIN_FRAME_WIDTH = "width";
   private static final String MAIN_FRAME_HEIGHT = "height";
   private static final String MAIN_FRAME_DIVIDER_POS = "divider_pos";
   private static final String MAIN_EXPOSURE = "exposure";
   private static final String MAIN_SAVE_METHOD = "saveMethod";
   private static final String SYSTEM_CONFIG_FILE = "sysconfig_file";
   private static final String OPEN_ACQ_DIR = "openDataDir";
   private static final String SCRIPT_CORE_OBJECT = "mmc";
   private static final String SCRIPT_ACQENG_OBJECT = "acq";
   private static final String SCRIPT_GUI_OBJECT = "gui";
   private static final String AUTOFOCUS_DEVICE = "autofocus_device";
   private static final String MOUSE_MOVES_STAGE = "mouse_moves_stage";
   private static final String EXPOSURE_SETTINGS_NODE = "MainExposureSettings";
   private static final String CONTRAST_SETTINGS_NODE = "MainContrastSettings";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;


   // cfg file saving
   private static final String CFGFILE_ENTRY_BASE = "CFGFileEntry"; // + {0, 1, 2, 3, 4}
   // GUI components
   private JComboBox comboBinning_;
   private JComboBox shutterComboBox_;
   private JTextField textFieldExp_;
   private JLabel labelImageDimensions_;
   private JToggleButton liveButton_;
   private JCheckBox autoShutterCheckBox_;
   private MMOptions options_;
   private boolean runsAsPlugin_;
   private JCheckBoxMenuItem centerAndDragMenuItem_;
   private JButton snapButton_;
   private JButton autofocusNowButton_;
   private JButton autofocusConfigureButton_;
   private JToggleButton toggleShutterButton_;
   private GUIColors guiColors_;
   private GraphFrame profileWin_;
   private PropertyEditor propertyBrowser_;
   private CalibrationListDlg calibrationListDlg_;
   private AcqControlDlg acqControlWin_;

   private JMenu pluginMenu_;
   private Map<String, JMenu> pluginSubMenus_;
   private List<LiveModeListener> liveModeListeners_
           = Collections.synchronizedList(new ArrayList<LiveModeListener>());
   private List<Component> MMFrames_
           = Collections.synchronizedList(new ArrayList<Component>());
   private AutofocusManager afMgr_;
   private final static String DEFAULT_CONFIG_FILE_NAME = "MMConfig_demo.cfg";
   private final static String DEFAULT_CONFIG_FILE_PROPERTY = "org.micromanager.default.config.file";
   private ArrayList<String> MRUConfigFiles_;
   private static final int maxMRUCfgs_ = 5;
   private String sysConfigFile_;
   private String startupScriptFile_;
   private ConfigGroupPad configPad_;
   private LiveModeTimer liveModeTimer_;
   private GraphData lineProfileData_;
   // labels for standard devices
   private String cameraLabel_;
   private String zStageLabel_;
   private String shutterLabel_;
   private String xyStageLabel_;
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
   private boolean running_;
   private boolean configChanged_ = false;
   private StrVector shutters_ = null;

   private JButton saveConfigButton_;
   private ScriptPanel scriptPanel_;
   private PipelinePanel pipelinePanel_;
   private org.micromanager.utils.HotKeys hotKeys_;
   private CenterAndDragListener centerAndDragListener_;
   private ZWheelListener zWheelListener_;
   private XYZKeyListener xyzKeyListener_;
   private AcquisitionManager acqMgr_;
   private static VirtualAcquisitionDisplay simpleDisplay_;
   private Color[] multiCameraColors_ = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN};
   private boolean liveModeSuspended_;
   public Font defaultScriptFont_ = null;
   public static final String SIMPLE_ACQ = "Snap/Live Window";
   public static FileType MM_CONFIG_FILE
            = new FileType("MM_CONFIG_FILE",
                           "Micro-Manager Config File",
                           "./MyScope.cfg",
                           true, "cfg");

   // Our instance
   private static MMStudioMainFrame gui_;
   // Callback
   private CoreEventCallback coreCallback_;
   // Lock invoked while shutting down
   private final Object shutdownLock_ = new Object();

   private JMenuBar menuBar_;
   private ConfigPadButtonPanel configPadButtonPanel_;
   private final JMenu switchConfigurationMenu_;
   private final MetadataPanel metadataPanel_;
   public static FileType MM_DATA_SET 
           = new FileType("MM_DATA_SET",
                 "Micro-Manager Image Location",
                 System.getProperty("user.home") + "/Untitled",
                 false, (String[]) null);
   private Thread acquisitionEngine2010LoadingThread_ = null;
   private Class<?> acquisitionEngine2010Class_ = null;
   private IAcquisitionEngine2010 acquisitionEngine2010_ = null;
   private final JSplitPane splitPane_;
   private PluginLoader pluginLoader_;

   private AbstractButton setRoiButton_;
   private AbstractButton clearRoiButton_;

   /**
    * Simple class used to cache static info
    */
   private class StaticInfo {

      public long width_;
      public long height_;
      public long bytesPerPixel_;
      public long imageBitDepth_;
      public double pixSizeUm_;
      public double zPos_;
      public double x_;
      public double y_;
   }
   private StaticInfo staticInfo_ = new StaticInfo();
   
   
   /**
    * Main procedure for stand alone operation.
    */
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         MMStudioMainFrame frame = new MMStudioMainFrame(false);
         frame.setVisible(true);
         frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      } catch (Throwable e) {
         ReportingUtils.showError(e, "A java error has caused Micro-Manager to exit.");
         System.exit(1);
      }
   }

   /**
    * MMStudioMainframe constructor
    * @param pluginStatus 
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public MMStudioMainFrame(boolean pluginStatus) {
      org.micromanager.diagnostics.ThreadExceptionLogger.setUp();

      // Set up event handling early, so following code can subscribe/publish
      // events as needed.
      EventManager manager = new EventManager();
      manager.register(this);

      startLoadingPipelineClass();

      options_ = new MMOptions();
      try {
         options_.loadSettings();
      } catch (NullPointerException ex) {
         ReportingUtils.logError(ex);
      }

      UIMonitor.enable(options_.debugLogEnabled_);
      
      guiColors_ = new GUIColors();

      pluginLoader_ = new PluginLoader();
      // plugins_ = new ArrayList<PluginItem>();

      gui_ = this;

      runsAsPlugin_ = pluginStatus;
      setIconImage(SwingResourceManager.getImage(MMStudioMainFrame.class,
            "icons/microscope.gif"));
      running_ = true;

      acqMgr_ = new AcquisitionManager();
      
      sysConfigFile_ = new File(DEFAULT_CONFIG_FILE_NAME).getAbsolutePath();
      sysConfigFile_ = System.getProperty(DEFAULT_CONFIG_FILE_PROPERTY,
            sysConfigFile_);

      if (options_.startupScript_.length() > 0) {
         startupScriptFile_ = new File(options_.startupScript_).getAbsolutePath();
      } else {
         startupScriptFile_ = "";
      }

      ReportingUtils.SetContainingFrame(gui_);
      
           
      // set the location for app preferences
      try {
         mainPrefs_ = Preferences.userNodeForPackage(this.getClass());
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
         Preferences p = Preferences.systemNodeForPackage(this.getClass());
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

      // load application preferences
      // NOTE: only window size and position preferences are loaded,
      // not the settings for the camera and live imaging -
      // attempting to set those automatically on startup may cause problems
      // with the hardware
      int x = mainPrefs_.getInt(MAIN_FRAME_X, 100);
      int y = mainPrefs_.getInt(MAIN_FRAME_Y, 100);
      int width = mainPrefs_.getInt(MAIN_FRAME_WIDTH, 644);
      int height = mainPrefs_.getInt(MAIN_FRAME_HEIGHT, 570);
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
      
      setBounds(x, y, width, height);
      setExitStrategy(options_.closeOnExit_);
      setTitle(MICRO_MANAGER_TITLE + " " + MMVersion.VERSION_STRING);
      setBackground(guiColors_.background.get((options_.displayBackground_)));
      setMinimumSize(new Dimension(605,480));
      
      menuBar_ = new JMenuBar();
      switchConfigurationMenu_ = new JMenu();

      setJMenuBar(menuBar_);

      initializeFileMenu();
      initializeToolsMenu();

      splitPane_ = createSplitPane(mainPrefs_.getInt(MAIN_FRAME_DIVIDER_POS, 200));
      getContentPane().add(splitPane_);

      createTopPanelWidgets((JPanel) splitPane_.getComponent(0));
      
      metadataPanel_ = createMetadataPanel((JPanel) splitPane_.getComponent(1));
      
      setupWindowHandlers();
      
      // Add our own keyboard manager that handles Micro-Manager shortcuts
      MMKeyDispatcher mmKD = new MMKeyDispatcher(gui_);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(mmKD);
      DropTarget dropTarget = new DropTarget(this, new DragDropUtil());

   }

   private void setupWindowHandlers() {
      // add window listeners
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            closeSequence(false);
         }

         @Override
         public void windowOpened(WindowEvent event) {
            initializationSequence();
         }
      });
        
   }
   
   /**
    * Initialize the program.
    */
   private void initializationSequence() {
      // Initialize hardware.
      try {
         core_ = new CMMCore();
      } catch(UnsatisfiedLinkError ex) {
         ReportingUtils.showError(ex, 
               "Failed to load the MMCoreJ_wrap native library");
         return;
      }

      core_.enableStderrLog(true);
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
              
      cameraLabel_ = "";
      shutterLabel_ = "";
      zStageLabel_ = "";
      xyStageLabel_ = "";
      engine_ = new AcquisitionWrapperEngine(acqMgr_);

      // This entity is a class property to avoid garbage collection.
      coreCallback_ = new CoreEventCallback(core_, engine_);

      try {
         core_.setCircularBufferMemoryFootprint(options_.circularBufferSizeMB_);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }

      engine_.setParentGUI(this);

      loadMRUConfigFiles();
      afMgr_ = new AutofocusManager(gui_);
      Thread pluginInitializer = initializePlugins();

      toFront();
      
      if (!options_.doNotAskForConfigFile_) {
         // Ask the user for a configuration file.
         MMIntroDlg introDlg = new MMIntroDlg(
               MMVersion.VERSION_STRING, MRUConfigFiles_);
         introDlg.setConfigFile(sysConfigFile_);
         introDlg.setBackground(
               guiColors_.background.get((options_.displayBackground_)));
         introDlg.setVisible(true);
         if (!introDlg.okChosen()) {
            // User aborted; close the program down.
            closeSequence(false);
            return;
         }
         sysConfigFile_ = introDlg.getConfigFile();
      }
      saveMRUConfigFiles();

      mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);

      paint(getGraphics());

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
      
      // before loading the system configuration, we need to wait 
      // until the plugins are loaded
      try {  
         pluginInitializer.join(2000);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex, "Plugin loader thread was interupted");
      }
      
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
            this, options_);
      addMMBackgroundListener(acqControlWin_);

      configPad_.setCore(core_);
      configPad_.setParentGUI(this);

      configPadButtonPanel_.setCore(core_);

      // initialize controls
      initializeHelpMenu();
      
      String afDevice = mainPrefs_.get(AUTOFOCUS_DEVICE, "");
      if (afMgr_.hasDevice(afDevice)) {
         try {
            afMgr_.selectDevice(afDevice);
         } catch (MMException ex) {
            // this error should never happen
            ReportingUtils.showError(ex);
         }
      }

      centerAndDragListener_ = new CenterAndDragListener(gui_);
      zWheelListener_ = new ZWheelListener(core_, gui_);
      gui_.addLiveModeListener(zWheelListener_);
      xyzKeyListener_ = new XYZKeyListener(core_, gui_);
      gui_.addLiveModeListener(xyzKeyListener_);

      // switch error reporting back on
      ReportingUtils.showErrorOn(true);
   }

   private Thread initializePlugins() {
      pluginMenu_ = GUIUtils.createMenuInMenuBar(menuBar_, "Plugins");
      Thread loadThread = new Thread(new ThreadGroup("Plugin loading"),
            new Runnable() {
               @Override
               public void run() {
                  // Needed for loading clojure-based jars:
                  Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
                  pluginLoader_.loadPlugins();
               }
      });
      loadThread.start();
      return loadThread;
   }
   
   private void handleError(String message) {
      if (isLiveModeOn()) {
         // Should we always stop live mode on any error?
         enableLiveMode(false);
      }
      JOptionPane.showMessageDialog(this, message);
      core_.logMessage(message);
   }

   public ImageWindow getImageWin() {
      return getSnapLiveWin();
   }

   public static VirtualAcquisitionDisplay getSimpleDisplay() {
      return simpleDisplay_;
   }

   public static void createSimpleDisplay(String name, ImageCache cache) throws MMScriptException {
      simpleDisplay_ = new VirtualAcquisitionDisplay(cache, name); 
   }

   private void checkSimpleAcquisition(int width, int height, int depth, 
         int bitDepth, int numCamChannels) {
      try {
         if (acquisitionExists(SIMPLE_ACQ)) {
            if ((getAcquisitionImageWidth(SIMPLE_ACQ) != width)
                    || (getAcquisitionImageHeight(SIMPLE_ACQ) != height)
                    || (getAcquisitionImageByteDepth(SIMPLE_ACQ) != depth)
                    || (getAcquisitionImageBitDepth(SIMPLE_ACQ) != bitDepth)
                    || (getAcquisitionMultiCamNumChannels(SIMPLE_ACQ) != numCamChannels)) {  //Need to close and reopen simple window
               closeAcquisitionWindow(SIMPLE_ACQ);
            }
         }
         if (!acquisitionExists(SIMPLE_ACQ)) {
            openAcquisition(SIMPLE_ACQ, "", 1, numCamChannels, 1, true);
            if (numCamChannels > 1) {
               for (long i = 0; i < numCamChannels; i++) {
                  String chName = core_.getCameraChannelName(i);
                  int defaultColor = multiCameraColors_[(int) i % multiCameraColors_.length].getRGB();
                  setChannelColor(SIMPLE_ACQ, (int) i, getChannelColor(chName, defaultColor));
                  setChannelName(SIMPLE_ACQ, (int) i, chName);
               }
            }
            initializeSimpleAcquisition(SIMPLE_ACQ, width, height, depth, bitDepth, numCamChannels);
            getAcquisition(SIMPLE_ACQ).promptToSave(false);
            getAcquisition(SIMPLE_ACQ).getAcquisitionWindow().getHyperImage().getWindow().toFront();
            this.updateCenterAndDragListener();
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   
   public void checkSimpleAcquisition() {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured");
         return;
      }
      int width = (int) core_.getImageWidth();
      int height = (int) core_.getImageHeight();
      int depth = (int) core_.getBytesPerPixel();
      int bitDepth = (int) core_.getImageBitDepth();
      int numCamChannels = (int) core_.getNumberOfCameraChannels();

      checkSimpleAcquisition(width, height, depth, bitDepth, numCamChannels);
   }


   public void checkSimpleAcquisition(TaggedImage image) {
      JSONObject tags = image.tags;
      try {
         int width = MDUtils.getWidth(tags);
         int height = MDUtils.getHeight(tags);
         int depth = MDUtils.getDepth(tags);
         int bitDepth = MDUtils.getBitDepth(tags);
         int numCamChannels = (int) core_.getNumberOfCameraChannels();
      
         checkSimpleAcquisition(width, height, depth, bitDepth, numCamChannels);
      }
      catch (Exception ex) {
         ReportingUtils.showError("Error extracting image info in checkSimpleAcquisition: " + ex);
      }
   }

   public void saveChannelColor(String chName, int rgb)
   {
      if (colorPrefs_ != null) {
         colorPrefs_.putInt("Color_" + chName, rgb);      
      }          
   }
   
   public Color getChannelColor(String chName, int defaultColor)
   {  
      if (colorPrefs_ != null) {
         defaultColor = colorPrefs_.getInt("Color_" + chName, defaultColor);
      }
      return new Color(defaultColor);
   }


   public void copyFromLiveModeToAlbum(VirtualAcquisitionDisplay display) throws MMScriptException, JSONException {
      ImageCache ic = display.getImageCache();
      int channels = ic.getSummaryMetadata().getInt("Channels");
      if (channels == 1) {
         //RGB or monchrome
         addToAlbum(ic.getImage(0, 0, 0, 0), ic.getDisplayAndComments());
      } else {
         //multicamera
         for (int i = 0; i < channels; i++) {
            addToAlbum(ic.getImage(i, 0, 0, 0), ic.getDisplayAndComments());
         }
      }
   }

   private void createActiveShutterChooser(JPanel topPanel) {
      createLabel("Shutter", false, topPanel, 111, 73, 158, 86); 

      shutterComboBox_ = new JComboBox();
      shutterComboBox_.setName("Shutter");
      shutterComboBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            try {
               if (shutterComboBox_.getSelectedItem() != null) {
                  core_.setShutterDevice((String) shutterComboBox_.getSelectedItem());
               }
            } catch (Exception e) {
               ReportingUtils.showError(e);
            }
         }
      });
      GUIUtils.addWithEdges(topPanel, shutterComboBox_, 170, 70, 275, 92);
   }

   private void createBinningChooser(JPanel topPanel) {
      createLabel("Binning", false, topPanel, 111, 43, 199, 64);

      comboBinning_ = new JComboBox();
      comboBinning_.setName("Binning");
      comboBinning_.setFont(new Font("Arial", Font.PLAIN, 10));
      comboBinning_.setMaximumRowCount(4);
      comboBinning_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            changeBinning();
         }
      });
      GUIUtils.addWithEdges(topPanel, comboBinning_, 200, 43, 275, 66);
   }

   private void createExposureField(JPanel topPanel) {
      createLabel("Exposure [ms]", false, topPanel, 111, 23, 198, 39);

      textFieldExp_ = new JTextField();
      textFieldExp_.addFocusListener(new FocusAdapter() {
         @Override
         public void focusLost(FocusEvent fe) {
            synchronized(shutdownLock_) {
            if (core_ != null)
               setExposure();
            }
         }
      });
      textFieldExp_.setFont(new Font("Arial", Font.PLAIN, 10));
      textFieldExp_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setExposure();
         }
      });
      GUIUtils.addWithEdges(topPanel, textFieldExp_, 203, 21, 276, 40);
   }

   private void toggleAutoShutter() {
      shutterLabel_ = core_.getShutterDevice();
      if (shutterLabel_.length() == 0) {
         toggleShutterButton_.setEnabled(false);
      } else {
         if (autoShutterCheckBox_.isSelected()) {
            try {
               core_.setAutoShutter(true);
               core_.setShutterOpen(false);
               toggleShutterButton_.setSelected(false);
               toggleShutterButton_.setText("Open");
               toggleShutterButton_.setEnabled(false);
            } catch (Exception e2) {
               ReportingUtils.logError(e2);
            }
         } else {
            try {
               core_.setAutoShutter(false);
               core_.setShutterOpen(false);
               toggleShutterButton_.setEnabled(true);
               toggleShutterButton_.setText("Open");
            } catch (Exception exc) {
               ReportingUtils.logError(exc);
            }
         }
      }
   }
   
   private void createShutterControls(JPanel topPanel) {
      autoShutterCheckBox_ = new JCheckBox();
      autoShutterCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      autoShutterCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            toggleAutoShutter();
         }
      });
      autoShutterCheckBox_.setIconTextGap(6);
      autoShutterCheckBox_.setHorizontalTextPosition(SwingConstants.LEADING);
      autoShutterCheckBox_.setText("Auto shutter");
      GUIUtils.addWithEdges(topPanel, autoShutterCheckBox_, 107, 96, 199, 119);

      toggleShutterButton_ = (JToggleButton) GUIUtils.createButton(true,
              "toggleShutterButton", "Open",
              "Open/close the shutter",
              new Runnable() {
                 public void run() {
                    toggleShutter();
                 }
              }, null, topPanel, 203, 96, 275, 117);      // Shutter button
   }

   private void createCameraSettingsWidgets(JPanel topPanel) {
      createLabel("Camera settings", true, topPanel, 109, 2, 211, 22);
      createExposureField(topPanel);
      createBinningChooser(topPanel);
      createActiveShutterChooser(topPanel);
      createShutterControls(topPanel);
   }

   private void createConfigurationControls(JPanel topPanel) {
      createLabel("Configuration settings", true, topPanel, 280, 2, 430, 22);

      saveConfigButton_ = (JButton) GUIUtils.createButton(false,
              "saveConfigureButton", "Save",
              "Save current presets to the configuration file",
              new Runnable() {
                 public void run() {
                  saveConfigPresets();
                 }
              }, null, topPanel, -80, 2, -5, 20);
      
      configPad_ = new ConfigGroupPad();
      configPadButtonPanel_ = new ConfigPadButtonPanel();
      configPadButtonPanel_.setConfigPad(configPad_);
      configPadButtonPanel_.setGUI(MMStudioMainFrame.getInstance());
      
      configPad_.setFont(new Font("", Font.PLAIN, 10));
      
      GUIUtils.addWithEdges(topPanel, configPad_, 280, 21, -4, -44);
      GUIUtils.addWithEdges(topPanel, configPadButtonPanel_, 280, -40, -4, -20);
   }

   private void createMainButtons(JPanel topPanel) {
      snapButton_ = (JButton) GUIUtils.createButton(false, "Snap", "Snap",
              "Snap single image",
              new Runnable() {
                 public void run() {
                    doSnap();
                 }
              }, "camera.png", topPanel, 7, 4, 95, 25);

      liveButton_ = (JToggleButton) GUIUtils.createButton(true,
              "Live", "Live",
              "Continuous live view",
              new Runnable() {
                 public void run() {
                    enableLiveMode(!isLiveModeOn());
                 }
              }, "camera_go.png", topPanel, 7, 26, 95, 47);

      /* toAlbumButton_ = (JButton) */ GUIUtils.createButton(false, "Album", "Album",
              "Acquire single frame and add to an album",
              new Runnable() {
                 public void run() {
                    snapAndAddToImage5D();
                 }
              }, "camera_plus_arrow.png", topPanel, 7, 48, 95, 69);

      /* MDA Button = */ GUIUtils.createButton(false,
              "Multi-D Acq.", "Multi-D Acq.",
              "Open multi-dimensional acquisition window",
              new Runnable() {
                 public void run() {
                    openAcqControlDialog();
                 }
              }, "film.png", topPanel, 7, 70, 95, 91);

      /* Refresh = */ GUIUtils.createButton(false, "Refresh", "Refresh",
              "Refresh all GUI controls directly from the hardware",
              new Runnable() {
                 public void run() {
                    core_.updateSystemStateCache();
                    updateGUI(true);
                 }
              }, "arrow_refresh.png", topPanel, 7, 92, 95, 113);
   }

   private static MetadataPanel createMetadataPanel(JPanel bottomPanel) {
      MetadataPanel metadataPanel = new MetadataPanel();
      GUIUtils.addWithEdges(bottomPanel, metadataPanel, 0, 0, 0, 0);
      metadataPanel.setBorder(BorderFactory.createEmptyBorder());
      return metadataPanel;
   }

   private void createPleaLabel(JPanel topPanel) {
      JLabel citePleaLabel = new JLabel("<html>Please <a href=\"http://micro-manager.org\">cite Micro-Manager</a> so funding will continue!</html>");
      citePleaLabel.setFont(new Font("Arial", Font.PLAIN, 11));
      GUIUtils.addWithEdges(topPanel, citePleaLabel, 7, 119, 270, 139);

      // When users click on the citation plea, we spawn a new thread to send
      // their browser to the MM wiki.
      citePleaLabel.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(new Runnable() {
               @Override
               public void run(){
                  try {
                     ij.plugin.BrowserLauncher.openURL("https://micro-manager.org/wiki/Citing_Micro-Manager");
                  } catch (IOException e1) {
                     ReportingUtils.showError(e1);
                  }
               }
            }).start();
         }
      });

      // add a listener to the main ImageJ window to catch it quitting out on us
      /*
       * The current version of ImageJ calls the command "Quit", which we 
       * handle in MMStudioPlugin.  Calling the closeSequence from here as well
       * leads to crashes since the core will be cleaned up by one of the two
       * threads doing the same thing.  I do not know since which version of 
       * ImageJ introduced this behavior - NS, 2014-04-26
      
      if (ij.IJ.getInstance() != null) {
         ij.IJ.getInstance().addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
               //closeSequence(true);
            };
         });
      }
      */
   }

   private JSplitPane createSplitPane(int dividerPos) {
      JPanel topPanel = new JPanel();
      JPanel bottomPanel = new JPanel();
      topPanel.setLayout(new SpringLayout());
      topPanel.setMinimumSize(new Dimension(580, 195));
      bottomPanel.setLayout(new SpringLayout());
      JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true,
              topPanel, bottomPanel);
      splitPane.setBorder(BorderFactory.createEmptyBorder());
      splitPane.setDividerLocation(dividerPos);
      splitPane.setResizeWeight(0.0);
      return splitPane;
   }

   private void createTopPanelWidgets(JPanel topPanel) {
      createMainButtons(topPanel);
      createCameraSettingsWidgets(topPanel);
      createPleaLabel(topPanel);
      createUtilityButtons(topPanel);
      createConfigurationControls(topPanel);
      labelImageDimensions_ = createLabel("", false, topPanel, 5, -20, 0, 0);
   }

   private void createUtilityButtons(JPanel topPanel) {
      // ROI
      
      createLabel("ROI", true, topPanel, 8, 140, 71, 154);
      
      setRoiButton_ = GUIUtils.createButton(false, "setRoiButton", null,
              "Set Region Of Interest to selected rectangle",
              new Runnable() {
                 public void run() {
                    setROI();
                 }
              }, "shape_handles.png", topPanel, 7, 154, 37, 174);

      clearRoiButton_ = GUIUtils.createButton(false, "clearRoiButton", null,
              "Reset Region of Interest to full frame",
              new Runnable() {
                 public void run() {
                    clearROI();
                 }
              }, "arrow_out.png", topPanel, 40, 154, 70, 174);
      
      // Zoom
      
      createLabel("Zoom", true, topPanel, 81, 140, 139, 154);

      GUIUtils.createButton(false, "zoomInButton", null,
              "Zoom in",
              new Runnable() {
                 public void run() {
                    zoomIn();
                 }
              }, "zoom_in.png", topPanel, 80, 154, 110, 174);

      GUIUtils.createButton(false, "zoomOutButton", null,
              "Zoom out",
              new Runnable() {
                 public void run() {
                    zoomOut();
                 }
              }, "zoom_out.png", topPanel, 113, 154, 143, 174);

      // Profile
      
      createLabel("Profile", true, topPanel, 154, 140, 217, 154);

      GUIUtils.createButton(false, "lineProfileButton", null,
              "Open line profile window (requires line selection)",
              new Runnable() {
                 public void run() {
                    openLineProfileWindow();
                 }
              }, "chart_curve.png", topPanel, 153, 154, 183, 174);

      // Autofocus

      createLabel("Autofocus", true, topPanel, 194, 140, 276, 154);

      autofocusNowButton_ = (JButton) GUIUtils.createButton(false,
              "autofocusNowButton", null,
              "Autofocus now",
              new Runnable() {
                 public void run() {
                    autofocusNow();
                 }
              }, "find.png", topPanel, 193, 154, 223, 174);


      autofocusConfigureButton_ = (JButton) GUIUtils.createButton(false,
              "autofocusConfigureButton", null,
              "Set autofocus options",
              new Runnable() {
                 public void run() {
                    showAutofocusDialog();
                 }
              }, "wrench_orange.png", topPanel, 226, 154, 256, 174);
   }

   private void initializeFileMenu() {
      JMenu fileMenu = GUIUtils.createMenuInMenuBar(menuBar_, "File");

      GUIUtils.addMenuItem(fileMenu, "Open (Virtual)...", null,
              new Runnable() {
                 public void run() {
                    new Thread() {
                       @Override
                       public void run() {
                          openAcquisitionData(false);
                       }
                    }.start();
                 }
              });

      GUIUtils.addMenuItem(fileMenu, "Open (RAM)...", null,
              new Runnable() {
                 public void run() {
                    new Thread() {
                       @Override
                       public void run() {
                          openAcquisitionData(true);
                       }
                    }.start();
                 }
              });

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Exit", null,
              new Runnable() {
                 public void run() {
                    closeSequence(false);
                 }
              });
   }
   
    private void initializeHelpMenu() {
        final JMenu helpMenu = GUIUtils.createMenuInMenuBar(menuBar_, "Help");
        
        GUIUtils.addMenuItem(helpMenu, "User's Guide", null,
                new Runnable() {
                   public void run() {
                try {
                    ij.plugin.BrowserLauncher.openURL("http://micro-manager.org/wiki/Micro-Manager_User%27s_Guide");
                } catch (IOException e1) {
                    ReportingUtils.showError(e1);
                }
            }
        });
        
       GUIUtils.addMenuItem(helpMenu, "Configuration Guide", null,
               new Runnable() {
                  public void run() {
                     try {
                        ij.plugin.BrowserLauncher.openURL("http://micro-manager.org/wiki/Micro-Manager_Configuration_Guide");
                     } catch (IOException e1) {
                        ReportingUtils.showError(e1);
                     }
                  }
               });        
        
       if (!systemPrefs_.getBoolean(RegistrationDlg.REGISTRATION, false)) {
          GUIUtils.addMenuItem(helpMenu, "Register your copy of Micro-Manager...", null,
                  new Runnable() {
                     public void run() {
                        try {
                           RegistrationDlg regDlg = new RegistrationDlg(systemPrefs_);
                           regDlg.setVisible(true);
                        } catch (Exception e1) {
                           ReportingUtils.showError(e1);
                        }
                     }
                  });
       }

       GUIUtils.addMenuItem(helpMenu, "Report Problem...", null,
               new Runnable() {
                  @Override
                  public void run() {
                     org.micromanager.diagnostics.gui.ProblemReportController.start(core_, options_);
                  }
               });

       GUIUtils.addMenuItem(helpMenu, "About Micromanager", null,
               new Runnable() {
                  public void run() {
                     MMAboutDlg dlg = new MMAboutDlg();
                     String versionInfo = "MM Studio version: " + MMVersion.VERSION_STRING;
                     versionInfo += "\n" + core_.getVersionInfo();
                     versionInfo += "\n" + core_.getAPIVersionInfo();
                     versionInfo += "\nUser: " + core_.getUserId();
                     versionInfo += "\nHost: " + core_.getHostName();
                     dlg.setVersionInfo(versionInfo);
                     dlg.setVisible(true);
                  }
               });
        
        
        menuBar_.validate();
    }

   private void initializeToolsMenu() {
      // Tools menu
      
      final JMenu toolsMenu = GUIUtils.createMenuInMenuBar(menuBar_, "Tools");

      GUIUtils.addMenuItem(toolsMenu, "Refresh GUI",
              "Refresh all GUI controls directly from the hardware",
              new Runnable() {
                 public void run() {
                    core_.updateSystemStateCache();
                    updateGUI(true);
                 }
              },
              "arrow_refresh.png");

      GUIUtils.addMenuItem(toolsMenu, "Rebuild GUI",
              "Regenerate Micro-Manager user interface",
              new Runnable() {
                 public void run() {
                    initializeGUI();
                    core_.updateSystemStateCache();
                 }
              });
      
      toolsMenu.addSeparator();
      
      GUIUtils.addMenuItem(toolsMenu, "Image Pipeline...",
            "Display the image processing pipeline",
            new Runnable() {
               public void run() {
                  pipelinePanel_.setVisible(true);
               }
            });

      GUIUtils.addMenuItem(toolsMenu, "Script Panel...",
              "Open Micro-Manager script editor window",
              new Runnable() {
                 public void run() {
                    scriptPanel_.setVisible(true);
                 }
              });

      GUIUtils.addMenuItem(toolsMenu, "Shortcuts...",
              "Create keyboard shortcuts to activate image acquisition, mark positions, or run custom scripts",
              new Runnable() {
                 public void run() {
                    HotKeysDialog hk = new HotKeysDialog(guiColors_.background.get((options_.displayBackground_)));
                    //hk.setBackground(guiColors_.background.get((options_.displayBackground_)));
                 }
              });

      GUIUtils.addMenuItem(toolsMenu, "Device/Property Browser...",
              "Open new window to view and edit property values in current configuration",
              new Runnable() {
                 public void run() {
                    createPropertyEditor();
                 }
              });
      
      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "XY List...",
              "Open position list manager window",
              new Runnable() {
                 public void run() {
                    showXYPositionList();
                 }
              },
              "application_view_list.png");

      GUIUtils.addMenuItem(toolsMenu, "Multi-Dimensional Acquisition...",
              "Open multi-dimensional acquisition setup window",
              new Runnable() {
                 public void run() {
                    openAcqControlDialog();
                 }
              },
              "film.png");
      
      
      centerAndDragMenuItem_ = GUIUtils.addCheckBoxMenuItem(toolsMenu,
              "Mouse Moves Stage (use Hand Tool)",
              "When enabled, double clicking or dragging in the snap/live\n"
              + "window moves the XY-stage. Requires the hand tool.",
              new Runnable() {
                 public void run() {
                    updateCenterAndDragListener();
                    IJ.setTool(Toolbar.HAND);
                    mainPrefs_.putBoolean(MOUSE_MOVES_STAGE, centerAndDragMenuItem_.isSelected());
                 }
              },
              mainPrefs_.getBoolean(MOUSE_MOVES_STAGE, false));
      
      GUIUtils.addMenuItem(toolsMenu, "Pixel Size Calibration...",
              "Define size calibrations specific to each objective lens.  "
              + "When the objective in use has a calibration defined, "
              + "micromanager will automatically use it when "
              + "calculating metadata",
              new Runnable() {
                 public void run() {
                    createCalibrationListDlg();
                 }
              });
      toolsMenu.addSeparator();

      GUIUtils.addMenuItem(toolsMenu, "Hardware Configuration Wizard...",
              "Open wizard to create new hardware configuration",
              new Runnable() {
                 public void run() {
                    runHardwareWizard();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu, "Load Hardware Configuration...",
              "Un-initialize current configuration and initialize new one",
              new Runnable() {
                 public void run() {
                    loadConfiguration();
                    initializeGUI();
                 }
              });

      GUIUtils.addMenuItem(toolsMenu, "Reload Hardware Configuration",
              "Shutdown current configuration and initialize most recently loaded configuration",
              new Runnable() {
                 public void run() {
                    loadSystemConfiguration();
                    initializeGUI();
                 }
              });

      
      for (int i=0; i<5; i++)
      {
         JMenuItem configItem = new JMenuItem();
         configItem.setText(Integer.toString(i));
         switchConfigurationMenu_.add(configItem);
      }
      
      switchConfigurationMenu_.setText("Switch Hardware Configuration");
      toolsMenu.add(switchConfigurationMenu_);
      switchConfigurationMenu_.setToolTipText("Switch between recently used configurations");

      GUIUtils.addMenuItem(toolsMenu, "Save Configuration Settings as...",
              "Save current configuration settings as new configuration file",
              new Runnable() {
                 public void run() {
                    saveConfigPresets();
                    updateChannelCombos();
                 }
              });

      toolsMenu.addSeparator();

      final MMStudioMainFrame thisInstance = this;
      GUIUtils.addMenuItem(toolsMenu, "Options...",
              "Set a variety of Micro-Manager configuration options",
              new Runnable() {
         public void run() {
            final int oldBufsize = options_.circularBufferSizeMB_;

            OptionsDlg dlg = new OptionsDlg(options_, core_, mainPrefs_,
                    thisInstance);
            dlg.setVisible(true);
            // adjust memory footprint if necessary
            if (oldBufsize != options_.circularBufferSizeMB_) {
               try {
                  core_.setCircularBufferMemoryFootprint(options_.circularBufferSizeMB_);
               } catch (Exception exc) {
                  ReportingUtils.showError(exc);
               }
            }
         }
      });
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

   private void updateSwitchConfigurationMenu() {
      switchConfigurationMenu_.removeAll();
      for (final String configFile : MRUConfigFiles_) {
         if (!configFile.equals(sysConfigFile_)) {
            GUIUtils.addMenuItem(switchConfigurationMenu_,
                    configFile, null,
                    new Runnable() {
               public void run() {
                  sysConfigFile_ = configFile;
                  loadSystemConfiguration();
                  mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
               }
            });
         }
      }
   }


   
   public final void addLiveModeListener (LiveModeListener listener) {
      if (liveModeListeners_.contains(listener)) {
         return;
      }
      liveModeListeners_.add(listener);
   }
   
   public void removeLiveModeListener(LiveModeListener listener) {
      liveModeListeners_.remove(listener);
   }
   
   public void callLiveModeListeners(boolean enable) {
      for (LiveModeListener listener : liveModeListeners_) {
         listener.liveModeEnabled(enable);
      }
   }
   
 
   /**
    * Part of ScriptInterface
    * Manipulate acquisition so that it looks like a burst
    */
   public void runBurstAcquisition() throws MMScriptException {
      double interval = engine_.getFrameIntervalMs();
      int nr = engine_.getNumFrames();
      boolean doZStack = engine_.isZSliceSettingEnabled();
      boolean doChannels = engine_.isChannelsSettingEnabled();
      engine_.enableZSliceSetting(false);
      engine_.setFrames(nr, 0);
      engine_.enableChannelsSetting(false);
      try {
         engine_.acquire();
      } catch (MMException e) {
         throw new MMScriptException(e);
      }
      engine_.setFrames(nr, interval);
      engine_.enableZSliceSetting(doZStack);
      engine_.enableChannelsSetting(doChannels);
   }

   public void runBurstAcquisition(int nr) throws MMScriptException {
      int originalNr = engine_.getNumFrames();
      double interval = engine_.getFrameIntervalMs();
      engine_.setFrames(nr, 0);
      this.runBurstAcquisition();
      engine_.setFrames(originalNr, interval);
   }

   public void runBurstAcquisition(int nr, String name, String root) throws MMScriptException {
      String originalRoot = engine_.getRootName();
      engine_.setDirName(name);
      engine_.setRootName(root);
      this.runBurstAcquisition(nr);
      engine_.setRootName(originalRoot);
   }

   /**
    * @Deprecated
    * @throws MMScriptException
    */
   public void startBurstAcquisition() throws MMScriptException {
      runAcquisition();
   }

   public boolean isBurstAcquisitionRunning() throws MMScriptException {
      if (engine_ == null)
         return false;
      return engine_.isAcquisitionRunning();
   }

   private void startLoadingPipelineClass() {
      Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
      acquisitionEngine2010LoadingThread_ = new Thread("Pipeline Class loading thread") {
         @Override
         public void run() {
            try {
               acquisitionEngine2010Class_  = Class.forName("org.micromanager.AcquisitionEngine2010");
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
               acquisitionEngine2010Class_ = null;
            }
         }
      };
      acquisitionEngine2010LoadingThread_.start();
   }


   
   /**
    * Shows images as they appear in the default display window. Uses
    * the default processor stack to process images as they arrive on
    * the rawImageQueue.
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


   private static JLabel createLabel(String text, boolean big,
           JPanel parentPanel, int west, int north, int east, int south) {
            final JLabel label = new JLabel();
      label.setFont(new Font("Arial",
              big ? Font.BOLD : Font.PLAIN,
              big ? 11 : 10));
      label.setText(text);
      GUIUtils.addWithEdges(parentPanel, label, west, north, east, south);
      return label;
   }

   public interface DisplayImageRoutine {
      public void show(TaggedImage image);
   }
   
   /**
    * used to store contrast settings to be later used for initialization of contrast of new windows.
    *  Shouldn't be called by loaded data sets, only
    * ones that have been acquired
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

   public HistogramSettings loadStoredChannelHisotgramSettings(String channelGroup, String channel, boolean mda) {
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

   private void setExposure() {
      try {
         if (!isLiveModeOn()) {
            core_.setExposure(NumberUtils.displayStringToDouble(
                    textFieldExp_.getText()));
         } else {
            liveModeTimer_.stop();
            core_.setExposure(NumberUtils.displayStringToDouble(
                    textFieldExp_.getText()));
            try {
               liveModeTimer_.begin();
            } catch (Exception e) {
               ReportingUtils.showError("Couldn't restart live mode");
               liveModeTimer_.stop();
            }
         }
        

         // Display the new exposure time
         double exposure = core_.getExposure();
         textFieldExp_.setText(NumberUtils.doubleToDisplayString(exposure));
         
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
         

      } catch (Exception exp) {
         // Do nothing.
      }
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

   private void updateTitle() {
      this.setTitle(MICRO_MANAGER_TITLE + " " + MMVersion.VERSION_STRING + " - " + sysConfigFile_);
   }

   public void updateLineProfile() {
      if (WindowManager.getCurrentWindow() == null || profileWin_ == null
            || !profileWin_.isShowing()) {
         return;
      }

      calculateLineProfileData(WindowManager.getCurrentImage());
      profileWin_.setData(lineProfileData_);
   }

   private void openLineProfileWindow() {
      if (WindowManager.getCurrentWindow() == null || WindowManager.getCurrentWindow().isClosed()) {
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
      profileWin_.setBackground(guiColors_.background.get((options_.displayBackground_)));
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

  

   private void setROI() {
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

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   private void clearROI() {
      try {
         boolean liveRunning = false;
         if (isLiveModeOn()) {
            liveRunning = true;
            enableLiveMode(false);
         }
         core_.clearROI();
         updateStaticInfo();
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
    * Returns singleton instance of MMStudioMainFrame
    */
   public static MMStudioMainFrame getInstance() {
      return gui_;
   }

   public MetadataPanel getMetadataPanel() {
      return metadataPanel_;
   }

   public final void setExitStrategy(boolean closeOnExit) {
      if (closeOnExit) {
         setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      }
      else {
         setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      }
   }

   @Override
   public void saveConfigPresets() {
      MicroscopeModel model = new MicroscopeModel();
      try {
         model.loadFromFile(sysConfigFile_);
         model.createSetupConfigsFromHardware(core_);
         model.createResolutionsFromHardware(core_);
         File f = FileDialogs.save(this, "Save the configuration file", MM_CONFIG_FILE);
         if (f != null) {
            model.saveToFile(f.getAbsolutePath());
            sysConfigFile_ = f.getAbsolutePath();
            mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
            configChanged_ = false;
            setConfigSaveButtonStatus(configChanged_);
            updateTitle();
         }
      } catch (MMConfigFileException e) {
         ReportingUtils.showError(e);
      }
   }

   protected void setConfigSaveButtonStatus(boolean changed) {
      saveConfigButton_.setEnabled(changed);
   }

   public String getAcqDirectory() {
      return openAcqDirectory_;
   }
   
   /**
    * Get currently used configuration file
    * @return - Path to currently used configuration file
    */
   public String getSysConfigFile() {
      return sysConfigFile_;
   }

   public void setAcqDirectory(String dir) {
      openAcqDirectory_ = dir;
   }

    /**
    * Open an existing acquisition directory and build viewer window.
    *
    */
   public void openAcquisitionData(boolean inRAM) {

      // choose the directory
      // --------------------
      File f = FileDialogs.openDir(this, "Please select an image data set", MM_DATA_SET);
      if (f != null) {
         if (f.isDirectory()) {
            openAcqDirectory_ = f.getAbsolutePath();
         } else {
            openAcqDirectory_ = f.getParent();
         }
         try {
            openAcquisitionData(openAcqDirectory_, inRAM);
         } catch (MMScriptException ex) {
            ReportingUtils.showError(ex);
         } 
         
      }
   }

   @Override
   public String openAcquisitionData(String dir, boolean inRAM, boolean show) 
           throws MMScriptException {
      String rootDir = new File(dir).getAbsolutePath();
      String name = new File(dir).getName();
      rootDir = rootDir.substring(0, rootDir.length() - (name.length() + 1));
      name = acqMgr_.getUniqueAcquisitionName(name);
      acqMgr_.openAcquisition(name, rootDir, show, !inRAM, true);
      try {
         getAcquisition(name).initialize();
      } catch (MMScriptException mex) {
         acqMgr_.closeAcquisition(name);
         throw (mex);
      }
     
      return name;
   }

   /**
    * Opens an existing data set. Shows the acquisition in a window.
    * @return The acquisition object.
    */
   @Override
   public String openAcquisitionData(String dir, boolean inRam) throws MMScriptException {
      return openAcquisitionData(dir, inRam, true);
   }

   protected void zoomOut() {
      ImageWindow curWin = WindowManager.getCurrentWindow();
      if (curWin != null) {
         ImageCanvas canvas = curWin.getCanvas();
         Rectangle r = canvas.getBounds();
         canvas.zoomOut(r.width / 2, r.height / 2);
      }
   }

   protected void zoomIn() {
      ImageWindow curWin = WindowManager.getCurrentWindow();
      if (curWin != null) {
         ImageCanvas canvas = curWin.getCanvas();
         Rectangle r = canvas.getBounds();
         canvas.zoomIn(r.width / 2, r.height / 2);
      }
   }

   protected void changeBinning() {
      try {
         boolean liveRunning = false;
         if (isLiveModeOn() ) {
            liveRunning = true;
            enableLiveMode(false);
        } 
         
         if (isCameraAvailable()) {
            Object item = comboBinning_.getSelectedItem();
            if (item != null) {
               core_.setProperty(cameraLabel_, MMCoreJ.getG_Keyword_Binning(), item.toString());
            }
         }
         updateStaticInfo();

         if (liveRunning) {
            enableLiveMode(true);
         }

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }

      
   }

   private void createPropertyEditor() {
      if (propertyBrowser_ != null) {
         propertyBrowser_.dispose();
      }

      propertyBrowser_ = new PropertyEditor();
      propertyBrowser_.setGui(this);
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      propertyBrowser_.setCore(core_);
   }

   private void createCalibrationListDlg() {
      if (calibrationListDlg_ != null) {
         calibrationListDlg_.dispose();
      }

      calibrationListDlg_ = new CalibrationListDlg(core_);
      calibrationListDlg_.setVisible(true);
      calibrationListDlg_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      calibrationListDlg_.setParentGUI(this);
   }

   public CalibrationListDlg getCalibrationListDlg() {
      if (calibrationListDlg_ == null) {
         createCalibrationListDlg();
      }
      return calibrationListDlg_;
   }

   private void createScriptPanel() {
      if (scriptPanel_ == null) {
         scriptPanel_ = new ScriptPanel(core_, options_, this);
         scriptPanel_.insertScriptingObject(SCRIPT_CORE_OBJECT, core_);
         scriptPanel_.insertScriptingObject(SCRIPT_ACQENG_OBJECT, engine_);
         scriptPanel_.setParentGUI(this);
         scriptPanel_.setBackground(guiColors_.background.get((options_.displayBackground_)));
         addMMBackgroundListener(scriptPanel_);
      }
   }

   private void createPipelinePanel() {
      if (pipelinePanel_ == null) {
         pipelinePanel_ = new PipelinePanel(this, engine_);
         pipelinePanel_.setBackground(guiColors_.background.get((options_.displayBackground_)));
         addMMBackgroundListener(pipelinePanel_);
      }
   }

   /**
    * Updates Status line in main window from cached values
    */
   private void updateStaticInfoFromCache() {
      String dimText = "Image info (from camera): " + staticInfo_.width_ + " X " + staticInfo_.height_ + " X "
            + staticInfo_.bytesPerPixel_ + ", Intensity range: " + staticInfo_.imageBitDepth_ + " bits";
      dimText += ", " + TextUtils.FMT0.format(staticInfo_.pixSizeUm_ * 1000) + "nm/pix";
      if (zStageLabel_.length() > 0) {
         dimText += ", Z=" + TextUtils.FMT2.format(staticInfo_.zPos_) + "um";
      }
      if (xyStageLabel_.length() > 0) {
         dimText += ", XY=(" + TextUtils.FMT2.format(staticInfo_.x_) + "," + TextUtils.FMT2.format(staticInfo_.y_) + ")um";
      }

      labelImageDimensions_.setText(dimText);
   }

   public void updateXYPos(double x, double y) {
      staticInfo_.x_ = x;
      staticInfo_.y_ = y;

      updateStaticInfoFromCache();
   }

   public void updateZPos(double z) {
      staticInfo_.zPos_ = z;

      updateStaticInfoFromCache();
   }

   public void updateXYPosRelative(double x, double y) {
      staticInfo_.x_ += x;
      staticInfo_.y_ += y;

      updateStaticInfoFromCache();
   }

   public void updateZPosRelative(double z) {
      staticInfo_.zPos_ += z;

      updateStaticInfoFromCache();
   }

   public void updateXYStagePosition(){

      double x[] = new double[1];
      double y[] = new double[1];
      try {
         if (xyStageLabel_.length() > 0) 
            core_.getXYPosition(xyStageLabel_, x, y);
      } catch (Exception e) {
          ReportingUtils.showError(e);
      }

      staticInfo_.x_ = x[0];
      staticInfo_.y_ = y[0];
      updateStaticInfoFromCache();
   }

   private void updatePixSizeUm (double pixSizeUm) {
      staticInfo_.pixSizeUm_ = pixSizeUm;

      updateStaticInfoFromCache();
   }

   private void updateStaticInfo() {
      double zPos = 0.0;
      double x[] = new double[1];
      double y[] = new double[1];

      try {
         if (zStageLabel_.length() > 0) {
            zPos = core_.getPosition(zStageLabel_);
         }
         if (xyStageLabel_.length() > 0) {
            core_.getXYPosition(xyStageLabel_, x, y);
         }
      } catch (Exception e) {
         ReportingUtils.showError(e, "Failed to get stage position");
      }

      staticInfo_.width_ = core_.getImageWidth();
      staticInfo_.height_ = core_.getImageHeight();
      staticInfo_.bytesPerPixel_ = core_.getBytesPerPixel();
      staticInfo_.imageBitDepth_ = core_.getImageBitDepth();
      staticInfo_.pixSizeUm_ = core_.getPixelSizeUm();
      staticInfo_.zPos_ = zPos;
      staticInfo_.x_ = x[0];
      staticInfo_.y_ = y[0];

      updateStaticInfoFromCache();
   }

   public void toggleShutter() {
      try {
         if (!toggleShutterButton_.isEnabled())
            return;
         toggleShutterButton_.requestFocusInWindow();
         if (toggleShutterButton_.getText().equals("Open")) {
            setShutterButton(true);
            core_.setShutterOpen(true);
         } else {
            core_.setShutterOpen(false);
            setShutterButton(false);
         }
      } catch (Exception e1) {
         ReportingUtils.showError(e1);
      }
   }

   private void updateCenterAndDragListener() {
      if (centerAndDragMenuItem_.isSelected()) {
         centerAndDragListener_.start();
      } else {
         centerAndDragListener_.stop();
      }
   }
   
   private void setShutterButton(boolean state) {
      if (state) {
         toggleShutterButton_.setText("Close");
      } else {
         toggleShutterButton_.setText("Open");
      }
   }
   
   
   private void checkPosListDlg() {
      if (posListDlg_ == null) {
         posListDlg_ = new PositionListDlg(core_, this, posList_, 
                 acqControlWin_,options_);
         GUIUtils.recallPosition(posListDlg_);
         posListDlg_.setBackground(gui_.getBackgroundColor());
         gui_.addMMBackgroundListener(posListDlg_);
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

   public Object getPixels() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null) {
         return ip.getProcessor().getPixels();
      }

      return null;
   }

   public void setPixels(Object obj) {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip == null) {
         return;
      }
      ip.getProcessor().setPixels(obj);
   }

   public int getImageHeight() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null)
         return ip.getHeight();
      return 0;
   }

   public int getImageWidth() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null)
         return ip.getWidth();
      return 0;
   }

   public int getImageDepth() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null)
         return ip.getBitDepth();
      return 0;
   }

   public ImageProcessor getImageProcessor() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip == null)
         return null;
      return ip.getProcessor();
   }

   private boolean isCameraAvailable() {
      return cameraLabel_.length() > 0;
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

   
   /**
    * Implements ScriptInterface
    */
   @Override
   @Deprecated
   public PositionListDlg getXYPosListDlg() {
      checkPosListDlg();
      return posListDlg_;
   }

   /**
    * Implements ScriptInterface
    */
   @Override
   public boolean isAcquisitionRunning() {
      if (engine_ == null)
         return false;
      return engine_.isAcquisitionRunning();
   }

   /**
    * Implements ScriptInterface
    */
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

      } catch (Exception ex) {
         throw new MMScriptException ("Format of version String should be \"a.b.c\"");
      }
   } 

   @Override
    public boolean isLiveModeOn() {
        return liveModeTimer_ != null && liveModeTimer_.isRunning();
   }
    
   public LiveModeTimer getLiveModeTimer() {
      if (liveModeTimer_ == null) {
         liveModeTimer_ = new LiveModeTimer();
      }
      return liveModeTimer_;
   }
   
   

   public void updateButtonsForLiveMode(boolean enable) {
      autoShutterCheckBox_.setEnabled(!enable);
      if (core_.getAutoShutter()) {
         toggleShutterButton_.setText(enable ? "Close" : "Open" );
      }
      snapButton_.setEnabled(!enable);
      //toAlbumButton_.setEnabled(!enable);
      liveButton_.setIcon(enable ? SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/cancel.png")
              : SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/camera_go.png"));
      liveButton_.setSelected(false);
      liveButton_.setText(enable ? "Stop Live" : "Live");
      
   }

   public boolean getLiveMode() {
      return isLiveModeOn();
   }

   public boolean updateImage() {
      try {
         if (isLiveModeOn()) {
               enableLiveMode(false);
               return true; // nothing to do, just show the last image
         }

         if (WindowManager.getCurrentWindow() == null) {
            return false;
         }

         ImagePlus ip = WindowManager.getCurrentImage();
         
         core_.snapImage();
         Object img = core_.getImage();

         ip.getProcessor().setPixels(img);
         ip.updateAndRepaintWindow();

         if (!isCurrentImageFormatSupported()) {
            return false;
         }
       
         updateLineProfile();
      } catch (Exception e) {
         ReportingUtils.showError(e);
         return false;
      }

      return true;
   }

   public boolean displayImage(final Object pixels) {
      if (pixels instanceof TaggedImage) {
         return displayTaggedImage((TaggedImage) pixels, true);
      } else {
         return displayImage(pixels, true);
      }
   }


   public boolean displayImage(final Object pixels, boolean wait) {
      checkSimpleAcquisition();
      try {   
            int width = getAcquisition(SIMPLE_ACQ).getWidth();
            int height = getAcquisition(SIMPLE_ACQ).getHeight();
            int byteDepth = getAcquisition(SIMPLE_ACQ).getByteDepth();          
            TaggedImage ti = ImageUtils.makeTaggedImage(pixels, 0, 0, 0,0, width, height, byteDepth);
            simpleDisplay_.getImageCache().putImage(ti);
            simpleDisplay_.imageReceived(ti);
            return true;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
         return false;
      }
   }

   public boolean displayImageWithStatusLine(Object pixels, String statusLine) {
      boolean ret = displayImage(pixels);
      simpleDisplay_.displayStatusLine(statusLine);
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
      boolean ret = false;
      long channels = core_.getNumberOfComponents();
      long bpp = core_.getBytesPerPixel();

      if (channels > 1 && channels != 4 && bpp != 1) {
         handleError("Unsupported image format.");
      } else {
         ret = true;
      }
      return ret;
   }

   public void doSnap() {
      doSnap(false);
   }

   public void doSnap(final boolean album) {
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
                  if (album) {
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

         if (simpleDisplay_ != null) {
            ImagePlus imgp = simpleDisplay_.getImagePlus();
            if (imgp != null) {
               ImageWindow win = imgp.getWindow();
               if (win != null) {
                  win.toFront();
               }
            }
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   /**
    * Is this function still needed?  It does some magic with tags. I found 
    * it to do harmful thing with tags when a Multi-Camera device is
    * present (that issue is now fixed).
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
         checkSimpleAcquisition(ti);
         setCursor(new Cursor(Cursor.WAIT_CURSOR));
         MDUtils.setSummary(ti.tags, getAcquisition(SIMPLE_ACQ).getSummaryMetadata());
         addStagePositionToTags(ti);
         addImage(SIMPLE_ACQ, ti, update, true);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return false;
      }
      if (update) {
         setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
         updateLineProfile();
      }
      return true;
   }
   
   public void addStagePositionToTags(TaggedImage ti) throws JSONException {
      if (gui_.xyStageLabel_.length() > 0) {
         MDUtils.setXPositionUm(ti.tags, gui_.staticInfo_.x_);
         MDUtils.setYPositionUm(ti.tags, gui_.staticInfo_.y_);
      }
      if (gui_.zStageLabel_.length() > 0) {
         MDUtils.setZPositionUm(ti.tags, gui_.staticInfo_.zPos_);
      }
   }

    private void configureBinningCombo() throws Exception {
        if (cameraLabel_.length() > 0) {
            ActionListener[] listeners;

            // binning combo
            if (comboBinning_.getItemCount() > 0) {
                comboBinning_.removeAllItems();
            }
            StrVector binSizes = core_.getAllowedPropertyValues(
                    cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            listeners = comboBinning_.getActionListeners();
            for (int i = 0; i < listeners.length; i++) {
                comboBinning_.removeActionListener(listeners[i]);
            }
            for (int i = 0; i < binSizes.size(); i++) {
                comboBinning_.addItem(binSizes.get(i));
            }

            comboBinning_.setMaximumRowCount((int) binSizes.size());
            if (binSizes.isEmpty()) {
                comboBinning_.setEditable(true);
            } else {
                comboBinning_.setEditable(false);
            }

            for (int i = 0; i < listeners.length; i++) {
                comboBinning_.addActionListener(listeners[i]);
            }
        }
    }

   public void initializeGUI() {
      try {

         // establish device roles
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         xyStageLabel_ = core_.getXYStageDevice();
         engine_.setZStageDevice(zStageLabel_);  
  
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

            GUIUtils.replaceComboContents(shutterComboBox_, items);
            String activeShutter = core_.getShutterDevice();
            if (activeShutter != null) {
               shutterComboBox_.setSelectedItem(activeShutter);
            } else {
               shutterComboBox_.setSelectedItem("");
            }
         }

         // Autofocus
         autofocusConfigureButton_.setEnabled(afMgr_.getDevice() != null);
         autofocusNowButton_.setEnabled(afMgr_.getDevice() != null);

         // Rebuild stage list in XY PositinList
         if (posListDlg_ != null) {
            posListDlg_.rebuildAxisList();
         }

         updateGUI(true);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }


   
    /**
    * Adds plugin_ items to the plugins menu
    * Adds submenus (currently only 1 level deep)
    * @param plugin_ - plugin_ to be added to the menu
    */
   public void addPluginToMenu(final PluginLoader.PluginItem plugin) {
      List<String> path = plugin.getMenuPath();
      if (path.size() == 1) {
         GUIUtils.addMenuItem(pluginMenu_, plugin.getMenuItem(), plugin.getTooltip(),
                 new Runnable() {
            public void run() {
               displayPlugin(plugin);
            }
         });
      }
      if (path.size() == 2) {
         if (pluginSubMenus_ == null) {
            pluginSubMenus_ = new HashMap<String, JMenu>();
         }
         String groupName = path.get(0);
         JMenu submenu = pluginSubMenus_.get(groupName);
         if (submenu == null) {
            submenu = new JMenu(groupName);
            pluginSubMenus_.put(groupName, submenu);
            submenu.validate();
            pluginMenu_.add(submenu);
         }
         GUIUtils.addMenuItem(submenu, plugin.getMenuItem(), plugin.getTooltip(),
                 new Runnable() {
            public void run() {
               displayPlugin(plugin);
            }
         });
      }
      
      pluginMenu_.validate();
      menuBar_.validate();
   }

   // Handle a plugin being selected from the Plugins menu.
   private static void displayPlugin(final PluginLoader.PluginItem plugin) {
      ReportingUtils.logMessage("Plugin command: " + plugin.getMenuItem());
      plugin.instantiate();
      switch (plugin.getPluginType()) {
         case PLUGIN_STANDARD:
            // Standard plugin; create its UI.
            ((MMPlugin) plugin.getPlugin()).show();
            break;
         case PLUGIN_PROCESSOR:
            // Processor plugin; check for existing processor of 
            // this type and show its UI if applicable; otherwise
            // create a new one.
            MMProcessorPlugin procPlugin = (MMProcessorPlugin) plugin.getPlugin();
            String procName = PluginLoader.getNameForPluginClass(procPlugin.getClass());
            DataProcessor<TaggedImage> pipelineProcessor = gui_.engine_.getProcessorRegisteredAs(procName);
            if (pipelineProcessor == null) {
               // No extant processor of this type; make a new one,
               // which automatically adds it to the pipeline.
               pipelineProcessor = gui_.engine_.makeProcessor(procName, gui_);
            }
            if (pipelineProcessor != null) {
               // Show the GUI for this processor. The extra null check is 
               // because making the processor (above) could have failed.
               pipelineProcessor.makeConfigurationGUI();
            }
            break;
         default:
            // Unrecognized plugin type; just skip it. 
            ReportingUtils.logError("Unrecognized plugin type " + plugin.getPluginType());
      }
   }
 
   @Subscribe
   public void onConfigGroupChanged(ConfigGroupChangedEvent event) {
      configPad_.refreshGroup(event.getGroupName(), event.getNewConfig());
   }

   @Subscribe
   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
      updatePixSizeUm(event.getNewPixelSizeUm());
   }

   @Subscribe
   public void onPropertiesChanged(PropertiesChangedEvent event) {
      updateGUI(true);
   }

   @Subscribe
   public void onStagePositionChanged(StagePositionChangedEvent event) {
      updateZPos(event.getPos());
   }

   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      updateXYPos(event.getXPos(), event.getYPos());
   }

   @Subscribe
   public void onExposureChanged(ExposureChangedEvent event) {
      if (event.getCameraName() == cameraLabel_) {
         textFieldExp_.setText(NumberUtils.doubleToDisplayString(event.getNewExposureTime()));
      }
   }

   public void updateGUI(boolean updateConfigPadStructure) {
      updateGUI(updateConfigPadStructure, false);
   }

   public void updateGUI(boolean updateConfigPadStructure, boolean fromCache) {

      try {
         // establish device roles
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         xyStageLabel_ = core_.getXYStageDevice();

         afMgr_.refresh();

         // camera settings
         if (isCameraAvailable()) {
            double exp = core_.getExposure();
            textFieldExp_.setText(NumberUtils.doubleToDisplayString(exp));
            configureBinningCombo();
            String binSize;
            if (fromCache) {
               binSize = core_.getPropertyFromCache(cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            } else {
               binSize = core_.getProperty(cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            }
            GUIUtils.setComboSelection(comboBinning_, binSize);
         }

         if (liveModeTimer_ == null || !liveModeTimer_.isRunning()) {
            autoShutterCheckBox_.setSelected(core_.getAutoShutter());
            boolean shutterOpen = core_.getShutterOpen();
            setShutterButton(shutterOpen);
            if (autoShutterCheckBox_.isSelected()) {
               toggleShutterButton_.setEnabled(false);
            } else {
               toggleShutterButton_.setEnabled(true);
            }
         }

         // active shutter combo
         if (shutters_ != null) {
            String activeShutter = core_.getShutterDevice();
            if (activeShutter != null) {
               shutterComboBox_.setSelectedItem(activeShutter);
            } else {
               shutterComboBox_.setSelectedItem("");
            }
         }

         // state devices
         if (updateConfigPadStructure && (configPad_ != null)) {
            configPad_.refreshStructure(fromCache);
            // Needed to update read-only properties.  May slow things down...
            if (!fromCache)
               core_.updateSystemStateCache();
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

      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      updateStaticInfo();
      updateTitle();

   }

   //TODO: Deprecated @Override
   public boolean okToAcquire() {
      return !isLiveModeOn();
   }

   //TODO: Deprecated @Override
   public void stopAllActivity() {
        if (this.acquisitionEngine2010_ != null) {
            this.acquisitionEngine2010_.stop();
        }
      enableLiveMode(false);
   }

   /**
    * Cleans up resources while shutting down 
    * 
    * @param calledByImageJ
    * @return flag indicating success.  Shut down should abort when flag is false 
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
      if (liveModeTimer_ != null)
         liveModeTimer_.stop();
      
       // check needed to avoid deadlock
       if (!calledByImageJ) {
           if (!WindowManager.closeAllWindows()) {
               core_.logMessage("Failed to close some windows");
           }
       }

      if (profileWin_ != null) {
         removeMMBackgroundListener(profileWin_);
         profileWin_.dispose();
      }

      if (scriptPanel_ != null) {
         removeMMBackgroundListener(scriptPanel_);
         scriptPanel_.closePanel();
      }

      if (pipelinePanel_ != null) {
         removeMMBackgroundListener(pipelinePanel_);
         pipelinePanel_.dispose();
      }

      if (propertyBrowser_ != null) {
         removeMMBackgroundListener(propertyBrowser_);
         propertyBrowser_.dispose();
      }

      if (acqControlWin_ != null) {
         removeMMBackgroundListener(acqControlWin_);
         acqControlWin_.close();
      }

      if (engine_ != null) {
         engine_.shutdown();
      }

      if (afMgr_ != null) {
         afMgr_.closeOptionsDialog();
      }

      engine_.disposeProcessors();

      pluginLoader_.disposePlugins();

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
      Rectangle r = this.getBounds();

      mainPrefs_.putInt(MAIN_FRAME_X, r.x);
      mainPrefs_.putInt(MAIN_FRAME_Y, r.y);
      mainPrefs_.putInt(MAIN_FRAME_WIDTH, r.width);
      mainPrefs_.putInt(MAIN_FRAME_HEIGHT, r.height);
      mainPrefs_.putInt(MAIN_FRAME_DIVIDER_POS, this.splitPane_.getDividerLocation());
      
      mainPrefs_.put(OPEN_ACQ_DIR, openAcqDirectory_);
      mainPrefs_.put(MAIN_SAVE_METHOD, 
              ImageUtils.getImageStorageClass().getName());

      // save field values from the main window
      // NOTE: automatically restoring these values on startup may cause
      // problems
      mainPrefs_.put(MAIN_EXPOSURE, textFieldExp_.getText());

      // NOTE: do not save auto shutter state

      if (afMgr_ != null && afMgr_.getDevice() != null) {
         mainPrefs_.put(AUTOFOCUS_DEVICE, afMgr_.getDevice().getDeviceName());
      }
   }

   private void loadConfiguration() {
      File f = FileDialogs.openFile(this, "Load a config file",MM_CONFIG_FILE);
      if (f != null) {
         sysConfigFile_ = f.getAbsolutePath();
         configChanged_ = false;
         setConfigSaveButtonStatus(configChanged_);
         mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
         loadSystemConfiguration();
      }
   }


   public synchronized boolean closeSequence(boolean calledByImageJ) {

      if (!this.isRunning()) {
         if (core_ != null) {
            core_.logMessage("MMStudioMainFrame::closeSequence called while running_ is false");
         }
         return true;
      }
      
      if (engine_ != null && engine_.isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(
               this,
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

      running_ = false;

      saveSettings();
      try {
         configPad_.saveSettings();
         options_.saveSettings();
         hotKeys_.saveSettings();
      } catch (NullPointerException e) {
         if (core_ != null)
            this.logError(e);
      }     
      // disposing sometimes hangs ImageJ!
      // this.dispose();
      if (options_.closeOnExit_) {
         if (!runsAsPlugin_) {
            System.exit(0);
         } else {
            ImageJ ij = IJ.getInstance();
            if (ij != null) {
               ij.quit();
            }
         }
      } else {
         this.dispose();
      }
      
      return true;
   }

   /*
   public void applyContrastSettings(ContrastSettings contrast8,
         ContrastSettings contrast16) {
      ImagePlus img = WindowManager.getCurrentImage();
      if (img == null|| VirtualAcquisitionDisplay.getDisplay(img) == null )
         return;
      if (img.getBytesPerPixel() == 1)     
         VirtualAcquisitionDisplay.getDisplay(img).setChannelContrast(0,
                 contrast8.min, contrast8.max, contrast8.gamma);
      else
         VirtualAcquisitionDisplay.getDisplay(img).setChannelContrast(0, 
                 contrast16.min, contrast16.max, contrast16.gamma);
   }
   */

   //TODO: Deprecated @Override
   public ContrastSettings getContrastSettings() {
      ImagePlus img = WindowManager.getCurrentImage();
      if (img == null || VirtualAcquisitionDisplay.getDisplay(img) == null )
         return null;
      return VirtualAcquisitionDisplay.getDisplay(img).getChannelContrastSettings(0);
   }
   
/*
   public boolean is16bit() {
      ImagePlus ip = WindowManager.getCurrentImage();
      if (ip != null && ip.getProcessor() instanceof ShortProcessor) {
         return true;
      }
      return false;
   }
   * */

   public boolean isRunning() {
      return running_;
   }

   /**
    * Executes the beanShell script. This script instance only supports
    * commands directed to the core object.
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
            // insert core object only
            interp.set(SCRIPT_CORE_OBJECT, core_);
            interp.set(SCRIPT_ACQENG_OBJECT, engine_);
            interp.set(SCRIPT_GUI_OBJECT, this);

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
    */
   private boolean loadSystemConfiguration() {
      boolean result = true;

      saveMRUConfigFiles();

      final WaitDialog waitDlg = new WaitDialog(
              "Loading system configuration, please wait...");

      waitDlg.setAlwaysOnTop(true);
      waitDlg.showDialog();
      this.setEnabled(false);

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

         ReportingUtils.showError(err);
         result = false;
      } finally {
         waitDlg.closeDialog();
      }
      setEnabled(true);
      initializeGUI();

      updateSwitchConfigurationMenu();

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
               value = MRUConfigFiles_.get(icfg).toString();
            }
            mainPrefs_.put(CFGFILE_ENTRY_BASE + icfg.toString(), value);
         }
      }
   }

   private void loadMRUConfigFiles() {
      sysConfigFile_ = mainPrefs_.get(SYSTEM_CONFIG_FILE, sysConfigFile_);
      // startupScriptFile_ = mainPrefs_.get(STARTUP_SCRIPT_FILE,
      // startupScriptFile_);
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
      if (0 < sysConfigFile_.length()) {
         if (!MRUConfigFiles_.contains(sysConfigFile_)) {
            // in case persistant data is inconsistent
            if (maxMRUCfgs_ <= MRUConfigFiles_.size()) {
               MRUConfigFiles_.remove(maxMRUCfgs_ - 1);
            }
            MRUConfigFiles_.add(0, sysConfigFile_);
         }
      }
   }

   /**
    * Opens Acquisition dialog.
    */
   private void openAcqControlDialog() {
      try {
         if (acqControlWin_ == null) {
            acqControlWin_ = new AcqControlDlg(engine_, mainPrefs_, this, options_);
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
   

   private void updateChannelCombos() {
      if (this.acqControlWin_ != null) {
         this.acqControlWin_.updateChannelAndGroupCombo();
      }
   }
     
   private void runHardwareWizard() {
      try {
         if (configChanged_) {
            Object[] options = {"Yes", "No"};
            int n = JOptionPane.showOptionDialog(null,
                  "Save Changed Configuration?", "Micro-Manager",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.QUESTION_MESSAGE, null, options,
                  options[0]);
            if (n == JOptionPane.YES_OPTION) {
               saveConfigPresets();
            }
            configChanged_ = false;
         }

         boolean liveRunning = false;
         if (isLiveModeOn()) {
            liveRunning = true;
            enableLiveMode(false);
         }

         // unload all devices before starting configurator
         core_.reset();
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // run Configurator
         ConfiguratorDlg2 cfg2 = null;
         try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            cfg2 = new ConfiguratorDlg2(core_, sysConfigFile_);
         } finally {
            setCursor(Cursor.getDefaultCursor());        		 
         }

         if (cfg2 == null)
         {
            ReportingUtils.showError("Failed to launch Hardware Configuration Wizard");
            return;
         }
         cfg2.setVisible(true);
         GUIUtils.preventDisplayAdapterChangeExceptions();

         // re-initialize the system with the new configuration file
         sysConfigFile_ = cfg2.getFileName();

         mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
         loadSystemConfiguration();
         GUIUtils.preventDisplayAdapterChangeExceptions();

         if (liveRunning) {
            enableLiveMode(liveRunning);
         }

      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   private void autofocusNow() {
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
         }.start(); // or any other method from Autofocus.java API
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
      toFront();
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
      setConfigSaveButtonStatus(configChanged_);
   }
    
   /**
    * Lets JComponents register themselves so that their background can be
    * manipulated
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
               textFieldExp_.setText(NumberUtils.doubleToDisplayString(exposure));
               setExposure();
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError("Failed to set Exposure prefs using Channelgroup: "
                 + channelGroup + ", channel: " + channel + ", exposure: " + exposure);
      }
   }
     
   @Override
   public void enableRoiButtons(final boolean enabled) {
       SwingUtilities.invokeLater(new Runnable() {
           @Override
           public void run() {
               setRoiButton_.setEnabled(enabled);
               clearRoiButton_.setEnabled(enabled);
           }
       });
   }

   /**
    * Returns the current background color
    * @return current background color
    */
   @Override
   public Color getBackgroundColor() {
      return guiColors_.background.get((options_.displayBackground_));
   }

   /*
    * Changes background color of this window and all other MM windows
    */
   @Override
   public void setBackgroundStyle(String backgroundType) {
      setBackground(guiColors_.background.get((backgroundType)));
      paint(MMStudioMainFrame.this.getGraphics());
      
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
      if (simpleDisplay_ == null) {
         return null;
      }
      return simpleDisplay_.getHyperImage().getWindow();
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
    * @Deprecated used to be part of api
    */
   public String runAcqusition(String name, String root) throws MMScriptException {
      return runAcquisition(name, root);
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
      } catch (Exception ex) {
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
      this.openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices,
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
      this.openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, nrPositions, show, false);
   }


   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, boolean show)
         throws MMScriptException {
      this.openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 0, show, false);
   }   

   @Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, int nrPositions, boolean show, boolean save)
         throws MMScriptException {
      acqMgr_.openAcquisition(name, rootDir, show, save);
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      acq.setDimensions(nrFrames, nrChannels, nrSlices, nrPositions);
   }

   //@Override
   public void openAcquisition(String name, String rootDir, int nrFrames,
         int nrChannels, int nrSlices, boolean show, boolean virtual)
         throws MMScriptException {
      this.openAcquisition(name, rootDir, nrFrames, nrChannels, nrSlices, 0, show, virtual);
   }

   //@Override
   public String createAcquisition(JSONObject summaryMetadata, boolean diskCached) {
      return createAcquisition(summaryMetadata, diskCached, false);
   }
   
   @Override
   @Deprecated
   public String createAcquisition(JSONObject summaryMetadata, boolean diskCached, boolean displayOff) {
      return acqMgr_.createAcquisition(summaryMetadata, diskCached, engine_, displayOff);
   }
   
   //@Override
   public void initializeSimpleAcquisition(String name, int width, int height,
         int byteDepth, int bitDepth, int multiCamNumCh) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      acq.setImagePhysicalDimensions(width, height, byteDepth, bitDepth, multiCamNumCh);
      acq.initializeSimpleAcq();
   }

   /**
    * Call the below function with values extracted from the provided 
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

   /**
    * @Deprecated  use closeAcquisitionWindow instead
    * @Deprecated - used to be in api/AcquisitionEngine
    */
   public void closeAcquisitionImage5D(String acquisitionName) throws MMScriptException {
      acqMgr_.closeImageWindow(acquisitionName);
   }

   @Override
   public void closeAcquisitionWindow(String acquisitionName) throws MMScriptException {
      acqMgr_.closeImageWindow(acquisitionName);
   }

   /**
    * @Deprecated - used to be in api/AcquisitionEngine
    * Since Burst and normal acquisition are now carried out by the same engine,
    * loadBurstAcquistion simply calls loadAcquisition
    * t
    * @param path - path to file specifying acquisition settings
    */
   public void loadBurstAcquisition(String path) throws MMScriptException {
      this.loadAcquisition(path);
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

   public void setAcquisitionSystemState(String acqName, JSONObject md) throws MMScriptException {
//      acqMgr_.getAcquisition(acqName).setSystemState(md);
      setAcquisitionSummary(acqName, md);
   }

   //@Override
   public void setAcquisitionSummary(String acqName, JSONObject md) throws MMScriptException {
      acqMgr_.getAcquisition(acqName).setSummaryProperties(md);
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
      if (enable == isLiveModeOn()) {
         return;
      }
      if (enable) {
         try {
            if (core_.getCameraDevice().length() == 0) {
               ReportingUtils.showError("No camera configured");
               updateButtonsForLiveMode(false);
               return;
            }
            if (liveModeTimer_ == null) {
               liveModeTimer_ = new LiveModeTimer();
            }
            liveModeTimer_.begin();
            callLiveModeListeners(enable);
         } catch (Exception e) {
            ReportingUtils.showError(e);
            liveModeTimer_.stop();
            callLiveModeListeners(false);
            updateButtonsForLiveMode(false);
            return;
         }
      } else {
         liveModeTimer_.stop();
         callLiveModeListeners(enable);
      }
      updateButtonsForLiveMode(enable);
   }

   public String createNewAlbum() {
      return acqMgr_.createNewAlbum();
   }

   public void appendImage(String name, TaggedImage taggedImg) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      int f = 1 + acq.getLastAcquiredFrame();
      try {
         MDUtils.setFrameIndex(taggedImg.tags, f);
         } catch (JSONException e) {
            throw new MMScriptException("Unable to set the frame index.");
         }
      acq.insertTaggedImage(taggedImg, f, 0, 0);
   }

   @Override
   public void addToAlbum(TaggedImage taggedImg) throws MMScriptException {
      addToAlbum(taggedImg, null);
   }
   
   public void addToAlbum(TaggedImage taggedImg, JSONObject displaySettings) throws MMScriptException {
      normalizeTags(taggedImg);
      acqMgr_.addToAlbum(taggedImg,displaySettings);
   }

   public void addImage(String name, Object img, int frame, int channel,
         int slice) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      acq.insertImage(img, frame, channel, slice);
   }

   //@Override
   public void addImage(String name, TaggedImage taggedImg) throws MMScriptException {
      MMAcquisition acq = acqMgr_.getAcquisition(name);
      if (!acq.isInitialized()) {
         JSONObject tags = taggedImg.tags;
         
         // initialize physical dimensions of the image
         try {
            initializeAcquisitionFromTags(name, tags);
         } catch (JSONException e) {
            throw new MMScriptException(e);
         }
      }
      acq.insertImage(taggedImg);
   }
   
   @Override
   /**
    * The basic method for adding images to an existing data set.
    * If the acquisition was not previously initialized, it will attempt to initialize it from the available image data
    */
   public void addImageToAcquisition(String name,
           int frame,
           int channel,
           int slice,
           int position,
           TaggedImage taggedImg) throws MMScriptException {

      // TODO: complete the tag set and initialize the acquisition
      MMAcquisition acq = acqMgr_.getAcquisition(name);

      int positions = acq.getPositions();
      
      // check position, for multi-position data set the number of declared positions should be at least 2
      if (acq.getPositions() <= 1 && position > 0) {
         throw new MMScriptException("The acquisition was open as a single position data set.\n"
                 + "Open acqusition with two or more positions in order to crate a multi-position data set.");
      }

      // check position, for multi-position data set the number of declared positions should be at least 2
      if (acq.getChannels() <= channel) {
         throw new MMScriptException("This acquisition was opened with " + acq.getChannels() + " channels.\n"
                 + "The channel number must not exceed declared number of positions.");
      }


      JSONObject tags = taggedImg.tags;

      // if the acquisition was not previously initialized, set physical dimensions of the image
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
   /**
    * A quick way to implicitly snap an image and add it to the data set. Works
    * in the same way as above.
    */
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

   //@Override
   public void addImage(String name, TaggedImage img, boolean updateDisplay) throws MMScriptException {
      acqMgr_.getAcquisition(name).insertImage(img, updateDisplay);
   }

   //@Override
   public void addImage(String name, TaggedImage taggedImg,
           boolean updateDisplay,
           boolean waitForDisplay) throws MMScriptException {
      acqMgr_.getAcquisition(name).insertImage(taggedImg, updateDisplay, waitForDisplay);
   }

   //@Override
   public void addImage(String name, TaggedImage taggedImg, int frame, int channel,
           int slice, int position) throws MMScriptException {
      try {
         acqMgr_.getAcquisition(name).insertImage(taggedImg, frame, channel, slice, position);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
   }

   //@Override
   public void addImage(String name, TaggedImage taggedImg, int frame, int channel, 
           int slice, int position, boolean updateDisplay) throws MMScriptException {
      try {
         acqMgr_.getAcquisition(name).insertImage(taggedImg, frame, channel, slice, position, updateDisplay);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }  
   }

   //@Override
   public void addImage(String name, TaggedImage taggedImg, int frame, int channel,
           int slice, int position, boolean updateDisplay, boolean waitForDisplay) throws MMScriptException {
      try {
         acqMgr_.getAcquisition(name).insertImage(taggedImg, frame, channel, slice, position, updateDisplay, waitForDisplay);
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }
   }

   /**
    * Closes all acquisitions
    */
   @Override
   public void closeAllAcquisitions() {
      acqMgr_.closeAll();
   }

   @Override
   public String[] getAcquisitionNames()
   {
      return acqMgr_.getAcquisitionNames();
   }
   
   @Override
   @Deprecated
   public MMAcquisition getAcquisition(String name) throws MMScriptException {
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
            acquisitionEngine2010_ = (IAcquisitionEngine2010) acquisitionEngine2010Class_.getConstructor(ScriptInterface.class).newInstance(this);
         }
         return acquisitionEngine2010_;
      } catch (Exception e) {
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
      updateStaticInfo();
      if (liveRunning) {
         enableLiveMode(true);
      }

   }

   public void snapAndAddToImage5D() {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured");
         return;
      }
      try {
         if (this.isLiveModeOn()) {
            copyFromLiveModeToAlbum(simpleDisplay_);
         } else {
            doSnap(true);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
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
    */
   @Override
   public void addMMListener(MMListenerInterface newL) {
      coreCallback_.addMMListener(newL);
   }

   /**
    * Allows MMListeners to remove themselves
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
   
}
