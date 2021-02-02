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

import ij.gui.OvalRoi;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Rectangle;
import java.text.ParseException;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.StrVector;
import mmcorej.TaggedImage;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;
import org.apache.commons.math3.optim.univariate.UnivariatePointValuePair;

import mmcorej.org.json.JSONException;

import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

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
   private final static String[] SCORINGMETHODS = {"Edges", "StdDev", "Mean", 
      "NormalizedVariance", "SharpEdges", "Redondo", "Volath", "Volath5", 
      "MedianEdges", "Tenengrad", "FFTBandpass"};
   private final static String FFT_UPPER_CUTOFF = "FFTUpperCutoff(%)";
   private final static String FFT_LOWER_CUTOFF = "FFTLowerCutoff(%)";

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

   private double searchRange = 10;
   private double absTolerance = 1.0;
   private double cropFactor = 1;
   private String channel = "";
   private double exposure = 100;
   private String show = "No";
   private String scoringMethod = "Edges";
   private double fftUpperCutoff = 14;
   private double fftLowerCutoff = 2.5;
   private int imageCount_;
   private long startTimeMs_;
   private double startZUm_;
   private boolean liveModeOn_;

   public OughtaFocus() {
      super.createProperty(SEARCH_RANGE, NumberUtils.doubleToDisplayString(searchRange));
      super.createProperty(TOLERANCE, NumberUtils.doubleToDisplayString(absTolerance));
      super.createProperty(CROP_FACTOR, NumberUtils.doubleToDisplayString(cropFactor));
      super.createProperty(EXPOSURE, NumberUtils.doubleToDisplayString(exposure));
      super.createProperty(FFT_LOWER_CUTOFF, NumberUtils.doubleToDisplayString(fftLowerCutoff));
      super.createProperty(FFT_UPPER_CUTOFF, NumberUtils.doubleToDisplayString(fftUpperCutoff));
      super.createProperty(SHOW_IMAGES, show, SHOWVALUES);
      super.createProperty(SCORING_METHOD, scoringMethod, SCORINGMETHODS);
      super.createProperty(CHANNEL, "");
   }

   @Override
   public void applySettings() {
      try {
         searchRange = NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE));
         absTolerance = NumberUtils.displayStringToDouble(getPropertyValue(TOLERANCE));
         cropFactor = NumberUtils.displayStringToDouble(getPropertyValue(CROP_FACTOR));
         cropFactor = clip(0.01, cropFactor, 1.0);
         channel = getPropertyValue(CHANNEL);
         exposure = NumberUtils.displayStringToDouble(getPropertyValue(EXPOSURE));
         fftLowerCutoff = NumberUtils.displayStringToDouble(getPropertyValue(FFT_LOWER_CUTOFF));
         fftLowerCutoff = clip(0.0, fftLowerCutoff, 100.0);
         fftUpperCutoff = NumberUtils.displayStringToDouble(getPropertyValue(FFT_UPPER_CUTOFF));
         fftUpperCutoff = clip(0.0, fftUpperCutoff, 100.0);
         show = getPropertyValue(SHOW_IMAGES);
         scoringMethod = getPropertyValue(SCORING_METHOD);

      } catch (MMException | ParseException ex) {
         studio_.logs().logError(ex);
      }
   }

   @Override
   public double fullFocus() throws Exception {
      startTimeMs_ = System.currentTimeMillis();
      applySettings();
      Rectangle oldROI = studio_.core().getROI();
      CMMCore core = studio_.getCMMCore();
      liveModeOn_ = studio_.live().isLiveModeOn();

      //ReportingUtils.logMessage("Original ROI: " + oldROI);
      int w = (int) (oldROI.width * cropFactor);
      int h = (int) (oldROI.height * cropFactor);
      int x = oldROI.x + (oldROI.width - w) / 2;
      int y = oldROI.y + (oldROI.height - h) / 2;
      Rectangle newROI = new Rectangle(x, y, w, h);
      //ReportingUtils.logMessage("Setting ROI to: " + newROI);
      Configuration oldState = null;
      if (channel.length() > 0) {
         String chanGroup = core.getChannelGroup();
         oldState = core.getConfigGroupState(chanGroup);
         core.setConfig(chanGroup, channel);
      }

      // avoid wasting time on setting roi if it is the same
      if (cropFactor < 1.0) {
         studio_.app().setROI(newROI);
         core.waitForDevice(core.getCameraDevice());
      }
      double oldExposure = core.getExposure();
      core.setExposure(exposure);

      double z = runAutofocusAlgorithm();

      if (cropFactor < 1.0) {
         studio_.app().setROI(oldROI);
         core.waitForDevice(core.getCameraDevice());
      }
      if (oldState != null) {
         core.setSystemState(oldState);
      }
      core.setExposure(oldExposure);
      setZPosition(z);
      return z;
   }

   private static class LocalException extends RuntimeException {
     // The x value that caused the problem.
     private final double x_;
     private final Exception ex_;

     public LocalException(double x, Exception ex) {
         x_ = x;
         ex_ = ex;
     }

     public double getX() {
         return x_;
     }
     
     public Exception getException() {
        return ex_;
     } 
 }
   
   private double runAutofocusAlgorithm() throws Exception {
      UnivariateFunction scoreFun = (double d) -> {
         try {
            return measureFocusScore(d);
         } catch (Exception e) {
            throw new LocalException(d, e);
         }
      };
      
      UnivariateObjectiveFunction uof = new UnivariateObjectiveFunction(scoreFun);

      BrentOptimizer brentOptimizer =
         new BrentOptimizer(BRENT_RELATIVE_TOLERANCE, absTolerance);

      imageCount_ = 0;

      CMMCore core = studio_.getCMMCore();
      double z = core.getPosition(core.getFocusDevice());
      startZUm_ = z;
      
      UnivariatePointValuePair result = brentOptimizer.optimize(uof, 
              GoalType.MAXIMIZE,
              new MaxEval(100),
              new SearchInterval(z - searchRange / 2, z + searchRange / 2));
      studio_.logs().logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterations()
              + ", z=" + TextUtils.FMT2.format(result.getPoint())
              + ", dz=" + TextUtils.FMT2.format(result.getPoint() - startZUm_)
              + ", t=" + (System.currentTimeMillis() - startTimeMs_));
      return result.getPoint();
   }

   private void setZPosition(double z) throws Exception {
      CMMCore core = studio_.getCMMCore();
      String focusDevice = core.getFocusDevice();
      core.setPosition(focusDevice, z);
      core.waitForDevice(focusDevice);
   }


   public static ImageProcessor makeMonochromeProcessor(CMMCore core, Object pixels) {
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

   public static Object getMonochromePixels(TaggedImage image) throws JSONException, Exception {
      if (MDUtils.isRGB32(image)) {
         final byte[][] planes = ImageUtils.getColorPlanesFromRGB32((byte[]) image.pix);
         final int numPixels = planes[0].length;
         byte[] monochrome = new byte[numPixels];
         for (int j=0;j<numPixels;++j) {
            monochrome[j] = (byte) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
         }
         return monochrome;
      } else if (MDUtils.isRGB64(image)) {
         final short[][] planes = ImageUtils.getColorPlanesFromRGB64((short[]) image.pix);
         final int numPixels = planes[0].length;
         short[] monochrome = new short[numPixels];
         for (int j=0;j<numPixels;++j) {
            monochrome[j] = (short) ((planes[0][j] + planes[1][j] + planes[2][j]) / 3);
         }
         return monochrome;
      } else {
         return image.pix;  // Presumably already a gray image.
      }
   }


   public double measureFocusScore(double z) throws Exception {
      CMMCore core = studio_.getCMMCore();
      long start = System.currentTimeMillis();
      try {
         setZPosition(z);
         long tZ = System.currentTimeMillis() - start;

         TaggedImage img;
         if (liveModeOn_) {
            img = core.getLastTaggedImage();
         } else {
            core.waitForDevice(core.getCameraDevice());
            core.snapImage();
            final TaggedImage img1 = core.getTaggedImage();
            img = img1;
            if (show.contentEquals("Yes")) {
               SwingUtilities.invokeLater(() -> {
                  try {
                     studio_.live().displayImage(studio_.data().convertTaggedImage(img1));
                  }
                  catch (JSONException | IllegalArgumentException e) {
                     studio_.logs().showError(e);
                  }
               });
            }
         }
         long tI = System.currentTimeMillis() - start - tZ;
         ImageProcessor proc = makeMonochromeProcessor(core, getMonochromePixels(img));
         double score = computeScore(proc);
         long tC = System.currentTimeMillis() - start - tZ - tI;
         studio_.logs().logMessage("OughtaFocus: image=" + imageCount_++
                 + ", t=" + (System.currentTimeMillis() - startTimeMs_)
                 + ", z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score)
                 + ", Tz=" + tZ + ", Ti=" + tI + ", Tc=" + tC);
         return score;
      } catch (Exception e) {
         studio_.logs().logError(e);
         throw e;
      }
   }

   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getNumberOfImages() {
      return imageCount_;
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
         double z = core.getPosition(core.getFocusDevice());
         core.waitForDevice(core.getCameraDevice());
         core.snapImage();
         TaggedImage img = core.getTaggedImage();
         if (show.contentEquals("Yes")) {
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

   private double computeEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   private double computeSharpEdges(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      ImageProcessor proc1 = proc.duplicate();
      // mean intensity of the edge map
      proc1.sharpen();
      proc1.findEdges();
      double meanEdge = proc1.getStatistics().mean;

      return meanEdge / meanIntensity;
   }

   private double computeMean(ImageProcessor proc) {
      return proc.getStatistics().mean;
   }

   private double computeNormalizedStdDev(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      return stats.stdDev / stats.mean;
   }

   private double computeNormalizedVariance(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      return (stats.stdDev * stats.stdDev) / stats.mean;
   }

   
   // this is NOT a traditional Laplace filter; the "center" weight is
   // actually the bottom-center cell of the 3x3 matrix.  AFAICT it's a
   // typo in the source paper, but works better than the traditional
   // Laplace filter.
   //
   // Redondo R, Bueno G, Valdiviezo J et al.  "Autofocus evaluation for
   // brightfield microscopy pathology", J Biomed Opt 17(3) 036008 (2012)
   //
   // from
   //
   // Russel M, Douglas T.  "Evaluation of autofocus algorithms for
   // tuberculosis microscopy". Proc 29th International Conference of the
   // IEEE EMBS, Lyon, 3489-3492 (22-26 Aug 2007)
   private double computeRedondo(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;

      for (int i = 1; i < w - 1; ++i) {
         for (int j = 1; j < h - 1; ++j) {
            double p = proc.getPixel(i - 1, j)
                    + proc.getPixel(i + 1, j)
                    + proc.getPixel(i, j - 1)
                    + proc.getPixel(i, j + 1)
                    - 4 * (proc.getPixel(i - 1, j));
            sum += (p * p);
         }
      }

      return sum;
   }

     
   /**
    * From "Autofocusing Algorithm Selection in Computer Microscopy" 
    * (doi: 10.1109/IROS.2005.1545017). 
    * 2016 paper (doi:10.1038/nbt.3708) concludes this is best  most 
    * non-spectral metric for their light sheet microscopy application
    * @author Jon
    */
   private double computeTenengrad(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
      int[] ken2 = {1, 2, 1, 0, 0, 0, -1, -2, -1};

      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.pow(proc.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2);
         }
      }
      return sum;
   }
   
   // Volath's 1D autocorrelation
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133-146 (1988).
   private double computeVolath(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum1 = 0.0;
      double sum2 = 0.0;

      for (int i = 1; i < w - 1; ++i) {
         for (int j = 0; j < h; ++j) {
            sum1 += proc.getPixel(i, j) * proc.getPixel(i + 1, j);
         }
      }

      for (int i = 0; i < w - 2; ++i) {
         for (int j = 0; j < h; ++j) {
            sum2 += proc.getPixel(i, j) * proc.getPixel(i + 2, j);
         }
      }

      return (sum1 - sum2);
   }

   // Volath 5 - smooths out high-frequency (suppresses noise)
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133-146 (1988).
   private double computeVolath5(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;

      for (int i = 0; i < w - 1; ++i) {
         for (int j = 0; j < h; ++j) {
            sum += proc.getPixel(i, j) * proc.getPixel(i + 1, j);
         }
      }

      ImageStatistics stats = proc.getStatistics();

      sum -= ((w - 1) * h * stats.mean * stats.mean);
      return sum;
   }
   
   
 /**
    * Modified version of the algorithm used by the AutoFocus JAF(H&P) code
    * in Micro-Manager's Autofocus.java by Pakpoom Subsoontorn & Hernan Garcia.
    * Looks for diagonal edges in both directions, then combines them (RMS).
    * (Original algorithm only looked for edges in one diagonal direction).
    * Similar to Edges algorithm except it does no normalization by original
    * intensity and adds a median filter before edge detection.
    * @author Jon
    */
   private double computeMedianEdges(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      int[] ken2 = {0, 1, 2, -1, 0, 1, -2, -1, 0};

      proc.medianFilter();    // 3x3 median filter
      ImageProcessor proc2 = proc.duplicate();
      proc.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.sqrt(Math.pow(proc.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2));
         }
      }
      return sum;
   }

   /**
    * Per suggestion of William "Bill" Mohler @ UConn.  Returns the power in a
    * specified band of spatial frequencies via the FFT.  Key according to Bill is
    * to use an unscaled FFT, so this is provided using a modified ImageJ class.
    * @author Jon
    */
   private double computeFFTBandpass(ImageProcessor proc) {
      try {
         // gets power spectrum (FFT) without scaling result
         FHT_NoScaling myFHT = new FHT_NoScaling(proc);
         myFHT.transform();
         ImageProcessor ps = myFHT.getPowerSpectrum_noScaling();
         int midpoint = ps.getHeight()/2;
         final int scaled_lower = (int) Math.round(fftLowerCutoff/100*midpoint);
         final int start_lower = Math.round(midpoint-scaled_lower);
         final int scaled_upper = (int) Math.round(fftUpperCutoff/100*midpoint);
         final int start_upper = Math.round(midpoint-scaled_upper);
         OvalRoi innerCutoff = new OvalRoi(start_lower, start_lower,
               2*scaled_lower+1, 2*scaled_lower+1);
         OvalRoi outerCutoff = new OvalRoi(start_upper, start_upper,
               2*scaled_upper+1, 2*scaled_upper+1);
         ps.setColor(0);
         ps.fillOutside(outerCutoff);
         ps.fill(innerCutoff);
         ps.setRoi(outerCutoff);
         return ps.getStatistics().mean;
      } catch (Exception e) {
         studio_.logs().logError(e);
         return 0;
      }
   }
   
   
   @Override
   public double computeScore(final ImageProcessor proc) {
      if (scoringMethod.contentEquals("Mean")) {
         return computeMean(proc);
      } else if (scoringMethod.contentEquals("StdDev")) {
         return computeNormalizedStdDev(proc);
      } else if (scoringMethod.contentEquals("NormalizedVariance")) {
         return computeNormalizedVariance(proc);
      } else if (scoringMethod.contentEquals("Edges")) {
         return computeEdges(proc);
      } else if (scoringMethod.contentEquals("SharpEdges")) {
         return computeSharpEdges(proc);
      } else if (scoringMethod.contentEquals("Redondo")) {
         return computeRedondo(proc);
      } else if (scoringMethod.contentEquals("Volath")) {
         return computeVolath(proc);
      } else if (scoringMethod.contentEquals("Volath5")) {
         return computeVolath5(proc);
      } else if (scoringMethod.contentEquals("MedianEdges")) {
         return computeMedianEdges(proc);
      } else if (scoringMethod.contentEquals("Tenengrad")) {
         return computeTenengrad(proc);
      } else if (scoringMethod.contentEquals("FFTBandpass")) {
         return computeFFTBandpass(proc);
      } else {
         return 0;
      }
   }
   
   @Override
   public void setContext(Studio app) {
      studio_ = app;
      studio_.events().registerForEvents(this);
   }

   @Override
   public PropertyItem[] getProperties() {
      CMMCore core = studio_.getCMMCore();
      String channelGroup = core.getChannelGroup();
      StrVector channels = core.getAvailableConfigs(channelGroup);
      String allowedChannels[] = new String[(int)channels.size() + 1];
      allowedChannels[0] = "";

      try {
         PropertyItem p = getProperty(CHANNEL);
         boolean found = false;
         for (int i = 0; i < channels.size(); i++) {
            allowedChannels[i+1] = channels.get(i);
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
}
