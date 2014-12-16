/*
 * Utilities for Gaussian fitting ImageJ plugins
 * Needs org.apache.commons.math and jfreechart
 * Includes the actual Gaussian functions
 */

package edu.valelab.GaussianFit.utils;


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

import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.axis.LogAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


/**
 *
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

   /**
    * Create a frame with a plot of the data given in XYSeries
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
      renderer.setBaseShapesVisible(true);
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
      renderer.setBaseShapesVisible(true);
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
    */
   public static void plotDataN(String title, XYSeries[] data, String xTitle,
                 String yTitle, int xLocation, int yLocation, boolean showShapes, Boolean logLog) {
      
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
         if (d.getMinX() < minX)
            minX = d.getMinX();
         if (d.getMaxX() > maxX)
            maxX = d.getMaxX();
         if (d.getMinY() < minY)
            minY = d.getMinY();
         if (d.getMaxY() > maxY)
            maxY = d.getMaxY();
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
      
      ChartFrame graphFrame = new ChartFrame(title, chart);
      graphFrame.getChartPanel().setMouseWheelEnabled(true);
      graphFrame.pack();
      graphFrame.setLocation(xLocation, yLocation);
      graphFrame.setVisible(true);
   }


   
   /**
   * Rotates a set of XY data points such that the direction of largest
   * variance is a line around the X-axis.  Equivalent to total least square
   * analysis - which finds the best fit line perpendicular to the data points
   */
   public static ArrayList<Point2D.Double> pcaRotate(ArrayList<Point2D.Double> xyPoints) {
      double[][] data = new double[2][xyPoints.size()];
      for (int i =0; i< xyPoints.size(); i++) {
         data[0][i] = xyPoints.get(i).getX();
         data[1][i] = xyPoints.get(i).getY();
      }
      double meanX = StatUtils.mean(data[0]);
      double meanY = StatUtils.mean(data[1]);
      for (int i = 0; i< data[0].length; i++) {
         data[0][i] = data[0][i] - meanX;
         data[1][i] = data[1][i] - meanY;
      }

      Array2DRowRealMatrix dataM = new Array2DRowRealMatrix(data);

      SingularValueDecompositionImpl sVD = new SingularValueDecompositionImpl(dataM);
      RealMatrix output = sVD.getUT().multiply(dataM);

      ArrayList<Point2D.Double> result = new ArrayList<Point2D.Double>();
      for (int i = 0; i < output.getColumnDimension(); i++) {
         result.add(new Point2D.Double(output.getEntry(0,i), output.getEntry(1,i)));
      }

      return result;
   }

   public static double sqr(double val) {
      return val*val;
   }

   public static double cube(double val) {
      return val * val * val;
   }

 /**
    * Gaussian function of the form:
    * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
    * A = params[INT]  (amplitude)
    * b = params[BGR]  (background)
    * xc = params[XC]
    * yc = params[YC]
    * sig = params[S]
    */
   public static double gaussian(double[] params, int x, int y) {
      if (params.length < 5) {
                       // Problem, what do we do???
                       //MMScriptException e;
                       //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }

      double exponent = (sqr(x - params[XC])  + sqr(y - params[YC])) / (2 * sqr(params[S]));
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }

   /**
    * Derivative (Jacobian) of the above function
    *
    * @param params - Parameters to be optimized
    * @param x - x position in the image
    * @param y - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ(double[] params, int x, int y) {
      double q = gaussian(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
         q/params[INT],
         1.0,
         dx * q/sqr(params[S]),
         dy * q/sqr(params[S]),
         (sqr(dx) + sqr(dy)) * q/cube(params[S])
      };
      return result;
   }


   /**
    * Gaussian function of the form:
    * f = A * e^(-((x-xc)^2/sigma_x^2 + (y-yc)^2/sigma_y^2)/2) + b
    * A = params[INT]  (total intensity)
    * b = params[BGR]  (background)
    * xc = params[XC]
    * yc = params[YC]
    * sig_x = params[S1]
    * sig_y = params[S2]
    */
   public static double gaussian2DXY(double[] params, int x, int y) {
      if (params.length < 6) {
                       // Problem, what do we do???
                       //MMScriptException e;
                       //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }

      double exponent = ( (sqr(x - params[XC]))/(2*sqr(params[S1])))  +
              (sqr(y - params[YC]) / (2 * sqr(params[S2])));
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }

    /**
    * Derivative (Jacobian) of the above function
    *
     *
     * p = A,b,xc,yc,sigma_x,sigma_y
         f = A * e^(-((x-xc)^2/sigma_x^2 + (y-yc)^2/sigma_y^2)/2) + b
         J = {
          q/A,
          1,
          dx*q/sigma_x^2,
          dy*q/sigma_y^2,
          dx^2*q/sigma_x^3,
          dy^2*q/sigma_y^3
         }
    * @param params - Parameters to be optimized
    * @param x - x position in the image
    * @param y - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ2DXY(double[] params, int x, int y) {
      double q = gaussian2DXY(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
         q/params[INT],
         1.0,
         dx * q/sqr(params[S1]),
         dy * q/sqr(params[S2]),
         sqr(dx) * q /cube(params[S1]),
         sqr(dy) * q /cube(params[S2])
      };
      return result;
   }

   /**
    * Gaussian function of the form:
    * f =  A * e^(-(a*(x-xc)^2 + c*(y-yc)^2 + 2*b*(x-xc)*(y-yc))/2) + B
    * A = params[INT]  (total intensity)
    * B = params[BGR]  (background)
    * xc = params[XC]
    * yc = params[YC]
    * a = params[S1]
    * b = params[S2]
    * c = params[S3]
    */
   public static double gaussian2DEllips(double[] params, int x, int y) {
      if (params.length < 7) {
                       // Problem, what do we do???
                       //MMScriptException e;
                       //e.message = "Params for Gaussian function has too few values"; //throw (e);
      }

      double exponent = ( (params[S1] * sqr(x - params[XC])) +
                          (params[S3] * sqr(y - params[YC])) +
                          (2.0 * params[S2] * (x - params[XC]) * (y - params[YC]))
                           ) / 2 ;
      double res = params[INT] * Math.exp(-exponent) + params[BGR];
      return res;
   }


    /**
    * Derivative (Jacobian) of gaussian2DEllips
    * p = A,B,xc,yc,a,b,c
    * J = {
    * q/A,
    * 1,
    * (a*dx + b*dy)*q,
    * (b*dx + c*dy)*q,
    * -1/2*dx^2*q,
    * -dx*dy*q,
    * -1/2*dy^2*q
    * }
    * @param params - Parameters to be optimized
    * @param x - x position in the image
    * @param y - y position in the image
    * @return - array with the derivates for each of the parameters
    */
   public static double[] gaussianJ2DEllips(double[] params, int x, int y) {
      double q = gaussian2DEllips(params, x, y) - params[BGR];
      double dx = x - params[XC];
      double dy = y - params[YC];
      double[] result = {
         q/params[INT],
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

      result[1] = Math.sqrt((sqr(costheta) - sqr(sintheta)) / ((costheta * a) - (sintheta * c)) );
      result[2] = Math.sqrt((sqr(costheta) - sqr(sintheta)) / ((costheta * c) - (sintheta * a)) );
      
      /*
      double c0 = Math.sqrt(0.5 + (a-c) / 2 * (Math.sqrt(sqr(a-c) + 4 * sqr(b))));
      double s0 = sqr(1- sqr(c0));

      result[1] = 1 / (a + c + b/(c0+s0));
      result[2] = 1 / (a + c - b/(c0+s0));

      if (result[2] > result[1])
         result[0] = Math.asin(c0);
      else
         result[0] = Math.acos(c0);
      */
      return result;
   }

   
   /**
    * Linear Regression to find the best line between a set of points
    * returns an array where [0] = slope and [1] = offset
    * Input: arrays with x and y data points
    * Not used anymore
    *
   public double[] fitLine(Vector<Point2D.Double> xyPoints) {
      double[][] xWithOne = new double[xyPoints.size()][2];
      double[][] yWithOne = new double[xyPoints.size()][2];
      for (int i =0; i< xyPoints.size(); i++) {
         xWithOne[i][0] = xyPoints.get(i).getX();
         xWithOne[i][1] = 1;
         yWithOne[i][0] = xyPoints.get(i).getY();
         yWithOne[i][1] = 1;
      }

      Array2DRowRealMatrix xM = new Array2DRowRealMatrix(xWithOne);
      Array2DRowRealMatrix yM = new Array2DRowRealMatrix(yWithOne);

      QRDecompositionImpl qX = new QRDecompositionImpl(xM);
      BlockRealMatrix mX = (BlockRealMatrix) qX.getSolver().solve(yM);

      RealMatrix theY = xM.multiply(mX);
      double ansX = theY.subtract(yM).getColumnVector(0).getNorm();
      print ("Answer X: " + ansX);

      QRDecompositionImpl qY = new QRDecompositionImpl(yM);
      BlockRealMatrix mY = (BlockRealMatrix) qY.getSolver().solve(xM);

      RealMatrix theX = yM.multiply(mY);
      double ansY = theX.subtract(xM).getColumnVector(0).getNorm();
      print ("Answer Y: " + ansY);

      double[][] res = mX.getData();
      double[] ret = new double[2];
      ret[0] = res[0][0];
      ret[1] = res[1][0];

      if (ansY < ansX) {
         res = mY.getData();
         ret[0] = 1 / res[0][0];
         ret[1] = - res[1][0]/res[0][0];
      }

      return ret;
   }

   public AffineTransform computeAffineTransform(double a, double b) {
      AffineTransform T = new AffineTransform();
      T.rotate(-Math.atan(a));
      T.translate(0, -b);
      return T;
   }
   */

}
