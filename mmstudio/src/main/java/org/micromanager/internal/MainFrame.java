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
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
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

import org.micromanager.events.ConfigGroupChangedEvent;
import org.micromanager.events.ChannelExposureEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.events.StartupCompleteEvent;
import org.micromanager.events.internal.ChannelGroupEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.MouseMovesStageEvent;
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

import org.micromanager.quickaccess.internal.QuickAccessFactory;

/**
 * GUI code for the primary window of the program. And nothing else.
 */
public class MainFrame extends MMFrame implements LiveModeListener {

   private static final String MICRO_MANAGER_TITLE = "Micro-Manager";
   private static final String MAIN_FRAME_DIVIDER_POS = "divider_pos";
   private static final String MAIN_EXPOSURE = "exposure";

   // Size constraint for normal buttons.
   private static final String BIGBUTTON_SIZE = "w 88!, h 22!";
   // Size constraint for small buttons.
   private static final String SMALLBUTTON_SIZE = "w 30!, h 20!";

   // GUI components
   private JComboBox comboBinning_;
   private JComboBox shutterComboBox_;
   private JTextField textFieldExp_;
   private JComboBox chanGroupSelect_;
   // Toggles activity of chanGroupSelect_ on or off.
   private boolean shouldChangeChannelGroup_;
   private JLabel labelImageDimensions_;
   private JToggleButton liveButton_;
   private JCheckBox autoShutterCheckBox_;
   private JLabel shutterIcon_;
   private JButton snapButton_;
   private JToggleButton handMovesButton_;
   private JButton autofocusNowButton_;
   private JButton autofocusConfigureButton_;
   private JButton saveConfigButton_;
   private JButton toggleShutterButton_;

   private ConfigGroupPad configPad_;

   private final Font defaultFont_ = new Font("Arial", Font.PLAIN, 10);

   private final CMMCore core_;
   private final MMStudio studio_;
   private final SnapLiveManager snapLiveManager_;

   private ConfigPadButtonPanel configPadButtonPanel_;

   private AbstractButton setRoiButton_;
   private AbstractButton clearRoiButton_;

   @SuppressWarnings("LeakingThisInConstructor")
   public MainFrame(MMStudio studio, CMMCore core, SnapLiveManager manager) {
      super("main micro manager frame");
      org.micromanager.internal.diagnostics.ThreadExceptionLogger.setUp();

      studio_ = studio;
      core_ = core;
      snapLiveManager_ = manager;
      snapLiveManager_.addLiveModeListener(this);

      setTitle(MICRO_MANAGER_TITLE + " " + MMVersion.VERSION_STRING);
      setMinimumSize(new Dimension(595, 250));

      JPanel contents = new JPanel();
      // Minimize insets.
      contents.setLayout(new MigLayout("insets 1, gap 0"));
      setContentPane(contents);

      contents.add(createComponents(), "grow");

      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      setupWindowHandlers();

      // Add our own keyboard manager that handles Micro-Manager shortcuts
      MMKeyDispatcher mmKD = new MMKeyDispatcher();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(mmKD);
      DropTarget dropTarget = new DropTarget(this, new DragDropUtil());

      resetPosition();
      setExitStrategy(OptionsDlg.getShouldCloseOnExit());
      setIconImage(Toolkit.getDefaultToolkit().getImage(
               getClass().getResource("/org/micromanager/icons/microscope.gif")));

      pack();
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
      this.loadAndRestorePosition(100, 100, 644, 220);
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

      chanGroupSelect_ = new JComboBox();
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
      subPanel.add(comboBinning_, "gapleft push, wrap");

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
      shutterPanel.add(shutterComboBox_, "gapleft push, wrap");

      // Auto/manual shutter control.
      autoShutterCheckBox_ = new JCheckBox("Auto");
      autoShutterCheckBox_.setToolTipText("Toggle auto shutter, in which the shutter opens automatically when an image is taken, and closes when imaging finishes.");
      autoShutterCheckBox_.setFont(defaultFont_);
      autoShutterCheckBox_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            studio_.toggleAutoShutter();
         }
      });
      shutterPanel.add(autoShutterCheckBox_, "w 72!, h 20!, split 3");

      shutterIcon_ = new JLabel(
            IconLoader.getIcon("/org/micromanager/icons/shutter_open.png"));
      shutterIcon_.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseReleased(MouseEvent e) {
            if (autoShutterCheckBox_.isSelected()) {
               autoShutterCheckBox_.setSelected(false);
               studio_.toggleAutoShutter();
            }
            toggleShutter();
         }
      });
      shutterPanel.add(shutterIcon_, "growx, alignx center");

      toggleShutterButton_ = new JButton("Open");
      toggleShutterButton_.setToolTipText("Open/close the shutter, when autoshutter is not enabled.");
      toggleShutterButton_.setFont(defaultFont_);
      toggleShutterButton_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               toggleShutter();
            }
      });
      shutterPanel.add(toggleShutterButton_, "growx, h 20!, wrap");
      subPanel.add(shutterPanel);
      return subPanel;
   }

   private JPanel createConfigurationControls() {
      JPanel subPanel = new JPanel(
            new MigLayout("filly, flowy, insets 1, gap 0"));
      subPanel.add(createLabel("Configuration settings", true),
            "flowx, split 2");

      saveConfigButton_ = createButton("Save", null,
         "Save current presets to the configuration file",
         new Runnable() {
            @Override
            public void run() {
               studio_.saveConfigPresets();
            }
         });
      subPanel.add(saveConfigButton_,
            "gapleft push, alignx right, w 88!, h 20!");

      configPad_ = new ConfigGroupPad();
      configPadButtonPanel_ = new ConfigPadButtonPanel();
      configPadButtonPanel_.setConfigPad(configPad_);
      configPadButtonPanel_.setGUI(studio_);

      configPad_.setFont(defaultFont_);

      // Allow the config pad to grow horizontally. Its preferred height is
      // only 420px, hence why we override it here.
      subPanel.add(configPad_,
            "growy, alignx center, w min:320:pref, h min:9999:9999, span");
      subPanel.add(configPadButtonPanel_,
            "growx, alignx center, w 320!, h 20!, span");
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

      liveButton_ = (JToggleButton) QuickAccessFactory.makeGUI(
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

      // This icon based on the public-domain icon at
      // https://openclipart.org/detail/2757/movie-tape
      JButton mdaButton = createButton("Multi-D Acq.", "film.png",
         "Open multi-dimensional acquisition window",
         new Runnable() {
            @Override
            public void run() {
               studio_.openAcqControlDialog();
            }
         });
      subPanel.add(mdaButton, BIGBUTTON_SIZE);

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

      JButton closeAllButton = createButton("Close All",
            "close_windows.png", "Close all open image windows, optionally prompting to save unsaved data.",
            new Runnable() {
               @Override
               public void run() {
                  studio_.displays().promptToCloseWindows();
               }
            });
      subPanel.add(closeAllButton, BIGBUTTON_SIZE);
      return subPanel;
   }

   private JLabel createPleaLabel() {
      JLabel citePleaLabel = new JLabel("<html>Please <a href=\"http://micro-manager.org\">cite Micro-Manager</a> so funding will continue!</html>");
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
      JPanel overPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      JPanel subPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      subPanel.add(createCommonActionButtons(), "growy, aligny top");
      subPanel.add(createImagingSettingsWidgets(), "gapleft 10, growx, wrap");
      subPanel.add(createUtilityButtons(), "span, wrap");
      subPanel.add(createPleaLabel(), "span, wrap");
      overPanel.add(subPanel, "gapbottom push");
      overPanel.add(createConfigurationControls(), "growy, wrap");
      labelImageDimensions_ = createLabel("", false);
      overPanel.add(labelImageDimensions_, "growx, span, gap 2 0 2 0");
      return overPanel;
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
                  studio_.showXYPositionList();
               }
            });
      stagePanel.add(listButton, SMALLBUTTON_SIZE);

      subPanel.add(stagePanel, "gapleft 16");

      // Autofocus
      JPanel autoPanel = new JPanel(new MigLayout("flowx, insets 1, gap 0"));
      autoPanel.add(createLabel("Autofocus", true),
            "span 2, alignx center, growx, wrap");
      autofocusNowButton_ = createButton(null, "find.png",
         "Autofocus now",
         new Runnable() {
            @Override
            public void run() {
               studio_.autofocusNow();
            }
         });
      autoPanel.add(autofocusNowButton_, SMALLBUTTON_SIZE);

      autofocusConfigureButton_ = createButton(null,
            "wrench_orange.png", "Set autofocus options",
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
    * @param text text to be shown 
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
      updateShutterIcon();
   }

   public void toggleShutter() {
      try {
         if (!toggleShutterButton_.isEnabled()) {
            return;
         }
         toggleShutterButton_.requestFocusInWindow();
         if (toggleShutterButton_.getText().equals("Open")) {
            core_.setShutterOpen(true);
            setShutterButton(true);
         } else {
            core_.setShutterOpen(false);
            setShutterButton(false);
         }
      } catch (Exception e1) {
         ReportingUtils.showError(e1);
      }
   }

   public void updateAutoShutterUI(boolean isAuto) {
      StaticInfo.shutterLabel_ = core_.getShutterDevice();
      if (StaticInfo.shutterLabel_.length() == 0) {
         // No shutter device.
         setToggleShutterButtonEnabled(false);
      } else {
         setToggleShutterButtonEnabled(!isAuto);
         try {
            setShutterButton(core_.getShutterOpen());
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
         if (!isAuto) {
            toggleShutterButton_.setSelected(true);
         }
      }
   }

   @Override
   public void liveModeEnabled(boolean isEnabled) {
      autoShutterCheckBox_.setEnabled(!isEnabled);
      if (core_.getAutoShutter()) {
         toggleShutterButton_.setText(isEnabled ? "Close" : "Open" );
         updateShutterIcon();
      }
      snapButton_.setEnabled(!isEnabled);
   }

   /**
    * Set the shutter icon to be the right one out of open/closed and
    * auto/manual.
    */
   private void updateShutterIcon() {
      try {
         String path = "/org/micromanager/icons/shutter_";
         String tooltip = "The shutter is ";
         // Note that we use the mode both for the tooltip and for
         // constructing the image file path, since it happens to work out.
         String mode = core_.getShutterOpen() ? "open" : "closed";
         path += mode;
         tooltip += mode;
         if (core_.getAutoShutter()) {
            path += "_auto";
            tooltip += ". Autoshutter is enabled";
         }
         path += ".png";
         tooltip += ". Click to open or close the shutter.";
         shutterIcon_.setIcon(IconLoader.getIcon(path));
         shutterIcon_.setToolTipText(tooltip);
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Unable to get shutter state");
      }
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

   @Subscribe
   public void onGUIRefresh(GUIRefreshEvent event) {
      refreshChannelGroup();
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
      for (String group : core_.getAvailableConfigGroups().toArray()) {
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

   public void setAutoShutterSelected(boolean isSelected) {
      autoShutterCheckBox_.setSelected(isSelected);
   }

   public void setToggleShutterButtonEnabled(boolean isEnabled) {
      toggleShutterButton_.setEnabled(isEnabled);
   }

   public void setShutterComboSelection(String activeShutter) {
      shutterComboBox_.setSelectedItem(activeShutter);
      if (activeShutter.equals("") || core_.getAutoShutter()) {
         setToggleShutterButtonEnabled(false);
      } else {
         setToggleShutterButtonEnabled(true);
      }
   }

   public boolean getAutoShutterChecked() {
      return autoShutterCheckBox_.isSelected();
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
           }
       });
   }
}
