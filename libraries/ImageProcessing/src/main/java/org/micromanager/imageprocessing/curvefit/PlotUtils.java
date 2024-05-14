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

package org.micromanager.imageprocessing.curvefit;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.Studio;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * Utility class to make it simple to show a plot of XY data.
 * Multiple datasets can be shown simultaneously
 *
 *
 * @author nico
 */
public class PlotUtils {
   private final Studio studio_;

   public PlotUtils(Studio studio) {
      studio_ = studio;
   }

   /**
    * Simple class whose sole intention is to intercept the dispose function and
    * use it to store window position and size.
    */
   @SuppressWarnings("serial")
   class MyChartFrame extends ChartFrame {

      MyChartFrame(String s, JFreeChart jc) {
         this(s, jc, false);
      }

      MyChartFrame(String s, JFreeChart jc, Boolean b) {
         super(s, jc, b);
         this.setLocation(100, 100);
         this.setSize(300, 400);
         WindowPositioning.setUpBoundsMemory(this, MyChartFrame.class, s);
      }

   }

   /**
    * Create a frame with a plot of the data given in XYSeries overwrite any
    * previously created frame with the same title.
    *

    * @param title shown in the top of the plot
    * @param data array with data series to be plotted
    * @param xTitle Title of the X axis
    * @param yTitle Title of the Y axis
    * @param showShapes whether to draw shapes at the data points
    * @param annotation to be shown in plot
    * @param markerPosition Optional position of marker on the x-axis (leave null if undesired)
    * @return Frame that displays the data
    */
   public Frame plotDataN(String title, XYSeries[] data, String xTitle,
                          String yTitle, boolean[] showShapes, String annotation,
                          Double markerPosition) {

      // if we already have a plot open with this title, close it, but remember
      // its position
      Frame[] gfs = ChartFrame.getFrames();
      for (Frame f : gfs) {
         if (f.getTitle().equals(title)) {
            f.dispose();
         }
      }

      // JFreeChart code
      final XYSeriesCollection dataset = new XYSeriesCollection();
      // calculate min and max to scale the graph
      double minX = data[0].getMinX();
      double minY = data[0].getMinY();
      double maxX = data[0].getMaxX();
      double maxY = data[0].getMaxY();
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
              false, // Show Legend
              true, // Use tooltips
              false // Configure chart to generate URLs?
      );
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);

      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);

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

      for (int i = 0; i < data.length; i++) {
         if (showShapes.length > i && !showShapes[i]) {
            renderer.setSeriesShapesVisible(i, false);
         }
      }

      XYAnnotation an = new XYTextAnnotation(annotation, maxX - 0.01, maxY);
      plot.addAnnotation(an);

      renderer.setUseFillPaint(true);

      if (markerPosition != null) {
         ValueMarker marker = new ValueMarker(markerPosition);  // position is the value on the axis
         marker.setPaint(Color.black);
         ((XYPlot) chart.getPlot()).addDomainMarker(marker);
      }

      final MyChartFrame graphFrame = new MyChartFrame(title, chart);
      // weird: pack() resizes the window, so remember here and later set it back.
      final Dimension size = graphFrame.getSize();
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            graphFrame.dispose();
         }
      });
      graphFrame.setSize(size);
      graphFrame.setVisible(true);

      return graphFrame;
   }


}
