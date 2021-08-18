/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.panels;

import java.io.File;
import java.util.List;
import java.util.Objects;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import com.asiimaging.crisp.CRISPFrame;
import com.asiimaging.crisp.CRISPPlugin;
import com.asiimaging.crisp.device.CRISP;
import com.asiimaging.crisp.plot.FocusDataSet;
import com.asiimaging.crisp.plot.PlotFrame;
import com.asiimaging.crisp.utils.FileUtils;
import com.asiimaging.ui.Button;
import com.asiimaging.ui.Panel;

import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.utils.FileDialogs;

/**
 * The ui panel that has buttons for creating plots and viewing CRISP data.
 */
public class PlotPanel extends Panel {

    private Button btnPlot;
    private Button btnView;

    private final Studio studio;
    private final CRISPFrame frame;
    private final FileDialogs.FileType FOCUS_CURVE_FILE_TYPE;
    
    public PlotPanel(final Studio studio, final CRISPFrame frame, final MigLayout layout) {
        super(layout);
        this.studio = Objects.requireNonNull(studio);
        this.frame = Objects.requireNonNull(frame);
        // file type filter
        FOCUS_CURVE_FILE_TYPE = new FileDialogs.FileType(
            "FOCUS_CURVE_DATA",
            "Focus Curve Data (.csv)",
            "",
            false,
            "csv"
        );
        init();
    }
    
    private void init() {
        Button.setDefaultSize(140, 30);
        btnPlot = new Button("Focus Curve Plot");
        btnView = new Button("View Data");

        final JLabel lblVersion = new JLabel("v" + CRISPPlugin.version);
        
        // handle user events
        registerEventHandlers();

        btnPlot.setToolTipText("Plot the focus curve.");
        btnView.setToolTipText("View a plot of CRISP focus curve data stored in a csv file.");
        
        // add components to panel
        add(btnPlot, "gapleft 30");
        add(btnView, "");
        add(lblVersion, "");
    }
    
    public void disableFocusCurveButtonTiger() {
        if (frame.getCRISP().isTiger()) {
            btnPlot.setEnabled(false);
            btnPlot.setToolTipText("This feature is not available on Tiger yet.");
        }
    }

    private void showPlotWindow() {
        // data is now stored in device property strings
        final String focusCurveData = frame.getCRISP().getAllFocusCurveData();
        
        // create the focus curve data out of the String data
        final FocusDataSet data = new FocusDataSet();
        data.parseString(focusCurveData);
        
        // create a plot window out of the focus curve data and display it
        PlotFrame.createPlotWindow(
            "CRISP Data Plot",
            "Focus Curve",
            "Position",
            "Error",
            data.createXYSeries()
        );
    }

    /**
     * Creates the event handlers for Button objects.
     */
    private void registerEventHandlers() {
        // plot the focus curve and show the plot
        btnPlot.registerListener(event -> {
            // disable polling while getting focus curve data
            final boolean isPollingEnabled = frame.getSpinnerPanel().isPollingEnabled();
            if (isPollingEnabled) {
                frame.getSpinnerPanel().setPollingCheckBox(false);
            }
            // get the focus curve
            btnPlot.setEnabled(false);
            getFocusCurve(isPollingEnabled); // starts a thread
        });

        // select a file and open a data viewer frame
        btnView.registerListener(event -> {
            showFocusCurvePlot();
        });
    }

    /**
     * Open a window to view the focus curve data.
     */
    private void showFocusCurvePlot() {
        // open a file browser
        final File file = FileDialogs.openFile(
            frame,
            "Please select the file to open",
            FOCUS_CURVE_FILE_TYPE
        );

        if (file == null) {
            return; // no selection => early exit
        }

        try {
            // create the focus curve data out of the csv file
            final List<String> lines = FileUtils.readFile(file);
            final FocusDataSet data = FocusDataSet.createFromCSV(lines);

            // create a plot window out of the focus curve data and display it
            PlotFrame.createPlotWindow(
                "CRISP Data Viewer",
                "Focus Curve",
                "Position",
                "Error",
                data.createXYSeries()
            );
        } catch (Exception e) {
            studio.logs().showError("could not open the file: " + file.toString());
        }
    }

    /**
     * Get the focus curve and store it in device property strings.
     * <p>
     * The data is stored in: {@code "Focus Curve Data0...23"}.
     */
    private void getFocusCurve(final boolean isPollingEnabled) {
        final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                frame.getCRISP().getFocusCurve();
                return null;
            }
            
            @Override
            protected void done() {
                // enable polling if it was on previously
                if (isPollingEnabled) {
                    frame.getSpinnerPanel().setPollingCheckBox(true);
                }
                btnPlot.setEnabled(isPollingEnabled);
                showPlotWindow();
            }
            
        };

        // start the thread
        worker.execute();
    }
}
