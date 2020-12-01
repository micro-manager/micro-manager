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

package com.asiimaging.crisp.plot;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
//import org.jfree.chart.plot.XYPlot;
//import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYDataset;
//import org.jfree.data.xy.XYSeries;
//import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.utils.ReportingUtils;

import com.asiimaging.crisp.data.Icons;
import com.asiimaging.crisp.utils.ObjectUtils;

// https://www.javatpoint.com/jfreechart-key-classes

/**
 * Creates a custom JFrame to display data plots.
 *
 */
@SuppressWarnings("serial")
public class PlotFrame extends JFrame {
    
    private static final int WINDOW_SIZE_X = 600;
    private static final int WINDOW_SIZE_Y = 400;
    private static final int WINDOW_LOCATION_X = 100;
    private static final int WINDOW_LOCATION_Y = 100;
    
    private JFreeChart chart;
    private ChartPanel panel;
    
    public PlotFrame(final String title) {
        super(title);
        chart = null;
        panel = null;
        
        // set size and location to default values
        setLocation(WINDOW_LOCATION_X, WINDOW_LOCATION_Y);
        setSize(new Dimension(WINDOW_SIZE_X, WINDOW_SIZE_Y));
        
        // set the window icon to be a microscope
        setIconImage(Icons.MICROSCOPE_ICON.getImage());
        
        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
    
    public static PlotFrame createPlotWindow(
            final String windowTitle, 
            final String plotTitle, 
            final String xAxisLabel, 
            final String yAxisLabel, 
            final XYDataset dataset) {
        
        final PlotFrame plot = new PlotFrame(windowTitle);
        
        plot.chart = ChartFactory.createScatterPlot(
            plotTitle,
            xAxisLabel,
            yAxisLabel,
            dataset,
            PlotOrientation.VERTICAL,
            false, // show legend
            false, // use tooltips
            false  // generate urls
        );

        plot.panel = new ChartPanel(plot.chart);
        plot.setContentPane(plot.panel);
        plot.setVisible(true);
        return plot;
    }
    
//        final XYPlot plot = (XYPlot)chart.getPlot();
//        plot.setBackgroundPaint(Color.white);
//        plot.setRangeGridlinePaint(Color.lightGray);
        
//        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
//        renderer.setSeriesPaint(0, Color.BLUE); // line color
//        renderer.setSeriesStroke(0, new BasicStroke(1.0f)); // stroke weight
//        plot.setRenderer(renderer);
    
    /**
     * Save the plot as a PNG file. Image size depends 
     * on the current size of the frame's content pane.
     * 
     * @param filepath Where to save the file.
     */
    public void saveAsPNG(final String filepath) {
        ObjectUtils.requireNonNull(chart);
        final Dimension size = getContentPane().getSize();
        try {
            final File file = new File(filepath);
            ChartUtilities.saveChartAsPNG(file, chart, size.width, size.height);
        } catch (IOException e) {
            ReportingUtils.showError("Could not save the plot as a PNG file.");
        }
    }
}
