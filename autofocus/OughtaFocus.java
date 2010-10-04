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
import java.text.ParseException;

import mmcorej.CMMCore;
import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.UnivariateRealFunction;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.univariate.BrentOptimizer;

import org.micromanager.metadata.AcquisitionData;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;

public class OughtaFocus extends AutofocusBase implements org.micromanager.api.Autofocus {

   private CMMCore core_;
   private static final String AF_DEVICE_NAME = "OughtaFocus";
   private static final String SEARCH_RANGE = "SearchRange_um";
   private static final String TOLERANCE = "Tolerance_um";
   private static final String CROP_FACTOR = "CropFactor";
   private static final String BINNING = "Binning";
   private static final String CHANNEL = "Channel";

   private double searchRange;
   private double tolerance;
   private double binning;
   private double cropFactor;
   private String channel;

   public OughtaFocus() {
      super();
      createProperty(SEARCH_RANGE, Double.toString(searchRange));
      createProperty(TOLERANCE, Double.toString(tolerance));
      createProperty(CROP_FACTOR, Double.toString(cropFactor));
      createProperty(BINNING, Double.toString(binning));
      createProperty(CHANNEL, channel);

      super.loadSettings();
   }

   public void applySettings() {
      try {
         searchRange = NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE));
         tolerance = NumberUtils.displayStringToDouble(getPropertyValue(TOLERANCE));
         cropFactor = NumberUtils.displayStringToDouble(getPropertyValue(CROP_FACTOR));
         binning = NumberUtils.displayStringToDouble(getPropertyValue(BINNING));
         channel = getPropertyValue(CHANNEL);
      } catch (MMException ex) {
         ReportingUtils.logError(ex);
      } catch (ParseException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public void setMMCore(CMMCore core) {
      core_ = core;
   }

   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   public double fullFocus() throws MMException {
      applySettings();

      UnivariateRealFunction scoreFun = new UnivariateRealFunction() {

         public double value(double d) throws FunctionEvaluationException {
            return measureFocusScore(d);
         }
      };

      BrentOptimizer brentOptimizer = new BrentOptimizer();
      brentOptimizer.setAbsoluteAccuracy(tolerance);
      try {
         double z = core_.getPosition(core_.getFocusDevice());
         brentOptimizer.optimize(scoreFun, GoalType.MAXIMIZE,
                 z - searchRange/2, z  + searchRange/2);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return 1;
      }
      return 0;
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
         core_.snapImage();
         Object img = core_.getImage();
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
