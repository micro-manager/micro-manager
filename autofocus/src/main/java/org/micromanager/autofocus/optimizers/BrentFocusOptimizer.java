package org.micromanager.autofocus.optimizers;

import ij.process.ImageProcessor;
import java.text.DecimalFormat;
import java.util.function.Function;
import mmcorej.CMMCore;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.internal.utils.TextUtils;

/**
 * This class uses the Brent Method alongside control of MMStudio's default camera
 * and Z-stage to perform autofocusing. The Brent Method optimizer will try to maximize
 * the value returned by the `imgScoringFunction` so this function should return
 * larger values as the image sharpness increases.
 *
 * @author Nick Anthony
 */
public class BrentFocusOptimizer implements FocusOptimizer {
   // Note on the tolerance settings for the Brent optimizer:
   //
   // The reason BrentOptimizer needs both a relative and absolute tolerance
   // (the _rel_ and _abs_ arguments to the constructor) is that it is
   // designed for use with arbitrary floating-point numbers. A given
   // floating point type (e.g. double) always has a certain relative
   // precision, but larger values have less absolute precision (e.g.
   // 1.0e100 + 1.0 == 1.0e100).
   //
   // So if the result of the optimization is a large FP number, having just
   // an absolute tolerance (say, 0.01) would result in the algorithm
   // never terminating (or failing when it reaches the max iterations
   // constraint, if any). Using a reasonable relative tolerance can prevent
   // this and allow the optimization to finish when it reaches the
   // nearly-best-achievable optimum given the FP type.
   //
   // Z stage positions, of course, don't have this property and behave like
   // a fixed-point data type, so only the absolute tolerance is important.
   // As long as we have a value of _abs_ that is greater than the stage's
   // minimum step size, the optimizer should always converge (barring other
   // issues, such as a pathological target function (=focus score)), as
   // long as we don't run into the FP data type limitations.
   //
   // So here we need to select _rel_ to be large enough for
   // the `double` type and small enough to be negligible in terms of stage
   // position values. Since we don't expect huge values for the stage
   // position, we can be relatively conservative and use a value that is
   // much larger than the recommended minimum (2 x epsilon).
   //
   // For the user, it remains important to set a reasonable absolute
   // tolerance.
   //
   // 1.0e-9 is a reasonable relative tolerance to use here, since it
   // translates to 1 nm when the stage position is 1 m (1e6 um). Thinking
   // of piezo stages, a generous position of 1000 um would give relative
   // tolerance of 1 pm, again small enough to be negligible.
   //
   // The machine epsilon for double is 2e-53, so we could use a much
   // smaller value (down to 2e-27 or so) if we wanted to, but OughtaFocus
   // has been tested for quite some time with _rel_ = 1e-9 (the default
   // relative tolerance in commons-math 2) and appeared to function
   // correctly.
   private static final double BRENT_RELATIVE_TOLERANCE = 1e-9;
   private int imageCount_;
   private Studio studio_;
   private String zDrive_;
   private long startTimeMs_;
   private boolean displayImages_ = false;
   private double searchRange_ = 10;
   private double absoluteTolerance_ = 1.0;
   private final Function<ImageProcessor, Double> imgScoringFunction_;

   /**
    * The constructor takes a function that calculates a focus score.
    *
    * @param imgScoringFunction A function that takes an ImageJ `ImageProcessor`
    *                           and returns a double indicating a measure of the
    *                           image sharpness. A large value indicates a sharper image.
    */
   public BrentFocusOptimizer(Function<ImageProcessor, Double> imgScoringFunction) {
      imgScoringFunction_ = imgScoringFunction;
   }

   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void setZDrive(String driveName) {
      zDrive_ = driveName;
   }

   /**
    * Setter for Display Image flag.
    *
    * @param display If `true` then the images taken by the focuser will be displayed in
    *                real-time.
    */
   @Override
   public void setDisplayImages(boolean display) {
      displayImages_ = display;
   }

   @Override
   public boolean getDisplayImages() {
      return displayImages_;
   }

   @Override
   public int getImageCount() {
      return imageCount_;
   }

   @Override
   public void setSearchRange(double searchRange) {
      searchRange_ = searchRange;
   }

   @Override
   public double getSearchRange() {
      return searchRange_;
   }

   @Override
   public void setAbsoluteTolerance(double tolerance) {
      absoluteTolerance_ = tolerance;
   }

   @Override
   public double getAbsoluteTolerance() {
      return absoluteTolerance_;
   }

   /**
    * Runs the actual algorithm.
    *
    * @return Optimal Z stage position.
    * @throws Exception A common exception is failure to set the Z position in the hardware
    */
   @Override
   public double runAutofocusAlgorithm() throws Exception {
      if (studio_ == null)  {
         throw new Exception("Programming error: Studio is not set.");
      }
      if (zDrive_.isEmpty()) {
         zDrive_ = studio_.getCMMCore().getFocusDevice();
      }

      startTimeMs_ = System.currentTimeMillis();

      UnivariateObjectiveFunction uof = new UnivariateObjectiveFunction(
              (double d) -> {
                 try {
                    return measureFocusScore(zDrive_, d);
                 } catch (Exception e) {
                    throw new RuntimeException(e);
                 }
              }
      );

      BrentOptimizer brentOptimizer =
              new BrentOptimizer(BRENT_RELATIVE_TOLERANCE, absoluteTolerance_);

      imageCount_ = 0;

      double z = studio_.core().getPosition(zDrive_);

      UnivariatePointValuePair result = brentOptimizer.optimize(uof,
              GoalType.MAXIMIZE,
              new MaxEval(100),
              new SearchInterval(z - searchRange_ / 2, z + searchRange_ / 2));
      studio_.logs().logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterations()
              + ", z=" + TextUtils.FMT2.format(result.getPoint())
              + ", dz=" + TextUtils.FMT2.format(result.getPoint() - z)
              + ", t=" + (System.currentTimeMillis() - startTimeMs_));
      return result.getPoint();
   }

   private double measureFocusScore(String zDrive, double z) throws Exception {
      CMMCore core = studio_.getCMMCore();
      long start = System.currentTimeMillis();
      try {
         core.setPosition(zDrive, z);
         core.waitForDevice(zDrive);
         final long tZ = System.currentTimeMillis() - start;
         core.waitForDevice(core.getCameraDevice());
         final Image img = studio_.live().snap(displayImages_).get(0);
         if (img == null) {
            throw new Exception("Failed to acquire image.");
         }
         long tI = System.currentTimeMillis() - start - tZ;
         ImageProcessor proc = studio_.data().ij().createProcessor(img);
         double score = imgScoringFunction_.apply(proc);
         long tC = System.currentTimeMillis() - start - tZ - tI;
         studio_.logs().logMessage("OughtaFocus: image=" + imageCount_++
                 + ", t=" + (System.currentTimeMillis() - startTimeMs_)
                 + ", z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score)
                 + ", Tz=" + tZ + ", Ti=" + tI + ", Tc=" + tC);
         return score;
      } catch (Exception e) {
         String zString = new DecimalFormat("0.00#").format(z);
         throw new Exception(e.getMessage() + ". Position: " + zString, e);
      }
   }

}
