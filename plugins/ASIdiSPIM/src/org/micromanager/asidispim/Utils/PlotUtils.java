///////////////////////////////////////////////////////////////////////////////
//FILE:          PlotUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2015
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


package org.micromanager.asidispim.Utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;

/**
 * Utility class to make it simple to show a plot of XY data
 * Multiple datasets can be shown simultanuously
 * 
 * 
 * @author nico
 */
public class PlotUtils {
   final String prefsNode_;
   final Prefs prefs_;
   
   public PlotUtils(Prefs prefs, String prefsNode) {
      prefs_ = prefs;
      prefsNode_ = prefsNode;
   }
   
   /**
    * Simple class whose sole intention is to intercept the dispose function and
    * use it to store window position and size
    */
   class MyChartFrame extends ChartFrame {

      final String node_;

      MyChartFrame(String s, JFreeChart jc) {
         this(s, jc, false);
      }

      MyChartFrame(String s, JFreeChart jc, Boolean b) {
         super(s, jc, b);

         node_ = prefsNode_ + "_" + s;

         Point screenLoc = new Point();
         screenLoc.x = prefs_.getInt(node_,
                 Properties.Keys.PLUGIN_AUTOFOCUS_WINDOWPOSX, 100);
         screenLoc.y = prefs_.getInt(node_,
                 Properties.Keys.PLUGIN_AUTOFOCUS_WINDOWPOSY, 100);
         Dimension windowSize = new Dimension();
         windowSize.width = prefs_.getInt(node_,
                 Properties.Keys.PLUGIN_AUTOFOCUS_WINDOW_WIDTH, 300);
         windowSize.height = prefs_.getInt(node_,
                 Properties.Keys.PLUGIN_AUTOFOCUS_WINDOW_HEIGHT, 400);
         setLocation(screenLoc.x, screenLoc.y);
         setSize(windowSize);
      }

      @Override
      public void dispose() {
         // store window position and size to prefs
         prefs_.putInt(node_, Properties.Keys.PLUGIN_AUTOFOCUS_WINDOWPOSX,
                 getX());
         prefs_.putInt(node_, Properties.Keys.PLUGIN_AUTOFOCUS_WINDOWPOSY,
                 getY());
         prefs_.putInt(node_, Properties.Keys.PLUGIN_AUTOFOCUS_WINDOW_WIDTH,
                 getWidth());
         prefs_.putInt(node_, Properties.Keys.PLUGIN_AUTOFOCUS_WINDOW_HEIGHT,
                 getHeight());
         super.dispose();
      }
   }

   /**
    * Create a frame with a plot of the data given in XYSeries overwrite any
    * previously created frame with the same title
    *
    * @param title
    * @param data
    * @param xTitle
    * @param yTitle
    * @param showShapes
    * @param logLog
    * @return Frame that displays the data
    */
   public Frame plotDataN(String title, XYSeries[] data, String xTitle,
           String yTitle, boolean showShapes, Boolean logLog) {

      // if we already have a plot open with this title, close it, but remember
      // its position:
      Frame[] gfs = ChartFrame.getFrames();
      for (Frame f : gfs) {
         if (f.getTitle().equals(title)) {
            f.dispose();
         }
      }

      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
      // calculate min and max to scale the graph
      double minX, minY, maxX, maxY;
      minX = data[0].getMinX();
      minY = data[0].getMinY();
      maxX = data[0].getMaxX();
      maxY = data[0].getMaxY();
      for (XYSeries d : data) {
         dataset.addSeries(d);
         if (d.getMinX() < minX) {
            minX = d.getMinX();
         }
         if (d.getMaxX() > maxX) {
            maxX = d.getMaxX();
         }
         if (d.getMinY() < minY) {
            minY = d.getMinY();
         }
         if (d.getMaxY() > maxY) {
            maxY = d.getMaxY();
         }
      }

      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
              xTitle, // x-axis Label
              yTitle, // y-axis Label
              dataset, // Dataset
              PlotOrientation.VERTICAL, // Plot Orientation
              true, // Show Legend
              true, // Use tooltips
              false // Configure chart to generate URLs?
      );
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      if (logLog) {
         LogAxis xAxis = new LogAxis(xTitle);
         xAxis.setTickUnit(new NumberTickUnit(1.0, new java.text.DecimalFormat(), 10));
         plot.setDomainAxis(xAxis);
         plot.setDomainGridlinePaint(Color.lightGray);
         plot.setDomainGridlineStroke(new BasicStroke(1.0f));
         plot.setDomainMinorGridlinePaint(Color.lightGray);
         plot.setDomainMinorGridlineStroke(new BasicStroke(0.2f));
         plot.setDomainMinorGridlinesVisible(true);
         LogAxis yAxis = new LogAxis(yTitle);
         yAxis.setTickUnit(new NumberTickUnit(1.0, new java.text.DecimalFormat(), 10));
         plot.setRangeAxis(yAxis);
         plot.setRangeGridlineStroke(new BasicStroke(1.0f));
         plot.setRangeMinorGridlinePaint(Color.lightGray);
         plot.setRangeMinorGridlineStroke(new BasicStroke(0.2f));
         plot.setRangeMinorGridlinesVisible(true);
      }

      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setBaseShapesVisible(true);

      for (int i = 0; i < data.length; i++) {
         renderer.setSeriesFillPaint(i, Color.white);
         renderer.setSeriesLinesVisible(i, true);
      }

      renderer.setSeriesPaint(0, Color.blue);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);

      if (data.length > 1) {
         renderer.setSeriesPaint(1, Color.red);
         Shape square = new Rectangle2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
         renderer.setSeriesShape(1, square, false);
      }
      if (data.length > 2) {
         renderer.setSeriesPaint(2, Color.darkGray);
         Shape rect = new Rectangle2D.Float(-2.0f, -1.0f, 4.0f, 2.0f);
         renderer.setSeriesShape(2, rect, false);
      }
      if (data.length > 3) {
         renderer.setSeriesPaint(3, Color.magenta);
         Shape rect = new Rectangle2D.Float(-1.0f, -2.0f, 2.0f, 4.0f);
         renderer.setSeriesShape(3, rect, false);
      }

      if (!showShapes) {
         for (int i = 0; i < data.length; i++) {
            renderer.setSeriesShapesVisible(i, false);
         }
      }

      renderer.setUseFillPaint(true);

      if (!logLog) {
         // Since the axis autoscale only on the first dataset, we need to scale ourselves
         NumberAxis yAxis = (NumberAxis) plot.getRangeAxis();
         yAxis.setAutoRangeIncludesZero(false);
         yAxis.setRangeWithMargins(minY, maxY);

         ValueAxis xAxis = plot.getDomainAxis();
         xAxis.setRangeWithMargins(minX, maxX);
      }

      final MyChartFrame graphFrame = new MyChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            graphFrame.dispose();
         }
      });

      graphFrame.setVisible(true);

      return graphFrame;
   }

}
