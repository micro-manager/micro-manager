/*
 * Utilities for Gaussian fitting ImageJ plugins
 * Needs org.apache.commons.math and jfreechart
 * @author - Nico Stuurman,  2012
 * 
 * 
Copyright (c) 2012-2017, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit.utils;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.SingularValueDecompositionImpl;
import org.apache.commons.math.stat.StatUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYBarPainter;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBarRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.function.Function2D;
import org.jfree.data.general.DatasetUtils;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


/**
 * @author nico
 */
public class GaussianUtils {

   public static final int INT = 0;
   public static final int BGR = 1;
   public static final int XC = 2;
   public static final int YC = 3;
   public static final int S = 4;
   public static final int S1 = 4;
   public static final int S2 = 5;
   public static final int S3 = 6;

   // private static final double ONEOVER2PI = 1.0 / (2.0 * Math.PI);

   /**
    * Create a frame with a plot of the data given in XYSeries
    *
    * @param title
    * @param data
    * @param xTitle
    * @param yTitle
    * @param xLocation
    * @param yLocation
    */
   public static void plotData(String title, XYSeries data, String xTitle,
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
      XYPlot plot = (XYPlot) chart.getPlot();
      plot.setBackgroundPaint(Color.white);
      plot.setRangeGridlinePaint(Color.lightGray);
      XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();
      renderer.setDefaultShapesVisible(true);
      renderer.setSeriesPaint(0, Color.black);
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      renderer.setUseFillPaint(true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }

   /**
    * Create a frame with a plot of the data given in XYSeries
    *
    * @param title
    * @param data1
    * @param data2
    * @param xTitle
    * @param yTitle
    * @param xLocation
    * @param yLocation
    */
   public static void plotData2(String title, XYSeries data1, XYSeries data2, String xTitle,
         String yTitle, int xLocation, int yLocation) {
      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
      dataset.addSeries(data1);
      dataset.addSeries(data2);
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
      renderer.setSeriesPaint(0, Color.blue);
      renderer.setSeriesFillPaint(0, Color.white);
      renderer.setSeriesLinesVisible(0, true);
      renderer.setSeriesPaint(1, Color.red);
      renderer.setSeriesFillPaint(1, Color.white);
      renderer.setSeriesLinesVisible(1, true);
      Shape circle = new Ellipse2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(0, circle, false);
      Shape square = new Rectangle2D.Float(-2.0f, -2.0f, 4.0f, 4.0f);
      renderer.setSeriesShape(1, square, false);
      renderer.setUseFillPaint(true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }

   /**
    * Create a frame with a plot of the data given in XYSeries
    *
    * @param title
    * @param data
    * @param xTitle
    * @param yTitle
    * @param xLocation
    * @param yLocation
    * @param showShapes
    * @param logLog
    */
   public static void plotDataN(String title, XYSeries[] data, String xTitle,
         String yTitle, int xLocation, int yLocation, boolean showShapes, Boolean logLog) {

      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
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
         double yPad = 0.02 * (maxY - minY);
         if (yPad <= 0.0) {
            yPad = 1.0;
         }
         yAxis.setRangeWithMargins(minY - yPad, maxY + yPad);

         ValueAxis xAxis = plot.getDomainAxis();
         double xPad = 0.02 * (maxX - minX);
         if (xPad <= 0.0) {
            xPad = 1.0;
         }
         xAxis.setRangeWithMargins(minX - xPad, maxX + xPad);
      }

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }


   /**
    * Plots a histogram of distance data and calculates the P2D function and plots it
    *
    * @param title     - of the plot
    * @param data      - distance measurements (in nm)
    * @param max       - highest value in the histogram plot
    * @param fitResult - double[0] is mu, double[1] is sigma
    */
   public static void plotP2D(final String title, final double[] data,
         final double max, double[] fitResult) {
      final double testNrBins = (data.length / max) * 5.0;
      final int nrBins = testNrBins > 25 ? (int) testNrBins : 25;
      final double min = 0.0;
      final HistogramDataset hds = new HistogramDataset();
      hds.addSeries("Distances", data, nrBins, min, max);

      XYSeriesCollection p2dDataSet = new XYSeriesCollection();

      NumberAxis xAxis = new NumberAxis("distance(nm)");
      xAxis.setAutoRangeIncludesZero(true);
      NumberAxis yAxis = new NumberAxis("n");
      yAxis.setAutoRangeIncludesZero(true);
      NumberAxis yAxis2 = new NumberAxis("p2d");
      yAxis2.setAutoRangeIncludesZero(true);

      XYBarRenderer renderer1 = new XYBarRenderer(0);
      renderer1.setDrawBarOutline(false);
      renderer1.setBarPainter(new StandardXYBarPainter());
      renderer1.setShadowVisible(false);
      Color color1 = new Color(79, 129, 189);
      renderer1.setSeriesPaint(0, color1);

      XYPlot plot = new XYPlot(hds, xAxis, yAxis, renderer1);
      plot.setDomainPannable(true);
      plot.setRangePannable(true);
      plot.setForegroundAlpha(0.85f);
      plot.setBackgroundPaint(Color.lightGray);
      plot.setRangeAxis(0, yAxis);

      if (fitResult.length == 2) {
         Function2D p1 = new P2D(fitResult[0], fitResult[1]);
         XYSeries s1 = DatasetUtils.sampleFunction2DToSeries(p1, min,
               max, 4 * nrBins, "p2d");
         double xAtMaxY = 0.0;
         double maxY = s1.getMaxY();
         for (int i = 0; i < s1.getItemCount(); i++) {
            if (s1.getY(i).doubleValue() == maxY) {
               xAtMaxY = s1.getX(i).doubleValue();
            }
         }
         p2dDataSet.addSeries(s1);
         XYItemRenderer renderer2 = new StandardXYItemRenderer();
         Color color2 = new Color(160, 80, 40);
         renderer2.setSeriesPaint(0, color2);
         plot.setDataset(1, p2dDataSet);
         plot.setRenderer(1, renderer2);
         plot.setRangeAxis(1, yAxis2);
         plot.mapDatasetToRangeAxis(1, 1);
         double xAnPos = xAtMaxY + 0.5 * fitResult[1];
         XYPointerAnnotation xypa = new XYPointerAnnotation(
               "\u03BC = " + NumberUtils.doubleToDisplayString(fitResult[0], 2) // mu
                     + " \u03C3 = " + NumberUtils.doubleToDisplayString(fitResult[1 ], 2), // mu
               xAnPos, p1.getValue(xAnPos), 15 * Math.PI / 8);
         xypa.setLabelOffset(4.0);
         xypa.setTextAnchor(TextAnchor.HALF_ASCENT_LEFT);
         xypa.setBackgroundPaint(color2);
         renderer2.addAnnotation(xypa);
      }

      plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

      JFreeChart chart = new JFreeChart(title,
            JFreeChart.DEFAULT_TITLE_FONT, plot, true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(300, 300);
      graphFrame.setVisible(true);

   }

   /**
    * Plots a histogram of data and calculates the Gaussian function and plots it
    *
    * @param title     - of the plot
    * @param data      - distance measurements (in nm)
    * @param min       - lowest value in the histogram plot
    * @param max       - highest value in the histogram plot
    * @param fitResult - double[0] is mu, double[1] is sigma
    * @param x         - X position of the window on screen (pixels)
    * @param y         - Y position of the window on screen (pixels)
    */
   public static void plotGaussian(final String title, final double[] data,
         final double min, final double max, double[] fitResult,
         int x, int y) {
      final double testNrBins = (data.length / max) * 5.0;
      final int nrBins = testNrBins > 25 ? (int) testNrBins : 25;
      final HistogramDataset hds = new HistogramDataset();
      hds.addSeries("Distances", data, nrBins, min, max);

      XYSeriesCollection p2dDataSet = new XYSeriesCollection();

      NumberAxis xAxis = new NumberAxis("distance(nm)");
      xAxis.setAutoRangeIncludesZero(true);
      NumberAxis yAxis = new NumberAxis("n");
      yAxis.setAutoRangeIncludesZero(true);
      NumberAxis yAxis2 = new NumberAxis("Gaussian");
      yAxis2.setAutoRangeIncludesZero(true);

      XYBarRenderer renderer1 = new XYBarRenderer(0);
      renderer1.setDrawBarOutline(false);
      renderer1.setBarPainter(new StandardXYBarPainter());
      renderer1.setShadowVisible(false);
      Color color1 = new Color(79, 129, 189);
      renderer1.setSeriesPaint(0, color1);

      XYPlot plot = new XYPlot(hds, xAxis, yAxis, renderer1);
      plot.setDomainPannable(true);
      plot.setRangePannable(true);
      plot.setForegroundAlpha(0.85f);
      plot.setBackgroundPaint(Color.lightGray);
      plot.setRangeAxis(0, yAxis);

      if (fitResult.length == 2) {
         Function2D p1 = new Gaussian1D(fitResult[0], fitResult[1]);
         XYSeries s1 = DatasetUtils.sampleFunction2DToSeries(p1, min,
               max, 4 * nrBins, "Gaussian");
         double xAtMaxY = 0.0;
         double maxY = s1.getMaxY();
         for (int i = 0; i < s1.getItemCount(); i++) {
            if (s1.getY(i).doubleValue() == maxY) {
               xAtMaxY = s1.getX(i).doubleValue();
            }
         }
         p2dDataSet.addSeries(s1);
         XYItemRenderer renderer2 = new StandardXYItemRenderer();
         Color color2 = new Color(160, 80, 40);
         renderer2.setSeriesPaint(0, color2);
         plot.setDataset(1, p2dDataSet);
         plot.setRenderer(1, renderer2);
         plot.setRangeAxis(1, yAxis2);
         plot.mapDatasetToRangeAxis(1, 1);
         double xAnPos = xAtMaxY + 0.5 * fitResult[1];
         XYPointerAnnotation xypa = new XYPointerAnnotation(
               "\u03BC = " + NumberUtils.doubleToDisplayString(fitResult[0], 2) // mu
                     + " \u03C3 = " + NumberUtils.doubleToDisplayString(fitResult[1], 2), // mu
               xAnPos, p1.getValue(xAnPos), 15 * Math.PI / 8);
         xypa.setLabelOffset(4.0);
         xypa.setTextAnchor(TextAnchor.HALF_ASCENT_LEFT);
         xypa.setBackgroundPaint(color2);
         renderer2.addAnnotation(xypa);
      }

      plot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);

      JFreeChart chart = new JFreeChart(title,
            JFreeChart.DEFAULT_TITLE_FONT, plot, true);

      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(x, y);
      graphFrame.setVisible(true);
   }

   /**
    * Rotates a set of XY data points such that the direction of largest variance is a line around
    * the X-axis.  Equivalent to total least square analysis - which finds the best fit line
    * perpendicular to the data points
    *
    * @param xyPoints
    * @return
    */
   public static ArrayList<Point2D.Double> pcaRotate(ArrayList<Point2D.Double> xyPoints) {
      double[][] data = new double[2][xyPoints.size()];
      for (int i = 0; i < xyPoints.size(); i++) {
         data[0][i] = xyPoints.get(i).getX();
         data[1][i] = xyPoints.get(i).getY();
      }
      double meanX = StatUtils.mean(data[0]);
      double meanY = StatUtils.mean(data[1]);
      for (int i = 0; i < data[0].length; i++) {
         data[0][i] = data[0][i] - meanX;
         data[1][i] = data[1][i] - meanY;
      }

      Array2DRowRealMatrix dataM = new Array2DRowRealMatrix(data);

      SingularValueDecompositionImpl sVD = new SingularValueDecompositionImpl(dataM);
      RealMatrix output = sVD.getUT().multiply(dataM);

      ArrayList<Point2D.Double> result = new ArrayList<>();
      for (int i = 0; i < output.getColumnDimension(); i++) {
         result.add(new Point2D.Double(output.getEntry(0, i), output.getEntry(1, i)));
      }

      return result;
   }

   public static double sqr(double val) {
      return val * val;
   }

   public static double cube(double val) {
      return val * val * val;
   }

   /**
    * Gaussian function of the form: A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b A = params[INT]
    * (amplitude) b = params[BGR]  (background) xc = params[XC] yc = params[YC]
    *
    * @param params - Parameters to be optimized
    * @param s      - width of Gaussian in pixels
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array
    */
   public static double gaussianFixS(double[] params, double s, int x, int y) {
      if (params.length < 4) {
         // Problem, what do we do???
         //MMScriptException e;
         //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }
      double exponent = (sqr(x - params[XC]) + sqr(y - params[YC])) / (2 * sqr(s));
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }

   /**
    * Gaussian function of the form: A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b A = params[INT]
    * (amplitude) b = params[BGR]  (background) xc = params[XC] yc = params[YC] sig = params[S]
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array
    */
   public static double gaussian(double[] params, int x, int y) {
      if (params.length < 5) {
         // Problem, what do we do???
         //MMScriptException e;
         //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }
      double exponent = (sqr(x - params[XC]) + sqr(y - params[YC])) / (2 * sqr(params[S]));
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }

   /**
    * Derivative (Jacobian) of the above function
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ(double[] params, int x, int y) {
      double q = gaussian(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
            q / params[INT],
            1.0,
            dx * q / sqr(params[S]),
            dy * q / sqr(params[S]),
            (sqr(dx) + sqr(dy)) * q / cube(params[S])
      };
      return result;
   }


   /**
    * Derivative (Jacobian) of the above function
    *
    * @param params - Parameters to be optimized
    * @param s
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJFixS(double[] params, final double s,
         final int x, final int y) {
      double q = gaussianFixS(params, s, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
            q / params[INT],
            1.0,
            dx * q / sqr(s),
            dy * q / sqr(s),
            (sqr(dx) + sqr(dy)) * q / cube(s)
      };
      return result;
   }


   /**
    * Gaussian function of the form: f = A * e^(-((x-xc)^2/sigma_x^2 + (y-yc)^2/sigma_y^2)/2) + b A
    * = params[INT]  (total intensity) b = params[BGR]  (background) xc = params[XC] yc = params[YC]
    * sig_x = params[S1] sig_y = params[S2]
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array
    */
   public static double gaussian2DXY(double[] params, int x, int y) {
      if (params.length < 6) {
         // Problem, what do we do???
         //MMScriptException e;
         //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }

      double exponent = ((sqr(x - params[XC])) / (2 * sqr(params[S1])))
            + (sqr(y - params[YC]) / (2 * sqr(params[S2])));
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }

   /**
    * Derivative (Jacobian) of the above function.
    *
    * <p>p = A,b,xc,yc,sigma_x,sigma_y f = A * e^(-((x-xc)^2/sigma_x^2
    * + (y-yc)^2/sigma_y^2)/2) + b J =
    * { q/A, 1, dx*q/sigma_x^2, dy*q/sigma_y^2, dx^2*q/sigma_x^3, dy^2*q/sigma_y^3 }
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ2DXY(double[] params, int x, int y) {
      double q = gaussian2DXY(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
            q / params[INT],
            1.0,
            dx * q / sqr(params[S1]),
            dy * q / sqr(params[S2]),
            sqr(dx) * q / cube(params[S1]),
            sqr(dy) * q / cube(params[S2])
      };
      return result;
   }

   /**
    * Gaussian function of the form: f =  A * e^(-(a*(x-xc)^2 + c*(y-yc)^2 + 2*b*(x-xc)*(y-yc))/2) +
    * B A = params[INT]  (total intensity) B = params[BGR]  (background) xc = params[XC] yc =
    * params[YC] a = params[S1] b = params[S2] c = params[S3]
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array
    */
   public static double gaussian2DEllips(double[] params, int x, int y) {
      if (params.length < 7) {
         // Problem, what do we do???
         //MMScriptException e;
         //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }

      double exponent = ((params[S1] * sqr(x - params[XC]))
            + (params[S3] * sqr(y - params[YC]))
            + (2.0 * params[S2] * (x - params[XC]) * (y - params[YC]))
      ) / 2;
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }


   /**
    * Derivative (Jacobian) of gaussian2DEllips p = A,B,xc,yc,a,b,c J = { q/A, 1, (a*dx + b*dy)*q,
    * (b*dx + c*dy)*q, -1/2*dx^2*q, -dx*dy*q, -1/2*dy^2*q }
    *
    * @param params - Parameters to be optimized
    * @param x      - x position in the image
    * @param y      - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ2DEllips(double[] params, int x, int y) {
      double q = gaussian2DEllips(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
            q / params[INT],
            1.0,
            (params[S1] * dx + params[S2] * dy) * q,
            (params[S2] * dx + params[S3] * dy) * q,
            -0.5 * sqr(dx) * q,
            -dx * dy * q,
            -0.5 * sqr(dy) * q
      };
      return result;
   }

   /**
    * Converts paramers from 2DEllipse fit to theta, sigma_x and sigma_y
    *
    * @param a - params[S1] from Gaussian fit
    * @param b - params[S2] from Gaussian fit
    * @param c - params[S3] from Gaussian fit
    * @return double[3] containing, theta, sigmax, and sigmay in that order
    */
   public static double[] ellipseParmConversion(double a, double b, double c) {
      double[] result = new double[3];

      double u = (a - c) / b;
      double m = (-u + Math.sqrt(sqr(u) + 1)) / 2.0;
      result[0] = Math.atan(m);

      double costheta = Math.cos(result[0]);
      double sintheta = Math.sin(result[0]);

      result[1] = Math.sqrt((sqr(costheta) - sqr(sintheta)) / ((costheta * a) - (sintheta * c)));
      result[2] = Math.sqrt((sqr(costheta) - sqr(sintheta)) / ((costheta * c) - (sintheta * a)));
      
      return result;
   }

}