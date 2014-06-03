///////////////////////////////////////////////////////////////////////////////
//FILE:          OptionsDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, September 12, 2006
//               Mark Tsuchida (Layout, June 2014)
//
// COPYRIGHT:    University of California, San Francisco, 2006-2014
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

package org.micromanager;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import mmcorej.CMMCore;

import org.micromanager.api.ScriptInterface;
import org.micromanager.logging.LogFileManager;
import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMDialog;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.UIMonitor;

/**
 * Options dialog for MMStudio.
 *
 */
public class OptionsDlg extends MMDialog {
   private static final long serialVersionUID = 1L;

   private JTextField startupScriptFile_;
   private JTextField bufSizeField_;
   private JTextField logDeleteDaysField_;
   private JComboBox comboDisplayBackground_;

   private MMOptions opts_;
   private CMMCore core_;
   private Preferences mainPrefs_;
   private ScriptInterface parent_;
   private GUIColors guiColors_;

   /**
    * Create the dialog
    */
   public OptionsDlg(MMOptions opts, CMMCore core, Preferences mainPrefs, ScriptInterface parent) {
      super();
      parent_ = parent;
      opts_ = opts;
      core_ = core;
      mainPrefs_ = mainPrefs;
      guiColors_ = new GUIColors();

      setResizable(false);
      setModal(true);
      setTitle("Micro-Manager Options");
      if (opts_.displayBackground_.equals("Day")) {
         setBackground(java.awt.SystemColor.control);
      } else if (opts_.displayBackground_.equals("Night")) {
         setBackground(java.awt.Color.gray);
      }

      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/OptionsDlg"));

      Rectangle r = getBounds();
      loadPosition(r.x, r.y);

      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent e) {
            closeRequested();
         }
      });

      final JCheckBox debugLogEnabledCheckBox = new JCheckBox();
      debugLogEnabledCheckBox.setText("Enable debug logging");
      debugLogEnabledCheckBox.setToolTipText("Enable verbose logging for troubleshooting and debugging");
      debugLogEnabledCheckBox.setSelected(opts_.debugLogEnabled_);
      debugLogEnabledCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            opts_.debugLogEnabled_ = debugLogEnabledCheckBox.isSelected();
            core_.enableDebugLog(opts_.debugLogEnabled_);
            UIMonitor.enable(opts_.debugLogEnabled_);
         }
      });

      final JCheckBox doNotAskForConfigFileCheckBox = new JCheckBox();
      doNotAskForConfigFileCheckBox.setText("Do not ask for config file at startup");
      doNotAskForConfigFileCheckBox.setSelected(opts_.doNotAskForConfigFile_);
      doNotAskForConfigFileCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.doNotAskForConfigFile_ = doNotAskForConfigFileCheckBox.isSelected();
         }
      });

      final JCheckBox deleteLogCheckBox = new JCheckBox();
      deleteLogCheckBox.setText("Delete log files after");
      deleteLogCheckBox.setSelected(opts_.deleteOldCoreLogs_);
      deleteLogCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            opts_.deleteOldCoreLogs_ = deleteLogCheckBox.isSelected();
         }
      });

      logDeleteDaysField_ =
         new JTextField(Integer.toString(opts_.deleteCoreLogAfterDays_), 2);

      final JButton deleteLogFilesButton = new JButton();
      deleteLogFilesButton.setText("Delete Log Files Now");
      deleteLogFilesButton.setToolTipText("Delete all CoreLog files except " +
            "for the current one");
      deleteLogFilesButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            LogFileManager.deleteLogFilesDaysOld(0, core_.getPrimaryLogFile());
         }
      });

      final JButton clearRegistryButton = new JButton();
      clearRegistryButton.setText("Reset Preferences");
      clearRegistryButton.setToolTipText("Clear all preference settings and restore defaults");
      clearRegistryButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            try {
               boolean previouslyRegistered = mainPrefs_.getBoolean(RegistrationDlg.REGISTRATION, false);
               mainPrefs_.clear();
               Preferences acqPrefs = mainPrefs_.node(mainPrefs_.absolutePath() + "/" + AcqControlDlg.ACQ_SETTINGS_NODE);
               acqPrefs.clear();

               // restore registration flag
               mainPrefs_.putBoolean(RegistrationDlg.REGISTRATION, previouslyRegistered);

            } catch (BackingStoreException exc) {
               ReportingUtils.showError(e);
            }
         }
      });

      bufSizeField_ = new JTextField(Integer.toString(opts_.circularBufferSizeMB_), 5);

      comboDisplayBackground_ = new JComboBox(guiColors_.styleOptions);
      comboDisplayBackground_.setMaximumRowCount(2);
      comboDisplayBackground_.setSelectedItem(opts_.displayBackground_);
      comboDisplayBackground_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            changeBackground();
         }
      });

      startupScriptFile_ = new JTextField(opts_.startupScript_, 10);

      final JCheckBox autoreloadDevicesCheckBox = new JCheckBox();
      autoreloadDevicesCheckBox.setText("Auto-reload devices (Danger!)");
      autoreloadDevicesCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.autoreloadDevices_ = autoreloadDevicesCheckBox.isSelected();
         }
      });

      final JCheckBox closeOnExitCheckBox = new JCheckBox();
      closeOnExitCheckBox.setText("Close app when quitting MM");
      closeOnExitCheckBox.setSelected(opts_.closeOnExit_);
      closeOnExitCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.closeOnExit_ = closeOnExitCheckBox.isSelected();
            MMStudioMainFrame.getInstance().setExitStrategy(opts_.closeOnExit_);
         }
      });

      final JComboBox prefZoomCombo = new JComboBox();
      prefZoomCombo.setModel(new DefaultComboBoxModel(new String[]{
         "8%", "12%", "16%",  "25%",  "33%", "50%", "75%", "100%", "150%","200%","300%","400%","600%"
      }));
      double mag = opts_.windowMag_;
      int index = 0;
      if (mag == 0.25 / 3.0) {
         index = 0;
      } else if (mag == 0.125) {
         index = 1;
      } else if (mag == 0.16) {
         index = 2;
      } else if (mag == 0.25) {
         index = 3;
      } else if (mag == 0.33) {
         index = 4;
      } else if (mag == 0.5) {
         index = 5;
      } else if (mag == 0.75) {
         index = 6;
      } else if (mag == 1.0) {
         index = 7;
      } else if (mag == 1.5) {
         index = 8;
      } else if (mag == 2.0) {
         index = 9;
      } else if (mag == 3.0) {
         index = 10;
      } else if (mag == 4.0) {
         index = 11;
      } else if (mag == 6.0) {
         index = 12;
      }
      prefZoomCombo.setSelectedIndex(index);
      prefZoomCombo.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            switch (prefZoomCombo.getSelectedIndex()) {
               case (0):
                  opts_.windowMag_ = 0.25 / 3.0;
                  break;
               case (1):
                  opts_.windowMag_ = 0.125;
                  break;
               case (2):
                  opts_.windowMag_ = 0.16;
                  break;
               case (3):
                  opts_.windowMag_ = 0.25;
                  break;
               case (4):
                  opts_.windowMag_ = 0.33;
                  break;
               case (5):
                  opts_.windowMag_ = 0.5;
                  break;
               case (6):
                  opts_.windowMag_ = 0.75;
                  break;
               case (7):
                  opts_.windowMag_ = 1.0;
                  break;
               case (8):
                  opts_.windowMag_ = 1.5;
                  break;
               case (9):
                  opts_.windowMag_ = 2.0;
                  break;
               case (10):
                  opts_.windowMag_ = 3.0;
                  break;
               case (11):
                  opts_.windowMag_ = 4.0;
                  break;
               case (12):
                  opts_.windowMag_ = 6.0;
                  break;
            }
         }
      });
      
      final JCheckBox metadataFileWithMultipageTiffCheckBox = new JCheckBox();
      metadataFileWithMultipageTiffCheckBox.setText("Create metadata.txt file with Image Stack Files");
      metadataFileWithMultipageTiffCheckBox.setSelected(opts_.mpTiffMetadataFile_);
      metadataFileWithMultipageTiffCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.mpTiffMetadataFile_ = metadataFileWithMultipageTiffCheckBox.isSelected();
         }
      });
      
      final JCheckBox separateFilesForPositionsMPTiffCheckBox = new JCheckBox();
      separateFilesForPositionsMPTiffCheckBox.setText("Save XY positions in separate Image Stack Files");
      separateFilesForPositionsMPTiffCheckBox.setSelected(opts_.mpTiffSeparateFilesForPositions_);
      separateFilesForPositionsMPTiffCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.mpTiffSeparateFilesForPositions_ = separateFilesForPositionsMPTiffCheckBox.isSelected();
         }
      });
  
      final JCheckBox syncExposureMainAndMDA = new JCheckBox();
      syncExposureMainAndMDA.setText("Sync exposure between Main and MDA windows");
      syncExposureMainAndMDA.setSelected(opts_.syncExposureMainAndMDA_);
      syncExposureMainAndMDA.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.syncExposureMainAndMDA_ = syncExposureMainAndMDA.isSelected();
         }
      });
  
      final JCheckBox hideMDAdisplay = new JCheckBox();
      hideMDAdisplay.setText("Hide MDA display");
      hideMDAdisplay.setSelected(opts_.hideMDADisplay_);
      hideMDAdisplay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            opts_.hideMDADisplay_ = hideMDAdisplay.isSelected();
         }
      });

      final JButton closeButton = new JButton();
      closeButton.setText("Close");
      closeButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent ev) {
            closeRequested();
         }
      });


      setLayout(new net.miginfocom.swing.MigLayout(
               "fill, insets dialog",
               "[fill]"));

      add(debugLogEnabledCheckBox, "wrap");
      add(doNotAskForConfigFileCheckBox, "wrap");

      add(new JLabel("Sequence Buffer Size:"), "split 3, gapright push");
      add(bufSizeField_, "gapright related");
      add(new JLabel("MB"), "wrap");

      add(new JLabel("Display Background:"), "split 2, gapright push");
      add(comboDisplayBackground_, "wrap");

      add(new JLabel("Startup Script:"), "split 2, gapright push");
      add(startupScriptFile_, "wrap");

      add(deleteLogCheckBox, "split 3, gapright related");
      add(logDeleteDaysField_, "gapright related");
      add(new JLabel("days"), "gapright push, wrap");

      add(deleteLogFilesButton, "sizegroup clearBtns, split 2");
      add(clearRegistryButton, "sizegroup clearBtns, wrap");

      add(autoreloadDevicesCheckBox, "wrap");
      add(closeOnExitCheckBox, "wrap");

      add(new JLabel("Preferred Image Window Zoom:"),
         "split 2, gapright push");
      add(prefZoomCombo, "wrap");

      add(metadataFileWithMultipageTiffCheckBox, "wrap");
      add(separateFilesForPositionsMPTiffCheckBox, "wrap");
      add(syncExposureMainAndMDA, "wrap");
      add(hideMDAdisplay, "wrap");

      add(closeButton, "gapleft push");

      pack();
   }

   private void changeBackground() {
      String background = (String) comboDisplayBackground_.getSelectedItem();
      opts_.displayBackground_ = background;
      setBackground(guiColors_.background.get(background));

      if (parent_ != null) // test for null just to avoid crashes (should never be null)
      {
         // set background and trigger redraw of parent and its descendant windows
         MMStudioMainFrame.getInstance().setBackgroundStyle(background);
      }
   }

   private void closeRequested() {
      int seqBufSize;
      int deleteLogDays;
      try {
         seqBufSize =
            NumberUtils.displayStringToInt(bufSizeField_.getText());
         deleteLogDays =
            NumberUtils.displayStringToInt(logDeleteDaysField_.getText());
      }
      catch (Exception ex) {
         ReportingUtils.showError(ex);
         return;
      }

      opts_.circularBufferSizeMB_ = seqBufSize;
      opts_.startupScript_ = startupScriptFile_.getText();
      opts_.deleteCoreLogAfterDays_ = deleteLogDays;
      opts_.saveSettings();

      savePosition();
      parent_.makeActive();
      dispose();
   }
}
