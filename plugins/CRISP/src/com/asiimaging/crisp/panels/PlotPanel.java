///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Brandon Simpson
//
// COPYRIGHT:    Applied Scientific Instrumentation, 2020
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

package com.asiimaging.crisp.panels;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

import com.asiimaging.crisp.CRISPPlugin;
import com.asiimaging.crisp.control.CRISP;
import com.asiimaging.crisp.control.CRISPTimer;
import com.asiimaging.crisp.plot.FocusDataSet;
import com.asiimaging.crisp.plot.PlotFrame;
import com.asiimaging.crisp.ui.Button;
import com.asiimaging.crisp.ui.FileSelect;
import com.asiimaging.crisp.ui.Panel;
import com.asiimaging.crisp.utils.FileUtils;

@SuppressWarnings("serial")
public class PlotPanel extends Panel {

    private Button btnPlot;
    private Button btnView;
    private JLabel lblVersion;
    
    private final JFrame frame;
    private final CRISP crisp;
    private final SpinnerPanel panel;
    
    private static final FileSelect FILE_SELECT = new FileSelect(
        "Please select the file to open", FileSelect.FILES_ONLY
    );
    
    public PlotPanel(final CRISP crisp, final SpinnerPanel panel, final JFrame frame) {
        super();
        this.crisp = crisp;
        this.panel = panel;
        this.frame = frame;
        FILE_SELECT.addFilter("Focus Curve Data (.csv)", "csv");
    }
    
    public void createComponents() {
        Button.setDefaultSize(140, 30);
        btnPlot = new Button("Focus Curve Plot");
        btnView = new Button("View Data");
        
        lblVersion = new JLabel("v" + CRISPPlugin.version);
        
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
        if (crisp.isTiger()) {
            btnPlot.setEnabled(false);
            btnPlot.setToolTipText("This feature is not available on Tiger yet.");
        }
    }
    
    public void showPlotWindow() {
        // data is now stored in device property strings
        final String focusCurveData = crisp.getAllFocusCurveData();
        
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
        btnPlot.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                btnPlot.setEnabled(false);
                getFocusCurve(); // starts a thread
            }
        });
        
        // select a file and open a data viewer frame
        btnView.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // open a file browser
                final String selection = FILE_SELECT.openDialogBox(frame);
                String path = "";
                if (!selection.isEmpty()) {
                    path = selection;
                } else {
                    return; // no selection => early exit
                }
                
                // create the focus curve data out of the csv file
                final ArrayList<String> file = new ArrayList<String>(FileUtils.readTextFile(path));
                final FocusDataSet data = FocusDataSet.createFromCSV(file);
                
                // create a plot window out of the focus curve data and display it
                PlotFrame.createPlotWindow(
                    "CRISP Data Viewer",
                    "Focus Curve",
                    "Position",
                    "Error",
                    data.createXYSeries()
                );
            }
        });   
    }
    
    /**
     * Get the focus curve and store it in device property strings.
     * <p>
     * The data is stored in: {@code "Focus Curve Data0...23"}.
     */
    private void getFocusCurve() {
        // disable polling during focus curve acquisition
        final boolean isPollingEnabled = panel.isPollingEnabled();
        if (isPollingEnabled) {
            panel.setPollingCheckBox(false);
        }
        
        final SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                crisp.getFocusCurve();
                return null;
            }
            
            @Override
            protected void done() {
                // re-enable polling if it was on
                if (isPollingEnabled) {
                    panel.setPollingCheckBox(true);
                }
                btnPlot.setEnabled(true);
                showPlotWindow();
            }
            
        };
        worker.execute();
    }
}
