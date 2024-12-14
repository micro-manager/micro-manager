/*
 * Project: ASI PLogic Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2024, Applied Scientific Instrumentation
 */

package com.asiimaging.plogic.ui.tabs;

import com.asiimaging.plogic.PLogicControlModel;
import com.asiimaging.plogic.model.devices.ASIPLogic;
import com.asiimaging.plogic.ui.asigui.Button;
import com.asiimaging.plogic.ui.asigui.CheckBox;
import com.asiimaging.plogic.ui.asigui.ComboBox;
import com.asiimaging.plogic.ui.asigui.Panel;
import com.asiimaging.plogic.ui.utils.BrowserUtils;
import com.asiimaging.plogic.ui.utils.DialogUtils;
import com.asiimaging.plogic.ui.utils.FileUtils;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import org.micromanager.internal.utils.FileDialogs;

// TODO: prevent user from changing TriggerSource and RefreshPropertyUpdates during updates
public class DeviceTab extends Panel {

   private JLabel lblAxisLetter_;
   private JLabel lblFirmwareVersion_;
   private JLabel lblFirmwareBuild_;
   private JLabel lblFirmwareDate_;
   private JLabel lblPLogicMode_;
   private JLabel lblNumCells_;

   private ComboBox cmbSelectDevice_;
   private ComboBox cmbTriggerSource_;

   private CheckBox cbxEditCellUpdateAuto_;
   private CheckBox cbxRefreshProperties_;

   private Button btnSaveSettings_;
   private Button btnClearLogicCells_;
   private Button btnClearCellStates_;
   private Button btnOpenManual_;

   private Button btnSaveJson_;
   private Button btnLoadJson_;

   private Button btnRefresh_;

   private final TabPanel tab_;
   private final PLogicControlModel model_;

   private final FileDialogs.FileType jsonFileType;

   public DeviceTab(final PLogicControlModel model, final TabPanel tab) {
      model_ = Objects.requireNonNull(model);
      tab_ = Objects.requireNonNull(tab);
      jsonFileType = new FileDialogs.FileType(
            "PLogic Settings",
            "PLogic Settings (.json)",
            "plogic_settings",
            false,
            "json"
      );
      createUserInterface();
      createEventHandlers();
   }

   /**
    * Create the user interface.
    */
   private void createUserInterface() {
      setMigLayout(
            "insets 10 10 10 10",
            "[]10[]",
            "[]5[]"
      );

      // number of PLogic devices
      final int numDevices = model_.getNumDevices();
      final JLabel lblNumDevices = new JLabel("Number of Devices: " + numDevices);

      // init objects here - update text later
      lblFirmwareVersion_ = new JLabel("");
      lblFirmwareBuild_ = new JLabel("");
      lblFirmwareDate_ = new JLabel("");
      lblPLogicMode_ = new JLabel("");
      lblAxisLetter_ = new JLabel("");
      lblNumCells_ = new JLabel("");

      final JLabel lblSelectDevice = new JLabel("PLogic Device:");
      final JLabel lblTriggerSource = new JLabel("Trigger Source:");

      // device selection
      final String[] devices = model_.getPLogicDevices();
      cmbSelectDevice_ = new ComboBox(devices, devices[0], 130, 24);

      // trigger source
      final String[] triggerSources = ASIPLogic.TriggerSource.toArray();
      cmbTriggerSource_ = new ComboBox(triggerSources, triggerSources[0], 130, 24);

      // check boxes
      cbxEditCellUpdateAuto_ = new CheckBox("Update Cells Automatically", true);
      cbxRefreshProperties_ = new CheckBox("Refresh Property Values", false);

      // buttons
      btnSaveSettings_ = new Button("Save Settings", 120, 30);
      btnClearLogicCells_ = new Button("Clear Logic Cells", 120, 30);
      btnClearCellStates_ = new Button("Clear Cell States", 120, 30);
      btnOpenManual_ = new Button("Open Manual...", 120, 30);

      btnSaveJson_ = new Button("Save To Json", 120, 30);
      btnLoadJson_ = new Button("Load From Json", 120, 30);

      btnRefresh_ = new Button("Refresh Cells", 120, 30);

      // tooltips
      cmbSelectDevice_.setToolTipText("<html>Select the current PLogic device.<br>"
            + "This will query the controller and update the tabs.</html>");
      cmbTriggerSource_.setToolTipText("<html>Set the <b>" + ASIPLogic.Properties.TRIGGER_SOURCE
            + "</b> property to the selected item.</html>");
      cbxRefreshProperties_.setToolTipText("<html>Set the <b>"
            + ASIPLogic.Properties.EDIT_CELL_UPDATE_AUTO + "</b> property to <b>"
            + ASIPLogic.Values.YES + "</b> or <b>" + ASIPLogic.Values.NO + "</b>.</html>");
      cbxRefreshProperties_.setToolTipText("<html>Set the <b>"
            + ASIPLogic.Properties.REFRESH_PROPERTY_VALUES + "</b> property to <b>"
            + ASIPLogic.Values.YES + "</b> or <b>" + ASIPLogic.Values.NO + "</b>.</html>");
      btnSaveSettings_.setToolTipText("<html>Set the <b>" + ASIPLogic.Properties.SAVE_CARD_SETTINGS
            + "</b> property to <b>" + ASIPLogic.SaveSettings.Z + "</b>.</html>");
      btnClearLogicCells_.setToolTipText("<html>Set the <b>" + ASIPLogic.Properties.SET_CARD_PRESET
            + "</b> property to <b>" + ASIPLogic.Preset.ALL_CELLS_ZERO + "</b>.</html>");
      btnClearCellStates_.setToolTipText("<html>Set the <b>"
            + ASIPLogic.Properties.CLEAR_ALL_CELL_STATES + "</b> property to <b>"
            + ASIPLogic.Values.DO_IT + "</b>.</html>");

      btnOpenManual_.setToolTipText(
            "Open the default browser and navigate to the online manual.");
      btnSaveJson_.setToolTipText(
            "Save the Physical I/O and Logic Cells of the selected PLogic device to JSON.");
      btnLoadJson_.setToolTipText(
            "Load settings from a JSON files and send serial commands to the controller.");
      btnRefresh_.setToolTipText("Send serial commands to the controller to update the "
            + "Logic Cells and Physical I/O tabs.");

      // update ui with values from the controller
      updateTabFromController();

      final Panel selectPanel = new Panel();
      selectPanel.add(lblNumDevices, "span 2, wrap");
      selectPanel.add(lblSelectDevice, "");
      selectPanel.add(cmbSelectDevice_, "wrap");
      selectPanel.add(lblTriggerSource, "");
      selectPanel.add(cmbTriggerSource_, "wrap");

      final Panel devicePanel = new Panel("Device");
      devicePanel.add(lblAxisLetter_, "wrap");
      devicePanel.add(lblNumCells_, "wrap");
      devicePanel.add(lblPLogicMode_, "wrap");

      final Panel firmwarePanel = new Panel("Firmware");
      firmwarePanel.add(lblFirmwareVersion_, "wrap");
      firmwarePanel.add(lblFirmwareBuild_, "wrap");
      firmwarePanel.add(lblFirmwareDate_, "wrap");

      add(selectPanel, "wrap");
      add(devicePanel, "growx, wrap");
      add(firmwarePanel, "growx, wrap");
      add(cbxEditCellUpdateAuto_, "wrap");
      add(cbxRefreshProperties_, "wrap");
      add(btnClearLogicCells_, "split 2");
      add(btnClearCellStates_, "wrap");
      add(btnSaveSettings_, "split 2");
      add(btnOpenManual_, "wrap");

      add(new JLabel("Save or load settings file:"), "wrap");
      add(btnSaveJson_, "split 2");
      add(btnLoadJson_, "wrap");
      add(new JLabel("Refresh the Logic Cells and Physical I/O tabs:"), "wrap");
      add(btnRefresh_, "");
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {
      // select the PLogic device
      cmbSelectDevice_.registerListener(e -> selectDevice());

      // select the trigger source
      cmbTriggerSource_.registerListener(e ->
            model_.plc().triggerSource(
                  ASIPLogic.TriggerSource.fromString(cmbTriggerSource_.getSelected())));

      // edit cell updates automatically
      cbxEditCellUpdateAuto_.registerListener(e -> {
         if (!cbxEditCellUpdateAuto_.isSelected()) {
            DialogUtils.showMessage(cbxEditCellUpdateAuto_, "Information",
                  "This should always be checked to update the cells correctly.");
         } else {
            model_.plc().isAutoUpdateCellsOn(true);
         }
      });

      // refresh properties
      cbxRefreshProperties_.registerListener(e ->
            model_.plc().isRefreshPropertyValuesOn(cbxRefreshProperties_.isSelected()));

      // clear all cells and ui
      btnClearLogicCells_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnClearLogicCells_,
               "Updating", "Wait for updates to finish.");
         } else {
            final boolean result = DialogUtils.showConfirmDialog(btnClearLogicCells_,
                  "Clear Cells", "Clear all logic cells?");
            if (result) {
               model_.plc().preset(ASIPLogic.Preset.ALL_CELLS_ZERO);
               tab_.getLogicCellsTab().clearLogicCellsFromButton();
            }
         }
      });

      btnClearCellStates_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnClearCellStates_,
                  "Updating", "Wait for updates to finish.");
         } else {
            model_.plc().clearAllCellStates();
         }
      });

      btnSaveSettings_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnSaveSettings_,
                  "Updating", "Wait for updates to finish.");
         } else {
            final boolean result = DialogUtils.showConfirmDialog(btnClearLogicCells_,
                  "Save Settings", "Save settings to the controller?");
            if (result) {
            model_.plc().saveSettings();
            }
         }
      });

      // open the default web browser
      btnOpenManual_.registerListener(e -> openBrowserToManual());

      // save and load json
      btnSaveJson_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnSaveJson_,
                  "Updating", "Wait for updates to finish.");
         } else {
            saveJsonFile();
         }
      });
      btnLoadJson_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnLoadJson_,
                  "Updating", "Wait for updates to finish.");
         } else {
            loadJsonFile();
         }
      });

      // update plc cells
      btnRefresh_.registerListener(e -> {
         if (model_.isUpdating()) {
            DialogUtils.showMessage(btnRefresh_, "Updating",
                  "Wait for updates to finish.");
         } else {
            tab_.updateTabsFromController();
         }
      });
   }

   /**
    * Select the device.
    */
   private void selectDevice() {
      // Note: invokeLater is used to prevent having to click the dialogs twice (focus related)
      EventQueue.invokeLater(() -> {
         // check if the device is already selected
         final String deviceName = model_.plc().deviceName();
         final String deviceSelected = cmbSelectDevice_.getSelected();
         // Note: we can use setSelected(deviceName) to come back
         // here and do nothing, useful if the user clicks No.
         if (deviceName.equals(deviceSelected)) {
            return; // early exit => device currently selected
         }

         // ask to stop updates from controller
         if (model_.isUpdating()) {
            final boolean clickedYes = DialogUtils.showConfirmDialog(cmbSelectDevice_,
                    "PLogic Device", "The controller is currently updating.\n" +
                            "Please wait for updates to finish.\nStop updates?");
            if (clickedYes) {
               model_.isUpdating(false);
               model_.studio().logs().logMessage("Stop Updates From Device Selection ComboBox");
            }
            cmbSelectDevice_.setSelected(deviceName);
            return; // early exit => stop updates
         }

         // ask to switch devices
         final boolean clickedYes = DialogUtils.showConfirmDialog(cmbSelectDevice_,
                 "PLogic Device", "Change device to " + deviceSelected + " and update the UI?");
         if (!clickedYes) {
            cmbSelectDevice_.setSelected(deviceName);
            return; // early exit => clicked No
         }

         // compare the number of cells, so we can change the ui if necessary
         final int prevIndex = model_.selectedIndex();
         final int nextIndex = cmbSelectDevice_.getSelectedIndex();
         model_.selectedIndex(nextIndex);
         tab_.getFrame().setDeviceLabel(cmbSelectDevice_.getSelected()); // update ui
         // update the logic cells tab based on the number of cells only
         // if the number of cells has changed, PLOGIC_16 => PLOGIC_24
         if (model_.numCellsEqual(prevIndex, nextIndex)) {
            tab_.getLogicCellsTab().refreshTab();
            tab_.packFrame();
         }
         // available cells types will match the selected firmware
         tab_.getLogicCellsTab().updateCellTypeComboBoxes();
         tab_.getWizardsTab().hideWizards();
         // update tabs by sending serial commands to the controller
         updateTabFromController();
         tab_.updateTabsFromController();
      });
   }

   /**
    * Updates the user interface with values from the controller.
    */
   private void  updateTabFromController() {
      final ASIPLogic plc = model_.plc();

      // get values from controller
      final int numCells = plc.numCells();
      final double version = plc.firmwareVersion();
      final String build = plc.firmwareBuildName();
      final String compileDate = plc.firmwareCompileDate();
      final ASIPLogic.ShutterMode plcMode = plc.shutterMode();
      final String axisLetter = plc.axisLetter();

      // format firmware version double to 3.50 from 3.5
      DecimalFormat df = new DecimalFormat("0.00");

      // update labels
      lblFirmwareVersion_.setText("Version: " + df.format(version));
      lblFirmwareBuild_.setText("Build Name: " + build);
      lblFirmwareDate_.setText("Compile Date: " + compileDate);
      lblPLogicMode_.setText("PLogic Mode: " + plcMode + " (pre-init)");
      lblNumCells_.setText("Number of Cells: " + numCells);
      lblAxisLetter_.setText("Axis Letter: " + axisLetter);

      cmbTriggerSource_.setSelected(plc.triggerSource().toString());
      cbxEditCellUpdateAuto_.setSelected(plc.isAutoUpdateCellsOn());
      cbxRefreshProperties_.setSelected(plc.isRefreshPropertyValuesOn());
   }

   /**
    * Opens a file browser to save the PLogic settings to a json file.
    */
   private void saveJsonFile() {
      // open a file browser
      final File file = FileDialogs.save(
            null,
            "Save the settings file...",
            jsonFileType
      );
      if (file == null) {
         return; // early exit => no selection
      }
      // convert PLogicState to json and save to file
      final String json = model_.plc().state().toPrettyJson();
      try {
         FileUtils.saveFile(json, file.toString(), "json");
      } catch (IOException ex) {
         model_.studio().logs().logError("could not save PLogic settings to json!");
      }
   }

   /**
    * Opens a file browser to load the PLogic settings from a json file.
    */
   private void loadJsonFile() {
      final File file = FileDialogs.openFile(
            null,
            "Load the settings file...",
            jsonFileType
      );
      if (file == null) {
         return; // early exit => no selection
      }
      // load the file and set PLogicState
      try {
         final String json = FileUtils.readFile(file.toString(), "json");
         // ask to overwrite settings
         final boolean result = DialogUtils.showConfirmDialog(btnLoadJson_,
               "Load Settings", "This will overwrite the current settings for "
                     + model_.plc().deviceName() + ", do you want to continue?");
         if (result) {
            loadJsonThread(json);
         }
      } catch (IOException ex) {
         model_.studio().logs().logError("could not load PLogic settings from json!");
      }
   }

   /**
    * Load the json into the plc state and then send serial commands
    * to update the controller. Update the UI with the new values.
    *
    * <p>Note: This happens on a separate thread.
    *
    * @param json the json as a {@code String}
    */
   private void loadJsonThread(final String json) {
      SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

         @Override
         protected Void doInBackground() {
            model_.studio().logs().logMessage("Finished Sending PLogic Program");
            model_.isUpdating(true);

            // set plc state from json
            model_.plc().state(json);

            // clear ui
            tab_.getLogicCellsTab().clearLogicCells();
            tab_.getIOCellsTab().clearIOCells();

            // send serial commands to plc (updates plc state)
            model_.plc().state().updateDevice(model_, tab_.getFrame());

            // update ui from plc state
            cmbTriggerSource_.setSelected(
                  model_.plc().state().triggerSource().toString());
            tab_.getLogicCellsTab().initLogicCells();
            tab_.getIOCellsTab().initIOCells();

            model_.isUpdating(false);
            model_.studio().logs().logMessage("Finished Sending PLogic Program");
            return null;
         }

         @Override
         protected void done() {
            // Note: need to do this to catch exceptions
            try {
               get();
            } catch (final InterruptedException ex) {
               throw new RuntimeException(ex);
            } catch (final ExecutionException ex) {
               throw new RuntimeException(ex.getCause());
            }
         }

      };

      worker.execute();
   }

   /**
    * Open a confirmation dialog and ask to navigate to the PLogic manual.
    */
   private void openBrowserToManual() {
      final boolean result = DialogUtils.showConfirmDialog(btnOpenManual_,
            "Open Browser", "Open the default browser and navigate to the "
                  + "Tiger Programmable Logic Card Manual?");
      if (result) {
         BrowserUtils.openWebsite(model_.studio(),
               "https://asiimaging.com/docs/tiger_programmable_logic_card");
      }
   }

   public CheckBox getEditCellUpdatesCheckBox() {
      return cbxEditCellUpdateAuto_;
   }

}
