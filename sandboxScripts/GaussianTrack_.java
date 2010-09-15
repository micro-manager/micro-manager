import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Ellipse2D.Float;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.lang.Math;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.*;
import ij.measure.ResultsTable;
import ij.text.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.IJ;

import org.apache.commons.math.analysis.*;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.BlockRealMatrix;
import org.apache.commons.math.linear.SingularValueDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.commons.math.linear.QRDecompositionImpl;
import org.apache.commons.math.optimization.direct.NelderMead;
import org.apache.commons.math.optimization.direct.MultiDirectional;
import org.apache.commons.math.optimization.fitting.ParametricRealFunction;
import org.apache.commons.math.optimization.fitting.CurveFitter;
import org.apache.commons.math.optimization.general.LevenbergMarquardtOptimizer;
import org.apache.commons.math.optimization.RealPointValuePair;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.SimpleScalarValueChecker;
import org.apache.commons.math.stat.StatUtils;

// For plotting with JChart2
/*
import info.monitorenter.gui.chart.Chart2D;
import info.monitorenter.gui.chart.ZoomableChart;
import info.monitorenter.gui.chart.controls.LayoutFactory; 
import info.monitorenter.gui.chart.IAxis.AxisTitle;
import info.monitorenter.gui.chart.ITrace2D;
import info.monitorenter.gui.chart.traces.Trace2DSimple;
import info.monitorenter.gui.chart.views.ChartPanel;
import info.monitorenter.gui.chart.traces.painters.TracePainterDisc;
import info.monitorenter.gui.chart.traces.painters.TracePainterLine;
*/

// For plotting with JFreeChart
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;




public class GaussianTrack_ implements PlugIn {
	double[] params0_ = {16000.0, 5.0, 5.0, 1.0, 850.0};
	double[] steps_ = new double[5];
	String [] paramNames_ = {"A", "x_c", "y_c", "sigma", "b"};

   GaussianResidual gs_;
   NelderMead nm_;
   SimpleScalarValueChecker convergedChecker_;;

   static final String XCOLNAME = "X";
   static final String YCOLNAME = "Y";

	private void print(String myText) {
		ij.IJ.log(myText);
	}

   public class GaussianResidual implements MultivariateRealFunction {
      short[] data_;
      int nx_;
      int ny_;
      int count_ = 0;


      public void setImage(short[] data, int width, int height) {
          data_ = data;
          nx_ = width;
          ny_ = height;
      }

      public double value(double[] params) {
          double residual = 0.0;
          for (int i = 0; i < nx_; i++) {
             for (int j = 0; j < ny_; j++) {
                residual += sqr(gaussian(params, i, j) - data_[(j*nx_) + i]);
             }
          }
          return residual;
      }

      public double sqr(double val) {
         return val*val;
      }

      public double gaussian(double[] params, int x, int y) {

         /* Gaussian function of the form:
          * A *  exp(-((x-xc)^2+(y-yc)^2)/(2 sigy^2))+b
          * A = params[0]  (total intensity)
          * xc = params[1]
          * yc = params[2]
          * sig = params[3]
          * b = params[4]  (background)
          */

         if (params.length < 5) {
                          // Problem, what do we do???
                          //MMScriptException e;
                          //e.message = "Params for Gaussian function has too few values"; //throw (e);
         }

         double exponent = (sqr(x - params[1])  + sqr(y - params[2])) / (2 * sqr(params[3]));
         double res = params[0] * Math.exp(-exponent) + params[4];
         return res;
      }
   }


   /**
    * Rotates a set of XY data points such that the direcion of largest
    * variance is a line around the X-axis.  Equivalent to total least square
    * analysis - which finds the best fit line perpendicular to the data points
    */
   public Vector<Point2D.Double> pcaRotate(Vector<Point2D.Double> xyPoints) {
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

      Vector<Point2D.Double> result = new Vector<Point2D.Double>();
      for (int i = 0; i < output.getColumnDimension(); i++) {
         result.add(new Point2D.Double(output.getEntry(0,i), output.getEntry(1,i)));
      }

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


   /**
    * Create a frame with a plot of the data given in XYSeries
    */
   public void plotData(String title, XYSeries data, int xLocation, int yLocation) {
      // JFreeChart code
      XYSeriesCollection dataset = new XYSeriesCollection();
      dataset.addSeries(data);
      JFreeChart chart = ChartFactory.createScatterPlot(title, // Title
                "Time (interval)", // x-axis Label
                "Distance (nm)", // y-axis Label
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
    * KeyListener and MouseListenerclass for ResultsTable
    * When user selected a line in the ResulsTable and presses a key,
    * the corresponding image will move to the correct slice and draw the ROI
    * that was used to calculate the Gaussian fit
    * Works only in conjunction with appropriate column names
    * Up and down keys also work as expected
    */
   public class MyK implements KeyListener, MouseListener{
      ImagePlus siPlus_;
      ResultsTable res_;
      TextWindow win_;
      TextPanel tp_;
      int hBS_;
      public MyK(ImagePlus siPlus, ResultsTable res, TextWindow win, int halfBoxSize) {
         siPlus_ = siPlus;
         res_ = res;
         win_ = win;
         tp_ = win.getTextPanel();
         hBS_ = halfBoxSize;
      }
      public void keyPressed(KeyEvent e) {
         int key = e.getKeyCode();
         int row = tp_.getSelectionStart();
         if (key == KeyEvent.VK_J) {
            if (row > 0) {
               row--;
               tp_.setSelection(row, row);
            }
         } else if (key == KeyEvent.VK_K) {
            if  (row < tp_.getLineCount() - 1) {
               row++;
               tp_.setSelection(row, row);
            }
         }
         update();
      }
      public void keyReleased(KeyEvent e) {}
      public void keyTyped(KeyEvent e) {}

      public void mouseReleased(MouseEvent e) {
         update();
      }
      public void mousePressed(MouseEvent e) {}
      public void mouseClicked(MouseEvent e) {}
      public void mouseEntered(MouseEvent e) {};
      public void mouseExited(MouseEvent e) {};
      private void update() {
         int row = tp_.getSelectionStart();
         if (row >= 0 && row < tp_.getLineCount()) {
            if (siPlus_ != IJ.getImage()) {
               siPlus_.getWindow().toFront();
               win_.toFront();
            }
            int frame = (int) res_.getValue("Frame", row);
            int x = (int)res_.getValue("XMax", row);
            int y = (int) res_.getValue("YMax", row);
            siPlus_.setSlice(frame);
            siPlus_.setRoi(new Roi(x - hBS_ , y - hBS_, 2 * hBS_, 2 * hBS_));
         }
      }

   }


   /**
    * Performs Gaussian Fit on a given ImageProcessor
    * Estimates initial values for the fit and send of to Apache fitting code
    */
	public double[] doGaussianFit (ImageProcessor siProc) {

      short[] imagePixels = (short[])siProc.getPixels();
		gs_.setImage((short[])siProc.getPixels(), siProc.getWidth(), siProc.getHeight());

      // Hard code estimate for sigma:
      params0_[3] = 1.115;

      // estimate background by averaging pixels at the edge
      double bg = 0.0;
      int n = 0;
      int lastRowOffset = (siProc.getHeight() - 1) * siProc.getWidth();
      for (int i =0; i < siProc.getWidth(); i++) {
         bg += imagePixels[i];
         bg += imagePixels[i + lastRowOffset];
         n += 2;
      }
      for (int i = 1; i < siProc.getHeight() - 1; i++) {
         bg += imagePixels[i * siProc.getWidth()];
         bg += imagePixels[(i + 1) *siProc.getWidth() - 1];
         n += 2;
      }
      params0_[4] = bg / n;


      // estimate signal by subtracting background from total intensity 
      double ti = 0.0;
      double mt = 0.0;
      for (int i = 0; i < siProc.getHeight() * siProc.getWidth(); i++) {
         mt += imagePixels[i];
      }
      ti = mt - ( (bg / n) * siProc.getHeight() * siProc.getWidth());
      params0_[0] = ti / (2 * Math.PI * params0_[3] * params0_[3]);
      // print("Total signal: " + ti + "Estimate: " + params0_[0]);

      // estimate center of mass
      double mx = 0.0;
      double my = 0.0;
      for (int i = 0; i < siProc.getHeight() * siProc.getWidth(); i++) {
         //mx += (imagePixels[i] - params0_[4]) * (i % siProc.getWidth() );
         //my += (imagePixels[i] - params0_[4]) * (Math.floor (i / siProc.getWidth()));
         mx += imagePixels[i]  * (i % siProc.getWidth() );
         my += imagePixels[i]  * (Math.floor (i / siProc.getWidth()));
      }
      params0_[1] = mx/mt;
      params0_[2] = my/mt;

      print("Centroid: " + mx/mt + " " + my/mt);

      // set step size during estimate
		for (int i=0;i<params0_.length;++i)
			steps_[i] = params0_[i]*0.3;

		nm_.setStartConfiguration(steps_);
		nm_.setConvergenceChecker(convergedChecker_);

		nm_.setMaxIterations(200);
		double[] paramsOut = {0.0};
		try {
			RealPointValuePair result = nm_.optimize(gs_, GoalType.MINIMIZE, params0_);
			paramsOut = result.getPoint();
		} catch (Exception e) {}

      return paramsOut;
	}

	public void run(String arg) {

      // objects used in Gaussian fitting
		gs_ = new GaussianResidual();
		nm_ = new NelderMead();
		convergedChecker_ = new SimpleScalarValueChecker(1e-5,-1);

      // Filters for results of Gaussian fit
      double intMin = 100;
      double intMax = 1E7;
      double sigmaMin = 0.8;
      double sigmaMax = 2.1;

      // half the size of the box used for Gaussian fitting in pixels
      int halfSize = 6;

      // Needed to calculate # of photons and estimate error
      double photonConversionFactor = 10.41;
      double gain = 50;
      double pixelSize = 107; // nm/pixel

      // derived from above values:
      double cPCF = photonConversionFactor / gain;

      // initial setting for Maximum Finder
      int noiseTolerance = 100;

      // for now, take the active ImageJ image (this should be an image of a difraction limited spot
		ImagePlus siPlus = IJ.getImage();

      Roi originalRoi = siPlus.getRoi();
      if (null == originalRoi) { 
         IJ.error("Please draw a Roi around the spot you want to track");
         return;
      }
      int sliceN = siPlus.getSlice();

      ResultsTable rt = new ResultsTable();

      Rectangle rect = originalRoi.getBounds();
      int xc = (int) (rect.getX() + 0.5 * rect.getWidth());
      int yc = (int) (rect.getY() + 0.5 * rect.getHeight());


		long startTime = System.nanoTime();

      Vector<Point2D.Double> xyPoints = new Vector<Point2D.Double>();
      for (int i = sliceN; i <= siPlus.getNSlices(); i++) {
         // Search in next slice in same Roi for local maximum
         Roi spotRoi = new Roi(xc - halfSize, yc - halfSize, 2 * halfSize, 2*halfSize);
         siPlus.setSlice(i);
         siPlus.setRoi(spotRoi);

         // Find maximum in Roi
         IJ.run("Find Maxima...", "noise=" + noiseTolerance + " output=List");
         ResultsTable rtS = ResultsTable.getResultsTable();
         if (rtS.getCounter() >=1) {
            xc = (int) rtS.getValueAsDouble(0, 0);
            yc = (int) rtS.getValueAsDouble(1, 0);
         }

         // Set Roi for fitting centered around maximum
         spotRoi = new Roi(xc - halfSize, yc - halfSize, 2 * halfSize, 2*halfSize);
         siPlus.setRoi(spotRoi);
         ImageProcessor ip = siPlus.getProcessor().crop();
         
         double[]paramsOut = doGaussianFit(ip);
         if (paramsOut.length >= 4) {                                         
            double anormalized = paramsOut[0] * (2 * Math.PI * paramsOut[3] * paramsOut[3]);
            double x = (paramsOut[1] - halfSize + xc) * pixelSize;
            double y = (paramsOut[2] - halfSize + yc) * pixelSize;
            xyPoints.add(new Point2D.Double(x, y));
            // TOOD: quality control
            boolean report = anormalized > intMin && anormalized < intMax &&  
                              paramsOut[3] > sigmaMin && paramsOut[3] < sigmaMax;

            rt.incrementCounter();
            rt.addValue("Frame", i);
            double N = anormalized * cPCF;
            rt.addValue("Intensity (#p)", N);
            rt.addValue("Background", paramsOut[4]);
            rt.addValue(XCOLNAME, x);
            rt.addValue(YCOLNAME, y);
            double s = paramsOut[3] * pixelSize;
            rt.addValue("S (nm)", paramsOut[3] * pixelSize);
            // calculate error using formular from Thomson et al (2002)
            // (dx)2 = (s*s + (a*a/12)) / N + (8*pi*s*s*s*s * b*b) / (a*a*N*N)
            double error = (s*s + (pixelSize * pixelSize)/12) / N;
            rt.addValue ("sigma", error);

            rt.addValue("XMax", xc);
            rt.addValue("YMax", yc);

         }  
      }

      long endTime = System.nanoTime();
		double took = (endTime - startTime) / 1E6;

		print("Calculation took: " + took + " milli seconds"); 

      siPlus.setSlice(sliceN);
      siPlus.setRoi(originalRoi);

      if (rt.getCounter() <= 1) {
         IJ.error("Not enough data points");
         return;
      }

      String rtTitle = "Gaussian Fit Tracking Result for " + siPlus.getWindow().getTitle();
      rt.show(rtTitle);

      // Attach listener to TextPanel
      TextPanel tp;
      Frame frame = WindowManager.getFrame(rtTitle);
      TextWindow win;
      if (frame!=null && frame instanceof TextWindow) {
         win = (TextWindow)frame;
         tp = win.getTextPanel();
         MyK myk = new MyK(siPlus, rt, win, halfSize);
         tp.addKeyListener(myk);
         tp.addMouseListener(myk);
      }

      Vector<Point2D.Double> xyCorrPoints = pcaRotate(xyPoints);

      XYSeries xData = new XYSeries("On Track");
      XYSeries yData = new XYSeries("Off Track");
      for (int i = 0; i < xyPoints.size(); i++) {
          xData.add(i, xyCorrPoints.get(i).getX());
          yData.add(i, xyCorrPoints.get(i).getY());
      }

      plotData("On-Axis", xData, 0, 0);
      plotData("Off-Axis", yData, 500, 0);


      /* 
      * Chart On-Axis Movement with JChart2
      *
      ZoomableChart chart = new ZoomableChart();
      chart.getAxisY().setPaintGrid(true);
      chart.getAxisX().setAxisTitle(new AxisTitle("Time - #"));
      chart.getAxisY().setAxisTitle(new AxisTitle("Movement (nm)"));

      // Create an ITrace: 
      ITrace2D trace = new Trace2DSimple(); 
      trace.setTracePainter(new TracePainterDisc());
      ITrace2D traceLine = new Trace2DSimple();
      traceLine.setTracePainter(new TracePainterLine());
      chart.addTrace(trace);
      chart.addTrace(traceLine);

      LayoutFactory factory = LayoutFactory.getInstance();
      ChartPanel chartpanel = new ChartPanel(chart);

      for (int i = 0; i < xyPoints.size(); i++) {
         trace.addPoint(i, xyCorrPoints.get(i).getX());
         traceLine.addPoint(i, xyCorrPoints.get(i).getX());
      }

      // Make it visible:
      final JFrame graphFrameX = new JFrame("On Axis Movement");
      // add the chart to the frame: 
      graphFrameX.getContentPane().add(chart);
      graphFrameX.setSize(400,300);
      graphFrameX.setLocation(30, 30);
      graphFrameX.setJMenuBar(factory.createChartMenuBar(chartpanel, false));
      graphFrameX.addWindowListener(
         new WindowAdapter(){
            public void windowClosing(WindowEvent e){
               graphFrameX.removeWindowListener(this);
               graphFrameX.dispose();
          }
        }
      );
      graphFrameX.setVisible(true);
      

      * 
      * Chart Off-Axis Movement with JChart2
      *
      ZoomableChart chartY = new ZoomableChart();
      chartY.getAxisY().setPaintGrid(true);
      chartY.getAxisX().setAxisTitle(new AxisTitle("Time - #"));
      chartY.getAxisY().setAxisTitle(new AxisTitle("Movement (nm)"));

      // Create an ITrace: 
      ITrace2D traceY = new Trace2DSimple(); 
      traceY.setTracePainter(new TracePainterDisc());
      ITrace2D traceYLine = new Trace2DSimple();
      traceYLine.setTracePainter(new TracePainterLine());
      chartY.addTrace(traceY);
      chartY.addTrace(traceYLine);

      ChartPanel chartpanelY = new ChartPanel(chartY);

      for (int i = 0; i < xyPoints.size(); i++) {
         traceY.addPoint(i, xyCorrPoints.get(i).getY());
         traceYLine.addPoint(i, xyCorrPoints.get(i).getY());
      }

      // Make it visible:
      final JFrame graphFrameY = new JFrame("Off Axis Movement");
      // add the chart to the frame: 
      graphFrameY.getContentPane().add(chartY);
      graphFrameY.setSize(400,300);
      graphFrameY.setLocation(430, 30);
      graphFrameY.setJMenuBar(factory.createChartMenuBar(chartpanelY, false));
      graphFrameY.addWindowListener(
         new WindowAdapter(){
            public void windowClosing(WindowEvent e){
               graphFrameY.removeWindowListener(this);
               graphFrameY.dispose();
            }
         }
      );
      graphFrameY.setVisible(true);
      */
   }

}
