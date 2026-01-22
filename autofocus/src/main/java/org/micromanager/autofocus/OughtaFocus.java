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

import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.text.ParseException;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import mmcorej.TaggedImage;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.autofocus.optimizers.BrentFocusOptimizer;
import org.micromanager.autofocus.optimizers.FocusOptimizer;
import org.micromanager.autofocus.optimizers.ZStackFocusOptimizer;
import org.micromanager.imageprocessing.ImgSharpnessAnalysis;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.PropertyItem;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Arthur Edelstein's Autofocus plugin using the Brent Optimizer.
 */
@Plugin(type = AutofocusPlugin.class)
public class OughtaFocus extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin {

   private Studio studio_;
   private static final String AF_DEVICE_NAME = "OughtaFocus";
   private static final String OPTIMIZER_STRATEGY = "OptimizerStrategy";
   private static final String[] OPTIMIZERS = {"Brent", "Z-Stack"};
   private static final String FOCUSDRIVE = "FocusDrive";
   private static final String SEARCH_RANGE = "SearchRange_um";
   private static final String TOLERANCE = "Tolerance_um";
   private static final String CROP_FACTOR = "CropFactor";
   private static final String CHANNEL = "Channel";
   private static final String EXPOSURE = "Exposure";
   private static final String SHOW_IMAGES = "ShowImages";
   private static final String SHOW_GRAPH = "ShowGraph";
   private static final String SCORING_METHOD = "Maximize";
   private static final String[] SHOWVALUES = {"Yes", "No"};
   private static final String FFT_UPPER_CUTOFF = "FFTUpperCutoff(%)";
   private static final String FFT_LOWER_CUTOFF = "FFTLowerCutoff(%)";
   private static final String KEEP_SHUTTER_OPEN = "KeepShutterOpen";
   private final ImgSharpnessAnalysis fcsAnalysis_ = new ImgSharpnessAnalysis();
   private final BrentFocusOptimizer brentFocusOptimizer_;
   private final ZStackFocusOptimizer zStackFocusOptimizer_;
   private FocusOptimizer focusOptimizer_;

   private String channel_ = "";
   private String zDrive_ = "";
   private double exposure_ = 100;
   private boolean displayImages_ = false;
   private boolean displayGraph_ = false;
   private double cropFactor_ = 1;
   private String optimizer_ = OPTIMIZERS[0];
   private boolean keepShutterOpen_ = false;

   /**
    * Constructor for the OughtaFocus class. This is the most versatible autofocus plugin.
    * Originally written by Arthur using the Brent optimizer to use the minimum number of images
    * to establish best focus, later added were options to do a Z series and graph
    * the focus curves.  Image analysis code is done in the ImageProcessing library.
    */
   public OughtaFocus() {
      brentFocusOptimizer_ = new BrentFocusOptimizer(fcsAnalysis_::compute);
      zStackFocusOptimizer_  = new ZStackFocusOptimizer(fcsAnalysis_::compute);
      focusOptimizer_ = brentFocusOptimizer_;

      super.createProperty(OPTIMIZER_STRATEGY, OPTIMIZERS[0], OPTIMIZERS);
      super.createProperty(FOCUSDRIVE, "");
      super.createProperty(SEARCH_RANGE,
              NumberUtils.doubleToDisplayString(focusOptimizer_.getSearchRange()));
      super.createProperty(TOLERANCE,
              NumberUtils.doubleToDisplayString(focusOptimizer_.getAbsoluteTolerance()));
      super.createProperty(CROP_FACTOR,
              NumberUtils.doubleToDisplayString(cropFactor_));
      super.createProperty(EXPOSURE,
              NumberUtils.doubleToDisplayString(exposure_));
      super.createProperty(FFT_LOWER_CUTOFF,
              NumberUtils.doubleToDisplayString(fcsAnalysis_.getFFTLowerCutoff()));
      super.createProperty(FFT_UPPER_CUTOFF,
              NumberUtils.doubleToDisplayString(fcsAnalysis_.getFFTUpperCutoff()));
      super.createProperty(SHOW_IMAGES, SHOWVALUES[1], SHOWVALUES);
      super.createProperty(SHOW_GRAPH, SHOWVALUES[1], SHOWVALUES);
      super.createProperty(SCORING_METHOD,
              fcsAnalysis_.getComputationMethod().name(),
              ImgSharpnessAnalysis.Method.getNames()
      );
      super.createProperty(CHANNEL, "");
      super.createProperty(KEEP_SHUTTER_OPEN, SHOWVALUES[1], SHOWVALUES);
   }

   @Override
   public void applySettings() {
      try {
         optimizer_ = getPropertyValue(OPTIMIZER_STRATEGY);
         if (optimizer_.equals(OPTIMIZERS[0])) {
            focusOptimizer_ = brentFocusOptimizer_;
         } else {
            focusOptimizer_ = zStackFocusOptimizer_;
         }
         focusOptimizer_.setContext(studio_);
         zDrive_ = getPropertyValue(FOCUSDRIVE);
         if (zDrive_.isEmpty()) {
            zDrive_ = studio_.core().getFocusDevice();
         }
         focusOptimizer_.setZDrive(zDrive_);
         focusOptimizer_.setSearchRange(
                 NumberUtils.displayStringToDouble(getPropertyValue(SEARCH_RANGE)));
         focusOptimizer_.setAbsoluteTolerance(
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
         displayGraph_ = getPropertyValue(SHOW_GRAPH).contentEquals("Yes");
         keepShutterOpen_ = getPropertyValue(KEEP_SHUTTER_OPEN).contentEquals("Yes");
         // Only the zStack optimizer can display a graph
         zStackFocusOptimizer_.setDisplayGraph(displayGraph_);
         focusOptimizer_.setDisplayImages(displayImages_);
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
      if (!channel_.isEmpty()) {
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
      boolean oldAutoShutter = core.getAutoShutter(); // remember old state of autoShutter
      boolean oldShutter = core.getShutterOpen(); // remember old state of Shutter
      if (keepShutterOpen_) {
         core.setAutoShutter(false); // turn off Auto shutter
         core.setShutterOpen(true);  // open shutter
      }
      final double z = focusOptimizer_.runAutofocusAlgorithm();
      core.setPosition(zDrive_, z);
      if (keepShutterOpen_) {  // revert shutter state
         core.setAutoShutter(oldAutoShutter);
         core.setShutterOpen(oldShutter);  
      }
      if (cropFactor_ < 1.0) {
         studio_.app().setROI(oldROI);
         core.waitForDevice(core.getCameraDevice());
      }
      if (oldState != null) {
         core.setSystemState(oldState);
      }
      core.setExposure(oldExposure);
      core.waitForDevice(zDrive_);
      return z;
   }

   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public int getNumberOfImages() {
      return brentFocusOptimizer_.getImageCount();
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
         final double z = core.getPosition(zDrive_);
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
      brentFocusOptimizer_.setContext(studio_);
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

         PropertyItem drive = getProperty(FOCUSDRIVE);
         StrVector drives = core.getLoadedDevicesOfType(DeviceType.StageDevice);
         String[] availableDrives = new String[(int) drives.size() + 1];
         availableDrives[0] = "";
         found = false;
         for (int i = 0; i < drives.size(); i++) {
            availableDrives[i + 1] = drives.get(i);
            if (drive.value.equals(drives.get(i))) {
               found = true;
            }
         }
         drive.allowed = availableDrives;
         if (!found) {
            drive.value = availableDrives[0];
         }
         setProperty(drive);

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
