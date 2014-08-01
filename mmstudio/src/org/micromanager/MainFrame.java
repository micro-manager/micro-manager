package org.micromanager;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import java.awt.Dimension;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
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
import javax.swing.UIManager;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import org.micromanager.api.events.ConfigGroupChangedEvent;
import org.micromanager.events.EventManager;
import org.micromanager.imagedisplay.MetadataPanel;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.utils.DragDropUtil;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.MMKeyDispatcher;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TextUtils;

/**
 * GUI code for the primary window of the program. And nothing else.
 */
public class MainFrame extends JFrame implements LiveModeListener {

   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";
   private static final String MAIN_FRAME_X = "x";
   private static final String MAIN_FRAME_Y = "y";
   private static final String MAIN_FRAME_WIDTH = "width";
   private static final String MAIN_FRAME_HEIGHT = "height";
   private static final String MAIN_FRAME_DIVIDER_POS = "divider_pos";
   private static final String MAIN_EXPOSURE = "exposure";
   private static final int TOOLTIP_DISPLAY_DURATION_MILLISECONDS = 15000;
   private static final int TOOLTIP_DISPLAY_INITIAL_DELAY_MILLISECONDS = 2000;

   // GUI components
   private JComboBox comboBinning_;
   private JComboBox shutterComboBox_;
   private JTextField textFieldExp_;
   private JLabel labelImageDimensions_;
   private JToggleButton liveButton_;
   private JCheckBox autoShutterCheckBox_;
   private JButton snapButton_;
   private JButton autofocusNowButton_;
   private JButton autofocusConfigureButton_;
   private JButton saveConfigButton_;
   private JToggleButton toggleShutterButton_;
   
   private ConfigGroupPad configPad_;

   private Font defaultFont_ = new Font("Arial", Font.PLAIN, 10);

   private CMMCore core_;
   private MMStudio studio_;

   // Our instance
   private static MainFrame mainFrame_;

   private ConfigPadButtonPanel configPadButtonPanel_;
   private final MetadataPanel metadataPanel_;
   private final JSplitPane splitPane_;

   private AbstractButton setRoiButton_;
   private AbstractButton clearRoiButton_;

   @SuppressWarnings("LeakingThisInConstructor")
   public MainFrame(MMStudio studio, CMMCore core, Preferences prefs) {
      org.micromanager.diagnostics.ThreadExceptionLogger.setUp();

      studio_ = studio;
      core_ = core;
      studio_.addLiveModeListener(this);

      mainFrame_ = this;
      setTitle(MICRO_MANAGER_TITLE + " " + MMVersion.VERSION_STRING);
      setMinimumSize(new Dimension(605,480));
      
      splitPane_ = createSplitPane(
            prefs.getInt(MAIN_FRAME_DIVIDER_POS, 200));
      getContentPane().add(splitPane_);

      createTopPanelWidgets((JPanel) splitPane_.getComponent(0));
      
      metadataPanel_ = createMetadataPanel((JPanel) splitPane_.getComponent(1));

      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      setupWindowHandlers();

      EventManager.register(this);
      
      // Add our own keyboard manager that handles Micro-Manager shortcuts
      MMKeyDispatcher mmKD = new MMKeyDispatcher();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(mmKD);
      new DropTarget(this, new DragDropUtil());
      setVisible(true);
   }
      
   private void setupWindowHandlers() {
      // add window listeners
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            studio_.closeSequence(false);
         }
      });
        
   }

   public void loadApplicationPrefs(Preferences prefs, 
         boolean shouldCloseOnExit) {
      int x = prefs.getInt(MAIN_FRAME_X, 100);
      int y = prefs.getInt(MAIN_FRAME_Y, 100);
      int width = prefs.getInt(MAIN_FRAME_WIDTH, 644);
      int height = prefs.getInt(MAIN_FRAME_HEIGHT, 570);

      setBounds(x, y, width, height);
      setExitStrategy(shouldCloseOnExit);
   }
   
   public void paintToFront() {
      toFront();
      paint(getGraphics());
   }

   public void initializeConfigPad() {
      configPad_.setCore(core_);
      configPad_.setParentGUI(studio_);
      configPadButtonPanel_.setCore(core_);
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
      comboBinning_.setFont(defaultFont_);
      comboBinning_.setMaximumRowCount(4);
      comboBinning_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.changeBinning();
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
            studio_.setExposure();
         }
      });
      textFieldExp_.setFont(defaultFont_);
      textFieldExp_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.setExposure();
         }
      });
      GUIUtils.addWithEdges(topPanel, textFieldExp_, 203, 21, 276, 40);
   }

   private void createShutterControls(JPanel topPanel) {
      autoShutterCheckBox_ = new JCheckBox();
      autoShutterCheckBox_.setFont(defaultFont_);
      autoShutterCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.toggleAutoShutter();
         }
      });
      autoShutterCheckBox_.setIconTextGap(6);
      autoShutterCheckBox_.setHorizontalTextPosition(SwingConstants.LEADING);
      autoShutterCheckBox_.setText("Auto shutter");
      GUIUtils.addWithEdges(topPanel, autoShutterCheckBox_, 107, 96, 199, 119);

      toggleShutterButton_ = (JToggleButton) GUIUtils.createButton(true,
         "toggleShutterButton", "Open", "Open/close the shutter",
         new Runnable() {
            @Override
            public void run() {
               toggleShutter();
            }
         },
         null, topPanel, 203, 96, 275, 117);
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
            @Override
            public void run() {
               studio_.saveConfigPresets();
            }
         }, 
         null, topPanel, -80, 2, -5, 20);
      
      configPad_ = new ConfigGroupPad();
      configPadButtonPanel_ = new ConfigPadButtonPanel();
      configPadButtonPanel_.setConfigPad(configPad_);
      configPadButtonPanel_.setGUI(studio_);
      
      configPad_.setFont(defaultFont_);
      
      GUIUtils.addWithEdges(topPanel, configPad_, 280, 21, -4, -44);
      GUIUtils.addWithEdges(topPanel, configPadButtonPanel_, 280, -40, -4, -20);
   }

   /** 
    * Generate the "Snap", "Live", "Snap to album", "MDA", and "Refresh"
    * buttons.
    */
   private void createCommonActionButtons(JPanel topPanel) {
      snapButton_ = (JButton) GUIUtils.createButton(false, "Snap", "Snap",
         "Snap single image",
         new Runnable() {
            @Override
            public void run() {
               studio_.doSnap();
            }
         }, 
         "camera.png", topPanel, 7, 4, 95, 25);

      liveButton_ = (JToggleButton) GUIUtils.createButton(true,
         "Live", "Live", "Continuous live view",
         new Runnable() {
            @Override
            public void run() {
               studio_.enableLiveMode(!studio_.isLiveModeOn());
            }
         },
         "camera_go.png", topPanel, 7, 26, 95, 47);

      GUIUtils.createButton(false, "Album", "Album",
         "Acquire single frame and add to an album",
         new Runnable() {
            @Override
            public void run() {
               studio_.snapAndAddToImage5D();
            }
         }, 
         "camera_plus_arrow.png", topPanel, 7, 48, 95, 69);

      GUIUtils.createButton(false,
         "Multi-D Acq.", "Multi-D Acq.",
         "Open multi-dimensional acquisition window",
         new Runnable() {
            @Override
            public void run() {
               studio_.openAcqControlDialog();
            }
         }, 
         "film.png", topPanel, 7, 70, 95, 91);

      GUIUtils.createButton(false, "Refresh", "Refresh",
         "Refresh all GUI controls directly from the hardware",
         new Runnable() {
            @Override
            public void run() {
               core_.updateSystemStateCache();
               studio_.updateGUI(true);
            }
         },
         "arrow_refresh.png", topPanel, 7, 92, 95, 113);
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
            new Thread(GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Citing_Micro-Manager")).start();
         }
      });
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
      createCommonActionButtons(topPanel);
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
            @Override
            public void run() {
               studio_.setROI();
            }
         }, 
         "shape_handles.png", topPanel, 7, 154, 37, 174);

      clearRoiButton_ = GUIUtils.createButton(false, "clearRoiButton", null,
         "Reset Region of Interest to full frame",
         new Runnable() {
            @Override
            public void run() {
               studio_.clearROI();
            }
         },
         "arrow_out.png", topPanel, 40, 154, 70, 174);
      
      // Zoom
      createLabel("Zoom", true, topPanel, 81, 140, 139, 154);

      GUIUtils.createButton(false, "zoomInButton", null,
         "Zoom in",
         new Runnable() {
            @Override
            public void run() {
               studio_.zoomIn();
            }
         },
         "zoom_in.png", topPanel, 80, 154, 110, 174);

      GUIUtils.createButton(false, "zoomOutButton", null,
         "Zoom out",
         new Runnable() {
            @Override
            public void run() {
               studio_.zoomOut();
            }
         },
         "zoom_out.png", topPanel, 113, 154, 143, 174);

      // Line profile.
      createLabel("Profile", true, topPanel, 154, 140, 217, 154);

      GUIUtils.createButton(false, "lineProfileButton", null,
         "Open line profile window (requires line selection)",
         new Runnable() {
            @Override
            public void run() {
               studio_.openLineProfileWindow();
            }
         },
         "chart_curve.png", topPanel, 153, 154, 183, 174);

      // Autofocus
      createLabel("Autofocus", true, topPanel, 194, 140, 276, 154);
      autofocusNowButton_ = (JButton) GUIUtils.createButton(false,
         "autofocusNowButton", null, "Autofocus now",
         new Runnable() {
            @Override
            public void run() {
               studio_.autofocusNow();
            }
         }, 
         "find.png", topPanel, 193, 154, 223, 174);

      autofocusConfigureButton_ = (JButton) GUIUtils.createButton(false,
         "autofocusConfigureButton", null,
         "Set autofocus options",
         new Runnable() {
            @Override
            public void run() {
               studio_.showAutofocusDialog();
            }
         },
         "wrench_orange.png", topPanel, 226, 154, 256, 174);
   }

   public void updateTitle(String configFile) {
      this.setTitle(MICRO_MANAGER_TITLE + " " + MMVersion.VERSION_STRING + " - " + configFile);
   }

   public final void setExitStrategy(boolean closeOnExit) {
      if (closeOnExit) {
         setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      }
      else {
         setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
      }
   }

   protected void setConfigSaveButtonStatus(boolean changed) {
      saveConfigButton_.setEnabled(changed);
   }

   /**
    * Updates Status line in main window.
    */
   public void updateInfoDisplay(String text) {
      labelImageDimensions_.setText(text);
   }
   
   public void setShutterButton(boolean state) {
      if (state) {
         toggleShutterButton_.setText("Close");
      } else {
         toggleShutterButton_.setText("Open");
      }
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

   public void toggleAutoShutter(boolean enabled) {
      toggleShutterButton_.setEnabled(enabled);
      toggleShutterButton_.setText("Open");
      if (!enabled) {
         toggleShutterButton_.setSelected(false);
      }
   }

   @Override
   public void liveModeEnabled(boolean isEnabled) {
      autoShutterCheckBox_.setEnabled(!isEnabled);
      if (core_.getAutoShutter()) {
         toggleShutterButton_.setText(isEnabled ? "Close" : "Open" );
      }
      snapButton_.setEnabled(!isEnabled);
      liveButton_.setIcon(isEnabled ? SwingResourceManager.getIcon(MainFrame.class,
              "/org/micromanager/icons/cancel.png")
              : SwingResourceManager.getIcon(MainFrame.class,
              "/org/micromanager/icons/camera_go.png"));
      liveButton_.setSelected(false);
      liveButton_.setText(isEnabled ? "Stop Live" : "Live");
   }
   
   public void initializeShutterGUI(String[] items) {
      GUIUtils.replaceComboContents(shutterComboBox_, items);
      String activeShutter = core_.getShutterDevice();
      if (activeShutter != null) {
         shutterComboBox_.setSelectedItem(activeShutter);
      } else {
         shutterComboBox_.setSelectedItem("");
      }
   }

   public void updateAutofocusButtons(boolean isEnabled) {
      autofocusConfigureButton_.setEnabled(isEnabled);
      autofocusNowButton_.setEnabled(isEnabled);
   }
   
   @Subscribe
   public void onConfigGroupChanged(ConfigGroupChangedEvent event) {
      configPad_.refreshGroup(event.getGroupName(), event.getNewConfig());
   }

   public void configureBinningComboForCamera(String cameraLabel) {
      ActionListener[] listeners;
      if (comboBinning_.getItemCount() > 0) {
          comboBinning_.removeAllItems();
      }
      try {
         StrVector binSizes = core_.getAllowedPropertyValues(
                 cameraLabel, MMCoreJ.getG_Keyword_Binning());
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
      } catch (Exception e) {
         // getAllowedPropertyValues probably failed.
         ReportingUtils.showError(e);
      }
   }

   public void setBinSize(String binSize) {
      GUIUtils.setComboSelection(comboBinning_, binSize);
   }

   /**
    * Return the current selection from the comboBinning_ menu, or null.
    */
   public String getBinMode() {
      Object item = comboBinning_.getSelectedItem();
      if (item != null) {
         return item.toString();
      }
      return (String) null;
   }

   public void setAutoShutterSelected(boolean isSelected) {
      autoShutterCheckBox_.setSelected(isSelected);
   }

   public void setToggleShutterButtonEnabled(boolean isEnabled) {
      toggleShutterButton_.setEnabled(isEnabled);
   }

   public void setShutterComboSelection(String activeShutter) {
      shutterComboBox_.setSelectedItem(activeShutter);
   }

   public boolean getAutoShutterChecked() {
      return autoShutterCheckBox_.isSelected();
   }

   public ConfigGroupPad getConfigPad() {
      return configPad_;
   }

   /**
    * Save our settings to the provided Preferences object.
    */
   public void savePrefs(Preferences prefs) {
      Rectangle r = getBounds();

      prefs.putInt(MAIN_FRAME_X, r.x);
      prefs.putInt(MAIN_FRAME_Y, r.y);
      prefs.putInt(MAIN_FRAME_WIDTH, r.width);
      prefs.putInt(MAIN_FRAME_HEIGHT, r.height);
      
      prefs.putInt(MAIN_FRAME_DIVIDER_POS, splitPane_.getDividerLocation());
      prefs.put(MAIN_EXPOSURE, textFieldExp_.getText());
   }

   public void setDisplayedExposureTime(double exposure) {
      textFieldExp_.setText(NumberUtils.doubleToDisplayString(exposure));
   }

   public double getDisplayedExposureTime() {
      try {
         return NumberUtils.displayStringToDouble(textFieldExp_.getText());
      } catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't convert displayed exposure time to double");
      }
      return -1;
   }

   public void enableRoiButtons(final boolean enabled) {
       SwingUtilities.invokeLater(new Runnable() {
           @Override
           public void run() {
               setRoiButton_.setEnabled(enabled);
               clearRoiButton_.setEnabled(enabled);
           }
       });
   }

   public MetadataPanel getMetadataPanel() {
      return metadataPanel_;
   }
}
