 ///////////////////////////////////////////////////////////////////////////////
 //FILE:          
 //PROJECT:       
 //-----------------------------------------------------------------------------
 //
 // AUTHOR:       Nico Stuurman
 //
 // COPYRIGHT:    University of California, San Francisco 2015
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

package org.micromanager.pointandshootanalysis.plot;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.micromanager.pointandshootanalysis.display.WidgetSettings;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Utility class to make it simple to show a plot of XY data
 * Multiple datasets can be shown simultaneously
 * 
 * Copied from SpotIntensity Plugin
 * 
 * 
 * @author nico
 */
public class PlotUtils {
   private static int windowOffset_ = 100;
   MyChartFrame graphFrame_;
   
   private final MutablePropertyMapView prefs_;
   
   public PlotUtils(MutablePropertyMapView prefs) {
      prefs_ = prefs;
   }
   
   /**
    * Simple class whose sole intention is to intercept the dispose function and
    * use it to store window position and size
    */
   @SuppressWarnings("serial")
   private class MyChartFrame extends ChartFrame {
      private final static String WINDOWPOSX = "PlotWindowPosX";
      private final static String WINDOWPOSY = "PlotWindowPosY";
      private final static String WINDOWWIDTH = "PlotWindowWidth";
      private final static String WINDOWHEIGHT = "PlotWindowHeight";
      private final String s_;

      MyChartFrame(String s, JFreeChart jc) {
         this(s, jc, false);
      }

      MyChartFrame(String s, JFreeChart jc, Boolean b) {
         super(s, jc, b);
         s_ = s;
         
         Point screenLoc = new Point();
         screenLoc.x = prefs_.getInteger(WINDOWPOSX + s_, windowOffset_);
         screenLoc.y = prefs_.getInteger(WINDOWPOSY + s_, windowOffset_);
         windowOffset_ += 10;
         Dimension windowSize = new Dimension();
         windowSize.width = prefs_.getInteger(WINDOWWIDTH + s_, 300);
         windowSize.height = prefs_.getInteger(WINDOWHEIGHT + s_, 400);
         super.setLocation(screenLoc.x, screenLoc.y);
         super.setSize(windowSize);
      }

      @Override
      public void dispose() {
         // store window position and size to prefs
         prefs_.putInteger(WINDOWPOSX + s_, getX());
         prefs_.putInteger(WINDOWPOSY + s_, getY());
         prefs_.putInteger(WINDOWWIDTH + s_,getWidth());
         prefs_.putInteger(WINDOWHEIGHT + s_, getHeight());
         super.dispose();
      }
   }

   /**
    * Create a frame with a plot of the data given in XYSeries overwrite any
    * previously created frame with the same title
    *

    * @param title shown in the top of the plot
    * @param data array with data series to be plotted
    * @param xTitle Title of the X axis
    * @param yTitle Title of the Y axis
    * @param annotation to be shown in plot
    * @param yLimit - make sure the Y axis does not ho higher than this
    *                - ignored when null
    * @param colors
    * @return Frame that displays the data
    */
   public Frame plotData(String title, XYSeries[] data, String xTitle,
           String yTitle, String annotation, Double yLimit, Color[] colors) {

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
      if (yLimit != null) {
         if (maxY > yLimit) {
            maxY = yLimit;
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

      boolean[] showShapes = new boolean[data.length];
      for (int i = 0; i < showShapes.length; i++) {
         showShapes[i] = true;
      }

      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setBaseShapesVisible(true);

      for (int i = 0; i < data.length; i++) {
         renderer.setSeriesFillPaint(i, Color.white);
         renderer.setSeriesLinesVisible(i, true);
      }

      for (int i = 0; i < data.length; i++) {
         int index = i % WidgetSettings.COLORS.length;
         renderer.setSeriesPaint(i, WidgetSettings.COLORS[index]);
      }
      for (int i = 0; i < data.length; i++) {
         if (showShapes.length > i && !showShapes[i]) {
            renderer.setSeriesShapesVisible(i, false);
         }
      }
      
      // place annotation at 80 % of max X, maxY
      XYAnnotation an = new XYTextAnnotation(annotation, 
              maxX - 0.2 * (maxX - minX), maxY);
      plot.addAnnotation(an);
      plot.getRangeAxis().setRange(minY, maxY);

      renderer.setUseFillPaint(true);

      if (graphFrame_ == null)
         graphFrame_ = new MyChartFrame(title, chart);
      else
         graphFrame_.getChartPanel().setChart(chart);
      graphFrame_.getChartPanel().setMouseWheelEnabled(true);
      JPanel controlPanel = new JPanel();
      for (int i = 0; i < data.length; i++) {
         JCheckBox jcb = new JCheckBox(new VisibleAction(renderer, i,
                 (String) data[i].getKey()));
         jcb.setSelected(true);
         renderer.setSeriesVisible(i, true);
         controlPanel.add(jcb);
      }
      graphFrame_.add(controlPanel);

      graphFrame_.pack();
      final MyChartFrame privateFrame = graphFrame_;
      graphFrame_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            privateFrame.dispose();
         }
      });

      graphFrame_.setVisible(true);

      return graphFrame_;
   }
   

   public static XYSeries normalize (XYSeries input)
   {
      double max = input.getMaxY();
      // double min = input.getMinY();
      XYSeries output = new XYSeries( input.getKey(), 
              input.getAutoSort(), input.getAllowDuplicateXValues());
      for (int i = 0; i < input.getItemCount(); i++) {
         output.add(input.getX(i), (input.getY(i).doubleValue()) / (max) );
      }
      return output;
   }
   
   private static class VisibleAction extends AbstractAction {

        private final XYItemRenderer renderer;
        private final int index;

        public VisibleAction(XYItemRenderer renderer, int i, String s) {
            super(s);
            this.renderer = renderer;
            this.index = i;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            renderer.setSeriesVisible(index, !renderer.getSeriesVisible(index));
        }
    }
}
