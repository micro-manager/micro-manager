/*
 * Project: ASI Ring TIRF Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2022, Applied Scientific Instrumentation
 */
package com.asiimaging.tirf.ui.panels.tabs;

import com.asiimaging.tirf.model.data.Icons;
import com.asiimaging.tirf.model.devices.Scanner;
import com.asiimaging.tirf.ui.TIRFControlFrame;
import com.asiimaging.tirf.model.TIRFControlModel;
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

    private Spinner spnScannerH;
    private Spinner spnScannerI;

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
        spnFastCirclesRadius = Spinner.createFloatSpinner(model.getScanner().getFastCirclesRadius(), 0.0f, 90.0f, 0.01f);
        spnFastCirclesAsymmetry = Spinner.createFloatSpinner(model.getScanner().getFastCirclesAsymmetry(), 0.0f, 2.0f, 0.01f);

        tglBeamEnabled = new ToggleButton("Beam On", "Beam Off", Icons.ARROW_RIGHT, Icons.CANCEL, 130, 30);
        tglFastCirclesState = new ToggleButton("Fast Circles On", "Fast Circles Off", Icons.ARROW_RIGHT, Icons.CANCEL, 130, 30);
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
        spnCalibrationStart = Spinner.createFloatSpinner(0.5f, 0.0f, Float.MAX_VALUE, 0.1f);
        spnCalibrationIncrement = Spinner.createFloatSpinner(0.01f, Float.MIN_VALUE, Float.MAX_VALUE, 0.01f);
        btnCalibrate = new Button("Calibrate System", 120, 20);

        final Label lblScannerH = new Label("Scanner H:");
        final Label lblScannerI = new Label("Scanner I:");
        spnScannerH = Spinner.createFloatSpinner(model.getScanner().getPositionH(), -4000.0f, 4000.0f, 20.0f);
        spnScannerI = Spinner.createFloatSpinner(model.getScanner().getPositionI(), -4000.0f, 4000.0f, 20.0f);

        lblFastCirclesHz = new Label("Fast Circles Rate: 0 Hz");
        updateFastCirclesHzLabel(); // make sure it reflects current exposure

        btnSaveToFlash = new Button("Save Settings to Flash", 160, 22);

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
        add(spnScannerH, "");
        add(lblScannerI, "split 2");
        add(spnScannerI, "wrap");
        add(lblCalibration, "span 4, wrap");
        add(calibrationPanel, "span 4, wrap");
        add(btnSaveToFlash, "");
    }

    public void setBeamEnabledState(final boolean state) {
        tglBeamEnabled.setState(state);
    }

    public void setFastCirclesState(final boolean state) {
        tglFastCirclesState.setState(state);
    }

    public void updateFastCirclesHzLabel() {
        lblFastCirclesHz.setText("Fast Circles Rate: " + model.getScanner().getFastCirclesRate() + " Hz");
    }

    private void createEventHandlers() {
        // number of circles per camera exposure period
        spnNumFastCircles.registerListener(event -> {
            model.setNumFastCircles(spnNumFastCircles.getInt());
            model.getScanner().setFastCirclesStateRestart();
        });

        // correct scanner circle for asymmetry
        spnFastCirclesAsymmetry.registerListener(event -> {
            model.getScanner().setFastCirclesAsymmetry(spnFastCirclesAsymmetry.getFloat());
            model.getScanner().setFastCirclesStateRestart();
        });

        // the fast circles radius
        spnFastCirclesRadius.registerListener(event -> {
            model.getScanner().setFastCirclesRadius(spnFastCirclesRadius.getFloat());
            model.getScanner().setFastCirclesStateRestart();
        });

        // enable the scanner beam
        tglBeamEnabled.registerListener(event -> {
            model.getScanner().setBeamEnabled(tglBeamEnabled.isSelected());
            spnScannerH.setValue(model.getScanner().getPositionH());
            spnScannerI.setValue(model.getScanner().getPositionI());
        });

        // enable the fast circles state
        tglFastCirclesState.registerListener(event -> {
            final String value = tglFastCirclesState.isSelected() ?
                    Scanner.Values.FAST_CIRCLES_STATE.ON : Scanner.Values.FAST_CIRCLES_STATE.OFF;
            model.getScanner().setFastCirclesState(value);
        });

        // run the fast circles calibration routine
        btnCalibrate.registerListener(event -> {
            System.out.println("Calibration Start...");
            model.calibrateFastCircles(
                    spnCalibrationImages.getInt(),
                    spnCalibrationStart.getFloat(),
                    spnCalibrationIncrement.getFloat()
            );
//            model.getFastCircleData(
//                    spnCalibrationImages.getInt(),
//                    spnCalibrationStart.getFloat(),
//                    spnCalibrationIncrement.getFloat()
//            );
        });

        // sets the fast circles rate based on exposure
        btnSetFastCirclesHz.registerListener(event -> {
            model.getScanner().setFastCirclesRate(model.computeFastCirclesHz());
            model.getScanner().setFastCirclesStateRestart();
            updateFastCirclesHzLabel();
        });

        // move the scanner H axis
        spnScannerH.registerListener(event -> {
            model.getScanner().moveH(spnScannerH.getFloat());
            //System.out.println(model.getScanner().getPositionH());
        });

        // move the scanner I axis
        spnScannerI.registerListener(event -> {
            model.getScanner().moveI(spnScannerI.getFloat());
            //System.out.println(model.getScanner().getPositionI());
        });

        // save settings the the controller hardware
        btnSaveToFlash.registerListener(event -> {
            model.getScanner().setSaveSettings();
        });
    }

    public Spinner getSpinnerScannerH() {
        return spnScannerH;
    }

    public Spinner getSpinnerScannerI() {
        return spnScannerI;
    }
}
