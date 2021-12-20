///////////////////////////////////////////////////////////////////////////////
//FILE:           OughtaFocus.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for micro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Arthur Edelstein, October 2010
//                Based on SimpleAutofocus by Karl Hoover
//                and the Autofocus "H&P" plugin
//                by Pakpoom Subsoontorn & Hernan Garcia
//                Contributions by Jon Daniels (ASI): FFTBandpass, MedianEdges 
//                      and Tenengrad
//                Chris Weisiger: 2.0 port
//                Nico Stuurman: 2.0 port and Math3 port
//                Nick Anthony: Refactoring
//
//COPYRIGHT:      University of California San Francisco
//                
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $

package org.micromanager.autofocus;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.function.Function;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.org.json.JSONException;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;
import org.micromanager.AutofocusPlugin;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Arthur Edelstein's Autofocus plugin using the Brent Optimizer.
 */
@Plugin(type = AutofocusPlugin.class)
public class OughtaFocus extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin {

   private Studio studio_;
   private static final String AF_DEVICE_NAME = "OughtaFocus";
   private static final String SEARCH_RANGE = "SearchRange_um";
   private static final String TOLERANCE = "Tolerance_um";
   private static final String CROP_FACTOR = "CropFactor";
   private static final String CHANNEL = "Channel";
   private static final String EXPOSURE = "Exposure";
   private static final String SHOW_IMAGES = "ShowImages";
   private static final String SCORING_METHOD = "Maximize";
   private static final String[] SHOWVALUES = {"Yes", "No"};
   private static final String FFT_UPPER_CUTOFF = "FFTUpperCutoff(%)";
   private static final String FFT_LOWER_CUTOFF = "FFTLowerCutoff(%)";

   private final ImgSharpnessAnalysis fcsAnalysis_ = new ImgSharpnessAnalysis();
   private final BrentFocusOptimizer afOptimizer_;

   private String channel_ = "";
   private double exposure_ = 100;
   private boolean displayImages_ = false;
   private double cropFactor_ = 1;


   public OughtaFocus() {
      afOptimizer_ = new BrentFocusOptimizer(
            fcsAnalysis_::compute
      );
      
      super.createProperty(SEARCH_RANGE,
            NumberUtils.doubleToDisplayString(afOptimizer_.getSearchRange()));
      super.createProperty(TOLERANCE,
            NumberUtils.doubleToDisplayString(afOptimizer_.getAbsoluteTolerance()));
      super.createProperty(CROP_FACTOR,
            NumberUtils.doubleToDisplayString(cropFactor_));
      super.createProperty(EXPOSURE,
            NumberUtils.doubleToDisplayString(exposure_));
      super.createProperty(FFT_LOWER_CUTOFF,
            NumberUtils.doubleToDisplayString(fcsAnalysis_.getFFTLowerCutoff()));
      super.createProperty(FFT_UPPER_CUTOFF,
            NumberUtils.doubleToDisplayString(fcsAnalysis_.getFFTUpperCutoff()));
      super.createProperty(SHOW_IMAGES, SHOWVALUES[1], SHOWVALUES);
      super.createProperty(SCORING_METHOD, 
              fcsAnalysis_.getComputationMethod().name(), 
              ImgSharpnessAnalysis.Method.getNames()
      );
      super.createProperty(CHANNEL, "");
   }

   @Override
   public void applySettings() {
      try {
         afOptimizer_.setSearchRange(
               NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE)));
         afOptimizer_.setAbsoluteTolerance(
               NumberUtils.displayStringToDouble(getPropertyValue(TOLERANCE)));
         cropFactor_ = NumberUtils.displayStringToDouble(getPropertyValue(CROP_FACTOR));
         cropFactor_ = clip(0.01, cropFactor_, 1.0);
         channel_ = getPropertyValue(CHANNEL);
         exposure_ = NumberUtils.displayStringToDouble(getPropertyValue(EXPOSURE));
         double fftLowerCutoff =
               NumberUtils.displayStringToDouble(getPropertyValue(FFT_LOWER_CUTOFF));
         fftLowerCutoff = clip(0.0, fftLowerCutoff, 100.0);
         double fftUpperCutoff =
               NumberUtils.displayStringToDouble(getPropertyValue(FFT_UPPER_CUTOFF));
         fftUpperCutoff = clip(0.0, fftUpperCutoff, 100.0);
         fcsAnalysis_.setFFTCutoff(fftLowerCutoff, fftUpperCutoff);
         fcsAnalysis_.setComputationMethod(
               ImgSharpnessAnalysis.Method.valueOf(getPropertyValue(SCORING_METHOD)));
         displayImages_ = getPropertyValue(SHOW_IMAGES).contentEquals("Yes");
         afOptimizer_.setDisplayImages(displayImages_);
      } catch (MMException | ParseException ex) {
         studio_.logs().logError(ex);
      }
   }

   @Override
   public double fullFocus() throws Exception {
      applySettings();
      CMMCore core = studio_.getCMMCore();
      Rectangle oldROI = core.getROI();


      Configuration oldState = null;
      if (channel_.length() > 0) {
         String chanGroup = core.getChannelGroup();
         oldState = core.getConfigGroupState(chanGroup);
         core.setConfig(chanGroup, channel_);
      }

      // avoid wasting time on setting roi if it is the same
      if (cropFactor_ < 1.0) {
         int w = (int) (oldROI.width * cropFactor_);
         int h = (int) (oldROI.height * cropFactor_);
         int x = oldROI.x + (oldROI.width - w) / 2;
         int y = oldROI.y + (oldROI.height - h) / 2;
         studio_.app().setROI(new Rectangle(x, y, w, h));
         core.waitForDevice(core.getCameraDevice());
      }
      final double oldExposure = core.getExposure();
      core.setExposure(exposure_);

      final double z = afOptimizer_.runAutofocusAlgorithm();

      if (cropFactor_ < 1.0) {
         studio_.app().setROI(oldROI);
         core.waitForDevice(core.getCameraDevice());
      }
      if (oldState != null) {
         core.setSystemState(oldState);
      }
      core.setExposure(oldExposure);
      core.setPosition(z);
      core.waitForDevice(core.getFocusDevice());
      return z;
   }

   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getNumberOfImages() {
      return afOptimizer_.getImageCount();
   }

   @Override
   public String getVerboseStatus() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public double getCurrentFocusScore() {
      CMMCore core = studio_.getCMMCore();
      double score = 0.0;
      try {
         final double z = core.getPosition(core.getFocusDevice());
         core.waitForDevice(core.getCameraDevice());
         core.snapImage();
         TaggedImage img = core.getTaggedImage();
         if (displayImages_) {
            studio_.live().displayImage(studio_.data().convertTaggedImage(img));
         }
         ImageProcessor proc = ImageUtils.makeProcessor(core, img);
         score = computeScore(proc);
         studio_.logs().logMessage("OughtaFocus: z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score));
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
      return score;
   }
   
   @Override
   public double computeScore(final ImageProcessor proc) {
      return fcsAnalysis_.compute(proc);
   }
   
   @Override
   public void setContext(Studio app) {
      studio_ = app;
      studio_.events().registerForEvents(this);
      afOptimizer_.setContext(studio_);
   }

   @Override
   public PropertyItem[] getProperties() {
      CMMCore core = studio_.getCMMCore();
      String channelGroup = core.getChannelGroup();
      StrVector channels = core.getAvailableConfigs(channelGroup);
      String[] allowedChannels = new String[(int) channels.size() + 1];
      allowedChannels[0] = "";

      try {
         PropertyItem p = getProperty(CHANNEL);
         boolean found = false;
         for (int i = 0; i < channels.size(); i++) {
            allowedChannels[i + 1] = channels.get(i);
            if (p.value.equals(channels.get(i))) {
               found = true;
            }
         }
         p.allowed = allowedChannels;
         if (!found) {
            p.value = allowedChannels[0];
         }
         setProperty(p);
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      return super.getProperties();
   }

   private static double clip(double min, double val, double max) {
      return Math.min(Math.max(min, val), max);
   }

   @Override
   public String getName() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getHelpText() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getVersion() {
      return "2.0";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2010-2016";
   }


   /**
    * This class uses the Brent Method alongside control of MMStudio's default camera
    * and Z-stage to perform autofocusing. The Brent Method optimizer will try to maximize
    * the value returned by the `imgScoringFunction` so this function should return
    * larger values as the image sharpness increases.
    *
    * @author Nick Anthony
    */
   private static class BrentFocusOptimizer {
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
      // translates to 1 nm when the stage position is 1 m (1e6 µm). Thinking
      // of piezo stages, a generous position of 1000 µm would give relative
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
      private long startTimeMs_;
      private boolean displayImages_ = false;
      private double searchRange_ = 10;
      private double absoluteTolerance_ = 1.0;
      private final Function<ImageProcessor, Double> imgScoringFunction_;

      /**
       * The constructor takes a function that calculates a focus score.
       *
       * @param imgScoringFunction A function that takes an ImageJ `ImageProcessor`
       *     and returns a double indicating a measure of the images sharpness. A large
       *     value indicates a sharper image.
       */
      public BrentFocusOptimizer(Function<ImageProcessor, Double> imgScoringFunction) {
         imgScoringFunction_ = imgScoringFunction;
      }

      public void setContext(Studio studio) {
         studio_ = studio;
      }

      /**
       * Setter for Display Image flag.
       *
       * @param display If `true` then the images taken by the focuser will be displayed in real-time.
       */
      public void setDisplayImages(boolean display) {
         displayImages_ = display;
      }

      public boolean getDisplayImages() {
         return displayImages_;
      }

      public int getImageCount() {
         return imageCount_;
      }

      public void setSearchRange(double searchRange) {
         searchRange_ = searchRange;
      }

      public double getSearchRange() {
         return searchRange_;
      }

      public void setAbsoluteTolerance(double tolerance) {
         absoluteTolerance_ = tolerance;
      }

      public double getAbsoluteTolerance() {
         return absoluteTolerance_;
      }

      /**
       * Runs the actual algorithm.
       *
       * @return Focus Score.
       * @throws Exception A common exception is failure to set the Z position in the hardware
       */
      public double runAutofocusAlgorithm() throws Exception {
         startTimeMs_ = System.currentTimeMillis();

         UnivariateObjectiveFunction uof = new UnivariateObjectiveFunction(
                 (double d) -> {
                    try {
                       return measureFocusScore(d);
                    } catch (Exception e) {
                       throw new RuntimeException(e);
                    }
                 }
         );

         BrentOptimizer brentOptimizer =
                 new BrentOptimizer(BRENT_RELATIVE_TOLERANCE, absoluteTolerance_);

         imageCount_ = 0;

         CMMCore core = studio_.getCMMCore();
         double z = core.getPosition(core.getFocusDevice());

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

      private double measureFocusScore(double z) throws Exception {
         CMMCore core = studio_.getCMMCore();
         long start = System.currentTimeMillis();
         try {
            core.setPosition(z);
            core.waitForDevice(core.getFocusDevice());
            final long tZ = System.currentTimeMillis() - start;

            TaggedImage img;
            core.waitForDevice(core.getCameraDevice());
            core.snapImage();
            final TaggedImage img1 = core.getTaggedImage();
            img = img1;
            if (displayImages_) {
               SwingUtilities.invokeLater(() -> {
                  try {
                     studio_.live().displayImage(studio_.data().convertTaggedImage(img1));
                  } catch (JSONException | IllegalArgumentException e) {
                     studio_.logs().showError(e);
                  }
               });
            }

            long tI = System.currentTimeMillis() - start - tZ;
            ImageProcessor proc = makeMonochromeProcessor(core, getMonochromePixels(img));
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


      private static ImageProcessor makeMonochromeProcessor(CMMCore core, Object pixels) {
         //TODO replace these methods with studio_.data().getImageJConverter().toProcessor()
         int w = (int) core.getImageWidth();
         int h = (int) core.getImageHeight();
         if (pixels instanceof byte[]) {
            return new ByteProcessor(w, h, (byte[]) pixels, null);
         } else if (pixels instanceof short[]) {
            return new ShortProcessor(w, h, (short[]) pixels, null);
         } else {
            return null;
         }
      }

      private static Object getMonochromePixels(TaggedImage image) throws Exception {
         if (MDUtils.isRGB32(image)) {
            final byte[][] planes = ImageUtils.getColorPlanesFromRGB32((byte[]) image.pix);
            final int numPixels = planes[0].length;
            byte[] monochrome = new byte[numPixels];
            for (int j = 0; j < numPixels; ++j) {
               monochrome[j] = (byte) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
            }
            return monochrome;
         } else if (MDUtils.isRGB64(image)) {
            final short[][] planes = ImageUtils.getColorPlanesFromRGB64((short[]) image.pix);
            final int numPixels = planes[0].length;
            short[] monochrome = new short[numPixels];
            for (int j = 0; j < numPixels; ++j) {
               monochrome[j] = (short) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
            }
            return monochrome;
         } else {
            return image.pix;  // Presumably already a gray image.
         }
      }
   }
}