/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */

package com.asiimaging.tirf.ui.panels.tabs;

import com.asiimaging.tirf.model.TIRFControlModel;
import com.asiimaging.tirf.model.data.Icons;
import com.asiimaging.tirf.model.devices.Scanner;
import com.asiimaging.tirf.ui.components.Button;
import com.asiimaging.tirf.ui.components.Label;
import com.asiimaging.tirf.ui.components.Panel;
import com.asiimaging.tirf.ui.components.Spinner;
import com.asiimaging.tirf.ui.components.ToggleButton;
import java.awt.Font;
import java.util.Objects;

public class ScannerTab extends Panel {

   private ToggleButton tglBeamEnabled;
   private ToggleButton tglFastCirclesState;

   private Spinner spnNumFastCircles;
   private Spinner spnFastCirclesRadius;
   private Spinner spnFastCirclesAsymmetry;

   private Spinner spnCalibrationImages;
   private Spinner spnCalibrationStart;
   private Spinner spnCalibrationIncrement;

   private Button btnCalibrate;
   private Button btnSetFastCirclesHz;
   private Button btnSaveToFlash;
   private Button btnSetupPLogic;

   private Spinner spnScannerX;
   private Spinner spnScannerY;

   private Label lblFastCirclesHz;

   private final TIRFControlModel model;

   public ScannerTab(final TIRFControlModel model) {
      this.model = Objects.requireNonNull(model);
      setMigLayout(
            "",
            "[]10[]",
            "[]10[]"
      );
      createUserInterface();
   }

   private void createUserInterface() {
      final Label lblTitle = new Label("Scanner Control", Font.BOLD, 20);
      final Label lblCalibration = new Label("Fast Circles Radius Calibration", Font.BOLD, 16);

      final Label lblNumFastCircles = new Label("Number of Circles:");
      final Label lblFastCirclesRadius = new Label("Fast Circles Radius (Degrees):");
      final Label lblFastCirclesAsymmetry = new Label("Fast Circles Asymmetry:");

      spnNumFastCircles = Spinner.createIntegerSpinner(model.getNumFastCircles(), 1, 10, 1);
      spnFastCirclesRadius =
            Spinner.createDoubleSpinner(model.getScanner().getFastCirclesRadius(), 0.0, 90.0,
                  0.01);
      spnFastCirclesAsymmetry =
            Spinner.createDoubleSpinner(model.getScanner().getFastCirclesAsymmetry(), 0.0, 2.0,
                  0.01);

      tglBeamEnabled =
            new ToggleButton("Beam On", "Beam Off", Icons.ARROW_RIGHT, Icons.CANCEL, 130, 30);
      tglFastCirclesState =
            new ToggleButton("Fast Circles On", "Fast Circles Off", Icons.ARROW_RIGHT, Icons.CANCEL,
                  130, 30);
      tglBeamEnabled.setState(model.getScanner().getBeamEnabled());
      tglFastCirclesState.setState(model.getScanner().getFastCirclesEnabled());

      btnSetFastCirclesHz = new Button("Set Fast Circles Hz", 130, 30);

      final Panel calibrationPanel = new Panel();
      calibrationPanel.setMigLayout(
            "",
            "[]30[]",
            ""
      );

      final Label lblNumCalibrationImages = new Label("Calibration Images:");
      final Label lblCalibrationStartRadius = new Label("Calibration Start:");
      final Label lblCalibrationRadiusIncrement = new Label("Calibration Increment:");
      spnCalibrationImages = Spinner.createIntegerSpinner(10, 1, Integer.MAX_VALUE, 1);
      spnCalibrationStart = Spinner.createDoubleSpinner(0.5, 0.0, Double.MAX_VALUE, 0.1);
      spnCalibrationIncrement =
            Spinner.createDoubleSpinner(0.01, Double.MIN_VALUE, Double.MAX_VALUE, 0.01);
      btnCalibrate = new Button("Run Calibration Acq", 130, 20);

      final Label lblScannerH = new Label("Scanner " + model.getScanner().getAxisX() + "°:");
      final Label lblScannerI = new Label("Scanner " + model.getScanner().getAxisY() + "°:");
      spnScannerX = Spinner.createDoubleSpinner(0.0, -4.0, 4.0, 20.0);
      spnScannerY = Spinner.createDoubleSpinner(0.0, -4.0, 4.0, 20.0);

      lblFastCirclesHz = new Label("Fast Circles Rate: 0 Hz");
      updateFastCirclesHzLabel(); // make sure it reflects current exposure

      btnSaveToFlash = new Button("Save Settings to Flash", 160, 22);
      btnSaveToFlash.setToolTipText("Send the SAVESET Z command to the scanner device.");

      btnSetupPLogic = new Button("Setup PLogic", 120, 22);
      btnSetupPLogic.setToolTipText(
            "Send the PLogic card the program to change the camera trigger rate.");

      // create action listeners
      createEventHandlers();

      calibrationPanel.add(lblNumCalibrationImages, "");
      calibrationPanel.add(spnCalibrationImages, "wrap");
      calibrationPanel.add(lblCalibrationStartRadius, "");
      calibrationPanel.add(spnCalibrationStart, "wrap");
      calibrationPanel.add(lblCalibrationRadiusIncrement, "");
      calibrationPanel.add(spnCalibrationIncrement, "wrap");
      calibrationPanel.add(btnCalibrate, "wrap");

      // add ui elements to the panel
      add(lblTitle, "span 2, wrap");
      add(lblFastCirclesAsymmetry, "");
      add(spnFastCirclesAsymmetry, "wrap");
      add(lblFastCirclesRadius, "");
      add(spnFastCirclesRadius, "wrap");
      add(lblNumFastCircles, "");
      add(spnNumFastCircles, "wrap");
      add(tglBeamEnabled, "");
      add(btnSetFastCirclesHz, "wrap");
      add(tglFastCirclesState, "");
      add(lblFastCirclesHz, "wrap");
      add(lblScannerH, "split 2");
      add(spnScannerX, "");
      add(lblScannerI, "split 2");
      add(spnScannerY, "wrap");
      add(lblCalibration, "span 4, wrap");
      add(calibrationPanel, "span 4, wrap");
      add(btnSaveToFlash, "");
      add(btnSetupPLogic, "");
   }

   public void setBeamEnabledState(final boolean state) {
      tglBeamEnabled.setState(state);
   }

   public void setFastCirclesState(final boolean state) {
      tglFastCirclesState.setState(state);
   }

   public void updateFastCirclesHzLabel() {
      lblFastCirclesHz.setText(
            "Fast Circles Rate: " + model.getScanner().getFastCirclesRateHz() + " Hz");
   }

   private void createEventHandlers() {
      // number of circles per camera exposure period
      spnNumFastCircles.registerListener(event -> {
         model.setNumFastCircles(spnNumFastCircles.getInt());
         if (model.getScanner().getFastCirclesEnabled()) {
            model.getScanner().setFastCirclesStateRestart();
         }
      });

      // correct scanner circle for asymmetry
      spnFastCirclesAsymmetry.registerListener(event -> {
         model.getScanner().setFastCirclesAsymmetry(spnFastCirclesAsymmetry.getDouble());
         if (model.getScanner().getFastCirclesEnabled()) {
            model.getScanner().setFastCirclesStateRestart();
         }
      });

      // the fast circles radius
      spnFastCirclesRadius.registerListener(event -> {
         model.getScanner().setFastCirclesRadius(spnFastCirclesRadius.getDouble());
         if (model.getScanner().getFastCirclesEnabled()) {
            model.getScanner().setFastCirclesStateRestart();
         }
      });

      // enable the scanner beam
      tglBeamEnabled.registerListener(event ->
            model.getScanner().setBeamEnabled(tglBeamEnabled.isSelected()));

      // enable the fast circles state
      tglFastCirclesState.registerListener(event -> {
         final String value = tglFastCirclesState.isSelected()
               ? Scanner.Values.FastCirclesState.ON : Scanner.Values.FastCirclesState.OFF;
         model.getScanner().setFastCirclesState(value);
      });

      // run the fast circles calibration routine
      btnCalibrate.registerListener(event -> {
         //System.out.println("Calibration Start...");
         model.calibrateFastCircles(
               spnCalibrationImages.getInt(),
               spnCalibrationStart.getDouble(),
               spnCalibrationIncrement.getDouble()
         );
      });

      // sets the fast circles rate based on exposure
      btnSetFastCirclesHz.registerListener(event -> {
         model.getScanner().setFastCirclesRateHz(model.computeFastCirclesHz());
         // set the "one shot (NRT)" config value (trigger time in milliseconds)
         if (model.getScanner().getFirmwareVersion() >= 3.51) {
            model.getPLogic().setPointerPosition(1); // logic cell 1
            model.getPLogic().setCellConfig(model.computeOneShotTiming());
         }
         if (model.getScanner().getFastCirclesEnabled()) {
            model.getScanner().setFastCirclesStateRestart();
         }
         updateFastCirclesHzLabel();
      });

      // move the scanner X axis
      spnScannerX.registerListener(event ->
            model.getScanner().moveX(spnScannerX.getDouble()));

      // move the scanner Y axis
      spnScannerY.registerListener(event ->
            model.getScanner().moveY(spnScannerY.getDouble()));

      // save settings the controller hardware
      btnSaveToFlash.registerListener(event ->
            model.getScanner().setSaveSettings());

      // send the PLC program to the PLogic card
      btnSetupPLogic.registerListener(event -> model.setupPLogic());
   }

   public Spinner getSpinnerScannerX() {
      return spnScannerX;
   }

   public Spinner getSpinnerScannerY() {
      return spnScannerY;
   }
}
