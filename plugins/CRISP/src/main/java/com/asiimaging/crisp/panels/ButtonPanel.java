/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2024, Applied Scientific Instrumentation
 */

package com.asiimaging.crisp.panels;

import com.asiimaging.devices.crisp.CRISP;
import com.asiimaging.devices.crisp.CRISPTimer;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.Panel;
import java.util.Objects;

/**
 * The ui panel that has buttons for calibrating CRISP.
 */
public class ButtonPanel extends Panel {

   private Button btnLogCal;
   private Button btnDither;
   private Button btnSetGain;
   private Button btnReset;
   private Button btnSave;
   private Button btnLock;
   private Button btnUnlock;

   private final CRISP crisp;
   private final CRISPTimer timer;
   private final SpinnerPanel panel;

   public ButtonPanel(final CRISP crisp, final CRISPTimer timer, final SpinnerPanel panel) {
      this.crisp = Objects.requireNonNull(crisp);
      this.timer = Objects.requireNonNull(timer);
      this.panel = Objects.requireNonNull(panel);
      createUserInterface();
      createEventHandlers();
   }

   private void createUserInterface() {
      // CRISP control buttons
      Button.setDefaultSize(120, 30);
      btnLogCal = new Button("1: Log Cal");
      btnDither = new Button("2: Dither");
      btnSetGain = new Button("3: Set Gain");
      btnReset = new Button("Reset Offset");
      btnSave = new Button("Save Settings");

      Button.setDefaultSize(120, 60);
      btnLock = new Button("Lock");
      btnUnlock = new Button("Unlock");
      btnLock.setBoldFont(14);
      btnUnlock.setBoldFont(14);

      // set tooltips
      btnLogCal.setToolTipText("<html>Step 1 in the calibration routine.<br>"
              + "Property: <b>CRISP State</b><br>Property Value: <b>loG_cal</b><br>"
              + "Serial command: <b>LK F=72</b></html>");
      btnDither.setToolTipText("<html>Step 2 in the calibration routine.<br>"
              + "Property: <b>CRISP State</b><br>Property Value: <b>Dither</b><br>"
              + "Serial command: <b>LK F=102</b></html>");
      btnSetGain.setToolTipText("<html>Step 3 in the calibration routine.<br>"
              + "Property: <b>CRISP State</b><br>Property Value: <b>gain_Cal</b><br>"
              + "Serial command: <b>LK F=67</b></html>");
      btnReset.setToolTipText("<html>Reset the focus offset.<br>"
              + "Property: <b>CRISP State</b><br>Value: <b>Reset Focus Offset</b><br>"
              + "Serial command: <b>LK F=111</b></html>");
      btnSave.setToolTipText("<html>Save settings to the controller.<br>"
              + "Property: <b>CRISP State</b><br>Value: <b>Save to Controller</b><br>"
              + "Serial command: <b>SS Z</b></html>");

      btnLock.setToolTipText("<html>Focus lock.<br>"
              + "Property: <b>CRISP State</b><br>Property Value: <b>Lock</b><br>"
              + "Serial command: <b>LK F=83</b></html>");
      btnUnlock.setToolTipText("<html>Unlock focus.<br>"
              + "Property: <b>CRISP State</b><br>Property Value: <b>Unlock</b><br>"
              + "Serial command: <b>UL</b></html>");

      // add components to panel
      add(btnLogCal, "wrap");
      add(btnDither, "wrap");
      add(btnSetGain, "wrap");
      add(btnReset, "wrap");
      add(btnSave, "wrap");
      add(btnLock, "gaptop 100, wrap");
      add(btnUnlock, "");
   }

   /**
    * Enable or disable the calibration routine buttons.
    *
    * @param state true to enable components
    */
   public void setCalibrationButtonStates(final boolean state) {
      btnLogCal.setEnabled(state);
      btnDither.setEnabled(state);
      btnSetGain.setEnabled(state);
   }

   /**
    * Creates the event handlers for Button objects.
    */
   private void createEventHandlers() {

      // step 1 in the calibration routine
      btnLogCal.registerListener(event ->
              crisp.setStateLogCal(timer));

      // step 2 in the calibration routine
      btnDither.registerListener(event ->
              crisp.setStateDither());

      // step 3 in the calibration routine
      btnSetGain.registerListener(event ->
              crisp.setStateGainCal());

      // reset the focus offset to zero for the present position
      btnReset.registerListener(event ->
              crisp.setResetOffsets());

      // locks the focal position
      btnLock.registerListener(event -> {
         crisp.lock();
         panel.setEnabledFocusLockSpinners(false);
         setCalibrationButtonStates(false);
      });

      // unlocks the focal position
      btnUnlock.registerListener(event -> {
         crisp.unlock();
         panel.setEnabledFocusLockSpinners(true);
         setCalibrationButtonStates(true);
      });

      // saves all CRISP related settings
      btnSave.registerListener(event -> crisp.save());
   }
}
