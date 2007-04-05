///////////////////////////////////////////////////////////////////////////////
//FILE:          MMStudioMainFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Jul 18, 2005
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

package org.micromanager;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import bsh.EvalError;
import bsh.Interpreter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import java.io.File;
import java.io.IOException;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.conf.ConfiguratorDlg;
import org.micromanager.conf.MMConfigFileException;
import org.micromanager.conf.MicroscopeModel;
import org.micromanager.graph.ContrastPanel;
import org.micromanager.graph.GraphData;
import org.micromanager.graph.GraphFrame;
import org.micromanager.graph.HistogramFrame;
import org.micromanager.image5d.ChannelCalibration;
import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.ChannelDisplayProperties;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.image5d.Image5D_Stack_to_RGB;
import org.micromanager.image5d.Image5D_Stack_to_RGB_t;
import org.micromanager.image5d.Image5D_to_Stack;
import org.micromanager.image5d.Make_Montage;
import org.micromanager.image5d.Z_Project;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.DisplaySettings;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.navigation.PositionList;
import org.micromanager.script.MMScriptFrame;
import org.micromanager.utils.AcquisitionEngine;
import org.micromanager.utils.CfgFileFilter;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.DeviceControlGUI;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.LargeMessageDlg;
import org.micromanager.utils.MMImageWindow;
import org.micromanager.utils.ProgressBar;
import org.micromanager.utils.TextUtils;
import org.micromanager.utils.WaitDialog;

import com.swtdesigner.SwingResourceManager;

/*
 * Main panel and application class for the MMStudio.
 */
public class MMStudioMainFrame extends JFrame implements DeviceControlGUI {
   public static String LIVE_WINDOW_TITLE = "AcqWindow";
   
   private static final String MICRO_MANAGER_TITLE = "Micro-Manager 2.0";
   private static final String VERSION = "2.0.1";
   private static final long serialVersionUID = 3556500289598574541L;
   
   private static final String MAIN_FRAME_X = "x";
   private static final String MAIN_FRAME_Y = "y";   
   private static final String MAIN_FRAME_WIDTH = "width";
   private static final String MAIN_FRAME_HEIGHT = "height";
   
   private static final String MAIN_EXPOSURE = "exposure";
   private static final String MAIN_PIXEL_TYPE = "pixel_type";
   private static final String SYSTEM_CONFIG_FILE = "sysconfig_file";
   private static final String MAIN_STRETCH_CONTRAST = "stretch_contrast";
   
   private static final String CONTRAST_SETTINGS_8_MIN = "contrast8_MIN";
   private static final String CONTRAST_SETTINGS_8_MAX = "contrast8_MAX";
   private static final String CONTRAST_SETTINGS_16_MIN = "contrast16_MIN";
   private static final String CONTRAST_SETTINGS_16_MAX = "contrast16_MAX";
   private static final String OPEN_ACQ_DIR = "openDataDir";
   
   private static final String SCRIPT_CORE_OBJECT = "mmc";
   private static final String SCRIPT_ACQENG_OBJECT = "acq";
   
   // GUI components
   private JTextField textFieldGain_;
   private JComboBox comboBinning_;
   private JTextField textFieldExp_;
   private SpringLayout springLayout_;
   private JLabel labelImageDimensions_;
   private JToggleButton toggleButtonLive_;
   private JCheckBox autoShutterCheckBox_;
   private boolean autoShutterOrg_;
   private boolean shutterOrg_;
   private MMOptions options_;
   private boolean runsAsPlugin_;
   
   private JToggleButton toggleButtonShutter_;
   private JComboBox comboPixelType_;
   
   // display settings
   private ContrastSettings contrastSettings8_;
   private ContrastSettings contrastSettings16_;
   
   private MMImageWindow imageWin_;
   private HistogramFrame histWin_;
   private GraphFrame profileWin_;
   private MMScriptFrame scriptFrame_;
   private PropertyEditor propertyBrowser_;
   private AcqControlDlg acqControlWin_;
   private final static String DEFAULT_CONFIG_FILE_NAME = "MMConfig_demo.cfg";
   private final static String DEFAULT_SCRIPT_FILE_NAME = "MMStartup.bsh";

   private String sysConfigFile_; 
   private String startupScriptFile_; 
   private String sysStateFile_ = "MMSystemState.cfg";
   
   private ConfigGroupPad configPad_;
   private ContrastPanel contrastPanel_;
   
   private double interval_;
   private Timer timer_;
   private GraphData lineProfileData_;
   
   // labels for standard devices
   String cameraLabel_;
   String zStageLabel_;
   String shutterLabel_;
   
   // applications settings
   Preferences mainPrefs_;
   
   // MMcore
   CMMCore core_;
   AcquisitionEngine engine_;
   PositionList posList_;
   private String openAcqDirectory_ = "";
   private boolean running_;
   private boolean configChanged_ = false;
   private JButton saveConfigButton_;
   
   /**
    * Main procedure for stand alone operation.
    */
   public static void main(String args[]) {
      try {
         UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         MMStudioMainFrame frame = new MMStudioMainFrame(false);
         frame.setVisible(true);
         frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      } catch (Exception e) {
         e.printStackTrace();
         System.exit(1);
      }
   }
   
   public MMStudioMainFrame(boolean pluginStatus) {
      super();
      
      options_ = new MMOptions();
      options_.loadSettings();
      
      runsAsPlugin_ = pluginStatus;
      setIconImage(SwingResourceManager.getImage(MMStudioMainFrame.class, "icons/microscope.gif"));
      running_ = true;
      contrastSettings8_ = new ContrastSettings();
      contrastSettings16_ = new ContrastSettings();
      
      sysConfigFile_ = new String(System.getProperty("user.dir") + "/" + DEFAULT_CONFIG_FILE_NAME);
      startupScriptFile_ = new String(System.getProperty("user.dir") + "/" + DEFAULT_SCRIPT_FILE_NAME);
      // set the location for app preferences
      mainPrefs_ = Preferences.userNodeForPackage(this.getClass());
      
      // show registration dialog if not already registered
      // first check user preferences (for legacy compatibility reasons)
      boolean userReg = mainPrefs_.getBoolean(RegistrationDlg.REGISTRATION, false);
      if (!userReg) {
         
         // now check system preferences
         Preferences systemPrefs = Preferences.systemNodeForPackage(this.getClass());
         boolean systemReg = systemPrefs.getBoolean(RegistrationDlg.REGISTRATION, false);
         if (!systemReg) {
            // prompt for registration info
            RegistrationDlg dlg = new RegistrationDlg();
            dlg.setVisible(true);
         }
      }
                              
      // intialize timer
      interval_ = 30;
      ActionListener timerHandler = new ActionListener() {
         public void actionPerformed(ActionEvent evt) {
            snapSingleImage();
         }
      };
      timer_ = new Timer((int)interval_, timerHandler);
      timer_.stop();
      
      // load application preferences
      // NOTE: only window size and position preferencesa are loaded,
      // not the settings for the camera and live imaging -
      // attempting to set those automatically on stratup may cause problems with the hardware
      int x = mainPrefs_.getInt(MAIN_FRAME_X, 100);
      int y = mainPrefs_.getInt(MAIN_FRAME_Y, 100);
      boolean stretch = mainPrefs_.getBoolean(MAIN_STRETCH_CONTRAST, true);
      contrastSettings8_.min = mainPrefs_.getDouble(CONTRAST_SETTINGS_8_MIN, 0.0);
      contrastSettings8_.max = mainPrefs_.getDouble(CONTRAST_SETTINGS_8_MAX, 0.0);
      contrastSettings16_.min = mainPrefs_.getDouble(CONTRAST_SETTINGS_16_MIN, 0.0);
      contrastSettings16_.max = mainPrefs_.getDouble(CONTRAST_SETTINGS_16_MAX, 0.0);
      
      openAcqDirectory_ = mainPrefs_.get(OPEN_ACQ_DIR, "");
      
      setBounds(x, y, 610, 451);
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      springLayout_ = new SpringLayout();
      getContentPane().setLayout(springLayout_);
      setTitle(MICRO_MANAGER_TITLE);
            
      // Snap button
      // -----------
      final JButton buttonSnap = new JButton();
      buttonSnap.setIconTextGap(6);
      buttonSnap.setText("Snap");
      buttonSnap.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/camera.png"));
      buttonSnap.setFont(new Font("", Font.PLAIN, 10));
      buttonSnap.setToolTipText("Snap single image");
      buttonSnap.setMaximumSize(new Dimension(0, 0));
      buttonSnap.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            doSnap();
         }
      });
      getContentPane().add(buttonSnap);
      springLayout_.putConstraint(SpringLayout.SOUTH, buttonSnap, 25, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, buttonSnap, 4, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, buttonSnap, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, buttonSnap, 7, SpringLayout.WEST, getContentPane());
      
      // Initalize
      // ---------
      
      // Exposure field
      // ---------------
      final JLabel label_1 = new JLabel();
      label_1.setFont(new Font("Arial", Font.PLAIN, 10));
      label_1.setText("Exposure [ms]");
      getContentPane().add(label_1);
      springLayout_.putConstraint(SpringLayout.EAST, label_1, 198, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, label_1, 111, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, label_1, 39, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, label_1, 23, SpringLayout.NORTH, getContentPane());
      
      textFieldExp_ = new JTextField();
      textFieldExp_.setFont(new Font("Arial", Font.PLAIN, 10));
      textFieldExp_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            try {
               core_.setExposure(Double.parseDouble(textFieldExp_.getText()));
            } catch (Exception exp) {
               handleException(exp);
            }
         }
      });
      getContentPane().add(textFieldExp_);
      springLayout_.putConstraint(SpringLayout.SOUTH, textFieldExp_, 40, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, textFieldExp_, 21, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, textFieldExp_, 286, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, textFieldExp_, 203, SpringLayout.WEST, getContentPane());
      
      // Live button
      // -----------
      toggleButtonLive_ = new JToggleButton();
      toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/camera_go.png"));
      toggleButtonLive_.setIconTextGap(6);
      toggleButtonLive_.setToolTipText("Continuously acquire images");
      toggleButtonLive_.setFont(new Font("Arial", Font.BOLD, 10));
      toggleButtonLive_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            if (toggleButtonLive_.isSelected()){
               if (interval_ < 30.0)
                  interval_ = 30.0; // limit the interval to 30ms or more
               timer_.setDelay((int)interval_);
               enableLiveMode(true);
               toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/cancel.png"));
            } else {
               enableLiveMode(false);
               toggleButtonLive_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/camera_go.png"));
            }
         }
      });
      
      toggleButtonLive_.setText("Live");
      getContentPane().add(toggleButtonLive_);
      springLayout_.putConstraint(SpringLayout.SOUTH, toggleButtonLive_, 47, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, toggleButtonLive_, 26, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, toggleButtonLive_, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, toggleButtonLive_, 7, SpringLayout.WEST, getContentPane());
      
      // Shutter button
      // --------------
      
      toggleButtonShutter_ = new JToggleButton();
      toggleButtonShutter_.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            try {
               if (toggleButtonShutter_.isSelected()){
                  setShutterButton(true);
                  core_.setShutterOpen(true);
               } else {
                  core_.setShutterOpen(false);
                  setShutterButton(false);
               }
            } catch (Exception e1) {
               // TODO Auto-generated catch block
               e1.printStackTrace();
            }
         }
      });
      toggleButtonShutter_.setToolTipText("Open/close the shutter");
      toggleButtonShutter_.setIconTextGap(6);
      toggleButtonShutter_.setFont(new Font("Arial", Font.BOLD, 10));
      toggleButtonShutter_.setText("Open");
      getContentPane().add(toggleButtonShutter_);
      springLayout_.putConstraint(SpringLayout.EAST, toggleButtonShutter_, 275, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, toggleButtonShutter_, 203, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, toggleButtonShutter_, 136, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, toggleButtonShutter_, 115, SpringLayout.NORTH, getContentPane());
      
      // Profile
      // -------
      final JButton buttonProf = new JButton();
      buttonProf.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/chart_curve.png"));
      buttonProf.setFont(new Font("Arial", Font.PLAIN, 10));
      buttonProf.setToolTipText("Open line profile window (requires line selection)");
      buttonProf.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            openLineProfileWindow();
         }
      });
      buttonProf.setText("Profile");
      getContentPane().add(buttonProf);
      springLayout_.putConstraint(SpringLayout.SOUTH, buttonProf, 69, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, buttonProf, 48, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, buttonProf, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, buttonProf, 7, SpringLayout.WEST, getContentPane());
      
      final JMenuBar menuBar = new JMenuBar();
      setJMenuBar(menuBar);
      
      final JMenu fileMenu = new JMenu();
      fileMenu.setText("File");
      menuBar.add(fileMenu);

      final JMenuItem openMenuItem = new JMenuItem();
      openMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            openAcquisitionData();
         }
      });
      openMenuItem.setText("Open Acquisition Data as Image5D......");
      fileMenu.add(openMenuItem);

      fileMenu.addSeparator();
      
      final JMenuItem loadState = new JMenuItem();
      loadState.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadSystemState();
         }
      });
      loadState.setText("Load System State...");
      fileMenu.add(loadState);
      
      final JMenuItem saveStateAs = new JMenuItem();
      fileMenu.add(saveStateAs);
      saveStateAs.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            saveSystemState();
         }
      });
      saveStateAs.setText("Save System State As...");
                        
      fileMenu.addSeparator();
      
      final JMenuItem exitMenuItem = new JMenuItem();
      exitMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            closeSequence();
         }
      });
      fileMenu.add(exitMenuItem);
      exitMenuItem.setText("Exit");

      final JMenu image5dMenu = new JMenu();
      image5dMenu.setText("Image5D");
      menuBar.add(image5dMenu);

      final JMenuItem makeMontageMenuItem = new JMenuItem();
      makeMontageMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            //IJ.runPlugIn("Make_Montage", "");
            Make_Montage makeMontage = new Make_Montage();
            makeMontage.run("");
         }
      });
      makeMontageMenuItem.setText("Make Montage...");
      image5dMenu.add(makeMontageMenuItem);

      final JMenuItem zProjectMenuItem = new JMenuItem();
      zProjectMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            //IJ.runPlugIn("Z_Project", "");
            Z_Project projection = new Z_Project();
            projection.run("");
         }
      });
      zProjectMenuItem.setText("Z Project...");
      image5dMenu.add(zProjectMenuItem);

      final JMenuItem convertToRgbMenuItem = new JMenuItem();
      convertToRgbMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            //IJ.runPlugIn("org/micromanager/Image5D_Stack_to_RGB", "");
            Image5D_Stack_to_RGB stackToRGB = new Image5D_Stack_to_RGB();
            stackToRGB.run("");
         }
      });
      convertToRgbMenuItem.setText("Convert to RGB (z)");
      image5dMenu.add(convertToRgbMenuItem);

      final JMenuItem convertToRgbtMenuItem = new JMenuItem();
      convertToRgbtMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            //IJ.runPlugIn("Image5D_Stack_to_RGB_t", "");
            Image5D_Stack_to_RGB_t stackToRGB_t = new Image5D_Stack_to_RGB_t();
            stackToRGB_t.run("");
            
         }
      });
      convertToRgbtMenuItem.setText("Convert to RGB (t)");
      image5dMenu.add(convertToRgbtMenuItem);

      final JMenuItem convertToStackMenuItem = new JMenuItem();
      convertToStackMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            //IJ.runPlugIn("Image5D_to_Stack", "");
            Image5D_to_Stack image5DToStack = new Image5D_to_Stack();
            image5DToStack.run("");
         }
      });
      convertToStackMenuItem.setText("Convert to Stack");
      image5dMenu.add(convertToStackMenuItem);
      
      final JMenu toolsMenu = new JMenu();
      toolsMenu.setText("Tools");
      menuBar.add(toolsMenu);
      
      final JMenuItem refreshMenuItem = new JMenuItem();
      refreshMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            updateGUI();
         }
      });
      refreshMenuItem.setText("Refresh GUI");
      toolsMenu.add(refreshMenuItem);

      final JMenuItem rebuildGuiMenuItem = new JMenuItem();
      rebuildGuiMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            initializeGUI();
         }
      });
      rebuildGuiMenuItem.setText("Rebuild GUI");
      toolsMenu.add(rebuildGuiMenuItem);

      toolsMenu.addSeparator();

      final JMenuItem homeXyStageMenuItem = new JMenuItem();
      homeXyStageMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            homeXYStage();
         }
      });
      homeXyStageMenuItem.setText("Home XY Stage");
      toolsMenu.add(homeXyStageMenuItem);
      
      toolsMenu.addSeparator();
      
      final JMenuItem scriptingConsoleMenuItem = new JMenuItem();
      toolsMenu.add(scriptingConsoleMenuItem);
      scriptingConsoleMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            createScriptingConsole();
         }
      });
      scriptingConsoleMenuItem.setText("Scripting Console...");
      
      final JMenuItem propertyEditorMenuItem = new JMenuItem();
      toolsMenu.add(propertyEditorMenuItem);
      propertyEditorMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            createPropertyEditor();
         }
      });
      propertyEditorMenuItem.setText("Device/Property Browser...");

      toolsMenu.addSeparator();

      final JMenuItem configuratorMenuItem = new JMenuItem();
      configuratorMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            try {
               // unload all devices before starting configurator
               core_.reset();
               
               // run Configurator
               ConfiguratorDlg configurator = new ConfiguratorDlg(core_, sysConfigFile_);
               configurator.setVisible(true);
               
               // re-initialize the system with the new configuration file
               sysConfigFile_ = configurator.getFileName();
               mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
               loadSystemConfiguration();
               initializeGUI();
            } catch (Exception e) {
               handleException(e);
               return;
            }
         }
      });
      configuratorMenuItem.setText("Hardware Configuration Wizard...");
      toolsMenu.add(configuratorMenuItem);

      final JMenuItem loadSystemConfigMenuItem = new JMenuItem();
      toolsMenu.add(loadSystemConfigMenuItem);
      loadSystemConfigMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            loadConfiguration();
            updateTitle();
            initializeGUI();
         }
      });
      loadSystemConfigMenuItem.setText("Load Hardware Configuration...");

      final JMenuItem saveConfigurationPresetsMenuItem = new JMenuItem();
      saveConfigurationPresetsMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            saveConfigPresets();
         }
      });
      saveConfigurationPresetsMenuItem.setText("Save Configuration Presets");
      toolsMenu.add(saveConfigurationPresetsMenuItem);

      final JMenuItem optionsMenuItem = new JMenuItem();
      optionsMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            OptionsDlg dlg = new OptionsDlg(options_, core_, mainPrefs_);
            dlg.setVisible(true);
         }
      });
      optionsMenuItem.setText("Options...");
      toolsMenu.add(optionsMenuItem);

      final JMenu helpMenu = new JMenu();
      helpMenu.setText("Help");
      menuBar.add(helpMenu);
      
      final JMenuItem aboutMenuItem = new JMenuItem();
      aboutMenuItem.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            MMAboutDlg dlg = new MMAboutDlg();
            String versionInfo = "MM Studio version: " + VERSION;
            versionInfo += "\n" + core_.getVersionInfo();
            versionInfo += "\n" + core_.getAPIVersionInfo();
            versionInfo += "\nUser: " + core_.getUserId();
            versionInfo += "\nHost: " + core_.getHostName();
            
            dlg.setVersionInfo(versionInfo);
            dlg.setVisible(true);
         }
      });
      aboutMenuItem.setText("About...");
      helpMenu.add(aboutMenuItem);
      
      
      final JLabel binningLabel = new JLabel();
      binningLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      binningLabel.setText("Binning");
      getContentPane().add(binningLabel);
      springLayout_.putConstraint(SpringLayout.SOUTH, binningLabel, 88, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, binningLabel, 69, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, binningLabel, 203, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, binningLabel, 112, SpringLayout.WEST, getContentPane());
      
      labelImageDimensions_ = new JLabel();
      labelImageDimensions_.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(labelImageDimensions_);
      springLayout_.putConstraint(SpringLayout.SOUTH, labelImageDimensions_, -5, SpringLayout.SOUTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, labelImageDimensions_, -25, SpringLayout.SOUTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, labelImageDimensions_, -5, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, labelImageDimensions_, 5, SpringLayout.WEST, getContentPane());
      
      final JLabel pixelTypeLabel = new JLabel();
      pixelTypeLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      pixelTypeLabel.setText("Pixel type");
      getContentPane().add(pixelTypeLabel);
      springLayout_.putConstraint(SpringLayout.SOUTH, pixelTypeLabel, 64, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, pixelTypeLabel, 43, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, pixelTypeLabel, 197, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, pixelTypeLabel, 111, SpringLayout.WEST, getContentPane());
      
      comboPixelType_ = new JComboBox();
      comboPixelType_.setFont(new Font("Arial", Font.PLAIN, 10));
      comboPixelType_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            changePixelType();
         }
      });
      getContentPane().add(comboPixelType_);
      springLayout_.putConstraint(SpringLayout.SOUTH, comboPixelType_, 64, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, comboPixelType_, 43, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, comboPixelType_, 293, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, comboPixelType_, 203, SpringLayout.WEST, getContentPane());

      
      comboBinning_ = new JComboBox();
      comboBinning_.setFont(new Font("Arial", Font.PLAIN, 10));
      comboBinning_.setMaximumRowCount(4);
      comboBinning_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            changeBinning();
         }
      });
      getContentPane().add(comboBinning_);
      springLayout_.putConstraint(SpringLayout.EAST, comboBinning_, 293, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, comboBinning_, 203, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, comboBinning_, 89, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, comboBinning_, 68, SpringLayout.NORTH, getContentPane());
      
      
      configPad_ = new ConfigGroupPad();
      configPad_.setFont(new Font("", Font.PLAIN, 10));
      getContentPane().add(configPad_);
      springLayout_.putConstraint(SpringLayout.EAST, configPad_, -4, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, configPad_, 5, SpringLayout.EAST, comboBinning_);
      
      final JLabel cameraSettingsLabel = new JLabel();
      cameraSettingsLabel.setFont(new Font("Arial", Font.BOLD, 11));
      cameraSettingsLabel.setText("Camera settings");
      getContentPane().add(cameraSettingsLabel);
      springLayout_.putConstraint(SpringLayout.EAST, cameraSettingsLabel, 211, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, cameraSettingsLabel, 6, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, cameraSettingsLabel, 109, SpringLayout.WEST, getContentPane());
      
      final JLabel stateDeviceLabel = new JLabel();
      stateDeviceLabel.setFont(new Font("Arial", Font.BOLD, 11));
      stateDeviceLabel.setText("Configuration Presets");
      getContentPane().add(stateDeviceLabel);
      springLayout_.putConstraint(SpringLayout.SOUTH, stateDeviceLabel, 21, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, stateDeviceLabel, 5, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, stateDeviceLabel, 455, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, stateDeviceLabel, 305, SpringLayout.WEST, getContentPane());
      
      final JButton buttonAcqSetup = new JButton();
      buttonAcqSetup.setMargin(new Insets(2, 2, 2, 2));
      buttonAcqSetup.setIconTextGap(1);
      buttonAcqSetup.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/film.png"));
      buttonAcqSetup.setToolTipText("Open Acquistion dialog");
      buttonAcqSetup.setFont(new Font("Arial", Font.BOLD, 10));
      buttonAcqSetup.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            try {
               boolean needsGroupUpdate = true;
               if (acqControlWin_ == null) {
                  acqControlWin_ = new AcqControlDlg(engine_, mainPrefs_, options_);
                  needsGroupUpdate = false;
               }
               if (acqControlWin_.isActive())
                  acqControlWin_.setTopPosition();
               acqControlWin_.setVisible(true);
               
               // TODO: this call causes a strange exception the first time the dialog is created
               // something to do with the order in which combo box creation is performed
               
               //acqControlWin_.updateGroupsCombo();
            } catch(Exception exc) {
               exc.printStackTrace();
               handleError(exc.getMessage() +
                           "\nAcquistion window failed to open due to invalid or corrupted settings.\n" +
                           "Try resetting registry settings to factory defaults (Menu Tools|Options).");
            }
         }
      });
      buttonAcqSetup.setText("Acquisition");
      getContentPane().add(buttonAcqSetup);
      springLayout_.putConstraint(SpringLayout.SOUTH, buttonAcqSetup, 91, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, buttonAcqSetup, 70, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, buttonAcqSetup, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, buttonAcqSetup, 7, SpringLayout.WEST, getContentPane());
 
            
      autoShutterCheckBox_ = new JCheckBox();
      autoShutterCheckBox_.setFont(new Font("Arial", Font.PLAIN, 10));
      autoShutterCheckBox_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            core_.setAutoShutter(autoShutterCheckBox_.isSelected());
            if (shutterLabel_.length() > 0)
               try {
                  setShutterButton(core_.getShutterOpen());
               } catch (Exception e1) {
                  // do not complain here
               }
               if (autoShutterCheckBox_.isSelected())
                  toggleButtonShutter_.setEnabled(false);
               else
                  toggleButtonShutter_.setEnabled(true);
               
         }
      });
      autoShutterCheckBox_.setIconTextGap(6);
      autoShutterCheckBox_.setHorizontalTextPosition(SwingConstants.LEADING);
      autoShutterCheckBox_.setText("Auto shutter");
      getContentPane().add(autoShutterCheckBox_);
      springLayout_.putConstraint(SpringLayout.EAST, autoShutterCheckBox_, 202, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, autoShutterCheckBox_, 110, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, autoShutterCheckBox_, 137, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, autoShutterCheckBox_, 114, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      refreshButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/arrow_refresh.png"));
      refreshButton.setFont(new Font("Arial", Font.PLAIN, 10));
      refreshButton.setToolTipText("Refresh all GUI controls directly from the hardware");
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            updateGUI();
        }
      });
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout_.putConstraint(SpringLayout.SOUTH, refreshButton, 113, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, refreshButton, 92, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, refreshButton, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, refreshButton, 7, SpringLayout.WEST, getContentPane());
            
      // add window listeners
      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent e) {
            running_ = false;
            closeSequence();
         }
         
         public void windowOpened(WindowEvent e) {
            // -------------------
            // initialize hardware
            // -------------------
            core_ = new CMMCore();
            core_.enableDebugLog(options_.debugLogEnabled);
//           core_.clearLog();
            cameraLabel_ = new String("");
            shutterLabel_ = new String("");
            zStageLabel_ = new String("");
            
            engine_ = new MMAcquisitionEngineMT();
            
            engine_.setCore(core_);
            engine_.setPositionList(posList_);
            MMStudioMainFrame parent = (MMStudioMainFrame) e.getWindow();
            if (parent != null)
               engine_.setParentGUI(parent);
            
            posList_ = new PositionList();
            
            // load configuration from the file
            sysConfigFile_ = mainPrefs_.get(SYSTEM_CONFIG_FILE, sysConfigFile_);
            //startupScriptFile_ = mainPrefs_.get(STARTUP_SCRIPT_FILE, startupScriptFile_);
            MMIntroDlg introDlg = new MMIntroDlg(VERSION);
            introDlg.setConfigFile(sysConfigFile_);
            //introDlg.setScriptFile(startupScriptFile_);
            introDlg.setVisible(true);
            sysConfigFile_ = introDlg.getConfigFile();
            //startupScriptFile_ = introDlg.getScriptFile();
            mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);               
            //mainPrefs_.put(STARTUP_SCRIPT_FILE, startupScriptFile_);               
            
            paint(MMStudioMainFrame.this.getGraphics());
            
            loadSystemConfiguration();
            executeStartupScript();
                        
            configPad_.setCore(core_);
            if (parent != null)
               configPad_.setParentGUI(parent);
            
            // initialize controls
            initializeGUI();      
         }
      });

      final JButton setRoiButton = new JButton();
      setRoiButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/shape_handles.png"));
      setRoiButton.setFont(new Font("Arial", Font.PLAIN, 10));
      setRoiButton.setToolTipText("Set Region Of Interest to selected rectangle");
      setRoiButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            setROI();
         }
      });
      getContentPane().add(setRoiButton);
      springLayout_.putConstraint(SpringLayout.EAST, setRoiButton, 48, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, setRoiButton, 7, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, setRoiButton, 172, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, setRoiButton, 152, SpringLayout.NORTH, getContentPane());

      final JButton clearRoiButton = new JButton();
      clearRoiButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/arrow_out.png"));
      clearRoiButton.setFont(new Font("Arial", Font.PLAIN, 10));
      clearRoiButton.setToolTipText("Reset Region of Interest to full frame");
      clearRoiButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            clearROI();
         }
      });
      getContentPane().add(clearRoiButton);
      springLayout_.putConstraint(SpringLayout.EAST, clearRoiButton, 93, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, clearRoiButton, 51, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, clearRoiButton, 172, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, clearRoiButton, 152, SpringLayout.NORTH, getContentPane());

      final JLabel regionOfInterestLabel = new JLabel();
      regionOfInterestLabel.setFont(new Font("Arial", Font.BOLD, 11));
      regionOfInterestLabel.setText("ROI");
      getContentPane().add(regionOfInterestLabel);
      springLayout_.putConstraint(SpringLayout.SOUTH, regionOfInterestLabel, 152, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, regionOfInterestLabel, 138, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, regionOfInterestLabel, 71, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, regionOfInterestLabel, 8, SpringLayout.WEST, getContentPane());

      final JLabel gainLabel = new JLabel();
      gainLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      gainLabel.setText("Gain");
      getContentPane().add(gainLabel);
      springLayout_.putConstraint(SpringLayout.SOUTH, gainLabel, 108, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, gainLabel, 95, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, gainLabel, 136, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, gainLabel, 113, SpringLayout.WEST, getContentPane());

      textFieldGain_ = new JTextField();
      textFieldGain_.setFont(new Font("Arial", Font.PLAIN, 10));
      textFieldGain_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            try {
               if (isCameraAvailable()) {
                  core_.setProperty(core_.getCameraDevice(), MMCoreJ.getG_Keyword_Gain(), textFieldGain_.getText());
               }
            } catch (Exception exp) {
               handleException(exp);
            }
         }
      });
      getContentPane().add(textFieldGain_);
      springLayout_.putConstraint(SpringLayout.SOUTH, textFieldGain_, 112, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, textFieldGain_, 93, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, textFieldGain_, 275, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, textFieldGain_, 203, SpringLayout.WEST, getContentPane());

      contrastPanel_ = new ContrastPanel();
      contrastPanel_.setContrastStretch(stretch);
      contrastPanel_.setBorder(new LineBorder(Color.black, 1, false));
      getContentPane().add(contrastPanel_);
      springLayout_.putConstraint(SpringLayout.SOUTH, contrastPanel_, -26, SpringLayout.SOUTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, contrastPanel_, 176, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, contrastPanel_, -4, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, contrastPanel_, 7, SpringLayout.WEST, getContentPane());

      final JLabel regionOfInterestLabel_1 = new JLabel();
      regionOfInterestLabel_1.setFont(new Font("Arial", Font.BOLD, 11));
      regionOfInterestLabel_1.setText("Zoom");
      getContentPane().add(regionOfInterestLabel_1);
      springLayout_.putConstraint(SpringLayout.SOUTH, regionOfInterestLabel_1, 154, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, regionOfInterestLabel_1, 140, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, regionOfInterestLabel_1, 177, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, regionOfInterestLabel_1, 114, SpringLayout.WEST, getContentPane());

      final JButton zoomInButton = new JButton();
      zoomInButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            zoomIn();
         }
      });
      zoomInButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/zoom_in.png"));
      zoomInButton.setToolTipText("Set Region Of Interest to selected rectangle");
      zoomInButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(zoomInButton);
      springLayout_.putConstraint(SpringLayout.SOUTH, zoomInButton, 174, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, zoomInButton, 154, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, zoomInButton, 154, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, zoomInButton, 113, SpringLayout.WEST, getContentPane());

      final JButton zoomOutButton = new JButton();
      zoomOutButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            zoomOut();
         }
      });
      zoomOutButton.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "/org/micromanager/icons/zoom_out.png"));
      zoomOutButton.setToolTipText("Reset Region of Interest to full frame");
      zoomOutButton.setFont(new Font("Arial", Font.PLAIN, 10));
      getContentPane().add(zoomOutButton);
      springLayout_.putConstraint(SpringLayout.SOUTH, zoomOutButton, 174, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, zoomOutButton, 154, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, zoomOutButton, 199, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, zoomOutButton, 157, SpringLayout.WEST, getContentPane());

      final JButton addGroupButton_ = new JButton();
      addGroupButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            configPad_.addGroup();
            updateGUI();
         }
      });
      addGroupButton_.setToolTipText("Add new group of presets");
      addGroupButton_.setText("+");
      getContentPane().add(addGroupButton_);
      springLayout_.putConstraint(SpringLayout.EAST, addGroupButton_, 337, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, addGroupButton_, 295, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, addGroupButton_, 173, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, addGroupButton_, 155, SpringLayout.NORTH, getContentPane());

      final JButton removeGroupButton_ = new JButton();
      removeGroupButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (configPad_.removeGroup()) {
               configChanged_ = true;
               updateGUI();
               setConfigSaveButtonStatus(configChanged_);
            }
         }
      });
      removeGroupButton_.setToolTipText("Remove selected group of presets");
      removeGroupButton_.setText("-");
      getContentPane().add(removeGroupButton_);
      springLayout_.putConstraint(SpringLayout.SOUTH, removeGroupButton_, 173, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, removeGroupButton_, 155, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, removeGroupButton_, 382, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, removeGroupButton_, 340, SpringLayout.WEST, getContentPane());

      final JButton editPreset_ = new JButton();
      editPreset_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (configPad_.editPreset()) {
               configChanged_ = true;
               updateGUI();
               setConfigSaveButtonStatus(configChanged_);
            }
         }
      });
      editPreset_.setToolTipText("Edit selected preset");
      editPreset_.setText("Edit");
      getContentPane().add(editPreset_);
      springLayout_.putConstraint(SpringLayout.EAST, editPreset_, -7, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, editPreset_, -72, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, configPad_, 0, SpringLayout.NORTH, editPreset_);
      springLayout_.putConstraint(SpringLayout.NORTH, configPad_, 21, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, editPreset_, 173, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, editPreset_, 155, SpringLayout.NORTH, getContentPane());

      final JButton addPresetButton_ = new JButton();
      addPresetButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if(configPad_.addPreset()) {
               configChanged_ = true;
               updateGUI();
               setConfigSaveButtonStatus(configChanged_);
            }
         }
      });
      addPresetButton_.setToolTipText("Add preset");
      addPresetButton_.setText("+");
      getContentPane().add(addPresetButton_);
      springLayout_.putConstraint(SpringLayout.EAST, addPresetButton_, -114, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, addPresetButton_, -156, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, addPresetButton_, 173, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, addPresetButton_, 155, SpringLayout.NORTH, getContentPane());

      final JButton removePresetButton_ = new JButton();
      removePresetButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            if (configPad_.removePreset()) {
               configChanged_ = true;
               updateGUI();
               setConfigSaveButtonStatus(configChanged_);
            }
         }
      });
      removePresetButton_.setToolTipText("Remove currently selected preset");
      removePresetButton_.setText("-");
      getContentPane().add(removePresetButton_);
      springLayout_.putConstraint(SpringLayout.EAST, removePresetButton_, -72, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, removePresetButton_, -114, SpringLayout.EAST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, removePresetButton_, 173, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, removePresetButton_, 155, SpringLayout.NORTH, getContentPane());

      saveConfigButton_ = new JButton();
      saveConfigButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            saveConfigPresets();
         }
      });
      saveConfigButton_.setToolTipText("Save current presets to the configuration file");
      saveConfigButton_.setText("Save");
      saveConfigButton_.setEnabled(false);
      getContentPane().add(saveConfigButton_);
      springLayout_.putConstraint(SpringLayout.SOUTH, saveConfigButton_, 20, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, saveConfigButton_, 2, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.EAST, saveConfigButton_, 500, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, saveConfigButton_, 435, SpringLayout.WEST, getContentPane());

      final JButton refreshButton_1 = new JButton();
      refreshButton_1.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class, "icons/application_view_list.png"));
      refreshButton_1.addActionListener(new ActionListener() {
         private PositionListDlg posListDlg_;

         public void actionPerformed(ActionEvent arg0) {
            if (posListDlg_ == null) {
               posListDlg_ = new PositionListDlg(core_, posList_);
            }
            posListDlg_.setVisible(true);
          }
      });
      refreshButton_1.setToolTipText("Refresh all GUI controls directly from the hardware");
      refreshButton_1.setFont(new Font("Arial", Font.PLAIN, 10));
      refreshButton_1.setText("XY List");
      getContentPane().add(refreshButton_1);
      springLayout_.putConstraint(SpringLayout.EAST, refreshButton_1, 95, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.WEST, refreshButton_1, 7, SpringLayout.WEST, getContentPane());
      springLayout_.putConstraint(SpringLayout.SOUTH, refreshButton_1, 136, SpringLayout.NORTH, getContentPane());
      springLayout_.putConstraint(SpringLayout.NORTH, refreshButton_1, 115, SpringLayout.NORTH, getContentPane());

   }
   
   private void handleException (Exception e) {
      String errText = "Exception occurred: " + e.getMessage();
      handleError(errText);
   }
   
   private void handleError(String message) {
      if (timer_ != null) {
         enableLiveMode(false);
      }
      
//      if (toggleButtonLive_ != null)
//         toggleButtonLive_.setSelected(false);
      JOptionPane.showMessageDialog(this, message);     
   }
   
   private void updateTitle() {
      this.setTitle("System: " + sysConfigFile_);
   }
   
   private void updateHistogram(){
      if (isImageWindowOpen()) {
         //ImagePlus imp = IJ.getImage();
         ImagePlus imp = imageWin_.getImagePlus();
         if (imp != null) {
            contrastPanel_.setImagePlus(imp);
            contrastPanel_.setContrastSettings(contrastSettings8_, contrastSettings16_);
            contrastPanel_.update();
         }
         //contrastPanel_.setImagePlus(imageWin_.getImagePlus());
//         ContrastSettings cs = imageWin_.getCurrentContrastSettings();
      }
   }
   
   private void updateLineProfile(){
      if (!isImageWindowOpen() || profileWin_ == null || !profileWin_.isShowing())
         return;
      
      calculateLineProfileData(imageWin_.getImagePlus());
      profileWin_.setData(lineProfileData_);
   }
   
   private void openLineProfileWindow(){
      if (imageWin_ == null || imageWin_.isClosed())
         return;
      calculateLineProfileData(imageWin_.getImagePlus());
      if (lineProfileData_ == null)
         return;
      profileWin_ = new GraphFrame();
      profileWin_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      profileWin_.setData(lineProfileData_);
      profileWin_.setAutoScale();
      profileWin_.setTitle("Live line profile");
      profileWin_.setVisible(true);
   }
   
   private void calculateLineProfileData(ImagePlus imp){
      // generate line profile
      Roi roi = imp.getRoi();
      if (roi==null || !roi.isLine()) {
         
         // if there is no line ROI, create one
         Rectangle r = imp.getProcessor().getRoi();
         int iWidth = r.width;
         int iHeight = r.height;
         int iXROI = r.x;
         int iYROI = r.y;
         if (roi==null) {
            iXROI += iWidth/2;
            iYROI += iHeight/2; 
         }
         
         roi = new Line(iXROI-iWidth/4, iYROI-iWidth/4, iXROI + iWidth/4, iYROI + iHeight/4);
         imp.setRoi(roi);
         roi = imp.getRoi();
      }

      ImageProcessor ip = imp.getProcessor();     
      ip.setInterpolate(true);
      Line line = (Line)roi;
      
      if (lineProfileData_ == null)
         lineProfileData_ = new GraphData();
      lineProfileData_.setData(line.getPixels());
   }
   
   private void setROI() {
      if (imageWin_ == null || imageWin_.isClosed())
         return;
      
      Roi roi = imageWin_.getImagePlus().getRoi();
      
      try {
         if (roi==null) {
            // if there is no ROI, create one
            ImagePlus imp = imageWin_.getImagePlus();
            Rectangle r = imp.getProcessor().getRoi();
            int iWidth = r.width;
            int iHeight = r.height;
            int iXROI = r.x;
            int iYROI = r.y;
            if (roi==null) {
               iWidth /= 2;
               iHeight /= 2;
               iXROI += iWidth/2;
               iYROI += iHeight/2; 
            }
            
            imp.setRoi(iXROI, iYROI, iWidth, iHeight);
            roi = imp.getRoi();
         }
         
         if (roi.getType() != Roi.RECTANGLE) {
            handleError("ROI must be a rectangle.\nUse the ImageJ rectangle tool to draw the ROI.");
            return;
         }
         
         Rectangle r = roi.getBoundingRect();
         core_.setROI(r.x, r.y, r.width, r.height);
         updateStaticInfo();
         
      } catch (Exception e) {
         handleException(e);
      }      
   }
   
   private void clearROI() {
      try {
         core_.clearROI();
         updateStaticInfo();         
      } catch (Exception e) {
         handleException(e);
      }      
   }
   
   /**
    * Moves XY stage to its home position and calibrates.
    */
   private void homeXYStage() {
      String xyStage = core_.getXYStageDevice();
      if (xyStage.isEmpty()) {
         handleError("Default XYStage is not defined.\n" + "Use Configuration Wizard to define default XY Stage device.");
         return;
      }

      int option = JOptionPane.showConfirmDialog(this, "Home and calibrate the default XY device: " + xyStage + "?\n" +
            "Warning: if you choose YES the stage will move to its home position.",
            "XY Stage homing action", JOptionPane.YES_NO_OPTION);
      if (option == JOptionPane.YES_OPTION) {
         try {
            core_.home(xyStage);
         } catch(Exception e) {
            handleException(e);
         }
      }
   }
  
   private boolean openImageWindow(){
      try {
         ImageProcessor ip;
         long byteDepth = core_.getBytesPerPixel();
         if (byteDepth == 1){
            ip = new ByteProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
            if (contrastSettings8_.getRange() == 0.0)
               ip.setMinAndMax(0, 255);
            else
               ip.setMinAndMax(contrastSettings8_.min, contrastSettings8_.max);
         } else if (byteDepth == 2) {
            ip = new ShortProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
            if (contrastSettings16_.getRange() == 0.0)
               ip.setMinAndMax(0, 65535);
            else
               ip.setMinAndMax(contrastSettings16_.min, contrastSettings16_.max);
         }
         else if (byteDepth == 0) {
            handleError("Imaging device not initialized");
            return false;
         }
         else {
            handleError("Unsupported pixel depth: " + core_.getBytesPerPixel() + " bytes.");
            return false;
         }
         ip.setColor(Color.black);
         ip.fill();
         ImagePlus imp = new ImagePlus(LIVE_WINDOW_TITLE, ip);
         if (imageWin_ != null) {
            imageWin_.dispose();
            imageWin_.savePosition();
            imageWin_ = null;
         }
         
         imageWin_ = new MMImageWindow(imp);
         imageWin_.setContrastSettings(contrastSettings8_, contrastSettings16_);
                  
         // add listener to the IJ window to detect when it closes
         WindowListener wndCloser = new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
               imageWin_ = null;
               contrastPanel_.setImagePlus(null);
            }
         };
         
         WindowListener wndFocus = new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
               updateHistogram();
            }
         };
         
         WindowListener wndActive = new WindowAdapter() {
            public void windowActivated(WindowEvent e) {
               updateHistogram();
            }
         };
         
         imageWin_.addWindowListener(wndCloser);
         imageWin_.addWindowListener(wndFocus);
         imageWin_.addWindowListener(wndActive);
         
      } catch (Exception e){
         handleException(e);
         return false;
      }
      return true;
   }
   
   /**
    * Returns instance of the core uManager object;
    */
   public CMMCore getMMCore() {
      return core_;
   }
      
   protected void saveConfigPresets() {
      MicroscopeModel model = new MicroscopeModel();
      try {
         model.loadFromFile(sysConfigFile_);
         model.createSetupConfigsFromHardware(core_);
         JFileChooser fc = new JFileChooser();
         boolean saveFile = true;
         File f; 
         do {         
            fc.setSelectedFile(new File(model.getFileName()));
            int retVal = fc.showSaveDialog(this);
            if (retVal == JFileChooser.APPROVE_OPTION) {
               f = fc.getSelectedFile();
               
               // check if file already exists
               if( f.exists() ) { 
                  int sel = JOptionPane.showConfirmDialog(this,
                        "Overwrite " + f.getName(),
                        "File Save",
                        JOptionPane.YES_NO_OPTION);
                  
                  if(sel == JOptionPane.YES_OPTION)
                     saveFile = true;
                  else
                     saveFile = false;
               }
            } else {
               return; 
            }
         } while (saveFile == false);
         
         model.saveToFile(f.getAbsolutePath());
         sysConfigFile_ = f.getAbsolutePath();
         configChanged_ = false;
         setConfigSaveButtonStatus(configChanged_);
      } catch (MMConfigFileException e) {
         handleException(e);
      }
   }

   protected void setConfigSaveButtonStatus(boolean changed) {
      saveConfigButton_.setEnabled(changed);
   }

   /**
    * Open an existing acquisition directory and build image5d window.
    *
    */
   protected void openAcquisitionData() {
      
      // choose the directory
      // --------------------
      
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      fc.setSelectedFile(new File(openAcqDirectory_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         File f = fc.getSelectedFile();
         if (f.isDirectory()) {
            openAcqDirectory_ = f.getAbsolutePath();
         } else {
            openAcqDirectory_ = f.getParent();
         }
         
         ProgressBar progressBar = null;
         AcquisitionData ad = new AcquisitionData();
         try {
            
            // attempt to open metafile
            ad.load(openAcqDirectory_);
                        
            // create image 5d
            Image5D img5d = new Image5D(openAcqDirectory_, ad.getImageJType(), ad.getImageWidth(),
                  ad.getImageHeight(), ad.getNumberOfChannels(), ad.getNumberOfSlices(),
                  ad.getNumberOfFrames(), false);
            
            Color colors[] = ad.getChannelColors();
            String names[] = ad.getChannelNames();
            for (int i=0; i<ad.getNumberOfChannels(); i++) {
               
               ChannelCalibration chcal = new ChannelCalibration();
               // set channel name
               chcal.setLabel(names[i]);
               img5d.setChannelCalibration(i+1, chcal);
               
               // set color
               img5d.setChannelColorModel(i+1, ChannelDisplayProperties.createModelFromColor(colors[i]));            
            }
            
            progressBar = new ProgressBar ("Opening File...", 0, ad.getNumberOfChannels() * ad.getNumberOfFrames() * ad.getNumberOfSlices() );
            // set pixels
            int singleImageCounter = 0;
            for (int i=0; i<ad.getNumberOfFrames(); i++) {
               for (int j=0; j<ad.getNumberOfChannels(); j++)
                  for (int k=0; k<ad.getNumberOfSlices(); k++) {
                     img5d.setCurrentPosition(0, 0, j, k, i);
                     // read the file
                     
                     // insert pixels into the 5d image
                     img5d.setPixels(ad.getPixels(i, j, k));
                     
                     // set display settings for channels
                     if (k==0 && i==0) {
                        DisplaySettings ds[] = ad.getChannelDisplaySettings();
                        if (ds != null) {
                           // display properties are recorded in metadata use them...
                           double min = ds[j].min;
                           double max = ds[j].max;
                           img5d.setChannelMinMax(j+1, min, max);
                        } else {
                           // ...if not, autoscale channels based on the first slice of the first frame
                           ImageStatistics stats = img5d.getStatistics(); // get uncalibrated stats
                           double min = stats.min;
                           double max = stats.max;
                           img5d.setChannelMinMax(j+1, min, max);
                        }
                     }
                  }
               singleImageCounter++;
               progressBar.setProgress(singleImageCounter);
            }
            // pop-up 5d image window
            Image5DWindow i5dWin = new Image5DWindow(img5d);
            if (ad.getNumberOfChannels()==1)
               img5d.setDisplayMode(ChannelControl.ONE_CHANNEL_COLOR);
            else
               img5d.setDisplayMode(ChannelControl.OVERLAY);
            i5dWin.setAcquitionEngine(engine_);
            i5dWin.setMetadata(ad.getMetadata());
            img5d.changes = false;
         } catch (MMAcqDataException e) {
            handleError(e.getMessage());
         } finally {
            if (progressBar != null) {
               progressBar.setVisible(false);
               progressBar = null;
            }
         }
      }
   }

   protected void zoomOut() {
      if (!isImageWindowOpen())
         return;
      Rectangle r = imageWin_.getCanvas().getBounds();
      imageWin_.getCanvas().zoomOut(r.width/2, r.height/2);
   }

   protected void zoomIn() {
      if (!isImageWindowOpen())
         return;
      Rectangle r = imageWin_.getCanvas().getBounds();
      imageWin_.getCanvas().zoomIn(r.width/2, r.height/2);
   }

   protected void changeBinning() {
      try {
         //
         if (isCameraAvailable()) {
            Object item = comboBinning_.getSelectedItem();
            if (item != null)
               core_.setProperty(cameraLabel_, MMCoreJ.getG_Keyword_Binning(), item.toString());
         }
      } catch (Exception e) {
         handleException(e);
      }
      updateStaticInfo();
   }
      
   private void createPropertyEditor() {
      if (propertyBrowser_ != null)
         propertyBrowser_.dispose();
      
      propertyBrowser_ = new PropertyEditor();
      propertyBrowser_.setVisible(true);
      propertyBrowser_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      propertyBrowser_.setCore(core_);
      propertyBrowser_.setParentGUI(this);
   }
   
   private void createScriptingConsole() {
      if (scriptFrame_ == null || !scriptFrame_.isActive()) {
         scriptFrame_ = new MMScriptFrame();
         scriptFrame_.setVisible(true);
         scriptFrame_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
         scriptFrame_.insertScriptingObject(SCRIPT_CORE_OBJECT, core_);
         scriptFrame_.insertScriptingObject(SCRIPT_ACQENG_OBJECT, engine_);
         scriptFrame_.setParentGUI(this);
      }
   }
   
   private void updateStaticInfo(){
      try {
         double zPos = 0.0;
         String dimText = "Image size: " + core_.getImageWidth() + " X " + core_.getImageHeight() + " X " + core_.getBytesPerPixel() +
                           ", Intensity range: " + core_.getImageBitDepth() + " bits";
         if (zStageLabel_.length() > 0) {
            zPos = core_.getPosition(zStageLabel_);
            dimText +=  ", Z=" + zPos + "um";
         }
         labelImageDimensions_.setText(dimText);         
         
      } catch (Exception e){
         handleException(e);
      }
   }
   
   private void setShutterButton(boolean state) {
      if (state) {
         toggleButtonShutter_.setSelected(true);
         toggleButtonShutter_.setText("Close");
      } else {
         toggleButtonShutter_.setSelected(false);
         toggleButtonShutter_.setText("Open");
      }
   }
   
   private void changePixelType() {
      try {
         //
         if (isCameraAvailable()) {
            Object item = comboPixelType_.getSelectedItem();
            if (item != null)
               core_.setProperty(cameraLabel_, MMCoreJ.getG_Keyword_PixelType(), item.toString());
               long bitDepth = core_.getImageBitDepth();
               contrastPanel_.setPixelBitDepth((int)bitDepth, true);
         }         
      } catch (Exception e) {
         handleException(e);
      }
      updateStaticInfo();
   }
      
   ////////////////////////////////////////////////////////////////////////////
   // public interface available for scripting access
   ////////////////////////////////////////////////////////////////////////////
   
   public void snapSingleImage(){
      try {
         core_.setExposure(Double.parseDouble(textFieldExp_.getText()));
         updateImage();
      } catch (Exception e){
         handleException(e);
      }
   }
   
   public Object getPixels(){
      if (imageWin_ != null)
         return imageWin_.getImagePlus().getProcessor().getPixels();
      
      return null;
   }
   
   public void setPixels(Object obj){
      if (imageWin_ == null) {
         return;
      }
      
      imageWin_.getImagePlus().getProcessor().setPixels(obj);
   }
   
   public int getImageHeight(){
      if (imageWin_ != null)
         return imageWin_.getImagePlus().getHeight();
      return 0;
   }
   
   public int getImageWidth(){
      if (imageWin_ != null)
         return imageWin_.getImagePlus().getWidth();
      return 0;
   }
   
   public int getImageDepth(){
      if (imageWin_ != null)
         return imageWin_.getImagePlus().getBitDepth();
      return 0;
   }
   
   public ImageProcessor getImageProcessor(){
      if (imageWin_ == null)
         return null;
      return imageWin_.getImagePlus().getProcessor();
   }
   
   private boolean isCameraAvailable() {
      return cameraLabel_.length() > 0;
   }
   
   public boolean isImageWindowOpen() {
      if (imageWin_ == null || imageWin_.isClosed())
         return false;
      else
         return true;
   }
   
   public void updateImageGUI() {
      updateHistogram();
   }
   
   public void enableLiveMode(boolean enable){
      try {
         if (enable){
            if (timer_.isRunning())
               return;
            
            if (!isImageWindowOpen())
               openImageWindow();
            
            // turn off auto shutter and open the shutter
            //autoShutterCheckBox_.setEnabled(false);
            autoShutterOrg_ = core_.getAutoShutter();
            if (shutterLabel_.length() > 0)
               shutterOrg_ = core_.getShutterOpen();
            core_.setAutoShutter(false);
            shutterLabel_ = core_.getShutterDevice();
            if (shutterLabel_.length() > 0)
               core_.setShutterOpen(true);
            timer_.start();
            toggleButtonLive_.setText("Stop");
            toggleButtonShutter_.setEnabled(false);
         } else {
            if (!timer_.isRunning())
               return;
            timer_.stop();
            toggleButtonLive_.setText("Live");
            
            // restore auto shutter and close the shutter
            if (shutterLabel_.length() > 0)
               core_.setShutterOpen(shutterOrg_);
            core_.setAutoShutter(autoShutterOrg_);
            if (autoShutterOrg_)
               toggleButtonShutter_.setEnabled(false);
            else
               toggleButtonShutter_.setEnabled(true);
           //autoShutterCheckBox_.setEnabled(autoShutterOrg_);
         }
      } catch (Exception err) {
         JOptionPane.showMessageDialog(this, err.getMessage());     

      }
   }
    
   public boolean updateImage() {
      try {
         if (!isImageWindowOpen()){
            // stop live acquistion if the window is not open
            enableLiveMode(false);
            return true; // nothing to do
         }
         
         // warn the user if image dimensions do not match the current window
         if (imageWin_.getImagePlus().getProcessor().getWidth() != core_.getImageWidth() ||
               imageWin_.getImagePlus().getProcessor().getHeight() != core_.getImageHeight() ||
               imageWin_.getImagePlus().getBitDepth() != core_.getBytesPerPixel() * 8) {
            openImageWindow();
         }
         
         // update image window
         core_.snapImage();
         Object img = core_.getImage();
         imageWin_.getImagePlus().getProcessor().setPixels(img);
         imageWin_.getImagePlus().updateAndDraw();
         imageWin_.getCanvas().paint(imageWin_.getCanvas().getGraphics());
         
         // update related windows
         updateHistogram();
         updateLineProfile();
         
         // update coordinate and pixel info in imageJ by simulating mouse move
         Point pt = imageWin_.getCanvas().getCursorLoc();
         imageWin_.getImagePlus().mouseMoved(pt.x, pt.y);
         
      } catch (Exception e){
         handleException(e);
         return false;
      }
      
      return true;
   }
   
   private void doSnap() {
      try {
         if (!isImageWindowOpen())
            if (!openImageWindow())
               handleError("Image window open failed");
         String expStr = textFieldExp_.getText();
         if (expStr.length() > 0) {
            core_.setExposure(Double.parseDouble(expStr));
            updateImage();
         }
         else
            handleError("Exposure field is empty!");
      } catch (Exception e){
         handleException(e);
      }      
   }
      
   public void initializeGUI(){
      try {
         
         // establish device roles
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         engine_.setZStageDevice(zStageLabel_);
                  
         if (cameraLabel_.length() > 0) {
            
            // pixel type combo
            if (comboPixelType_.getItemCount() > 0)
               comboPixelType_.removeAllItems();
            StrVector pixTypes = core_.getAllowedPropertyValues(cameraLabel_, MMCoreJ.getG_Keyword_PixelType());
            ActionListener[] listeners = comboPixelType_.getActionListeners();
            for (int i=0; i<listeners.length; i++)            
               comboPixelType_.removeActionListener(listeners[i]);
            for (int i=0; i<pixTypes.size(); i++){
               comboPixelType_.addItem(pixTypes.get(i));
            }
            for (int i=0; i<listeners.length; i++)            
               comboPixelType_.addActionListener(listeners[i]);
            
            // binning combo
            if (comboBinning_.getItemCount() > 0)
               comboBinning_.removeAllItems();
            StrVector binSizes = core_.getAllowedPropertyValues(cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            listeners = comboBinning_.getActionListeners();
            for (int i=0; i<listeners.length; i++)            
               comboBinning_.removeActionListener(listeners[i]);
            for (int i=0; i<binSizes.size(); i++){
               comboBinning_.addItem(binSizes.get(i));
            }

            comboBinning_.setMaximumRowCount((int)binSizes.size());
            if (binSizes.size() == 0) {
                comboBinning_.setEditable(true);
             } else {
               comboBinning_.setEditable(false);
            }

            for (int i=0; i<listeners.length; i++)            
               comboBinning_.addActionListener(listeners[i]);
            
         }
         
         updateGUI();
      } catch (Exception e){
         handleException(e);
      }
   }

   public void updateGUI(){
      
      try {         
         // establish device roles
         cameraLabel_ = core_.getCameraDevice();
         shutterLabel_ = core_.getShutterDevice();
         zStageLabel_ = core_.getFocusDevice();
         engine_.setZStageDevice(zStageLabel_);
         
         // camera settings
         if (isCameraAvailable())
         {
            double exp = core_.getExposure();
            textFieldExp_.setText(Double.toString(exp));
            textFieldGain_.setText(core_.getProperty(cameraLabel_, MMCoreJ.getG_Keyword_Gain()));
            String binSize = core_.getProperty(cameraLabel_, MMCoreJ.getG_Keyword_Binning());
            GUIUtils.setComboSelection(comboBinning_, binSize);
            String pixType = core_.getProperty(cameraLabel_, MMCoreJ.getG_Keyword_PixelType());
            GUIUtils.setComboSelection(comboPixelType_, pixType);
            long bitDepth = core_.getImageBitDepth();
            contrastPanel_.setPixelBitDepth((int)bitDepth, false);
         }
           
         if (!timer_.isRunning()) {
            autoShutterCheckBox_.setSelected(core_.getAutoShutter());
            boolean shutterOpen = core_.getShutterOpen();
            setShutterButton(shutterOpen);
            if (autoShutterCheckBox_.isSelected()) {
               toggleButtonShutter_.setEnabled(false);
            } else {
               toggleButtonShutter_.setEnabled(true);
            }
            
            autoShutterOrg_ = core_.getAutoShutter();
         }         
                   
         // state devices
         configPad_.refresh();
      } catch (Exception e){
         handleException(e);
      }
      
      updateStaticInfo();
      updateTitle();
   }
   
   public boolean okToAcquire() {
      return !timer_.isRunning();
   }
   
   public void stopAllActivity(){
      enableLiveMode(false);
   }
   
   public void refreshImage(){
      if (imageWin_ != null)
         imageWin_.getImagePlus().updateAndDraw();     
   }
  
   private void cleanupOnClose() {
      timer_.stop();
      if (imageWin_ != null) {
         imageWin_.close();
         imageWin_.dispose();
      }
      
      if (histWin_ != null)
         histWin_.dispose();
      
      if (profileWin_ != null)
         profileWin_.dispose();
      
      if (scriptFrame_ != null)
         scriptFrame_.dispose();
      
      if (propertyBrowser_ != null)
         propertyBrowser_.dispose();
      
      if (acqControlWin_ != null)
         acqControlWin_.dispose();
      
      if (engine_ != null)
         engine_.shutdown();
      
      try {
         core_.reset();
      } catch(Exception err) {
         handleException(err);
      }
   }
   
   private void saveSettings() {
      Rectangle r = this.getBounds();
      
      mainPrefs_.putInt(MAIN_FRAME_X, r.x);
      mainPrefs_.putInt(MAIN_FRAME_Y, r.y);
      mainPrefs_.putInt(MAIN_FRAME_WIDTH, r.width);
      mainPrefs_.putInt(MAIN_FRAME_HEIGHT, r.height);
      
      mainPrefs_.putDouble(CONTRAST_SETTINGS_8_MIN, contrastSettings8_.min);
      mainPrefs_.putDouble(CONTRAST_SETTINGS_8_MAX, contrastSettings8_.max);
      mainPrefs_.putDouble(CONTRAST_SETTINGS_16_MIN, contrastSettings16_.min);
      mainPrefs_.putDouble(CONTRAST_SETTINGS_16_MAX, contrastSettings16_.max);
      mainPrefs_.putBoolean(MAIN_STRETCH_CONTRAST, contrastPanel_.isContrastStretch());
      
      mainPrefs_.put(OPEN_ACQ_DIR, openAcqDirectory_);
      
      // save field values from the main window
      // NOTE: automatically restoring these values on startup may cause problems
      mainPrefs_.put(MAIN_EXPOSURE, textFieldExp_.getText());
      if (comboPixelType_.getSelectedItem() != null)
         mainPrefs_.put(MAIN_PIXEL_TYPE, comboPixelType_.getSelectedItem().toString());
      // NOTE: do not save auto shutter state
//      mainPrefs_.putBoolean(MAIN_AUTO_SHUTTER, autoShutterCheckBox_.isSelected());
   }
      
   private void loadConfiguration() {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new CfgFileFilter());
      fc.setSelectedFile(new File(sysConfigFile_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         File f = fc.getSelectedFile();
         sysConfigFile_ = f.getAbsolutePath();
         configChanged_ = false;
         setConfigSaveButtonStatus(configChanged_);
         mainPrefs_.put(SYSTEM_CONFIG_FILE, sysConfigFile_);
         loadSystemConfiguration();
      }
   }

   private void loadSystemState() {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new CfgFileFilter());
      fc.setSelectedFile(new File(sysStateFile_));
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         File f = fc.getSelectedFile();
         sysStateFile_ = f.getAbsolutePath();
         try {
            //WaitDialog waitDlg = new WaitDialog("Loading saved state, please wait...");
            //waitDlg.showDialog();
            core_.loadSystemState(sysStateFile_);
            //waitDlg.closeDialog();
            initializeGUI();
         } catch (Exception e) {
            handleException(e);
            return;
         }
      }
   }
   
 

   private void saveSystemState() {
      JFileChooser fc = new JFileChooser();
      boolean saveFile = true;
      File f;
      
      do {         
         fc.setSelectedFile(new File(sysStateFile_));
         int retVal = fc.showSaveDialog(this);
         if (retVal == JFileChooser.APPROVE_OPTION) {
            f = fc.getSelectedFile();
            
            // check if file already exists
            if( f.exists() ) { 
               int sel = JOptionPane.showConfirmDialog( this,
                     "Overwrite " + f.getName(),
                     "File Save",
                     JOptionPane.YES_NO_OPTION);
               
               if(sel == JOptionPane.YES_OPTION)
                  saveFile = true;
               else
                  saveFile = false;
            }
         } else {
            return; 
         }
      } while (saveFile == false);
      
      sysStateFile_ = f.getAbsolutePath();
      
      try {
         core_.saveSystemState(sysStateFile_);
      } catch (Exception e) {
         handleException(e);
         return;
      }
   }   
   
   private void closeSequence() {
      if (engine_ != null && engine_.isAcquisitionRunning()) {
         int result = JOptionPane.showConfirmDialog(this,
               "Acquisition in progress. Are you sure you want to exit and discard all data?",
               "Micro-Manager", JOptionPane.YES_NO_OPTION,
               JOptionPane.INFORMATION_MESSAGE);
      
         if (result == JOptionPane.NO_OPTION)
            return;              
      }
      
      cleanupOnClose();
      saveSettings();
      options_.saveSettings();
      dispose();
      if (!runsAsPlugin_)
         System.exit(0);
   }
   
   public void applyContrastSettings(ContrastSettings contrast8, ContrastSettings contrast16) {
      contrastPanel_.applyContrastSettings(contrast8, contrast16);
   }

   public ContrastSettings getContrastSettings() {
      // TODO Auto-generated method stub
      return null;
   }

   public boolean is16bit() {
      if (isImageWindowOpen() && imageWin_.getImagePlus().getProcessor() instanceof ShortProcessor)
         return true;
      return false;
   }

   public boolean isRunning() {
      return running_;
   }
   
   /**
    * Executes the beanShell script.
    * This script instance only supports commands directed to the core object.
    */
   private void executeStartupScript() {
      // execute startup script
      File f = new File(startupScriptFile_);
      
      if (startupScriptFile_.length() > 0 && f.exists()) {
         WaitDialog waitDlg = new WaitDialog("Executing startup script, please wait...");
         waitDlg.showDialog();
         Interpreter interp = new Interpreter();
         try {
            // insert core object only
            interp.set(SCRIPT_CORE_OBJECT, core_);
            
            // read text file and evaluate            
            interp.eval(TextUtils.readTextFile(startupScriptFile_));
         } catch (IOException exc) {
            handleException(exc);
         } catch (EvalError exc) {
            handleException(exc);
         } finally {
            waitDlg.closeDialog();
         }
      }
   }
   
   /**
    * Loads sytem configuration from the cfg file.
    */
   private void loadSystemConfiguration() {
      WaitDialog waitDlg = new WaitDialog("Loading system configuration, please wait...");
      waitDlg.showDialog();
      try {
         
         if (sysConfigFile_.length() > 0) {                  
            // remember the selected file name
            core_.loadSystemConfiguration(sysConfigFile_);
            //waitDlg.closeDialog();                  
         }
      } catch (Exception err) {
         //handleException(err);
         // handle long error messages
         waitDlg.closeDialog();  
         LargeMessageDlg dlg = new LargeMessageDlg("Configuration error log", err.getMessage());
         dlg.setVisible(true);
      }
      waitDlg.closeDialog();      
   }
}

