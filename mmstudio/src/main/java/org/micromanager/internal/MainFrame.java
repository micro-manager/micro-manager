///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       
//
// COPYRIGHT:    University of California, San Francisco, 2014
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

package org.micromanager.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Color;
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
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import javax.swing.AbstractButton;
import javax.swing.border.MatteBorder;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;
import mmcorej.StrVector;

import net.miginfocom.swing.MigLayout;

import org.micromanager.acquisition.internal.AcquisitionSelector;
import org.micromanager.events.ConfigGroupChangedEvent;
import org.micromanager.events.ChannelExposureEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.events.internal.ChannelGroupEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.MouseMovesStageEvent;
import org.micromanager.events.internal.ShutterDevicesEvent;
import org.micromanager.internal.dialogs.OptionsDlg;
import org.micromanager.internal.dialogs.StageControlFrame;
import org.micromanager.internal.interfaces.LiveModeListener;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.DragDropUtil;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.MMKeyDispatcher;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;

import org.micromanager.quickaccess.internal.controls.ShutterControl;

import org.micromanager.quickaccess.internal.QuickAccessFactory;

/**
 * GUI code for the primary window of the program. And nothing else.
 */
public class MainFrame extends MMFrame implements LiveModeListener {

   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";
   private static final String MAIN_FRAME_DIVIDER_POS = "divider_pos";
   private static final String MAIN_EXPOSURE = "exposure";

   // Size constraint for normal buttons.
   private static final String BIGBUTTON_SIZE = "w 110!, h 22!";
   // Size constraint for small buttons.
   private static final String SMALLBUTTON_SIZE = "w 30!, h 20!";

   // GUI components
   private JLabel configFile_;
   private JLabel profileName_;
   private JComboBox comboBinning_;
   private JComboBox shutterComboBox_;
   private JTextField textFieldExp_;
   private JComboBox chanGroupSelect_;
   // Toggles activity of chanGroupSelect_ on or off.
   private boolean shouldChangeChannelGroup_;
   private JLabel labelImageDimensions_;
   private JButton liveButton_;
   private JCheckBox autoShutterCheckBox_;
   private JLabel shutterIcon_;
   private JButton toggleShutterButton_;
   private JButton snapButton_;
   private JToggleButton handMovesButton_;
   private JButton autofocusNowButton_;
   private JButton autofocusConfigureButton_;
   private JButton saveConfigButton_;

   private ConfigGroupPad configPad_;

   private final Font defaultFont_ = new Font("Arial", Font.PLAIN, 10);

   private final CMMCore core_;
   private final MMStudio studio_;
   private final SnapLiveManager snapLiveManager_;

   private ConfigPadButtonPanel configPadButtonPanel_;

   private AbstractButton setRoiButton_;
   private AbstractButton clearRoiButton_;
   private AbstractButton centerQuadButton_;

   @SuppressWarnings("LeakingThisInConstructor")
   public MainFrame(MMStudio studio, CMMCore core, SnapLiveManager manager,
         JMenuBar menuBar) {
      super("main micro manager frame");
      org.micromanager.internal.diagnostics.ThreadExceptionLogger.setUp();

      studio_ = studio;
      core_ = core;
      snapLiveManager_ = manager;
      snapLiveManager_.addLiveModeListener(this);

      setTitle(String.format("%s %s", MICRO_MANAGER_TITLE,
               MMVersion.VERSION_STRING));

      JPanel contents = new JPanel();
      // Minimize insets.
      contents.setLayout(new MigLayout("insets 1, gap 0, fill"));
      setContentPane(contents);

      contents.add(createComponents(), "grow");

      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      setupWindowHandlers();

      // Add our own keyboard manager that handles Micro-Manager shortcuts
      MMKeyDispatcher mmKD = new MMKeyDispatcher();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(mmKD);
      DropTarget dropTarget = new DropTarget(this, new DragDropUtil(studio_));

      setExitStrategy(OptionsDlg.getShouldCloseOnExit());
      setIconImage(Toolkit.getDefaultToolkit().getImage(
               getClass().getResource("/org/micromanager/icons/microscope.gif")));

      setJMenuBar(menuBar);

      setConfigText("");
      // Set minimum size so we can't resize smaller and hide some of our
      // contents. Our insets are only available after the first call to
      // pack().
      pack();
      setMinimumSize(getSize());
      resetPosition();
      DefaultEventManager.getInstance().registerForEvents(this);
   }

   private void setupWindowHandlers() {
      addWindowListener(new WindowAdapter() {
         // HACK: on OSX, some kind of system bug can disable the entire
         // menu bar at times (it has something to do with modal dialogs and
         // possibly with errors resulting from the code that handles their
         // output). Calling setEnabled() on the MenuBar does *not* fix the
         // enabled-ness of the menus. However, through experimentation, I've
         // figured out that setting the menubar to null and then back again
         // does fix the issue for all menus *except* the Help menu. Note that
         // if we named our Help menu e.g. "Help2" then it would behave
         // properly, so this is clearly something special to do with OSX.
         @Override
         public void windowActivated(WindowEvent event) {
            setMenuBar(null);
            setJMenuBar(getJMenuBar());
         }
         // Shut down when this window is closed.
         @Override
         public void windowClosing(WindowEvent event) {
            studio_.closeSequence(false);
         }
      });
   }

   public void resetPosition() {
      // put frame back where it was last time if possible
      loadAndRestorePosition(100, 100, 644, 220);
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

   private JButton createButton(String text, String iconPath,
         String help, final Runnable action) {
      JButton button = new JButton(text,
            IconLoader.getIcon("/org/micromanager/icons/" + iconPath));
      button.setMargin(new Insets(0, 0, 0, 0));
      button.setToolTipText(help);
      button.setFont(defaultFont_);
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            action.run();
         }
      });
      return button;
   }

   private static JLabel createLabel(String text, boolean big) {
      final JLabel label = new JLabel();
      label.setFont(new Font("Arial",
              big ? Font.BOLD : Font.PLAIN,
              big ? 11 : 10));
      label.setText(text);
      return label;
   }

   private JPanel createImagingSettingsWidgets() {
      JPanel subPanel = new JPanel(
            new MigLayout("flowx, fillx, insets 1, gap 0"));
      // HACK: This minor extra vertical gap aligns this text with the
      // Configuration Settings header over the config pad.
      subPanel.add(createLabel("Imaging settings", true), "gaptop 2, wrap");

      // Exposure time.
      subPanel.add(createLabel("Exposure [ms]", false), "split 2");

      textFieldExp_ = new JTextField(8);
      textFieldExp_.addFocusListener(new FocusAdapter() {
         @Override
         public void focusLost(FocusEvent fe) {
            studio_.setExposure(getDisplayedExposureTime());
         }
      });
      textFieldExp_.setFont(defaultFont_);
      textFieldExp_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.setExposure(getDisplayedExposureTime());
         }
      });
      subPanel.add(textFieldExp_, "gapleft push, wrap");

      // Channel group.
      subPanel.add(createLabel("Changroup", false), "split 2");

      // HACK: limit the width of this combo box, ignoring the width of the
      // entries inside of it.
      chanGroupSelect_ = new JComboBox() {
         @Override
         public Dimension getMinimumSize() {
            return new Dimension(110, super.getSize().height);
         }
      };
      chanGroupSelect_.setFont(defaultFont_);
      chanGroupSelect_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (!shouldChangeChannelGroup_) {
               // We're modifying this combobox, so we don't want it making
               // changes.
               return;
            }
            String newGroup = (String) chanGroupSelect_.getSelectedItem();
            studio_.getAcquisitionEngine().setChannelGroup(newGroup);
         }
      });
      subPanel.add(chanGroupSelect_, "gapleft push, wrap");

      // Binning.
      subPanel.add(createLabel("Binning", false), "split 2");

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
      subPanel.add(comboBinning_, "gapleft push, width 60::, wrap");

      // Shutter device.
      JPanel shutterPanel = new JPanel(
            new MigLayout("fillx, flowx, insets 0, gap 0"));
      shutterPanel.setBorder(BorderFactory.createLoweredBevelBorder());
      shutterPanel.add(createLabel("Shutter", false), "split 2");

      shutterComboBox_ = new JComboBox();
      shutterComboBox_.setName("Shutter");
      shutterComboBox_.setFont(defaultFont_);
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
      shutterPanel.add(shutterComboBox_, "gapleft push, width 60::, wrap");

      // Auto/manual shutter control.
      autoShutterCheckBox_ = ShutterControl.makeAutoShutterCheckBox(studio_);
      shutterPanel.add(autoShutterCheckBox_, "w 72!, h 20!, split 3");

      shutterIcon_ = ShutterControl.makeShutterIcon(studio_);
      shutterPanel.add(shutterIcon_, "growx, alignx center");

      toggleShutterButton_ = ShutterControl.makeShutterButton(studio_);
      shutterPanel.add(toggleShutterButton_, "growx, h 20!, wrap");
      subPanel.add(shutterPanel);
      return subPanel;
   }

   private JPanel createConfigurationControls() {
      JPanel subPanel = new JPanel(
            new MigLayout("fill, flowy, insets 1, gap 0"));
      subPanel.add(createLabel("Configuration settings", true),
            "flowx, growx, pushy 0, split 2");

      saveConfigButton_ = createButton("Save", null,
         "Save current presets to the configuration file",
         new Runnable() {
            @Override
            public void run() {
               studio_.promptToSaveConfigPresets();
            }
         });
      subPanel.add(saveConfigButton_,
            "pushy 0, gapleft push, alignx right, w 88!, h 20!");

      configPad_ = new ConfigGroupPad();
      configPadButtonPanel_ = new ConfigPadButtonPanel();
      configPadButtonPanel_.setConfigPad(configPad_);
      configPadButtonPanel_.setGUI(studio_);

      configPad_.setFont(defaultFont_);

      // Allowing the config pad to grow horizontally and vertically requires
      // us to override its preferred size.
      subPanel.add(configPad_,
            "grow, pushy 100, alignx center, w min::9999, h min::9999, span");
      subPanel.add(configPadButtonPanel_,
            "growx, pushy 0, alignx left, w 320!, h 20!, span");
      return subPanel;
   }

   /** 
    * Generate the "Snap", "Live", "Snap to album", "MDA", and "Refresh"
    * buttons.
    */
   private JPanel createCommonActionButtons() {
      JPanel subPanel = new JPanel(
            new MigLayout("flowy, insets 1, gap 1"));
      snapButton_ = (JButton) QuickAccessFactory.makeGUI(
            studio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.SnapButton"));
      snapButton_.setFont(defaultFont_);
      subPanel.add(snapButton_, BIGBUTTON_SIZE);

      liveButton_ = (JButton) QuickAccessFactory.makeGUI(
            studio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.LiveButton"));
      liveButton_.setFont(defaultFont_);
      subPanel.add(liveButton_, BIGBUTTON_SIZE);

      JButton albumButton = createButton("Album", "camera_plus_arrow.png",
         "Acquire single frame and add to an album",
         new Runnable() {
            @Override
            public void run() {
               studio_.album().addImages(studio_.live().snap(false));
            }
         });
      subPanel.add(albumButton, BIGBUTTON_SIZE);

      subPanel.add(AcquisitionSelector.makeSelector(studio_), BIGBUTTON_SIZE);

      JButton refreshButton = createButton("Refresh", "arrow_refresh.png",
         "Refresh all GUI controls directly from the hardware",
         new Runnable() {
            @Override
            public void run() {
               core_.updateSystemStateCache();
               studio_.updateGUI(true);
            }
         });
      subPanel.add(refreshButton, BIGBUTTON_SIZE);

      JButton closeAllButton = (JButton) QuickAccessFactory.makeGUI(
            studio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.CloseAllButton"));
      closeAllButton.setFont(defaultFont_);
      // HACK: Windows will helpfully replace "All" with "..." unless we do
      // this.
      closeAllButton.setMargin(new Insets(0, 0, 0, 0));
      subPanel.add(closeAllButton, BIGBUTTON_SIZE);
      return subPanel;
   }

   private JLabel createPleaLabel() {
      JLabel citePleaLabel = new JLabel("<html>Please <a href=\"http://micro-manager.org\">cite Micro-Manager</a> in your publications!</html>");
      citePleaLabel.setFont(new Font("Arial", Font.PLAIN, 11));

      // When users click on the citation plea, we spawn a new thread to send
      // their browser to the MM wiki.
      citePleaLabel.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Citing_Micro-Manager")).start();
         }
      });
      return citePleaLabel;
   }

   private JPanel createComponents() {
      JPanel overPanel = new JPanel(new MigLayout("fill, flowx, insets 1, gap 0"));
      overPanel.add(createConfigProfileLine(), "growx, spanx, wrap");
      JPanel subPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      subPanel.add(createCommonActionButtons(), "growy, aligny top");
      subPanel.add(createImagingSettingsWidgets(), "gapleft 10, growx, wrap");
      subPanel.add(createUtilityButtons(), "span, wrap");
      subPanel.add(createPleaLabel(), "span, wrap");
      overPanel.add(subPanel, "gapbottom push, grow 0, pushx 0");
      overPanel.add(createConfigurationControls(), "grow, wrap, pushx 100");
      // Must not be a completely empty label or else our size calculations
      // fail when setting the minimum size of the frame.
      labelImageDimensions_ = createLabel(" ", false);
      labelImageDimensions_.setBorder(new MatteBorder(1, 0, 0, 0,
               new Color(200, 200, 200)));
      overPanel.add(labelImageDimensions_, "growx, pushy 0, span, gap 2 0 2 0");
      return overPanel;
   }

   private JPanel createConfigProfileLine() {
      JPanel subPanel = new JPanel(
            new MigLayout("flowx, insets 0 1 0 1, gap 0, fill"));
      subPanel.setBorder(new MatteBorder(0, 0, 1, 0,
               new Color(200, 200, 200)));
      profileName_ = new JLabel();
      profileName_.setFont(defaultFont_);
      profileName_.setText("Profile: " + studio_.profile().getProfileName());
      subPanel.add(profileName_, "alignx left");
      configFile_ = new JLabel();
      configFile_.setFont(defaultFont_);
      subPanel.add(configFile_, "alignx right, gapleft push, wrap");
      return subPanel;
   }

   public void setUserName(String name) {
      profileName_.setText("Profile: " + name);
   }

   private JPanel createUtilityButtons() {
      JPanel subPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      // ROI
      JPanel roiPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      roiPanel.add(createLabel("ROI", true),
            "span 2, alignx center, growx, wrap");
      setRoiButton_ = createButton(null, "shape_handles.png",
         "Set Region Of Interest to selected rectangle",
         new Runnable() {
            @Override
            public void run() {
               studio_.setROI();
            }
         });
      roiPanel.add(setRoiButton_, SMALLBUTTON_SIZE);
      centerQuadButton_ = createButton(null, "center_quad.png",
         "Set Region Of Interest to center quad of camera",
         new Runnable() {
            @Override
            public void run() {
               studio_.setCenterQuad();
            }
         });
      roiPanel.add(centerQuadButton_, SMALLBUTTON_SIZE);

      clearRoiButton_ = createButton(null, "arrow_out.png",
         "Reset Region of Interest to full frame",
         new Runnable() {
            @Override
            public void run() {
               studio_.clearROI();
            }
         });
      roiPanel.add(clearRoiButton_, SMALLBUTTON_SIZE);

      subPanel.add(roiPanel);

      // Stage control
      JPanel stagePanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      stagePanel.add(createLabel("Stage", true),
            "span 3, alignx center, growx, wrap");
      // This icon is the public-domain icon at
      // https://openclipart.org/detail/198011/mono-move
      AbstractButton moveButton = createButton(null, "move.png",
            "Control the current stage with a virtual joystick",
            new Runnable() {
               @Override
               public void run() {
                  StageControlFrame.showStageControl();
               }
            });
      stagePanel.add(moveButton, SMALLBUTTON_SIZE);

      // This icon is based on the public-domain icons at
      // https://openclipart.org/detail/170328/eco-green-hand-icon
      // and
      // https://openclipart.org/detail/198011/mono-move
      handMovesButton_ = new JToggleButton(
            IconLoader.getIcon("/org/micromanager/icons/move_hand.png"));
      handMovesButton_.setToolTipText(
            "When set, you can double-click on the Snap/Live view to move the stage. Requires pixel sizes to be set (see Pixel Calibration), and that you use the hand tool.");
      handMovesButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               boolean isSelected = handMovesButton_.isSelected();
               studio_.updateCenterAndDragListener(isSelected);
               String path = isSelected ? "move_hand_on.png" : "move_hand.png";
               handMovesButton_.setIcon(IconLoader.getIcon(
                     "/org/micromanager/icons/" + path));
            }
      });
      stagePanel.add(handMovesButton_, SMALLBUTTON_SIZE);

      AbstractButton listButton = createButton(null, "application_view_list.png",
            "Show the Stage Position List dialog",
            new Runnable() {
               @Override
               public void run() {
                  studio_.app().showPositionList();
               }
            });
      stagePanel.add(listButton, SMALLBUTTON_SIZE);

      subPanel.add(stagePanel, "gapleft 16");

      // Autofocus
      JPanel autoPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      autoPanel.add(createLabel("Autofocus", true),
            "span 2, alignx center, growx, wrap");
      // Icon based on the public-domain icon at
      // http://www.clker.com/clipart-267005.html
      autofocusNowButton_ = createButton(null, "binoculars.png",
         "Autofocus now",
         new Runnable() {
            @Override
            public void run() {
               studio_.autofocusNow();
            }
         });
      autoPanel.add(autofocusNowButton_, SMALLBUTTON_SIZE);

      // Icon based on the public-domain icon at
      // http://publicdomainvectors.org/en/free-clipart/Adjustable-wrench-icon-vector-image/23097.html
      autofocusConfigureButton_ = createButton(null,
            "wrench.png", "Set autofocus options",
         new Runnable() {
            @Override
            public void run() {
               studio_.showAutofocusDialog();
            }
         });
      autoPanel.add(autofocusConfigureButton_, SMALLBUTTON_SIZE);

      subPanel.add(autoPanel, "gapleft 16");
      return subPanel;
   }

   public void setConfigText(String configFile) {
      // Recognize and specially treat empty config files.
      if (configFile.equals("")) {
         configFile = "(none)";
      }
      configFile_.setText("Config file: " + configFile);
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
    * @param text text to be shown 
    */
   public void updateInfoDisplay(String text) {
      labelImageDimensions_.setText(text);
   }

   public void toggleShutter() {
      try {
         if (!toggleShutterButton_.isEnabled()) {
            return;
         }
         toggleShutterButton_.requestFocusInWindow();
         studio_.shutter().setShutter(!studio_.shutter().getShutter());
      } catch (Exception e1) {
         ReportingUtils.showError(e1);
      }
   }

   @Override
   public void liveModeEnabled(boolean isEnabled) {
      snapButton_.setEnabled(!isEnabled);
   }

   @Subscribe
   public void onShutterDevices(ShutterDevicesEvent event) {
      refreshShutterGUI();
   }

   private void refreshShutterGUI() {
      List<String> devices = studio_.shutter().getShutterDevices();
      if (devices == null) {
         // No shutter devices available yet.
         return;
      }
      String[] items = new ArrayList<String>(devices).toArray(new String[] {});
      GUIUtils.replaceComboContents(shutterComboBox_, items);
      String activeShutter = null;
      try {
         activeShutter = studio_.shutter().getCurrentShutter();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error getting shutter device");
      }
      if (activeShutter != null) {
         shutterComboBox_.setSelectedItem(activeShutter);
      }
      else {
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

   @Subscribe
   public void onGUIRefresh(GUIRefreshEvent event) {
      refreshChannelGroup();
      refreshShutterGUI();
   }

   @Subscribe
   public void onStartupComplete(StartupCompleteEvent event) {
      refreshChannelGroup();
   }

   @Subscribe
   public void onChannelGroup(ChannelGroupEvent event) {
      refreshChannelGroup();
   }

   /**
    * Recreate the contents and current selection of the chanGroupSelect_
    * combobox. We have to temporarily disable its action listener so it
    * doesn't try to change the current channel group while we do this.
    */
   private void refreshChannelGroup() {
      shouldChangeChannelGroup_ = false;
      chanGroupSelect_.removeAllItems();
      for (String group : studio_.getAcquisitionEngine().getAvailableGroups()) {
         chanGroupSelect_.addItem(group);
      }
      chanGroupSelect_.setSelectedItem(core_.getChannelGroup());
      shouldChangeChannelGroup_ = true;
   }

   @Subscribe
   public void onMouseMovesStage(MouseMovesStageEvent event) {
      handMovesButton_.setSelected(event.getIsEnabled());
   }

   @Subscribe
   public void onChannelExposure(ChannelExposureEvent event) {
      if (event.getIsMainExposureTime()) {
         setDisplayedExposureTime(event.getNewExposureTime());
      }
   }

   private List<String> sortBinningItems(final List<String> items) {
      ArrayList<Integer> binSizes = new ArrayList<Integer>();

      // Check if all items are valid integers
      for (String s : items) {
         Integer i;
         try {
            i = Integer.valueOf(s);
         }
         catch (NumberFormatException e) {
            // Not a number; give up sorting and return original list
            return items;
         }
         binSizes.add(i);
      }

      Collections.sort(binSizes);
      ArrayList<String> ret = new ArrayList<String>();
      for (Integer i : binSizes) {
         ret.add(i.toString());
      }
      return ret;
   }

   public void configureBinningComboForCamera(String cameraLabel) {
      ActionListener[] listeners;
      if (comboBinning_.getItemCount() > 0) {
          comboBinning_.removeAllItems();
      }
      try {
         StrVector binSizes = core_.getAllowedPropertyValues(
                 cameraLabel, MMCoreJ.getG_Keyword_Binning());
         List<String> items = sortBinningItems(Arrays.asList(binSizes.toArray()));

         listeners = comboBinning_.getActionListeners();
         for (ActionListener listener : listeners) {
            comboBinning_.removeActionListener(listener);
         }

         for (String item : items) {
             comboBinning_.addItem(item);
         }

         comboBinning_.setMaximumRowCount(items.size());
         if (items.isEmpty()) {
             comboBinning_.setEditable(true);
         } else {
             comboBinning_.setEditable(false);
         }

         for (ActionListener listener : listeners) {
            comboBinning_.addActionListener(listener);
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
    * @return bin setting in UI as a String
    */
   public String getBinMode() {
      Object item = comboBinning_.getSelectedItem();
      if (item != null) {
         return item.toString();
      }
      return (String) null;
   }

   public ConfigGroupPad getConfigPad() {
      return configPad_;
   }

   /**
    * Save our settings to the user profile.
    */
   public void savePrefs() {
      this.savePosition();
      DefaultUserProfile.getInstance().setString(MainFrame.class,
            MAIN_EXPOSURE, textFieldExp_.getText());
   }

   public void setDisplayedExposureTime(double exposure) {
      textFieldExp_.setText(NumberUtils.doubleToDisplayString(exposure));
   }

   public double getDisplayedExposureTime() {
      try {
         return NumberUtils.displayStringToDouble(textFieldExp_.getText());
      } catch (ParseException e) {
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
               centerQuadButton_.setEnabled(enabled);
           }
       });
   }
}
