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
import org.micromanager.ApplicationSkin.SkinMode;
import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.StartupSettings;
import org.micromanager.internal.logging.LogFileManager;
import org.micromanager.internal.script.ScriptPanel;
import org.micromanager.internal.utils.MMDialog;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.UIMonitor;
import org.micromanager.internal.zmq.ZMQServer;

/**
 * Options dialog for MMStudio.
 *
 */
public final class OptionsDlg extends MMDialog {
   private static final long serialVersionUID = 1L;
   private static final String IS_DEBUG_LOG_ENABLED = "is debug logging enabled";
   private static final String SHOULD_CLOSE_ON_EXIT = "should close the entire program when the Micro-Manager plugin is closed";

   private final JTextField startupScriptFile_;
   private final JTextField bufSizeField_;
   private JTextField logDeleteDaysField_;
   private final JComboBox comboDisplayBackground_;

   private CMMCore core_;
   private MMStudio mmStudio_;
   private final UserProfile profile_;

   /**
    * Create the dialog
    * @param core - The Micro-Manager Core object
    * @param mmStudio - MMStudio object (including Studio implementation) 
    */
   public OptionsDlg(CMMCore core, MMStudio mmStudio) {
      super("global micro-manager options");
      mmStudio_ = mmStudio;
      core_ = core;

      profile_ = mmStudio.profile();
      final StartupSettings startupSettings = StartupSettings.create(profile_);

      super.setResizable(false);
      super.setModal(true);
      super.setAlwaysOnTop(true);
      super.setTitle("Micro-Manager Options");

      super.loadAndRestorePosition(100, 100);

      super.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      super.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent e) {
            closeRequested();
         }
      });

      final JCheckBox debugLogEnabledCheckBox = new JCheckBox();
      debugLogEnabledCheckBox.setText("Enable debug logging");
      debugLogEnabledCheckBox.setToolTipText("Enable verbose logging for troubleshooting and debugging");
      debugLogEnabledCheckBox.setSelected(getIsDebugLogEnabled(mmStudio_));
      debugLogEnabledCheckBox.addActionListener((final ActionEvent e) -> {
         boolean isEnabled = debugLogEnabledCheckBox.isSelected();
         setIsDebugLogEnabled(mmStudio_, isEnabled);
         core_.enableDebugLog(isEnabled);
         UIMonitor.enable(isEnabled);
      });

      final JCheckBox askForConfigFileCheckBox = new JCheckBox();
      final JCheckBox alwaysUseDefaultProfileCheckBox = new JCheckBox(
              "Always use the default user profile");
      alwaysUseDefaultProfileCheckBox.setToolTipText(
              "Always use the default user profile; no prompt will be displayed to select a profile at startup.");
      alwaysUseDefaultProfileCheckBox.setSelected(
              startupSettings.shouldSkipProfileSelectionAtStartup());
      alwaysUseDefaultProfileCheckBox.addActionListener((ActionEvent e) -> {
         boolean checked = alwaysUseDefaultProfileCheckBox.isSelected();
         startupSettings.setSkipProfileSelectionAtStartup(checked);
         askForConfigFileCheckBox.setEnabled(checked);
         if (checked) {
            startupSettings.setSkipConfigSelectionAtStartup(
                    askForConfigFileCheckBox.isSelected());
         } else {
            startupSettings.setSkipConfigSelectionAtStartup(false);
         }
      });

      // Slaving the "use default profile" setting.  
      // There is no logic in the splashcreen to do anything useful when only one
      // of these two is selected
      askForConfigFileCheckBox.setText("Ask for config file at startup");
      askForConfigFileCheckBox.setSelected(!startupSettings.shouldSkipConfigSelectionAtStartup());
      askForConfigFileCheckBox.addActionListener((ActionEvent arg0) -> {
         startupSettings.setSkipConfigSelectionAtStartup(
                 !askForConfigFileCheckBox.isSelected());
      });
      askForConfigFileCheckBox.setSelected(
              !startupSettings.shouldSkipConfigSelectionAtStartup());
      askForConfigFileCheckBox.setEnabled(alwaysUseDefaultProfileCheckBox.isSelected());

      final JCheckBox deleteLogCheckBox = new JCheckBox();
      deleteLogCheckBox.setText("Delete log files after");
      deleteLogCheckBox.setSelected(mmStudio_.getShouldDeleteOldCoreLogs());
      deleteLogCheckBox.addActionListener((ActionEvent e) -> {
         mmStudio_.setShouldDeleteOldCoreLogs(deleteLogCheckBox.isSelected());
      });

      logDeleteDaysField_ =
         new JTextField(Integer.toString(mmStudio_.getCoreLogLifetimeDays()), 2);

      final JButton deleteLogFilesButton = new JButton();
      deleteLogFilesButton.setText("Delete Log Files Now");
      deleteLogFilesButton.setToolTipText("Delete all CoreLog files except " +
            "for the current one");
      deleteLogFilesButton.addActionListener((final ActionEvent e) -> {
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
      });

      final JButton clearPreferencesButton = new JButton();
      clearPreferencesButton.setText("Reset Preferences");
      clearPreferencesButton.setToolTipText("Clear all preference settings and restore defaults");
      clearPreferencesButton.addActionListener((final ActionEvent e) -> {
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
         boolean haveRegistered = RegistrationDlg.getHaveRegistered(mmStudio_);
         profile_.clearSettingsForAllClasses();
         RegistrationDlg.setHaveRegistered(mmStudio_, haveRegistered);
         // Rather than updating all the GUI elements, let's just close
         // the dialog.
         dispose();
      });

      bufSizeField_ = new JTextField(
            Integer.toString(mmStudio_.getCircularBufferSize()), 5);

      String[] options = new String[SkinMode.values().length];
      for (int i = 0; i < SkinMode.values().length; ++i) {
         options[i] = SkinMode.values()[i].getDesc();
      }
      comboDisplayBackground_ = new JComboBox(options);
      comboDisplayBackground_.setMaximumRowCount(2);
      comboDisplayBackground_.setSelectedItem(mmStudio_.app().skin().getSkin().getDesc());
      comboDisplayBackground_.addActionListener((ActionEvent e) -> {
         changeBackground();
      });

      startupScriptFile_ = new JTextField(ScriptPanel.getStartupScript(mmStudio_));

      final JCheckBox closeOnExitCheckBox = new JCheckBox();
      closeOnExitCheckBox.setText("Close app when quitting MM");
      closeOnExitCheckBox.setSelected(getShouldCloseOnExit(mmStudio_));
      closeOnExitCheckBox.addActionListener((ActionEvent arg0) -> {
         boolean shouldClose = closeOnExitCheckBox.isSelected();
         setShouldCloseOnExit(mmStudio_, shouldClose);
         MMStudio.getFrame().setExitStrategy(shouldClose);
      });

      final JCheckBox metadataFileWithMultipageTiffCheckBox = new JCheckBox();
      metadataFileWithMultipageTiffCheckBox.setText("Create metadata.txt file with Image Stack Files");
      metadataFileWithMultipageTiffCheckBox.setSelected(
            StorageMultipageTiff.getShouldGenerateMetadataFile());
      metadataFileWithMultipageTiffCheckBox.addActionListener((ActionEvent arg0) -> {
         StorageMultipageTiff.setShouldGenerateMetadataFile(metadataFileWithMultipageTiffCheckBox.isSelected());
      });
      
      final JCheckBox separateFilesForPositionsMPTiffCheckBox = new JCheckBox();
      separateFilesForPositionsMPTiffCheckBox.setText("Save XY positions in separate Image Stack Files");
      separateFilesForPositionsMPTiffCheckBox.setSelected(
            StorageMultipageTiff.getShouldSplitPositions());
      separateFilesForPositionsMPTiffCheckBox.addActionListener((ActionEvent arg0) -> {
         StorageMultipageTiff.setShouldSplitPositions(separateFilesForPositionsMPTiffCheckBox.isSelected());
      });
  
      final JCheckBox syncExposureMainAndMDA = new JCheckBox();
      syncExposureMainAndMDA.setText("Sync exposure between Main and MDA windows");
      syncExposureMainAndMDA.setSelected(AcqControlDlg.getShouldSyncExposure());
      syncExposureMainAndMDA.addActionListener((ActionEvent arg0) -> {
         AcqControlDlg.setShouldSyncExposure(syncExposureMainAndMDA.isSelected());
      });
  
      final JCheckBox hideMDAdisplay = new JCheckBox();
      hideMDAdisplay.setText("Hide MDA display");
      hideMDAdisplay.setSelected(AcqControlDlg.getShouldHideMDADisplay());
      hideMDAdisplay.addActionListener((ActionEvent arg0) -> {
         AcqControlDlg.setShouldHideMDADisplay(hideMDAdisplay.isSelected());
      });
      
      final JCheckBox runServer = new JCheckBox();
      runServer.setText("Run server on port " + ZMQServer.DEFAULT_PORT_NUMBER);
      runServer.setSelected(mmStudio.getShouldRunZMQServer());
      runServer.addActionListener((ActionEvent arg0) ->  {
         if (runServer.isSelected()) {
            mmStudio_.runZMQServer();
         } else {
            mmStudio_.stopZMQServer();
         }
         mmStudio_.setShouldRunZMQServer(runServer.isSelected());         
      });

      final JButton closeButton = new JButton();
      closeButton.setText("Close");
      closeButton.addActionListener((final ActionEvent ev) -> {
         closeRequested();
      });
  


      super.setLayout(new net.miginfocom.swing.MigLayout(
               "fill, insets dialog",
               "[fill]"));

      super.add(new JLabel("Display Background:"), "split 2, gapright push");
      super.add(comboDisplayBackground_, "wrap");

      super.add(new JSeparator(), "wrap");

      super.add(new JLabel("Sequence Buffer Size:"), "split 3, gapright push");
      super.add(bufSizeField_, "gapright related");
      super.add(new JLabel("MB"), "wrap");

      super.add(new JSeparator(), "wrap");

      super.add(metadataFileWithMultipageTiffCheckBox, "wrap");
      super.add(separateFilesForPositionsMPTiffCheckBox, "wrap");

      super.add(new JSeparator(), "wrap");

      if (mmStudio_.profileAdmin().getUUIDOfCurrentProfile() ==
              mmStudio_.profileAdmin().getUUIDOfDefaultProfile()) {
         super.add(alwaysUseDefaultProfileCheckBox, "wrap");
         super.add(askForConfigFileCheckBox, "wrap");
      }
      
      super.add(new JSeparator(), "wrap");

      super.add(new JLabel("Startup Script:"), "split 2, grow 0, gapright related");
      super.add(startupScriptFile_, "wrap");

      super.add(closeOnExitCheckBox, "wrap");

      super.add(new JSeparator(), "wrap");

      super.add(debugLogEnabledCheckBox, "wrap");

      super.add(deleteLogCheckBox, "split 3, gapright related");
      super.add(logDeleteDaysField_, "gapright related");
      super.add(new JLabel("days"), "gapright push, wrap");

      super.add(deleteLogFilesButton,
            "split 3, gapleft push, gapright push, wrap");

      super.add(new JSeparator(), "wrap");

      super.add(syncExposureMainAndMDA, "wrap");
      super.add(hideMDAdisplay, "wrap");
      super.add(runServer, "wrap");

      super.add(new JSeparator(), "wrap");

      super.add(clearPreferencesButton,
            "split 2, sizegroup bottomBtns, gapright unrelated");
      super.add(closeButton, "sizegroup bottomBtns");

      super.pack();
   }

   private void changeBackground() {
      String background = (String) comboDisplayBackground_.getSelectedItem();

      mmStudio_.app().skin().setSkin(SkinMode.fromString(background));
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

      mmStudio_.setCircularBufferSize(seqBufSize);
      mmStudio_.setCoreLogLifetimeDays(deleteLogDays);

      ScriptPanel.setStartupScript(mmStudio_, startupScriptFile_.getText());
      mmStudio_.app().makeActive();
      dispose();
   }

   public static boolean getIsDebugLogEnabled(Studio studio) {
      return studio.profile().getSettings(OptionsDlg.class).getBoolean(
            IS_DEBUG_LOG_ENABLED, false);
   }

   public static void setIsDebugLogEnabled(Studio studio, boolean isEnabled) {
      studio.profile().getSettings(OptionsDlg.class).putBoolean(
            IS_DEBUG_LOG_ENABLED, isEnabled);
   }

   public static boolean getShouldCloseOnExit(Studio studio) {
      return studio.profile().getSettings(OptionsDlg.class).getBoolean(
            SHOULD_CLOSE_ON_EXIT, true);
   }

   public static void setShouldCloseOnExit(Studio studio, boolean shouldClose) {
      studio.profile().getSettings(OptionsDlg.class).putBoolean(
            SHOULD_CLOSE_ON_EXIT, shouldClose);
   }
}
