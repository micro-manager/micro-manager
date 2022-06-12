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

import boofcv.alg.misc.GImageStatistics;
import boofcv.alg.misc.PixelMath;
import boofcv.core.image.GConvertImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU16;
import ij.IJ;
import ij.ImagePlus;
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
import mmcorej.MMCoreJ;
import mmcorej.TaggedImage;
import org.micromanager.Studio;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.utils.MathFunctions;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.imageanalysis.AnalysisWindows2D;
import org.micromanager.internal.utils.imageanalysis.BoofCVImageConverter;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * Runs the automatic pixel size calibration routine.
 *
 * @author arthur
 */
public class AutomaticCalibrationThread extends CalibrationThread {
   private final Studio studio_;
   private final CMMCore core_;
   private final PixelCalibratorDialog dialog_;
   private final RectangleOverlay overlay_;

   private DisplayWindow liveWin_;
   private ImageProcessor referenceImage_;
   private GrayF32 windowImage_; // normalized window used for apodization
   private final boolean useWindow_ = false;

   private Point2D.Double xy0_;

   private double x;
   private double y;
   private int w;
   private int h;
   private int sideSmall;
   private static int index_;

   private class PointPair {
      private final Point2D.Double p1_;
      private final Point2D.Double p2_;

      public PointPair(Point2D.Double p1, Point2D.Double p2) {
         p1_ = p1;
         p2_ = p2;
      }

      public Point2D.Double getFirst() {
         return p1_;
      }

      public Point2D.Double getSecond() {
         return p2_;
      }
   }

   private class CalibrationFailedException extends Exception {

      private static final long serialVersionUID = 4749723616733251885L;

      public CalibrationFailedException(String msg) {
         super(msg);
         if (xy0_ != null) {
            try {
               core_.setXYPosition(xy0_.x, xy0_.y);
               studio_.live().snap(true);
            } catch (Exception ex) {
               // annoying but at this point better to not bother the user 
               // with failure after failure
            }
         }
         cleanup();

      }
   }

   AutomaticCalibrationThread(Studio app, PixelCalibratorDialog dialog) {
      studio_ = app;
      core_ = studio_.getCMMCore();
      dialog_ = dialog;
      overlay_ = new RectangleOverlay();
   }

   private void cleanup() {
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

   private ImageProcessor theSlide = null;


   /**
    * Measures the displacement between two images by cross-correlating,
    * and then finding the maximum value.
    * Accurate to one pixel only.  ???  Seems accurate to 0.1 pixel...
    */
   public static Point2D.Double measureDisplacement(ImageProcessor proc1,
                                                    ImageProcessor proc2, boolean display) {
      // Increasing the box size will make finding the procedure more robust with respect to errors.
      final int boxSize = 64;
      final int halfBoxSize = boxSize / 2;
      ImageProcessor result = ImageUtils.crossCorrelate(proc1, proc2);
      ImageProcessor resultCenter = getSubImage(result,
            result.getWidth() / 2 - halfBoxSize,
            result.getHeight() / 2 - halfBoxSize,
            boxSize,
            boxSize);
      resultCenter.setInterpolationMethod(ImageProcessor.BICUBIC);
      ImageProcessor resultCenterScaled = resultCenter.resize(
            resultCenter.getWidth() * 10);
      ImagePlus img = new ImagePlus("Cal" + index_, resultCenterScaled);
      index_++;
      // TODO: use a (Gaussian?) fit rather than finding the maximum
      // doing so may even make it unnecessary to blow up the box 10 fold
      Point p = ImageUtils.findMaxPixel(img);
      Point d = new Point(p.x - img.getWidth() / 2, p.y - img.getHeight() / 2);
      Point2D.Double d2 = new Point2D.Double(d.x / 10., d.y / 10.);
      if (display) {
         img.show();
      }
      return d2;
   }

   private Point2D.Double measureDisplacement(double x1, double y1, Point2D.Double d,
                                              boolean display, boolean sim)
         throws InterruptedException, CalibrationFailedException {
      if (AutomaticCalibrationThread.interrupted()) {
         throw new InterruptedException();
      }
      ImageProcessor snap = snapImageAt(x1, y1, sim);
      Rectangle guessRect = new Rectangle(
            (int) ((w - sideSmall) / 2 - d.x), (int) ((h - sideSmall) / 2 - d.y),
            sideSmall, sideSmall);
      ImageProcessor foundImage = getSubImage(snap,
            guessRect.x, guessRect.y, guessRect.width, guessRect.height);
      foundImage = subtractMinimum(foundImage);
      if (useWindow_) {
         foundImage = multiply(foundImage, windowImage_);
      }
      overlay_.set(guessRect);
      /*
      ImagePlus tmp = new ImagePlus("reference", referenceImage_);
      tmp.show();
      ImagePlus tmp2 = new ImagePlus("found", foundImage);
      tmp2.show();
      */
      Point2D.Double dChange = measureDisplacement(referenceImage_,
            foundImage, display);
      return new Point2D.Double(d.x + dChange.x, d.y + dChange.y);
   }

   /**
    * Returns an ROI as a separate ImageProcessor.
    *
    * @param proc input ImageProcessor
    * @param x    x location of ROI in pixels
    * @param y    y location of ROI in pixels
    * @param w    width in pixels
    * @param h    height in pixels
    * @return ROI as an ImageProcessor
    */
   public static ImageProcessor getSubImage(ImageProcessor proc, int x, int y, int w, int h) {
      FloatProcessor proc2 = new FloatProcessor(w, h);
      proc2.insert(proc, -x, -y);
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
         return simulateAcquire(theSlide, (int) (x + (3 * Math.random() - 1.5)),
               (int) (y + (3 * Math.random() - 1.5)));
      }
      else {
         try {
            Point2D.Double p0 = core_.getXYStagePosition();
            if (p0.distance(x, y) > (dialog_.safeTravelRadius() / 2)) {
               throw new CalibrationFailedException("XY stage safety limit reached.");
            }
            core_.setXYPosition(x, y);
            core_.waitForDevice(core_.getXYStageDevice());
            // even though the stage should no longer move, let the system calm down...
            core_.sleep(100);
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


   private PointPair runSearch(final double dxi, final double dyi, final boolean simulate)
         throws InterruptedException, CalibrationFailedException {

      double dx = dxi;
      double dy = dyi;
      Point2D.Double d = new Point2D.Double(0., 0.);

      // Now continue to double displacements and match acquired half-size 
      // images with expected half-size images

      for (int i = 0; i < 25; i++) {

         core_.logMessage(dx + "," + dy + "," + d);
         if ((2.0f * d.x + sideSmall / 2.0f) >= w / 2.
               || (2 * d.y + sideSmall / 2.) >= h / 2.
               || (2 * d.x - sideSmall / 2.) < -(w / 2.)
               || (2 * d.y - sideSmall / 2.) < -(h / 2.)) {
            break;
         }

         dx *= 2;
         dy *= 2;

         d.x *= 2;
         d.y *= 2;

         d = measureDisplacement(x + dx, y + dy, d, dialog_.debugMode(), simulate);
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
      return new PointPair(new Point2D.Double(d.x, d.y), stagePos);

   }

   /**
    * Multiplies two ImageProcessors.
    *
    * @param input  First ImageProcessor to be multiplied.
    * @param input2 Second ImageProcessor to be multiplies.
    * @return pixel by pixel multiplication of the two ImageProcessors.
    */
   public static ImageProcessor multiply(ImageProcessor input, GrayF32 input2) {
      // TODO: check input and output sizes
      GrayF32 floatInputAsBoofCV = new GrayF32(input.getWidth(), input.getHeight());
      GConvertImage.convert(BoofCVImageConverter.convert(input, false), floatInputAsBoofCV);
      GrayF32 outputBoofCV = new GrayF32(input.getWidth(), input.getHeight());
      PixelMath.multiply(input2, floatInputAsBoofCV, outputBoofCV);
      return BoofCVImageConverter.convert(outputBoofCV, false);
   }

   /**
    * Creates a new ImageProcessor with the minimum value of the input subtracted from all
    * input pixel values.
    *
    * @param input Input ImageProcessor
    * @return ImageProcessor with the minimum of the input pixels subtracted from all pixels.
    */
   public static ImageProcessor subtractMinimum(ImageProcessor input) {
      GrayU16 shortInputAsBoofCV = new GrayU16(input.getWidth(), input.getHeight());
      GConvertImage.convert(BoofCVImageConverter.convert(input, false), shortInputAsBoofCV);
      double min = GImageStatistics.min(shortInputAsBoofCV);
      GrayU16 resultAsBoofCV = new GrayU16(input.getWidth(), input.getHeight());
      PixelMath.minus(shortInputAsBoofCV, (int) min, resultAsBoofCV);
      return BoofCVImageConverter.convert(resultAsBoofCV, false);
   }

   private int smallestPowerOf2LessThanOrEqualTo(int x) {
      return 1 << ((int) Math.floor(Math.log(x) / Math.log(2)));
   }


   private AffineTransform getFirstApprox(final boolean simulate)
         throws InterruptedException, CalibrationFailedException {
      if (simulate && theSlide == null) {
         theSlide = IJ.getImage().getProcessor();
      }

      if (simulate) {
         x = 0.;
         y = 0.;
      }
      else {
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
      ImageProcessor baseImage = snapImageAt(x, y, simulate);

      w = baseImage.getWidth();
      h = baseImage.getHeight();
      int wSmall = smallestPowerOf2LessThanOrEqualTo(w / 4);
      int hSmall = smallestPowerOf2LessThanOrEqualTo(h / 4);
      sideSmall = Math.min(wSmall, hSmall);
      referenceImage_ = getSubImage(baseImage, (-sideSmall / 2 + w / 2),
            (-sideSmall / 2 + h / 2), sideSmall, sideSmall);
      referenceImage_ = subtractMinimum(referenceImage_);

      if (useWindow_) {
         float[] windowPixels = AnalysisWindows2D.hanWindow1DA(sideSmall);
         windowImage_ = new GrayF32();
         windowImage_.setData(windowPixels);
         windowImage_.reshape(sideSmall, sideSmall);
         referenceImage_ = multiply(referenceImage_, windowImage_);
      }


      Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<>();
      pointPairs.put(new Point2D.Double(0., 0.), new Point2D.Double(x, y));
      PointPair pp = runSearch(0.1, 0, simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());


      // Re-acquire the reference image, since we may not be exactly where 
      // we started from after having called runSearch().
      referenceImage_ = getSubImage(baseImage, (-sideSmall / 2 + w / 2),
            (-sideSmall / 2 + h / 2), sideSmall, sideSmall);
      referenceImage_ = subtractMinimum(referenceImage_);
      if (useWindow_) {
         referenceImage_ = multiply(referenceImage_, windowImage_);
      }

      pp = runSearch(0, 0.1, simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());

      return MathFunctions.generateAffineTransformFromPointPairs(pointPairs);
   }


   private PointPair measureCorner(final AffineTransform firstApprox, final Point c1,
                                   final boolean simulate)
         throws InterruptedException, CalibrationFailedException {
      Point2D.Double c1d = new Point2D.Double(c1.x, c1.y);
      Point2D.Double s1 = (Point2D.Double) firstApprox.transform(c1d, null);
      Point2D.Double c2 = measureDisplacement(s1.x, s1.y, c1d, dialog_.debugMode(), simulate);
      Point2D.Double s2;
      try {
         s2 = core_.getXYStagePosition();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         throw new CalibrationFailedException(ex.getMessage());
      }
      incrementProgress();

      return new PointPair(new Point2D.Double(c2.x, c2.y), s2);
   }


   private AffineTransform getSecondApprox(final AffineTransform firstApprox,
                                           final boolean simulate)
         throws InterruptedException, CalibrationFailedException {
      Map<Point2D.Double, Point2D.Double> pointPairs = new HashMap<>();
      // used to be side_small / 2, but it works better
      // to stay a bit away from the extreme corners
      int ax = w / 2 - sideSmall;
      int ay = h / 2 - sideSmall;

      PointPair pp = measureCorner(firstApprox, new Point(-ax, -ay), simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());
      pp = measureCorner(firstApprox, new Point(-ax, ay), simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());
      pp = measureCorner(firstApprox, new Point(ax, ay), simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());
      pp = measureCorner(firstApprox, new Point(ax, -ay), simulate);
      pointPairs.put(pp.getFirst(), pp.getSecond());
      try {
         // pointpairs, max error in pixels, max error in microns
         return MathFunctions.generateAffineTransformFromPointPairs(
               pointPairs, 5.0, Double.MAX_VALUE);
      } catch (Exception ex) {
         ReportingUtils.logError(ex.getMessage());
      }
      return null;
   }


   private AffineTransform runCalibration()
         throws InterruptedException, CalibrationFailedException {
      return runCalibration(false);
   }

   private AffineTransform runCalibration(boolean simulation)
         throws InterruptedException, CalibrationFailedException {
      try {
         xy0_ = core_.getXYStagePosition();
      } catch (Exception e) {
         throw new CalibrationFailedException(e.getMessage());
      }
      index_ = 0;
      final AffineTransform firstApprox = getFirstApprox(simulation);
      setProgress(20);
      final AffineTransform secondApprox = getSecondApprox(firstApprox, simulation);
      if (secondApprox != null) {
         ReportingUtils.logMessage(secondApprox.toString());
      }
      try {
         core_.setXYPosition(xy0_.x, xy0_.y);
         studio_.live().snap(true);
      } catch (Exception e) {
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
         try {
            String binning = core_.getProperty(core_.getCameraDevice(),
                  MMCoreJ.getG_Keyword_Binning());
            int binNr = NumberUtils.coreStringToInt(binning);
            if (binNr != 1) {
               result_.scale(1.0 / (double) binNr, 1.0 / (double) binNr);
            }
         } catch (Exception ex) {
            studio_.logs().logError("Error while determining binning");
         }
      } catch (InterruptedException e) {
         // User canceled
         SwingUtilities.invokeLater(() -> {
            cleanup();
            dialog_.calibrationFailed(true);
         });
         return;
      } catch (final CalibrationFailedException e) {
         SwingUtilities.invokeLater(() -> {
            cleanup();
            ReportingUtils.showError(e);
            dialog_.calibrationFailed(false);
         });
         return;
      }
      SwingUtilities.invokeLater(dialog_::calibrationDone);
   }

   private synchronized void incrementProgress() {
      progress_++;
      dialog_.update();
   }

   synchronized void setProgress(int value) {
      progress_ = value;
   }
}
