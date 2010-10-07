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

import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.univariate.BrentOptimizer;
import org.micromanager.MMStudioMainFrame;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MathFunctions;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

public class OughtaFocus extends AutofocusBase implements org.micromanager.api.Autofocus {

   private CMMCore core_;
   private final MMStudioMainFrame gui_;
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

   private boolean settingsLoaded_ = false;

   public OughtaFocus() {
      super();
      gui_ = MMStudioPlugin.getMMStudioMainFrameInstance();

      createProperty(SEARCH_RANGE, NumberUtils.doubleToDisplayString(searchRange));
      createProperty(TOLERANCE, NumberUtils.doubleToDisplayString(tolerance));
      createProperty(CROP_FACTOR, NumberUtils.doubleToDisplayString(cropFactor));
      createProperty(EXPOSURE, NumberUtils.doubleToDisplayString(exposure));
      createProperty(SHOW_IMAGES, show, showValues);
      createProperty(SCORING_METHOD, scoringMethod, scoringMethods);
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

   public void setMMCore(CMMCore core) {
      core_ = core;
      String chanGroup = core_.getChannelGroup();
      String curChan;
      try {
         curChan = core_.getCurrentConfig(chanGroup);
         createProperty(CHANNEL, curChan,
                 core_.getAvailableConfigs(core_.getChannelGroup()).toArray());
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      
      if (!settingsLoaded_) {
         super.loadSettings();
         settingsLoaded_ = true;
      }
   }

   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   public double fullFocus() throws MMException {
      applySettings();

      Thread th = new Thread() {
         public void run() {
            try {
               Rectangle oldROI = gui_.getROI();
               //ReportingUtils.logMessage("Original ROI: " + oldROI);
               int w = (int) (oldROI.width * cropFactor);
               int h = (int) (oldROI.height * cropFactor);
               int x = oldROI.x + (oldROI.width - w) / 2;
               int y = oldROI.y + (oldROI.height - h) / 2;
               Rectangle newROI = new Rectangle(x,y,w,h);
               //ReportingUtils.logMessage("Setting ROI to: " + newROI);
               Configuration oldState = null;
               if (channel.length() > 0) {
                  String chanGroup = core_.getChannelGroup();
                  oldState = core_.getConfigGroupState(chanGroup);
                  core_.setConfig(chanGroup, channel);
               }
               gui_.setROI(newROI);
               core_.waitForDevice(core_.getCameraDevice());
               double oldExposure = core_.getExposure();
               core_.setExposure(exposure);

               double z = runAutofocusAlgorithm();

               gui_.setROI(oldROI);
               core_.waitForDevice(core_.getCameraDevice());
               if (oldState != null) {
                  core_.setSystemState(oldState);
               }
               core_.setExposure(oldExposure);
               setZPosition(z);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
            
         }
      };

      if (show.contentEquals("Yes")) {
         th.start(); // Run on a separate thread.
      } else {
         th.run(); // Run it on this thread instead.
      }
      return 0;
   }

   private double runAutofocusAlgorithm() throws Exception {
      UnivariateRealFunction scoreFun = new UnivariateRealFunction() {
         public double value(double d) throws FunctionEvaluationException {
            return measureFocusScore(d);
         }
      };
      BrentOptimizer brentOptimizer = new BrentOptimizer();
      brentOptimizer.setAbsoluteAccuracy(tolerance);

      double z = core_.getPosition(core_.getFocusDevice());
      double zResult = brentOptimizer.optimize(scoreFun, GoalType.MAXIMIZE, z - searchRange / 2, z + searchRange / 2);
      ReportingUtils.logMessage("OughtaFocus Iterations: " + brentOptimizer.getIterationCount());
      return zResult;
      
   }

   private void setZPosition(double z) {
      try {
         String focusDevice = core_.getFocusDevice();
         core_.setPosition(focusDevice, z);
         core_.waitForDevice(focusDevice);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public double measureFocusScore(double z) {
      try {

         setZPosition(z);
         core_.waitForDevice(core_.getCameraDevice());
         core_.snapImage();
         Object img = core_.getImage();
         if (show.contentEquals("Yes")) {
            gui_.displayImage(img);
         }
         ImageProcessor proc = ImageUtils.makeProcessor(core_, img);
         return computeScore(proc);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   public double incrementalFocus() throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public int getNumberOfImages() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public AcquisitionData getFocusingSequence() throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVerboseStatus() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public double getCurrentFocusScore() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) throws MMException {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private double computeSharpness(ImageProcessor proc) {
      ImageStatistics stats = proc.getStatistics();
      double meanIntensity = proc.getStatistics().mean;
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
}
