///////////////////////////////////////////////////////////////////////////////
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

package org.micromanager.internal.dialogs;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import mmcorej.CMMCore;

import org.micromanager.CompatibilityInterface;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.Studio;
import org.micromanager.internal.logging.LogFileManager;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.UIMonitor;

/**
 * Options dialog for MMStudio.
 *
 */
public class OptionsDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private static final String IS_DEBUG_LOG_ENABLED = "is debug logging enabled";
   private static final String SHOULD_CLOSE_ON_EXIT = "should close the entire program when the Micro-Manager plugin is closed";

   private final JTextField startupScriptFile_;
   private final JTextField bufSizeField_;
   private JTextField logDeleteDaysField_;
   private final JComboBox comboDisplayBackground_;

   private CMMCore core_;
   private Studio parent_;

   /**
    * Create the dialog
    * @param core - The Micro-Manager Core object
    * @param parent - MMStudio api 
    */
   public OptionsDlg(CMMCore core, Studio parent) {
      super("global micro-manager options");
      parent_ = parent;
      core_ = core;

      setResizable(false);
      setModal(true);
      setAlwaysOnTop(true);
      setTitle("Micro-Manager Options");
      
      loadAndRestorePosition(100, 100);     

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
      debugLogEnabledCheckBox.setSelected(getIsDebugLogEnabled());
      debugLogEnabledCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            boolean isEnabled = debugLogEnabledCheckBox.isSelected();
            setIsDebugLogEnabled(isEnabled);
            core_.enableDebugLog(isEnabled);
            UIMonitor.enable(isEnabled);
         }
      });

      final JCheckBox askForConfigFileCheckBox = new JCheckBox();
      askForConfigFileCheckBox.setText("Ask for config file at startup");
      askForConfigFileCheckBox.setSelected(IntroDlg.getShouldAskForConfigFile());
      askForConfigFileCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            IntroDlg.setShouldAskForConfigFile(askForConfigFileCheckBox.isSelected());
         }
      });

      final JCheckBox alwaysUseDefaultProfileCheckBox = new JCheckBox(
            "Always use the default user profile");
      alwaysUseDefaultProfileCheckBox.setToolTipText("Always use the default user profile; no prompt will be displayed to select a profile at startup. Won't take effect until after a restart.");
      alwaysUseDefaultProfileCheckBox.setSelected(
            DefaultUserProfile.getShouldAlwaysUseDefaultProfile());
      alwaysUseDefaultProfileCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DefaultUserProfile.setShouldAlwaysUseDefaultProfile(
               alwaysUseDefaultProfileCheckBox.isSelected());
         }
      });

      final JCheckBox deleteLogCheckBox = new JCheckBox();
      deleteLogCheckBox.setText("Delete log files after");
      deleteLogCheckBox.setSelected(MMStudio.getShouldDeleteOldCoreLogs());
      deleteLogCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            MMStudio.setShouldDeleteOldCoreLogs(deleteLogCheckBox.isSelected());
         }
      });

      logDeleteDaysField_ =
         new JTextField(Integer.toString(MMStudio.getCoreLogLifetimeDays()), 2);

      final JButton deleteLogFilesButton = new JButton();
      deleteLogFilesButton.setText("Delete Log Files Now");
      deleteLogFilesButton.setToolTipText("Delete all CoreLog files except " +
            "for the current one");
      deleteLogFilesButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            String dir1 =
               LogFileManager.getLogFileDirectory().getAbsolutePath();
            String dir2 =
               LogFileManager.getLegacyLogFileDirectory().getAbsolutePath();
            String dirs;
            if (dir1.equals(dir2)) {
               dirs = dir1;
            }
            else {
               dirs = dir1 + " and " + dir2;
            }

            int answer = JOptionPane.showConfirmDialog(OptionsDlg.this,
               "<html><body><p style='width: 400px;'>" +
               "Delete all CoreLog files in " + dirs + "?" +
               "</p></body></html>",
               "Delete Log Files",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE);
            if (answer == JOptionPane.YES_OPTION) {
               LogFileManager.deleteLogFilesDaysOld(0,
                  core_.getPrimaryLogFile());
            }
         }
      });

      final JButton clearPreferencesButton = new JButton();
      clearPreferencesButton.setText("Reset Preferences");
      clearPreferencesButton.setToolTipText("Clear all preference settings and restore defaults");
      clearPreferencesButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(final ActionEvent e) {
            int answer = JOptionPane.showConfirmDialog(OptionsDlg.this,
               "Reset all preference settings?",
               "Reset Preferences",
               JOptionPane.YES_NO_OPTION,
               JOptionPane.QUESTION_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
               return;
            }
            // Clear everything except whether or not this user has
            // registered.
            boolean haveRegistered = RegistrationDlg.getHaveRegistered();
            DefaultUserProfile.getInstance().clearProfile();
            RegistrationDlg.setHaveRegistered(haveRegistered);
            // Rather than updating all the GUI elements, let's just close
            // the dialog.
            dispose();
         }
      });

      bufSizeField_ = new JTextField(
            Integer.toString(MMStudio.getCircularBufferSize()), 5);

      comboDisplayBackground_ = new JComboBox(CompatibilityInterface.BACKGROUND_OPTIONS);
      comboDisplayBackground_.setMaximumRowCount(2);
      comboDisplayBackground_.setSelectedItem(DaytimeNighttime.getBackgroundMode());
      comboDisplayBackground_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            changeBackground();
         }
      });

      startupScriptFile_ = new JTextField(ScriptPanel.getStartupScript());

      final JCheckBox closeOnExitCheckBox = new JCheckBox();
      closeOnExitCheckBox.setText("Close app when quitting MM");
      closeOnExitCheckBox.setSelected(getShouldCloseOnExit());
      closeOnExitCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            boolean shouldClose = closeOnExitCheckBox.isSelected();
            setShouldCloseOnExit(shouldClose);
            MMStudio.getFrame().setExitStrategy(shouldClose);
         }
      });

      final JCheckBox metadataFileWithMultipageTiffCheckBox = new JCheckBox();
      metadataFileWithMultipageTiffCheckBox.setText("Create metadata.txt file with Image Stack Files");
      metadataFileWithMultipageTiffCheckBox.setSelected(
            StorageMultipageTiff.getShouldGenerateMetadataFile());
      metadataFileWithMultipageTiffCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            StorageMultipageTiff.setShouldGenerateMetadataFile(metadataFileWithMultipageTiffCheckBox.isSelected());
         }
      });
      
      final JCheckBox separateFilesForPositionsMPTiffCheckBox = new JCheckBox();
      separateFilesForPositionsMPTiffCheckBox.setText("Save XY positions in separate Image Stack Files");
      separateFilesForPositionsMPTiffCheckBox.setSelected(
            StorageMultipageTiff.getShouldSplitPositions());
      separateFilesForPositionsMPTiffCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            StorageMultipageTiff.setShouldSplitPositions(separateFilesForPositionsMPTiffCheckBox.isSelected());
         }
      });
  
      final JCheckBox syncExposureMainAndMDA = new JCheckBox();
      syncExposureMainAndMDA.setText("Sync exposure between Main and MDA windows");
      syncExposureMainAndMDA.setSelected(AcqControlDlg.getShouldSyncExposure());
      syncExposureMainAndMDA.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            AcqControlDlg.setShouldSyncExposure(syncExposureMainAndMDA.isSelected());
         }
      });
  
      final JCheckBox hideMDAdisplay = new JCheckBox();
      hideMDAdisplay.setText("Hide MDA display");
      hideMDAdisplay.setSelected(AcqControlDlg.getShouldHideMDADisplay());
      hideMDAdisplay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent arg0) {
            AcqControlDlg.setShouldHideMDADisplay(hideMDAdisplay.isSelected());
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

      add(new JLabel("Display Background:"), "split 2, gapright push");
      add(comboDisplayBackground_, "wrap");

      add(new JSeparator(), "wrap");

      add(new JLabel("Sequence Buffer Size:"), "split 3, gapright push");
      add(bufSizeField_, "gapright related");
      add(new JLabel("MB"), "wrap");

      add(new JSeparator(), "wrap");

      add(metadataFileWithMultipageTiffCheckBox, "wrap");
      add(separateFilesForPositionsMPTiffCheckBox, "wrap");

      add(new JSeparator(), "wrap");

      add(askForConfigFileCheckBox, "wrap");
      add(alwaysUseDefaultProfileCheckBox, "wrap");

      add(new JLabel("Startup Script:"), "split 2, grow 0, gapright related");
      add(startupScriptFile_, "wrap");

      add(closeOnExitCheckBox, "wrap");

      add(new JSeparator(), "wrap");

      add(debugLogEnabledCheckBox, "wrap");

      add(deleteLogCheckBox, "split 3, gapright related");
      add(logDeleteDaysField_, "gapright related");
      add(new JLabel("days"), "gapright push, wrap");

      add(deleteLogFilesButton,
            "split 3, gapleft push, gapright push, wrap");

      add(new JSeparator(), "wrap");

      add(syncExposureMainAndMDA, "wrap");
      add(hideMDAdisplay, "wrap");

      add(new JSeparator(), "wrap");

      add(clearPreferencesButton,
            "split 2, sizegroup bottomBtns, gapright unrelated");
      add(closeButton, "sizegroup bottomBtns");

      pack();
   }

   private void changeBackground() {
      String background = (String) comboDisplayBackground_.getSelectedItem();

      parent_.compat().setBackgroundStyle(background);
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
      catch (ParseException ex) {
         ReportingUtils.showError(ex);
         return;
      }

      MMStudio.setCircularBufferSize(seqBufSize);
      MMStudio.setCoreLogLifetimeDays(deleteLogDays);

      ScriptPanel.setStartupScript(startupScriptFile_.getText());
      parent_.compat().makeActive();
      dispose();
   }

   public static boolean getIsDebugLogEnabled() {
      return DefaultUserProfile.getInstance().getBoolean(OptionsDlg.class,
            IS_DEBUG_LOG_ENABLED, false);
   }

   public static void setIsDebugLogEnabled(boolean isEnabled) {
      DefaultUserProfile.getInstance().setBoolean(OptionsDlg.class,
            IS_DEBUG_LOG_ENABLED, isEnabled);
   }

   public static boolean getShouldCloseOnExit() {
      return DefaultUserProfile.getInstance().getBoolean(OptionsDlg.class,
            SHOULD_CLOSE_ON_EXIT, true);
   }

   public static void setShouldCloseOnExit(boolean shouldClose) {
      DefaultUserProfile.getInstance().setBoolean(OptionsDlg.class,
            SHOULD_CLOSE_ON_EXIT, shouldClose);
   }
}
