
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
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.MMStudio;
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
   private final MMStudio app_;
   private final CMMCore core_;
   private final String xystage_;
   private Hashtable<Point2D.Double, Point2D.Double> pointPairs_;

   private boolean isDone_ = false;
   private AffineTransform result_ = null;

   private int progress_ = 0;
   private final PixelCalibratorPlugin plugin_;
   private ImageWindow liveWin_;
   private ImageProcessor referenceImage_;

   private double x;
   private double y;
   private int w;
   private int h;
   private int side_small;

   private class CalibrationFailedException extends Exception {
      public CalibrationFailedException(String msg) {
         super(msg);
      }
   }

   CalibrationThread(ScriptInterface app, PixelCalibratorPlugin plugin) {
      app_ = (MMStudio) app;
      plugin_ = plugin;
      core_ = app_.getMMCore();
      xystage_ = core_.getXYStageDevice();
   }



   private ImageProcessor theSlide = null;

   private ImageProcessor crossCorrelate(ImageProcessor proc1, ImageProcessor proc2) {
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

   private Point2D.Double measureDisplacement(ImageProcessor proc1, ImageProcessor proc2, boolean display) {
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

   private ImageProcessor getSubImage(ImageProcessor proc, int x, int y, int w, int h) {
      FloatProcessor proc2 = new FloatProcessor(w, h);
      proc2.insert(proc,-x,-y);
      return proc2;
   }

   private ImageProcessor simulateAcquire(ImageProcessor slideProc, int x, int y) {
      int width = slideProc.getWidth();
      int height = slideProc.getHeight();
      return getSubImage(slideProc, x, y, width, height);
   }

   private ImageProcessor snapImageAt(double x, double y, boolean simulate) throws CalibrationFailedException {
      if (simulate) {
         return simulateAcquire(theSlide,(int) (x+(3*Math.random()-1.5)),(int) (y+(3*Math.random()-1.5)));
      } else {
         try {
            Point2D.Double p0 = app_.getXYStagePosition();
            if (p0.distance(x, y) > (plugin_.safeTravelRadiusUm_ / 2)) {
               throw new CalibrationFailedException("XY stage safety limit reached.");
            }
            app_.setXYStagePosition(x, y);
            core_.waitForDevice(core_.getXYStageDevice());
            core_.snapImage();
            TaggedImage image = core_.getTaggedImage();
            app_.displayImage(image);
            if (liveWin_ == null)
               liveWin_ = app_.getSnapLiveManager().getSnapLiveWindow();
            liveWin_.setTitle("Calibrating...");
            return ImageUtils.makeMonochromeProcessor(image);
         } catch (CalibrationFailedException e) {
            throw e;
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            throw new CalibrationFailedException(ex.getMessage());
         }

      }
   }


   private Point2D.Double measureDisplacement(double x1, double y1, Point2D.Double d,
         boolean display, boolean sim)
      throws InterruptedException, CalibrationFailedException
   {
         if (CalibrationThread.interrupted())
            throw new InterruptedException();
         ImageProcessor snap = snapImageAt(x1,y1,sim);
         Rectangle guessRect = new Rectangle((int) ((w-side_small)/2-d.x),(int) ((h-side_small)/2-d.y),side_small,side_small);
         ImageProcessor foundImage = getSubImage(snap,guessRect.x, guessRect.y, guessRect.width, guessRect.height);
         liveWin_.getImagePlus().setRoi(guessRect);
         Point2D.Double dChange = measureDisplacement(referenceImage_, foundImage, display);
         return new Point2D.Double(d.x + dChange.x,d.y + dChange.y);
   }



   private Point2D.Double runSearch(double dxi, double dyi, boolean sim)
      throws InterruptedException, CalibrationFailedException
   {

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
         throw new CalibrationFailedException(ex.getMessage());
      }
      pointPairs_.put(new Point2D.Double(d.x, d.y),stagePos);
      return stagePos;

   }

   private int smallestPowerOf2LessThanOrEqualTo(int x) {
      return 1 << ((int) Math.floor(Math.log(x)/Math.log(2)));
   }



   private AffineTransform getFirstApprox(boolean sim)
      throws InterruptedException, CalibrationFailedException
   {
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
            throw new CalibrationFailedException(ex.getMessage());
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

      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
              (-side_small/2+h/2),side_small,side_small);

      pointPairs_.clear();
      pointPairs_.put(new Point2D.Double(0.,0.),new Point2D.Double(x,y));
      runSearch(0.1,0,sim);

      // Re-acquire the reference image, since we may not be exactly where 
      // we started from after having called runSearch().
      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
            (-side_small/2+h/2),side_small,side_small);

      runSearch(0,0.1,sim);

      return MathFunctions.generateAffineTransformFromPointPairs(pointPairs_);
   }

   private void measureCorner(AffineTransform firstApprox, Point c1, boolean sim)
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double c1d = new Point2D.Double(c1.x, c1.y);
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1d, null);
      Point2D.Double c2 = measureDisplacement(s1.x, s1.y, c1d, false, sim);
      Point2D.Double s2;
      try {
         s2 = app_.getXYStagePosition();
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      pointPairs_.put(new Point2D.Double(c2.x, c2.y), s2);
   }

   private AffineTransform getSecondApprox(AffineTransform firstApprox, boolean sim)
      throws InterruptedException, CalibrationFailedException
   {
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

   private AffineTransform firstApprox;
   private AffineTransform secondApprox;
   private double pixelSize;

   private AffineTransform runCalibration()
      throws InterruptedException, CalibrationFailedException
   {
      return runCalibration(false);
   }

   private AffineTransform runCalibration(boolean sim)
      throws InterruptedException, CalibrationFailedException
   {
      pointPairs_ = new Hashtable<Point2D.Double, Point2D.Double>();
      Point2D.Double xy0 = null;
      try {
         xy0 = app_.getXYStagePosition();
      }
      catch (MMScriptException e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      firstApprox = getFirstApprox(sim);
      setProgress(20);
      secondApprox = getSecondApprox(firstApprox, sim);
      if (secondApprox != null)
         ReportingUtils.logMessage(secondApprox.toString());
      try {
         app_.setXYStagePosition(xy0.x, xy0.y);
         app_.snapSingleImage();
      }
      catch (MMScriptException e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      liveWin_.setTitle("Calibrating...done.");
      liveWin_.getImagePlus().killRoi();
      return secondApprox;
   }

   @Override
   public void run() {
      synchronized (this) {
         progress_ = 0;
      }
      result_ = null;

      try {
         result_ = runCalibration();
      }
      catch (InterruptedException e) {
         // User canceled
         SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
               plugin_.calibrationFailed(true);
            }
         });
         return;
      }
      catch (final CalibrationFailedException e) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
               ReportingUtils.showError(e);
               plugin_.calibrationFailed(false);
            }
         });
         return;
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override public void run() {
            plugin_.calibrationDone();
         }
      });
   }

   AffineTransform getResult() {
      return result_;
   }

   synchronized int getProgress() {
      return progress_;
   }

   private synchronized void incrementProgress() {
      progress_++;
      plugin_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
}
