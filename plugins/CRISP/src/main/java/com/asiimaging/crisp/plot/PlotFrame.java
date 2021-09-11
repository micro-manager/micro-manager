/*
 * Project: ASI CRISP Control
 * License: BSD 3-clause, see LICENSE.md
 * Author: Brandon Simpson (brandon@asiimaging.com)
 * Copyright (c) 2014-2021, Applied Scientific Instrumentation
 */
package com.asiimaging.crisp.plot;


import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import javax.swing.JFrame;

import com.asiimaging.crisp.data.Icons;
import com.asiimaging.crisp.utils.FileUtils;
import com.asiimaging.ui.Button;
import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.micromanager.internal.utils.FileDialogs;

/**
 * Creates a custom JFrame to display data plots.
 *
 */
public class PlotFrame extends JFrame {
    
    private static final int WINDOW_SIZE_X = 600;
    private static final int WINDOW_SIZE_Y = 400;
    private static final int WINDOW_LOCATION_X = 100;
    private static final int WINDOW_LOCATION_Y = 100;
    
    private JFreeChart chart;
    private ChartPanel panel;
    private FocusDataSet data;

    private final Button btnSaveData;
    private final FileDialogs.FileType FOCUS_CURVE_FILE_TYPE;
    
    public PlotFrame(final String title) {
        super(title);
        FOCUS_CURVE_FILE_TYPE = new FileDialogs.FileType(
            "FOCUS_CURVE_DATA",
            "Focus Curve Data (.csv)",
            "",
            false,
            "csv"
        );
        
        // set size and location to default values
        setLocation(WINDOW_LOCATION_X, WINDOW_LOCATION_Y);
        setSize(new Dimension(WINDOW_SIZE_X, WINDOW_SIZE_Y));
        setMinimumSize(new Dimension(WINDOW_SIZE_X/2, WINDOW_SIZE_Y/2));
        
        // ui elements
        btnSaveData = new Button("Save Data");
        registerEventHandlers();
        
        // set the window icon to be a microscope
        setIconImage(Icons.MICROSCOPE.getImage());
        
        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
    
    public static PlotFrame createPlotWindow(
            final String windowTitle, 
            final String plotTitle, 
            final String xAxisLabel, 
            final String yAxisLabel, 
            final FocusDataSet data) {
        
        // create an XYSeries to use in JFreeChart
        final XYDataset dataset = data.createXYSeries();
        
        // create the frame using MigLayout
        final PlotFrame plotFrame = new PlotFrame(windowTitle);
        plotFrame.setLayout(new MigLayout("", "", ""));
        plotFrame.data = data;
        
        // create the plot
        plotFrame.chart = ChartFactory.createScatterPlot(
            plotTitle,
            xAxisLabel,
            yAxisLabel,
            dataset,
            PlotOrientation.VERTICAL,
            false, // show legend
            false, // use tooltips
            false  // generate urls
        );
        
        // plot style for dark mode
        plotFrame.chart.setBackgroundPaint(Color.DARK_GRAY);
        plotFrame.chart.getTitle().setPaint(Color.WHITE);
        final XYPlot plot = plotFrame.chart.getXYPlot();
        plot.getDomainAxis().setLabelPaint(Color.WHITE);
        plot.getRangeAxis().setLabelPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.DARK_GRAY);
        plot.setRangeGridlinePaint(Color.DARK_GRAY);
        plot.getDomainAxis().setTickLabelPaint(Color.WHITE);
        plot.getRangeAxis().setTickLabelPaint(Color.WHITE);
        
        // Override the resize method for ChartPanel
        plotFrame.panel = new ChartPanel(plotFrame.chart) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(plotFrame.getWidth(), plotFrame.getHeight());
            }
        };
        
        // add components to frame
        plotFrame.add(plotFrame.panel, "wrap");
        plotFrame.add(plotFrame.btnSaveData, "");
        plotFrame.setVisible(true);
        return plotFrame;
    }
    
    private void registerEventHandlers() {
        btnSaveData.registerListener(e -> {
            // open a file browser
            final File file = FileDialogs.openFile(
                    this,
                    "Please select the file to open",
                    FOCUS_CURVE_FILE_TYPE
            );

            if (file == null) {
                return; // no selection => early exit
            }

            // save the focus curve data as CSV
            try {
                FileUtils.saveFile(data.toCSV(), file.toString());
            } catch (IOException ex) {
                // TODO: log or show the error
                //DialogUtils.showMessage(btnSaveData, "Failed to save the data.");
            }
        });
    }
    
    /**
     * Save the plot as a PNG file. Image size depends
     * on the current size of the frame's content pane.
     *
     * @param filepath Where to save the file.
     */
    public void saveAsPNG(final String filepath) {
        Objects.requireNonNull(chart);
        final Dimension size = getContentPane().getSize();
        try {
            final File file = new File(filepath);
            ChartUtils.saveChartAsPNG(file, chart, size.width, size.height);
        } catch (IOException e) {
           // TODO: log or show error
           //ReportingUtils.showError("Could not save the plot as a PNG file.");
        }
    }
}
