///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstin, 2010
//               Nico Stuurman, 2018
//
// COPYRIGHT:    Regents of the University of California, 2010-2018
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

package org.micromanager.internal.pixelcalibrator;

import ij.IJ;
import ij.ImagePlus;
import ij.process.FHT;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.display.DisplayWindow;
import org.micromanager.Studio;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MathFunctions;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class CalibrationThread extends Thread {
   private final Studio studio_;
   private final CMMCore core_;
   private final PixelCalibratorDialog dialog_;
   private final RectangleOverlay overlay_;
   
   private Map<Point2D.Double, Point2D.Double> pointPairs_;

   private AffineTransform result_ = null;
   private int progress_ = 0;
   private DisplayWindow liveWin_;
   private ImageProcessor referenceImage_;

   private double x;
   private double y;
   private int w;
   private int h;
   private int side_small;

   private class CalibrationFailedException extends Exception {

      private static final long serialVersionUID = 4749723616733251885L;

      public CalibrationFailedException(String msg) {
         super(msg);
         if (overlay_ != null) {
            overlay_.setVisible(false);
         }
         if (liveWin_ != null) {
            liveWin_.setCustomTitle("Preview");
            if (overlay_ != null) {
               liveWin_.removeOverlay(overlay_);
            }
         }

      }
   }

   CalibrationThread(Studio app, PixelCalibratorDialog dialog) {
      studio_ = app;
      core_ = studio_.getCMMCore();
      dialog_ = dialog;
      overlay_ = new RectangleOverlay();
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

   private Point2D.Double measureDisplacement(ImageProcessor proc1, 
           ImageProcessor proc2, boolean display) {
      ImageProcessor result = crossCorrelate(proc1, proc2);
      ImageProcessor resultCenter = getSubImage(result, result.getWidth() / 2 - 8, result.getHeight() / 2 - 8, 16, 16);
      resultCenter.setInterpolationMethod(ImageProcessor.BICUBIC);
      ImageProcessor resultCenterScaled = resultCenter.resize(resultCenter.getWidth() * 10);
      ImagePlus img = new ImagePlus("", resultCenterScaled);
      Point p = ImageUtils.findMaxPixel(img);
      Point d = new Point(p.x - img.getWidth() / 2, p.y - img.getHeight() / 2);
      Point2D.Double d2 = new Point2D.Double(d.x / 10., d.y / 10.);
      if (display) {
         img.show();
      }
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

   private ImageProcessor snapImageAt(double x, double y, boolean simulate) 
           throws CalibrationFailedException {
      if (simulate) {
         return simulateAcquire(theSlide,(int) (x+(3*Math.random()-1.5)),(int) (y+(3*Math.random()-1.5)));
      } else {
         try {
            Point2D.Double p0 = core_.getXYStagePosition();
            if (p0.distance(x, y) > (dialog_.safeTravelRadius() / 2)) {
               throw new CalibrationFailedException("XY stage safety limit reached.");
            }
            core_.setXYPosition(x, y);
            core_.waitForDevice(core_.getXYStageDevice());
            core_.snapImage();
            TaggedImage image = core_.getTaggedImage();
            studio_.live().displayImage(studio_.data().convertTaggedImage(image));
            if (studio_.live().getDisplay() != null) {
               if (liveWin_ != studio_.live().getDisplay()) {
                  liveWin_ = studio_.live().getDisplay();
                  liveWin_.setCustomTitle("Calibrating...");
                  overlay_.setVisible(true);
                  liveWin_.addOverlay(overlay_);
               }
            }
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
         if (CalibrationThread.interrupted()) {
            throw new InterruptedException();
         }
         ImageProcessor snap = snapImageAt(x1,y1,sim);
         Rectangle guessRect = new Rectangle(
                 (int)  ((w-side_small)/2-d.x), (int) ((h-side_small)/2-d.y),
                 side_small, side_small);
         ImageProcessor foundImage = getSubImage(snap, 
                 guessRect.x, guessRect.y, guessRect.width, guessRect.height);
         overlay_.set(guessRect);
         Point2D.Double dChange = measureDisplacement(referenceImage_, 
                 foundImage, display);
         return new Point2D.Double(d.x + dChange.x,d.y + dChange.y);
   }



   private Point2D.Double runSearch(final double dxi, final double dyi, 
           final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {

      double dx = dxi;
      double dy = dyi;
      Point2D.Double d = new Point2D.Double(0., 0.);

      // Now continue to double displacements and match acquired half-size 
      // images with expected half-size images

      for (int i=0;i<25;i++) {

         core_.logMessage(dx+","+dy+","+d);
         if ((2*d.x+side_small/2)>=w/2 || (2*d.y+side_small/2)>=h/2 || (
                 2*d.x-side_small/2)<-(w/2) || (2*d.y-side_small/2)<-(h/2)) {
            break;
         }

         dx *= 2;
         dy *= 2;
         
         d.x *= 2;
         d.y *= 2;

         d = measureDisplacement(x+dx, y+dy, d, false, simulate);
         incrementProgress();
      }
      Point2D.Double stagePos;
      try {
         stagePos = core_.getXYStagePosition();
      } catch (Exception ex) {
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



   private AffineTransform getFirstApprox(final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {
      if (simulate && theSlide == null) {
         theSlide = IJ.getImage().getProcessor();
      }

      if (simulate) {
         x = 0.;
         y = 0.;
      } else {
         Point2D.Double p;
         try {
            p = core_.getXYStagePosition();
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            throw new CalibrationFailedException(ex.getMessage());
         }
         x = p.x;
         y = p.y;
      }

      // First find the smallest detectable displacement.
      ImageProcessor baseImage = snapImageAt(x,y,simulate);

      w = baseImage.getWidth();
      h = baseImage.getHeight();
      int w_small = smallestPowerOf2LessThanOrEqualTo(w/4);
      int h_small = smallestPowerOf2LessThanOrEqualTo(h/4);
      side_small = Math.min(w_small, h_small);

      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
              (-side_small/2+h/2),side_small,side_small);

      pointPairs_.clear();
      pointPairs_.put(new Point2D.Double(0.,0.),new Point2D.Double(x,y));
      runSearch(0.1,0,simulate);

      // Re-acquire the reference image, since we may not be exactly where 
      // we started from after having called runSearch().
      referenceImage_ = getSubImage(baseImage, (-side_small/2+w/2),
            (-side_small/2+h/2),side_small,side_small);

      runSearch(0,0.1,simulate);

      return MathFunctions.generateAffineTransformFromPointPairs(pointPairs_);
   }
   

   private void measureCorner(final AffineTransform firstApprox, final Point c1, 
           final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {
      Point2D.Double c1d = new Point2D.Double(c1.x, c1.y);
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1d, null);
      Point2D.Double c2 = measureDisplacement(s1.x, s1.y, c1d, false, simulate);
      Point2D.Double s2;
      try {
         s2 = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      pointPairs_.put(new Point2D.Double(c2.x, c2.y), s2);
      incrementProgress();
   }
   

   private AffineTransform getSecondApprox(final AffineTransform firstApprox, 
           final boolean simulate)
      throws InterruptedException, CalibrationFailedException
   {
      pointPairs_.clear();
      Point2D.Double s1 = new Point2D.Double();
      int ax = w/2 - side_small/2;
      int ay = h/2 - side_small/2;

      measureCorner(firstApprox, new Point(-ax,-ay), simulate);
      measureCorner(firstApprox, new Point(-ax,ay), simulate);
      measureCorner(firstApprox, new Point(ax,ay), simulate);
      measureCorner(firstApprox, new Point(ax,-ay), simulate);
      try {
         return MathFunctions.generateAffineTransformFromPointPairs(
                 pointPairs_, 2.0, Double.MAX_VALUE);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }


   private AffineTransform runCalibration()
      throws InterruptedException, CalibrationFailedException
   {
      return runCalibration(false);
   }

   
   private AffineTransform runCalibration(boolean simulation)
      throws InterruptedException, CalibrationFailedException
   {
      pointPairs_ = new HashMap<Point2D.Double, Point2D.Double>();
      Point2D.Double xy0 = null;
      try {
         xy0 = core_.getXYStagePosition();
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      final AffineTransform firstApprox = getFirstApprox(simulation);
      setProgress(20);
      final AffineTransform secondApprox = getSecondApprox(firstApprox, simulation);
      if (secondApprox != null) {
         ReportingUtils.logMessage(secondApprox.toString());
      }
      try {
         core_.setXYPosition(xy0.x, xy0.y);
         studio_.live().snap(true);
      }
      catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      overlay_.setVisible(false);
      if (liveWin_ != null) {
         liveWin_.setCustomTitle("Preview");
         liveWin_.removeOverlay(overlay_);
      }
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
               dialog_.calibrationFailed(true);
            }
         });
         return;
      }
      catch (final CalibrationFailedException e) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
               ReportingUtils.showError(e);
               dialog_.calibrationFailed(false);
            }
         });
         return;
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override public void run() {
            dialog_.calibrationDone();
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
      dialog_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
}
