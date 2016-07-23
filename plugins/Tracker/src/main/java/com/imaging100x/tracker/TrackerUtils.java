/*
 * Utility functions for the Tracker plugin
 * Nico Stuurman, 2/2014.  Copyright UCSF, BSD license
 * 
 */
package com.imaging100x.tracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.general.DatasetChangeEvent;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 *
 * @author nico
 */
public class TrackerUtils {
   private static final int SIZE = 600;
    /**
    * Create a frame with a plot of the data given in XYSeries
    */
   public static void plotData(String title, final XYSeries data, String xTitle,
           String yTitle, int xLocation, int yLocation) {
      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
      dataset.addSeries(data);
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
                xTitle, // x-axis Label
                yTitle, // y-axis Label
                dataset, // Dataset
                PlotOrientation.VERTICAL, // Plot Orientation
                false, // Show Legend
                true, // Use tooltips
                false // Configure chart to generate URLs?
            );
      final XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setBaseShapesVisible(true);
      renderer.setSeriesPaint(0, Color.black);
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.setPreferredSize(new Dimension(SIZE, SIZE) );
      graphFrame.setResizable(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
      

      dataset.addChangeListener(new DatasetChangeListener() {

         public void datasetChanged(DatasetChangeEvent dce) {
            double xRange = data.getMaxX() - data.getMinX();
            double yRange = data.getMaxY() - data.getMinY();
            double xAvg = (data.getMaxX() + data.getMinX()) / 2;
            double yAvg = (data.getMaxY() + data.getMinY()) / 2;
            double range = xRange;
            if (yRange > range) {
               range = yRange;
            }
            double offset = 0.55 * range;
            plot.getDomainAxis().setRange(xAvg - offset, xAvg + offset);
            plot.getRangeAxis().setRange(yAvg - offset, yAvg + offset);          
         }
         
      });
      
   }
}
