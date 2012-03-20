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

import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

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
import org.micromanager.acquisition.AcquisitionData;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;
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
   private final static String scoringMethods[] = {"Edges","StdDev","Mean"};

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

   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

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
      ReportingUtils.logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterationCount() +
            ", z=" + TextUtils.FMT2.format(zResult) +
            ", dz=" + TextUtils.FMT2.format(zResult - startZUm_) +
            ", t=" + (System.currentTimeMillis() - startTimeMs_));
      return zResult;
   }

   private void setZPosition(double z) throws Exception {
      CMMCore core = app_.getMMCore();
      String focusDevice = core.getFocusDevice();
      core.setPosition(focusDevice, z);
      core.waitForDevice(focusDevice);
   }

   public double measureFocusScore(double z) throws Exception {
      CMMCore core = app_.getMMCore();
      long start = System.currentTimeMillis();
      try {
         setZPosition(z);
         long tZ =  System.currentTimeMillis() - start;

         TaggedImage img = null;
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
         ImageProcessor proc = ImageUtils.makeProcessor(core, img.pix);
         double score = computeScore(proc);
         long tC = System.currentTimeMillis() - start - tZ - tI;
         ReportingUtils.logMessage("OughtaFocus: image=" + imageCount_++ +
               ", t=" + (System.currentTimeMillis() - startTimeMs_) +
               ", z=" + TextUtils.FMT2.format(z) + 
               ", score=" + TextUtils.FMT2.format(score) +
               ", Tz=" + tZ + ", Ti=" + tI + ", Tc=" + tC);
         return score;
      } catch (Exception e) {
         ReportingUtils.logError(e);
         throw e;
      }
   }

   public double incrementalFocus() throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getNumberOfImages() {
      return imageCount_;
   }

   public AcquisitionData getFocusingSequence() throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVerboseStatus() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public double getCurrentFocusScore() {
      CMMCore core = app_.getMMCore();
      double z=0.0;
      double score = 0.0;
      try {
         z = core.getPosition(core.getFocusDevice());
         core.waitForDevice(core.getCameraDevice());
         core.snapImage();
         Object img = core.getImage();
         if (show.contentEquals("Yes")) {
            app_.displayImage(img);
         }
         ImageProcessor proc = ImageUtils.makeProcessor(core, img);
         score = computeScore(proc);
         ReportingUtils.logMessage("OughtaFocus: z=" + TextUtils.FMT2.format(z) + 
                                   ", score=" + TextUtils.FMT2.format(score));
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
      return score;
   }

   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private double computeSharpness(ImageProcessor proc) {
      // mean intensity for the original image
      double meanIntensity = proc.getStatistics().mean;
      
      // mean intensity of the edge map
      proc.findEdges();
      double meanEdge = proc.getStatistics().mean;
      
      return meanEdge/meanIntensity;
   }

   private double computeMean(ImageProcessor proc) {
      return proc.getStatistics().mean;
   }

   private double computeNormalizedStdDev(ImageProcessor proc) {
      
      ImageStatistics stats = proc.getStatistics();
      return stats.stdDev / stats.mean;
   }

   private double computeScore(ImageProcessor proc) {
      if (scoringMethod.contentEquals("Mean")) {
         return computeMean(proc);
      } else if (scoringMethod.contentEquals("StdDev")) {
         return computeNormalizedStdDev(proc);
      } else if (scoringMethod.contentEquals("Edges")) {
         return computeSharpness(proc);
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
