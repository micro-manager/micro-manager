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
//                Additions by Jon Daniels (Applied Scientific Instrumentation)
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

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.process.FloatProcessor;
import ij.gui.OvalRoi;

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
   private static final String[] SHOWVALUES = {"Yes", "No"};
   private final static String[] SCORINGMETHODS = {"Edges", "StdDev", "Mean", 
      "NormalizedVariance", "SharpEdges", "Redondo", "Volath", "Volath5", 
      "MedianEdges", "Tenengrad", "FFTBandpass"
   };
   private final static String FFT_UPPER_CUTOFF = "FFTUpperCutoff(%)";
   private final static String FFT_LOWER_CUTOFF = "FFTLowerCutoff(%)";
   private double searchRange = 10;
   private double tolerance = 1;
   private double cropFactor = 1;
   private String channel = "";
   private double exposure = 100;
   private String show = "No";
   private String scoringMethod = "Edges";
   private double fft_upper_cutoff = 14;
   private double fft_lower_cutoff = 2.5;
   private int imageCount_;
   private long startTimeMs_;
   private double startZUm_;
   private boolean liveModeOn_;
   private boolean settingsLoaded_ = false;

   public OughtaFocus() {
      super();
      super.createProperty(SEARCH_RANGE, NumberUtils.doubleToDisplayString(searchRange));
      super.createProperty(TOLERANCE, NumberUtils.doubleToDisplayString(tolerance));
      super.createProperty(CROP_FACTOR, NumberUtils.doubleToDisplayString(cropFactor));
      super.createProperty(EXPOSURE, NumberUtils.doubleToDisplayString(exposure));
      super.createProperty(FFT_LOWER_CUTOFF, NumberUtils.doubleToDisplayString(fft_lower_cutoff));
      super.createProperty(FFT_UPPER_CUTOFF, NumberUtils.doubleToDisplayString(fft_upper_cutoff));
      super.createProperty(SHOW_IMAGES, show, SHOWVALUES);
      super.createProperty(SCORING_METHOD, scoringMethod, SCORINGMETHODS);
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
         fft_lower_cutoff = NumberUtils.displayStringToDouble(getPropertyValue(FFT_LOWER_CUTOFF));
         fft_lower_cutoff = MathFunctions.clip(0.0, fft_lower_cutoff, 100.0);
         fft_upper_cutoff = NumberUtils.displayStringToDouble(getPropertyValue(FFT_UPPER_CUTOFF));
         fft_upper_cutoff = MathFunctions.clip(0.0, fft_upper_cutoff, 100.0);
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

         @Override
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

                  @Override
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
      // looks for horizontal and vertical edges with 3x3 Sobel
      // filter and then combines them in RMS fashion
      // http://rsb.info.nih.gov/ij/developer/source/ij/process/ByteProcessor.java.html
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

   // Volath 5 - smoothes out high-frequency (suppresses noise)
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

      ImageProcessor proc1 = proc.duplicate();
      proc1.medianFilter();    // 3x3 median filter
      ImageProcessor proc2 = proc1.duplicate();
      proc1.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.sqrt(Math.pow(proc1.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2));
         }
      }
      return sum;
   }

   
   /**
    * From "Autofocusing Algorithm Selection in Computer Microscopy" (doi: 10.1109/IROS.2005.1545017)
    * 2016 paper (doi:10.1038/nbt.3708) concludes this is best  most non-spectral metric
    *   for their light sheet microscopy application
    * @author Jon
    */
   private double computeTenengrad(ImageProcessor proc) {
      int h = proc.getHeight();
      int w = proc.getWidth();
      double sum = 0.0;
      int[] ken1 = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
      int[] ken2 = {1, 2, 1, 0, 0, 0, -1, -2, -1};

      ImageProcessor proc1 = proc.duplicate();
      ImageProcessor proc2 = proc.duplicate();
      proc1.convolve3x3(ken1);
      proc2.convolve3x3(ken2);
      for (int i=0; i<w; i++){
         for (int j=0; j<h; j++){
            sum += Math.pow(proc1.getPixel(i,j),2) + Math.pow(proc2.getPixel(i, j), 2);
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
         final int scaled_lower = (int) Math.round(fft_lower_cutoff/100*midpoint);
         final int start_lower = Math.round(midpoint-scaled_lower);
         final int scaled_upper = (int) Math.round(fft_upper_cutoff/100*midpoint);
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
         ReportingUtils.logError(e);
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



   /**
    * This is a modified version of ImageJ FHT class which is in the public domain
    *   (http://rsb.info.nih.gov/ij/developer/source/ij/process/FHT.java.html).
    * This modified version is also released into the public domain.
    * Principal changes are in the method getPowerSpectrum()
    *   which has been changed to remove scaling and renamed getPowerSpectrum_NoScaling.
    * The pad method (renamed to padImage) was also copied from separate ImageJ code in the
    *   public domain at http://rsb.info.nih.gov/ij/developer/source/ij/plugin/FFT.java.html
    * All other changes are incidental like tweaking imports, renaming constructors,
    *   deleting unused code, making methods private, and declaring as a static nested class.
    * This code created by Jon Daniels (Applied Scientific Instrumentation) based on
    *   code from William (Bill) Mohler (University of Connecticut) which in turn
    *   was based on the public-domain ImageJ code.
    * @author Jon
    */
   static class FHT_NoScaling extends FloatProcessor {
      private boolean isFrequencyDomain;
      private int maxN;
      private float[] C;
      private float[] S;
      private int[] bitrev;
      private float[] tempArr;

      /** Constructs a FHT object from an ImageProcessor. Byte, short and RGB images
       are converted to float. Float images are duplicated. */
      public FHT_NoScaling(ImageProcessor ip) {
         this(padImage(ip), false);
      }

      private FHT_NoScaling(ImageProcessor ip, boolean isFrequencyDomain) {
         super(ip.getWidth(), ip.getHeight(), (float[])((ip instanceof FloatProcessor)?ip.duplicate().getPixels():ip.convertToFloat().getPixels()), null);
         this.isFrequencyDomain = isFrequencyDomain;
         maxN = getWidth();
         resetRoi();
      }

      /** Returns true of this FHT contains a square image with a width that is a power of two. */
      private boolean powerOf2Size() {
         int i=2;
         while(i<width) i *= 2;
         return i==width && width==height;
      }

      /** Performs a forward transform, converting this image into the frequency domain.
       The image contained in this FHT must be square and its width must be a power of 2. */
      public void transform() {
         transform(false);
      }

      private void transform(boolean inverse) {
         if (!powerOf2Size())
            throw new  IllegalArgumentException("Image not power of 2 size or not square: "+width+"x"+height);
         maxN = width;
         if (S==null)
            initializeTables(maxN);
         float[] fht = (float[])getPixels();
         rc2DFHT(fht, inverse, maxN);
         isFrequencyDomain = !inverse;
      }

      private void initializeTables(int maxN) {
         if (maxN>0x40000000)
            throw new  IllegalArgumentException("Too large for FHT:  "+maxN+" >2^30");
         makeSinCosTables(maxN);
         makeBitReverseTable(maxN);
         tempArr = new float[maxN];
      }

      private void makeSinCosTables(int maxN) {
         int n = maxN/4;
         C = new float[n];
         S = new float[n];
         double theta = 0.0;
         double dTheta = 2.0 * Math.PI/maxN;
         for (int i=0; i<n; i++) {
            C[i] = (float)Math.cos(theta);
            S[i] = (float)Math.sin(theta);
            theta += dTheta;
         }
      }

      private void makeBitReverseTable(int maxN) {
         bitrev = new int[maxN];
         int nLog2 = log2(maxN);
         for (int i=0; i<maxN; i++)
            bitrev[i] = bitRevX(i, nLog2);
      }

      /** Performs a 2D FHT (Fast Hartley Transform). */
      private void rc2DFHT(float[] x, boolean inverse, int maxN) {
         if (S==null) initializeTables(maxN);
         for (int row=0; row<maxN; row++)
            dfht3(x, row*maxN, inverse, maxN);
         transposeR(x, maxN);
         for (int row=0; row<maxN; row++)
            dfht3(x, row*maxN, inverse, maxN);
         transposeR(x, maxN);

         int mRow, mCol;
         float A,B,Cf,D,E;
         for (int row=0; row<=maxN/2; row++) { // Now calculate actual Hartley transform
            for (int col=0; col<=maxN/2; col++) {
               mRow = (maxN - row) % maxN;
               mCol = (maxN - col)  % maxN;
               A = x[row * maxN + col];    //  see Bracewell, 'Fast 2D Hartley Transf.' IEEE Procs. 9/86
               B = x[mRow * maxN + col];
               Cf = x[row * maxN + mCol];
               D = x[mRow * maxN + mCol];
               E = ((A + D) - (B + Cf)) / 2;
               x[row * maxN + col] = A - E;
               x[mRow * maxN + col] = B + E;
               x[row * maxN + mCol] = Cf + E;
               x[mRow * maxN + mCol] = D - E;
            }
         }
      }

      /** Performs an optimized 1D FHT of an array or part of an array.
       *  @param x        Input array; will be overwritten by the output in the range given by base and maxN.
       *  @param base     First index from where data of the input array should be read.
       *  @param inverse  True for inverse transform.
       *  @param maxN     Length of data that should be transformed; this must be always
       *                  the same for a given FHT object.
       *  Note that all amplitudes in the output 'x' are multiplied by maxN.
       */
      private void dfht3(float[] x, int base, boolean inverse, int maxN) {
         int i, stage, gpNum, gpSize, numGps, Nlog2;
         int bfNum, numBfs;
         int Ad0, Ad1, Ad2, Ad3, Ad4, CSAd;
         float rt1, rt2, rt3, rt4;

         if (S==null) initializeTables(maxN);
         Nlog2 = log2(maxN);
         BitRevRArr(x, base, Nlog2, maxN);   //bitReverse the input array
         gpSize = 2;     //first & second stages - do radix 4 butterflies once thru
         numGps = maxN / 4;
         for (gpNum=0; gpNum<numGps; gpNum++)  {
            Ad1 = gpNum * 4;
            Ad2 = Ad1 + 1;
            Ad3 = Ad1 + gpSize;
            Ad4 = Ad2 + gpSize;
            rt1 = x[base+Ad1] + x[base+Ad2];   // a + b
            rt2 = x[base+Ad1] - x[base+Ad2];   // a - b
            rt3 = x[base+Ad3] + x[base+Ad4];   // c + d
            rt4 = x[base+Ad3] - x[base+Ad4];   // c - d
            x[base+Ad1] = rt1 + rt3;      // a + b + (c + d)
            x[base+Ad2] = rt2 + rt4;      // a - b + (c - d)
            x[base+Ad3] = rt1 - rt3;      // a + b - (c + d)
            x[base+Ad4] = rt2 - rt4;      // a - b - (c - d)
         }

         if (Nlog2 > 2) {
            // third + stages computed here
            gpSize = 4;
            numBfs = 2;
            numGps = numGps / 2;
            for (stage=2; stage<Nlog2; stage++) {
               for (gpNum=0; gpNum<numGps; gpNum++) {
                  Ad0 = gpNum * gpSize * 2;
                  Ad1 = Ad0;     // 1st butterfly is different from others - no mults needed
                  Ad2 = Ad1 + gpSize;
                  Ad3 = Ad1 + gpSize / 2;
                  Ad4 = Ad3 + gpSize;
                  rt1 = x[base+Ad1];
                  x[base+Ad1] = x[base+Ad1] + x[base+Ad2];
                  x[base+Ad2] = rt1 - x[base+Ad2];
                  rt1 = x[base+Ad3];
                  x[base+Ad3] = x[base+Ad3] + x[base+Ad4];
                  x[base+Ad4] = rt1 - x[base+Ad4];
                  for (bfNum=1; bfNum<numBfs; bfNum++) {
                     // subsequent BF's dealt with together
                     Ad1 = bfNum + Ad0;
                     Ad2 = Ad1 + gpSize;
                     Ad3 = gpSize - bfNum + Ad0;
                     Ad4 = Ad3 + gpSize;

                     CSAd = bfNum * numGps;
                     rt1 = x[base+Ad2] * C[CSAd] + x[base+Ad4] * S[CSAd];
                     rt2 = x[base+Ad4] * C[CSAd] - x[base+Ad2] * S[CSAd];

                     x[base+Ad2] = x[base+Ad1] - rt1;
                     x[base+Ad1] = x[base+Ad1] + rt1;
                     x[base+Ad4] = x[base+Ad3] + rt2;
                     x[base+Ad3] = x[base+Ad3] - rt2;

                  } /* end bfNum loop */
               } /* end gpNum loop */
               gpSize *= 2;
               numBfs *= 2;
               numGps = numGps / 2;
            } /* end for all stages */
         } /* end if Nlog2 > 2 */

         if (inverse)  {
            for (i=0; i<maxN; i++)
               x[base+i] = x[base+i] / maxN;
         }
      }

      void transposeR (float[] x, int maxN) {
         int   r, c;
         float  rTemp;

         for (r=0; r<maxN; r++)  {
            for (c=r; c<maxN; c++) {
               if (r != c)  {
                  rTemp = x[r*maxN + c];
                  x[r*maxN + c] = x[c*maxN + r];
                  x[c*maxN + r] = rTemp;
               }
            }
         }
      }

      int log2 (int x) {
         int count = 31;
         while (!btst(x, count))
            count--;
         return count;
      }


      private boolean btst (int  x, int bit) {
         return ((x & (1<<bit)) != 0);
      }

      private void BitRevRArr (float[] x, int base, int bitlen, int maxN) {
         for (int i=0; i<maxN; i++) {
            tempArr[i] = x[base+bitrev[i]];
         }
         System.arraycopy(tempArr, 0, x, base, maxN);
      }

      private int bitRevX (int  x, int bitlen) {
         int  temp = 0;
         for (int i=0; i<=bitlen; i++)
            if ((x & (1<<i)) !=0)
               temp  |= (1<<(bitlen-i-1));
         return temp;
      }

      /** Returns an 8-bit power spectrum, log-scaled to 1-254. The image in this
       FHT is assumed to be in the frequency domain.
       Modified to remove scaling per William Mohler's tweaks. */
      public ImageProcessor getPowerSpectrum_noScaling () {
         if (!isFrequencyDomain)
            throw new  IllegalArgumentException("Frequency domain image required");
         int base;
         float  r;
         float[] fps = new float[maxN*maxN];
         byte[] ps = new byte[maxN*maxN];
         float[] fht = (float[])getPixels();

         for (int row=0; row<maxN; row++) {
            FHTps(row, maxN, fht, fps);
         }

         // no longer use min (=0), max, or scale (=1)

         for (int row=0; row<maxN; row++) {
            base = row*maxN;
            for (int col=0; col<maxN; col++) {
               r = fps[base+col];
               if (Float.isNaN(r) || r<1f)  // modified for no scaling
                  r = 0f;
               else
                  r = (float)Math.log(r);  // modified for no scaling
               ps[base+col] = (byte)(r+1f); // 1 is min value
            }
         }
         ImageProcessor ip = new ByteProcessor(maxN, maxN, ps, null);
         swapQuadrants(ip);
         return ip;
      }

      /** Power Spectrum of one row from 2D Hartley Transform. */
      private void FHTps(int row, int maxN, float[] fht, float[] ps) {
         int base = row*maxN;
         int l;
         for (int c=0; c<maxN; c++) {
            l = ((maxN-row)%maxN) * maxN + (maxN-c)%maxN;
            ps[base+c] = (sqr(fht[base+c]) + sqr(fht[l]))/2f;
         }
      }

      private static float sqr(float x) {
         return x*x;
      }

      /** Pad the image to be square and have dimensions being a power of 2
       Copied from http://rsb.info.nih.gov/ij/developer/source/ij/plugin/FFT.java.html
       (in public domain), changed name from pad() to padImage(),
       and tweaked to remove unused variables   */
      private static ImageProcessor padImage(ImageProcessor ip) {
         final int originalWidth = ip.getWidth();
         final int originalHeight = ip.getHeight();
         int maxN = Math.max(originalWidth, originalHeight);
         int i = 2;
         while(i<maxN) i *= 2;
         if (i==maxN && originalWidth==originalHeight) {
            return ip;
         }
         maxN = i;
         ImageStatistics stats = ImageStatistics.getStatistics(ip, ImageStatistics.MEAN, null);
         ImageProcessor ip2 = ip.createProcessor(maxN, maxN);
         ip2.setValue(stats.mean);
         ip2.fill();
         ip2.insert(ip, 0, 0);
         return ip2;
      }

      /** Swap quadrants 1 and 3 and 2 and 4 of the specified ImageProcessor
       so the power spectrum origin is at the center of the image.
       <pre>
           2 1
           3 4
       </pre>
       */
      private void swapQuadrants(ImageProcessor ip) {
         ImageProcessor t1, t2;
         int size = ip.getWidth()/2;
         ip.setRoi(size,0,size,size);
         t1 = ip.crop();
         ip.setRoi(0,size,size,size);
         t2 = ip.crop();
         ip.insert(t1,0,size);
         ip.insert(t2,size,0);
         ip.setRoi(0,0,size,size);
         t1 = ip.crop();
         ip.setRoi(size,size,size,size);
         t2 = ip.crop();
         ip.insert(t1,size,size);
         ip.insert(t2,0,0);
         ip.resetRoi();
      }

   }

}
