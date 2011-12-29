/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.pixelcalibrator;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Hashtable;
import mmcorej.CMMCore;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MathFunctions;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class CalibrationThread extends Thread {
   private final MMStudioMainFrame app_;
   private final CMMCore core_;
   private final String xystage_;
   private Hashtable<Point2D.Double, Point2D.Double> pointPairs_;

   boolean isDone_ = false;
   AffineTransform result_ = null;

   int progress_ = 0;
   private final PixelCalibratorPlugin plugin_;
   ImageWindow liveWin_;
   private ImageProcessor referenceImage_;

   double x;
   double y;
   int w;
   int h;
   int side_small;

   CalibrationThread(ScriptInterface app, PixelCalibratorPlugin plugin) {
      app_ = (MMStudioMainFrame) app;
      plugin_ = plugin;
      core_ = app_.getMMCore();
      xystage_ = core_.getXYStageDevice();
   }



   ImageProcessor theSlide = null;

   ImageProcessor crossCorrelate(ImageProcessor proc1, ImageProcessor proc2) {
      FHT h1 = new FHT(proc1);
      FHT h2 = new FHT(proc2);
      h1.transform();
      h2.transform();
      FHT result = h1.conjugateMultiply(h2);
      result.inverseTransform();
      result.swapQuadrants();
      result.resetMinAndMax();
      return result;
   }

   // Measures the displacement between two images by cross-correlating, and then finding the maximum value.
   // Accurate to one pixel only.

   Point2D.Double measureDisplacement(ImageProcessor proc1, ImageProcessor proc2, boolean display) {
      ImageProcessor result = crossCorrelate(proc1, proc2);
      ImageProcessor resultCenter = getSubImage(result, result.getWidth() / 2 - 8, result.getHeight() / 2 - 8, 16, 16);
      resultCenter.setInterpolationMethod(ImageProcessor.BICUBIC);
      ImageProcessor resultCenterScaled = resultCenter.resize(resultCenter.getWidth() * 10);
      ImagePlus img = new ImagePlus("", resultCenterScaled);
      Point p = ImageUtils.findMaxPixel(img);
      Point d = new Point(p.x - img.getWidth() / 2, p.y - img.getHeight() / 2);
      Point2D.Double d2 = new Point2D.Double(d.x / 10., d.y / 10.);
      if (display)
         img.show();
      return d2;
   }

   ImageProcessor getSubImage(ImageProcessor proc, int x, int y, int w, int h) {
      FloatProcessor proc2 = new FloatProcessor(w, h);
      proc2.insert(proc,-x,-y);
      return proc2;
   }

   ImageProcessor simulateAcquire(ImageProcessor slideProc, int x, int y) {
      int width = slideProc.getWidth();
      int height = slideProc.getHeight();
      return getSubImage(slideProc, x, y, width, height);
   }

   ImageProcessor snapImageAt(double x, double y, boolean simulate) throws InterruptedException {
      if (simulate) {
         return simulateAcquire(theSlide,(int) (x+(3*Math.random()-1.5)),(int) (y+(3*Math.random()-1.5)));
      } else {
         try {
            Point2D.Double p0 = app_.getXYStagePosition();
            if (p0.distance(x, y) > 1000) { // 1 millimeter
               throw new InterruptedException("XY stage safety limit reached.");
            }
            app_.setXYStagePosition(x, y);
            core_.waitForDevice(core_.getXYStageDevice());
            core_.snapImage();
            Object pix = core_.getImage();
            app_.displayImage(pix);
            if (liveWin_ == null)
               liveWin_ = app_.getImageWin();
            liveWin_.setTitle("Calibrating...");
            return ImageUtils.makeProcessor(core_,pix);
         } catch (InterruptedException e) {
            throw e;
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
            return null;
         }

      }
   }


   Point2D.Double measureDisplacement(double x1, double y1, Point2D.Double d, boolean display, boolean sim) throws InterruptedException {
         if (CalibrationThread.interrupted())
            throw new InterruptedException();
         ImageProcessor snap = (ImageProcessor) snapImageAt(x1,y1,sim);
         Rectangle guessRect = new Rectangle((int) ((w-side_small)/2-d.x),(int) ((h-side_small)/2-d.y),side_small,side_small);
         ImageProcessor foundImage = getSubImage(snap,guessRect.x, guessRect.y, guessRect.width, guessRect.height);
         liveWin_.getImagePlus().setRoi(guessRect);
         //new ImagePlus("found at "+dx+","+dy,foundImage).show();
         //new ImagePlus("simulated at "+dx+","+dy,simulatedImage).show();
         Point2D.Double dChange = measureDisplacement(referenceImage_, foundImage, display);
         return new Point2D.Double(d.x + dChange.x,d.y + dChange.y);
   }



   Point2D.Double runSearch(double dxi, double dyi, boolean sim) throws InterruptedException {

      double dx = dxi;
      double dy = dyi;
      Point2D.Double d = new Point2D.Double(0., 0.);

      // Now continue to double displacements and match acquired half-size images with expected half-size images

      for (int i=0;i<25;i++) {

         core_.logMessage(dx+","+dy+","+d);
         if ((2*d.x+side_small/2)>=w/2 || (2*d.y+side_small/2)>=h/2 || (2*d.x-side_small/2)<-(w/2) || (2*d.y-side_small/2)<-(h/2))
            break;

         dx = dx*2;
         dy = dy*2;
         
         d.x = d.x*2;
         d.y = d.y*2;

         d = measureDisplacement(x+dx, y+dy, d, false, sim);
         incrementProgress();
      }
      Point2D.Double stagePos;
      try {
         stagePos = app_.getXYStagePosition();
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         stagePos = null;
      }
      pointPairs_.put(new Point2D.Double(d.x, d.y),stagePos);
      return stagePos;

   }

   int smallestPowerOf2LessThanOrEqualTo(int x) {
      return 1 << ((int) Math.floor(Math.log(x)/Math.log(2)));
   }



   AffineTransform getFirstApprox(boolean sim) throws InterruptedException {
      if (sim && theSlide == null) {
         theSlide = IJ.getImage().getProcessor();
      }


      if (sim) {
         x = 0.;
         y = 0.;
      } else {
         Point2D.Double p;
         try {
            p = app_.getXYStagePosition();
         } catch (MMScriptException ex) {
            ReportingUtils.logError(ex);
            return null;
         }
         x = p.x;
         y = p.y;
      }

      // First find the smallest detectable displacement.
      ImageProcessor baseImage = snapImageAt(x,y,sim);

      w = baseImage.getWidth();
      h = baseImage.getHeight();
      int w_small = smallestPowerOf2LessThanOrEqualTo(w/4);
      int h_small = smallestPowerOf2LessThanOrEqualTo(h/4);
      side_small = Math.min(w_small, h_small);

      referenceImage_ = getSubImage(baseImage,(int) (-side_small/2+w/2),(int) (-side_small/2+h/2),side_small,side_small);

      pointPairs_.clear();
      pointPairs_.put(new Point2D.Double(0.,0.),new Point2D.Double(x,y));
      runSearch(0.1,0,sim);
      runSearch(0,0.1,sim);

      return MathFunctions.generateAffineTransformFromPointPairs(pointPairs_);
   }

   void measureCorner(AffineTransform firstApprox, Point c1, boolean sim) throws InterruptedException {
      Point2D.Double c1d = new Point2D.Double(c1.x, c1.y);
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1d, null);
      Point2D.Double c2 = measureDisplacement(s1.x, s1.y, c1d, false, sim);
      Point2D.Double s2;
      try {
         s2 = app_.getXYStagePosition();
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         return;
      }
      pointPairs_.put(new Point2D.Double(c2.x, c2.y), s2);
   }

   AffineTransform getSecondApprox(AffineTransform firstApprox, boolean sim) throws InterruptedException {
      pointPairs_.clear();
      Point2D.Double s1 = new Point2D.Double();
      int ax = w/2 - side_small/2;
      int ay = h/2 - side_small/2;

      Point c1 = new Point(-ax,-ay);
      measureCorner(firstApprox, c1, sim);
      incrementProgress();
      c1 = new Point(-ax,ay);
      measureCorner(firstApprox, c1, sim);
      incrementProgress();
      c1 = new Point(ax,ay);
      measureCorner(firstApprox, c1, sim);
      incrementProgress();
      c1 = new Point(ax,-ay);
      measureCorner(firstApprox, c1, sim);
      incrementProgress();
      try {
         return MathFunctions.generateAffineTransformFromPointPairs(pointPairs_, 2.0, Double.MAX_VALUE);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   AffineTransform firstApprox;
   AffineTransform secondApprox;
   double pixelSize;

   public AffineTransform runCalibration(boolean sim) throws MMScriptException, InterruptedException {
         pointPairs_ = new Hashtable();
         Point2D.Double xy0 = app_.getXYStagePosition();
         firstApprox = getFirstApprox(sim);
         setProgress(20);
         secondApprox = getSecondApprox(firstApprox, sim);
         if (secondApprox != null)
            ReportingUtils.logMessage(secondApprox.toString());
         app_.setXYStagePosition(xy0.x, xy0.y);
         app_.snapSingleImage();
         liveWin_.setTitle("Calibrating...done.");
         liveWin_.getImagePlus().killRoi();
         return secondApprox;
   }

   public void run() {
      progress_ = 0;
      result_ = null;

      try {
         result_ = runCalibration();
         plugin_.calibrationDone();
      } catch (InterruptedException e1) {

      } catch (MMScriptException e2) {
         ReportingUtils.showError(e2);
      }

   }

   public AffineTransform getResult() {
      return result_;
   }

   public AffineTransform runCalibration() throws MMScriptException, InterruptedException {
      return runCalibration(false);
   }

   
   synchronized int getProgress() {
      return progress_;
   }

   synchronized void incrementProgress() {
      progress_++;
      plugin_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
   
}
