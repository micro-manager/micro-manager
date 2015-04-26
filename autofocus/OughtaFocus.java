///////////////////////////////////////////////////////////////////////////////
//FILE:           OughtaFocus.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for mciro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Arthur Edelstein, October 2010
//                Based on SimpleAutofocus by Karl Hoover
//                and the Autofocus "H&P" plugin
//                by Pakpoom Subsoontorn & Hernan Garcia
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

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

import java.awt.Rectangle;
import java.text.ParseException;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.univariate.BrentOptimizer;
import org.json.JSONException;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.MathFunctions;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TextUtils;

public class OughtaFocus extends AutofocusBase implements org.micromanager.api.Autofocus {

   private ScriptInterface app_;
   private static final String AF_DEVICE_NAME = "OughtaFocus";
   private static final String SEARCH_RANGE = "SearchRange_um";
   private static final String TOLERANCE = "Tolerance_um";
   private static final String CROP_FACTOR = "CropFactor";
   private static final String CHANNEL = "Channel";
   private static final String EXPOSURE = "Exposure";
   private static final String SHOW_IMAGES = "ShowImages";
   private static final String SCORING_METHOD = "Maximize";
   private static final String showValues[] = {"Yes", "No"};
   private final static String scoringMethods[] = {"Edges", "StdDev", "Mean", "NormalizedVariance", "SharpEdges", "Redondo", "Volath", "Volath5"};
   private double searchRange = 10;
   private double tolerance = 1;
   private double cropFactor = 1;
   private String channel = "";
   private double exposure = 100;
   private String show = "No";
   private String scoringMethod = "Edges";
   private int imageCount_;
   private long startTimeMs_;
   private double startZUm_;
   private boolean liveModeOn_;
   private boolean settingsLoaded_ = false;

   public OughtaFocus() {
      super();
      createProperty(SEARCH_RANGE, NumberUtils.doubleToDisplayString(searchRange));
      createProperty(TOLERANCE, NumberUtils.doubleToDisplayString(tolerance));
      createProperty(CROP_FACTOR, NumberUtils.doubleToDisplayString(cropFactor));
      createProperty(EXPOSURE, NumberUtils.doubleToDisplayString(exposure));
      createProperty(SHOW_IMAGES, show, showValues);
      createProperty(SCORING_METHOD, scoringMethod, scoringMethods);
      imageCount_ = 0;
   }

   @Override
   public void applySettings() {
      try {
         searchRange = NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE));
         tolerance = NumberUtils.displayStringToDouble(getPropertyValue(TOLERANCE));
         cropFactor = NumberUtils.displayStringToDouble(getPropertyValue(CROP_FACTOR));
         cropFactor = MathFunctions.clip(0.01, cropFactor, 1.0);
         channel = getPropertyValue(CHANNEL);
         exposure = NumberUtils.displayStringToDouble(getPropertyValue(EXPOSURE));
         show = getPropertyValue(SHOW_IMAGES);
         scoringMethod = getPropertyValue(SCORING_METHOD);

      } catch (MMException ex) {
         ReportingUtils.logError(ex);
      } catch (ParseException ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   @Override
   public double fullFocus() throws MMException {
      startTimeMs_ = System.currentTimeMillis();
      applySettings();
      try {
         Rectangle oldROI = app_.getROI();
         CMMCore core = app_.getMMCore();
         liveModeOn_ = app_.isLiveModeOn();

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
            app_.setROI(newROI);
            core.waitForDevice(core.getCameraDevice());
         }
         double oldExposure = core.getExposure();
         core.setExposure(exposure);

         double z = runAutofocusAlgorithm();

         if (cropFactor < 1.0) {
            app_.setROI(oldROI);
            core.waitForDevice(core.getCameraDevice());
         }
         if (oldState != null) {
            core.setSystemState(oldState);
         }
         core.setExposure(oldExposure);
         setZPosition(z);
         return z;
      } catch (Exception ex) {
         throw new MMException(ex.getMessage());
      }
   }

   private double runAutofocusAlgorithm() throws Exception {
      UnivariateRealFunction scoreFun = new UnivariateRealFunction() {

         public double value(double d) throws FunctionEvaluationException {
            try {
               return measureFocusScore(d);
            } catch (Exception e) {
               throw new FunctionEvaluationException(e, d);
            }
         }
      };
      BrentOptimizer brentOptimizer = new BrentOptimizer();
      brentOptimizer.setAbsoluteAccuracy(tolerance);
      imageCount_ = 0;

      CMMCore core = app_.getMMCore();
      double z = core.getPosition(core.getFocusDevice());
      startZUm_ = z;
//      getCurrentFocusScore();
      double zResult = brentOptimizer.optimize(scoreFun, GoalType.MAXIMIZE, z - searchRange / 2, z + searchRange / 2);
      ReportingUtils.logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterationCount()
              + ", z=" + TextUtils.FMT2.format(zResult)
              + ", dz=" + TextUtils.FMT2.format(zResult - startZUm_)
              + ", t=" + (System.currentTimeMillis() - startTimeMs_));
      return zResult;
   }

   private void setZPosition(double z) throws Exception {
      CMMCore core = app_.getMMCore();
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

   public static Object getMonochromePixels(TaggedImage image) throws JSONException, MMScriptException {
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
      CMMCore core = app_.getMMCore();
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
               SwingUtilities.invokeLater(new Runnable() {

                  public void run() {
                     app_.displayImage(img1);
                  }
               });
            }
         }
         long tI = System.currentTimeMillis() - start - tZ;
         ImageProcessor proc = makeMonochromeProcessor(core, getMonochromePixels(img));
         double score = computeScore(proc);
         long tC = System.currentTimeMillis() - start - tZ - tI;
         ReportingUtils.logMessage("OughtaFocus: image=" + imageCount_++
                 + ", t=" + (System.currentTimeMillis() - startTimeMs_)
                 + ", z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score)
                 + ", Tz=" + tZ + ", Ti=" + tI + ", Tc=" + tC);
         return score;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         throw e;
      }
   }

   @Override
   public double incrementalFocus() throws MMException {
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
      CMMCore core = app_.getMMCore();
      double score = 0.0;
      try {
         double z = core.getPosition(core.getFocusDevice());
         core.waitForDevice(core.getCameraDevice());
         core.snapImage();
         TaggedImage img = core.getTaggedImage();
         if (show.contentEquals("Yes")) {
            app_.displayImage(img);
         }
         ImageProcessor proc = ImageUtils.makeProcessor(core, img);
         score = computeScore(proc);
         ReportingUtils.logMessage("OughtaFocus: z=" + TextUtils.FMT2.format(z)
                 + ", score=" + TextUtils.FMT2.format(score));
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      return score;
   }

   @Override
   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
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

   // Volath's 1D autocorrelation
   // Volath  D., "The influence of the scene parameters and of noise on
   // the behavior of automatic focusing algorithms,"
   // J. Microsc. 151, (2), 133 –146 (1988).
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
   // J. Microsc. 151, (2), 133 –146 (1988).
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
      } else {
         return 0;
      }
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      CMMCore core = app_.getMMCore();
      String chanGroup = core.getChannelGroup();
      String curChan;
      try {
         curChan = core.getCurrentConfig(chanGroup);
         createProperty(CHANNEL, curChan,
                 core.getAvailableConfigs(core.getChannelGroup()).toArray());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

      if (!settingsLoaded_) {
         super.loadSettings();
         settingsLoaded_ = true;
      }
   }
}
