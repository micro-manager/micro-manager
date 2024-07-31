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
import java.util.Objects;
import javax.swing.JLabel;

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

   private final PLogicControlModel model_;
   private final TabPanel tab_;

   public DeviceTab(final PLogicControlModel model, final TabPanel tab) {
      model_ = Objects.requireNonNull(model);
      tab_ = Objects.requireNonNull(tab);
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
      final int numDevices = model_.getPLogicDevices().length;
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
      cmbSelectDevice_ = new ComboBox(
            devices,
            devices[0],
            130, 24);

      // trigger source
      final String[] triggerSources = ASIPLogic.TriggerSource.toArray();
      cmbTriggerSource_ = new ComboBox(
            triggerSources,
            triggerSources[0],
            130, 24);

      // check boxes
      cbxEditCellUpdateAuto_ = new CheckBox("Update Cells Automatically", true);
      cbxRefreshProperties_ = new CheckBox("Refresh Property Values", false);

      // buttons
      btnSaveSettings_ = new Button("Save Settings", 120, 30);
      btnClearLogicCells_ = new Button("Clear Logic Cells", 120, 30);
      btnClearCellStates_ = new Button("Clear Cell States", 120, 30);
      btnOpenManual_ = new Button("Open Manual...", 120, 30);

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

      // update ui with values from the controller
      refreshUserInterface();

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
      add(btnOpenManual_, "");
   }

   /**
    * Create the event handlers.
    */
   private void createEventHandlers() {
      // combo boxes
      cmbSelectDevice_.registerListener(e -> {
         final String selected = cmbSelectDevice_.getSelected();
         final String deviceName = model_.plc().deviceName();
         if (deviceName.equals(selected)) {
            requestFocusInWindow();
            // TODO: fix focus issue, need to click buttons twice
            final boolean clickedYes = DialogUtils.showConfirmDialog(cmbSelectDevice_,
                  "Reload Settings", "Would you like to reload the cells for "
                        + deviceName + "?");
            if (!clickedYes) {
               return; // early exit => clicked "No" to avoid reloading the cells
            }
         }
         tab_.stopUpdateCells(); // stop current update to update cells from new card
         // compare the number of cells, so we can change the ui if necessary
         final int numCellsA = model_.plc().numCells();
         model_.plc().deviceName(selected);
         final int numCellsB = model_.plc().numCells();
         // update the logic cells tab based on the number of cells
         // only if the number of cells has changed, PLOGIC_16 => PLOGIC_24
         if (numCellsA != numCellsB) {
            tab_.getLogicCellsTab().refreshUserInterface();
            tab_.updateFrame();
         }
         refreshUserInterface(); // update this tab
         tab_.updateCells(); // update logic and I/O cells
      });

      cmbTriggerSource_.registerListener(e ->
            model_.plc().triggerSource(cmbTriggerSource_.getSelected()));

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

      // buttons
      btnClearLogicCells_.registerListener(
            e -> model_.plc().preset(ASIPLogic.Preset.ALL_CELLS_ZERO));
      btnClearCellStates_.registerListener(e -> model_.plc().clearAllCellStates());
      btnSaveSettings_.registerListener(e -> model_.plc().saveSettings());

      btnOpenManual_.registerListener(e -> {
         final boolean result = DialogUtils.showConfirmDialog(btnOpenManual_,
               "Open Browser", "Navigate to the Tiger Programmable Logic Card Manual?");
         if (result) {
            BrowserUtils.openWebsite(model_.studio(),
                  "https://asiimaging.com/docs/tiger_programmable_logic_card");
         }
      });
   }

   /**
    * Updates the user interface with values from the controller.
    */
   public void refreshUserInterface() {
      final ASIPLogic plc = model_.plc();

      // get values from controller
      final int numCells = plc.numCells();
      final double version = plc.firmwareVersion();
      final String build = plc.firmwareBuildName();
      final String compileDate = plc.firmwareCompileDate();
      final ASIPLogic.ShutterMode plcMode = plc.shutterMode();
      final String axisLetter = plc.axisLetter();

      // update labels
      lblFirmwareVersion_.setText("Version: " + version);
      lblFirmwareBuild_.setText("Build Name: " + build);
      lblFirmwareDate_.setText("Compile Date: " + compileDate);
      lblPLogicMode_.setText("PLogic Mode: " + plcMode + " (pre-init)");
      lblNumCells_.setText("Number of Cells: " + numCells);
      lblAxisLetter_.setText("Axis Letter: " + axisLetter);

      cmbTriggerSource_.setSelected(model_.plc().triggerSource().toString());
      cbxEditCellUpdateAuto_.setSelected(model_.plc().isAutoUpdateCellsOn());
      cbxRefreshProperties_.setSelected(model_.plc().isRefreshPropertyValuesOn());
   }

   public CheckBox getEditCellUpdatesCheckBox() {
      return cbxEditCellUpdateAuto_;
   }

}
