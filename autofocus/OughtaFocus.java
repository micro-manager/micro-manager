///////////////////////////////////////////////////////////////////////////////
//FILE:           OughtaFocus.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for mciro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Arthur Edelstein, October 2010
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
import java.util.logging.Level;
import java.util.logging.Logger;

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
   private static final String SHOW = "ShowImages";

   private double searchRange = 10;
   private double tolerance = 1;
   private double cropFactor = 1;
   private String channel = "";
   private double exposure = 100;
   private int show = 0;

   public OughtaFocus() {
      super();
      gui_ = MMStudioPlugin.getMMStudioMainFrameInstance();

      createProperty(SEARCH_RANGE, NumberUtils.doubleToDisplayString(searchRange));
      createProperty(TOLERANCE, NumberUtils.doubleToDisplayString(tolerance));
      createProperty(CROP_FACTOR, NumberUtils.doubleToDisplayString(cropFactor));
      createProperty(EXPOSURE, NumberUtils.doubleToDisplayString(exposure));
      createProperty(SHOW, NumberUtils.intToDisplayString(show));

   }

   public void applySettings() {
      try {
         searchRange = NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE));
         tolerance = NumberUtils.displayStringToDouble(getPropertyValue(TOLERANCE));
         cropFactor = NumberUtils.displayStringToDouble(getPropertyValue(CROP_FACTOR));
         channel = getPropertyValue(CHANNEL);
         exposure = NumberUtils.displayStringToDouble(getPropertyValue(EXPOSURE));
         show = NumberUtils.displayStringToInt(getPropertyValue(SHOW));
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

      super.loadSettings();
   }

   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   public double fullFocus() throws MMException {
      Thread th = new Thread() {

         public void run() {

            applySettings();
            try {
               Rectangle oldROI = gui_.getROI();
               Rectangle newROI = new Rectangle();
               newROI.width = (int) (oldROI.width * cropFactor);
               newROI.height = (int) (oldROI.height * cropFactor);
               newROI.x = oldROI.x + newROI.width / 2;
               newROI.y = oldROI.y + newROI.height / 2;
               String chanGroup = core_.getChannelGroup();
               Configuration oldState = core_.getConfigGroupState(chanGroup);
               core_.setConfig(chanGroup, channel);
               gui_.setROI(newROI);
               double oldExposure = core_.getExposure();
               core_.setExposure(exposure);

               runAutofocusAlgorithm();

               gui_.setROI(oldROI);
               core_.setSystemState(oldState);
               core_.setExposure(exposure);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      };
      th.start();
      if (show == 0) {
         try {
         th.join();
         } catch (InterruptedException ex) {
            ReportingUtils.showError(ex);
         }
      }
      return 0;
   }

   private void runAutofocusAlgorithm() {
      UnivariateRealFunction scoreFun = new UnivariateRealFunction() {

         public double value(double d) throws FunctionEvaluationException {
            return measureFocusScore(d);
         }
      };
      BrentOptimizer brentOptimizer = new BrentOptimizer();
      brentOptimizer.setAbsoluteAccuracy(tolerance);
      try {
         double z = core_.getPosition(core_.getFocusDevice());
         brentOptimizer.optimize(scoreFun, GoalType.MAXIMIZE, z - searchRange / 2, z + searchRange / 2);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
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
         System.out.println(z);
         setZPosition(z);
         core_.snapImage();
         Object img = core_.getImage();
         if (show == 1) {
            gui_.displayImage(img);
         }
         ImageProcessor proc = ImageUtils.makeProcessor(core_, img);
         ImageStatistics stats = proc.getStatistics();
         return stats.stdDev / stats.mean;
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
}
