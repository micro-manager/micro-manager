///////////////////////////////////////////////////////////////////////////////
//FILE:           TCAAutofocus.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      EMI/TCA AF plugin
//-----------------------------------------------------------------------------
//
//AUTHOR:         Andrey Andreev, aandreev@emila.org
//
//COPYRIGHT:      EMI LA
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

package org.micromanager.autofocus;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import mmcorej.Configuration;

import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.autofocus.tca_af.ComputeBestFocus300nm;
import org.micromanager.autofocus.tca_af.ComputeBestFocus460nm;
import org.micromanager.autofocus.tca_af.ComputeBestFocusFAD;
import org.micromanager.autofocus.tca_af.ComputeBestFocusNADH;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.PropertyItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

import org.jfree.data.xy.XYSeries;
import org.micromanager.imageprocessing.curvefit.PlotUtils;

/**
 */
@Plugin(type = AutofocusPlugin.class)
public class TCAAutofocus extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin  {

   
   private static final String KEY_CHANNEL = "Channel";
   private static final String NOCHANNEL = "";
   private static final String KEY_REL_Z_MIN = "Relative Z min";
   private static final String KEY_REL_Z_MAX = "Relative Z max";
   private static final String KEY_DELTA_Z = "Delta Z";
   private static final String KEY_DRYRUN = "Dry run";
   private static final String[] SHOWVALUES = {"Yes", "No"};

   private static final String KEY_FOCUS_ANALYZER = "Focus Analyzer";
   private static final String[] FOCUS_ANALYZER_STRINGS = {"460", "300", "NADH", "FAD"};
   //private static final String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   
   private static final String AF_DEVICE_NAME = "TCA AF 2.0";

   private Studio app_;
   private CMMCore core_;
   private ImageProcessor ipCurrent_ = null;

   public double sizeFirst_ = 2;
   public int numFirst_ = 1; // +/- #of snapshot
   public double sizeSecond_ = 0.2;
   public int numSecond_ = 5;
   public double thres_ = 0.02;
   public double cropSize_ = 0.2;
   public double deltaz_ = 1.0;
   public String channel_ = "";
   private double rel_z_min_ = -10.0;
   private double rel_z_max_ = 10.0;
   private boolean dryrun_ = false;
   private boolean verbose_ = true; // displaying debug info or not
   private String channelGroup_;
   private double curDist_;
   private String focusAnalyzer_ = "460"; // default to 460nm analyzer

   private static class FocusResults {
      public String[] metricNames;
      public double[][] metricValuesNorm;
      public double[] zFine;
      public double[][] smoothCurvesNorm;
      public double z_best_focus;
   }

   /**
    * Constructors creates needed properties.
    *
    */
   public TCAAutofocus() {
      super.createProperty(KEY_CHANNEL, channel_);
      super.createProperty(KEY_REL_Z_MIN, Double.toString(rel_z_min_));
      super.createProperty(KEY_REL_Z_MAX, Double.toString(rel_z_max_));
      super.createProperty(KEY_DELTA_Z, Double.toString(deltaz_));
      super.createProperty(KEY_DRYRUN, SHOWVALUES[1], SHOWVALUES);
      super.createProperty(KEY_FOCUS_ANALYZER, FOCUS_ANALYZER_STRINGS[0], FOCUS_ANALYZER_STRINGS);
   }


   @Override
   public void applySettings() {
      try {
         deltaz_ = Double.parseDouble(getPropertyValue(KEY_DELTA_Z));
         channel_ = getPropertyValue(KEY_CHANNEL);
         rel_z_min_ = Double.parseDouble(getPropertyValue(KEY_REL_Z_MIN));
         rel_z_max_ = Double.parseDouble(getPropertyValue(KEY_REL_Z_MAX));
         dryrun_ = getPropertyValue(KEY_DRYRUN).contentEquals("Yes");
         focusAnalyzer_ = getPropertyValue(KEY_FOCUS_ANALYZER);

      } catch (Exception e) {

         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

   private static FocusResults wrapResults(ComputeBestFocus460nm.Results source) {
      FocusResults result = new FocusResults();
      result.metricNames = source.metricNames;
      result.metricValuesNorm = source.metricValuesNorm;
      result.zFine = source.zFine;
      result.smoothCurvesNorm = source.smoothCurvesNorm;
      result.z_best_focus = source.z_best_focus;
      return result;
   }

   private static FocusResults wrapResults(ComputeBestFocus300nm.Results source) {
      FocusResults result = new FocusResults();
      result.metricNames = source.metricNames;
      result.metricValuesNorm = source.metricValuesNorm;
      result.zFine = source.zFine;
      result.smoothCurvesNorm = source.smoothCurvesNorm;
      result.z_best_focus = source.z_best_focus;
      return result;
   }

   private static FocusResults wrapResults(ComputeBestFocusNADH.Results source) {
      FocusResults result = new FocusResults();
      result.metricNames = source.metricNames;
      result.metricValuesNorm = source.metricValuesNorm;
      result.zFine = source.zFine;
      result.smoothCurvesNorm = source.smoothCurvesNorm;
      result.z_best_focus = source.zBestFocus;
      return result;
   }

   private static FocusResults wrapResults(ComputeBestFocusFAD.Results source) {
      FocusResults result = new FocusResults();
      result.metricNames = source.metricNames;
      result.metricValuesNorm = source.metricValuesNorm;
      result.zFine = source.zFine;
      result.smoothCurvesNorm = source.smoothCurvesNorm;
      result.z_best_focus = source.zBestFocus;
      return result;
   }

   @Override
   public double fullFocus() throws Exception {
      long t0 = System.currentTimeMillis();
      double bestDist = 5000;
      double bestSh = 0;
      

      if (core_ == null) {
         // if core object is not set attempt to get its global handle
         core_ = app_.getCMMCore();
      }

      if (core_ == null) {
         IJ.error("Unable to get Micro-Manager Core API handle.\n"
               + "If this module is used as ImageJ plugin, Micro-Manager Studio "
               + "must be running first!");
         return 0.0;
      }
      
      applySettings();

      //######################## START THE ROUTINE ###########
      double original_z = core_.getPosition(core_.getFocusDevice());
      double bestZ = original_z;
      
      try {
         IJ.log("TCA Autofocus started.");
         final boolean shutterOpen = core_.getShutterOpen();
         core_.setShutterOpen(true);
         final boolean autoShutter = core_.getAutoShutter();
         core_.setAutoShutter(false);

         //########System setup##########
         Configuration oldState = null;
         if (!channel_.isEmpty()) {
            // we are saving whole config in case we want to restore multiple things
            // for example, AF might want to change exposure and channel
            String chanGroup = core_.getChannelGroup();
            oldState = core_.getConfigGroupState(chanGroup);
            core_.setConfig(chanGroup, channel_);
         }
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         
         List<ImageProcessor> imageProcessors = new ArrayList<>();

         List<Double> zSampledList = new ArrayList<>();
         //double deltaz = 1;

         // create list of z positions with equal spacing based on number of images and assumed deltaz_samp

         //double rel_z_min = -80.0;
         //double rel_z_max = 40.0;
         int numImages = (int) ((rel_z_max_ - rel_z_min_) / deltaz_) + 1;


         for (int i = 0; i < numImages; i++) {
            core_.setPosition(core_.getFocusDevice(), rel_z_min_ + original_z + i * deltaz_);
            core_.waitForDevice(core_.getFocusDevice());
            curDist_ = core_.getPosition(core_.getFocusDevice());
            snapSingleImage();
            delayTime(50);
            if(i > 0){ // skip the first image as stage is too slow to update
               imageProcessors.add(ipCurrent_);
               zSampledList.add(curDist_ - original_z);
            }
         }
         IJ.log("Created Z positions list, deltaZ = " + deltaz_);

         double z_ini = 0.0;
         double deltaz_samp = deltaz_;
         double[] zSampled = zSampledList.stream().mapToDouble(Double::doubleValue).toArray();
         IJ.log("Starting focus score computation with Zsampled: " + Arrays.toString(zSampled));
         
         // get the channel of the current configuration
         focusAnalyzer_ = core_.getCurrentConfig("Channel");
   
         IJ.log("Selected focus analyzer: " + focusAnalyzer_);

         FocusResults results = null;
         switch (focusAnalyzer_) {
            case "460":
               IJ.log("Using 460nm focus analyzer");
               results = wrapResults(ComputeBestFocus460nm.computeBestFocus(imageProcessors, z_ini, deltaz_samp, zSampled));
               break;
            case "300":
               z_ini = -61.0;
               IJ.log("Using 300nm focus analyzer");
               results = wrapResults(ComputeBestFocus300nm.computeBestFocus(imageProcessors, z_ini, deltaz_samp, zSampled));
               break;
            case "NADH":
               z_ini = 5.0;
               IJ.log("Using NADH focus analyzer");
               results = wrapResults(ComputeBestFocusNADH.compute(imageProcessors, z_ini, deltaz_samp, zSampled));
               break;
            case "FAD":
               z_ini = -10.0;
               IJ.log("Using FAD focus analyzer");
               results = wrapResults(ComputeBestFocusFAD.compute(imageProcessors, z_ini, deltaz_samp, zSampled));
               break;
            default:
               IJ.log("Unknown focus analyzer selected, defaulting to 460nm");
               results = wrapResults(ComputeBestFocus460nm.computeBestFocus(imageProcessors, z_ini, deltaz_samp, zSampled));
               break;
         }

         if (results == null) {
            throw new IllegalStateException("Focus results should never be null after analyzer selection.");
         }

         


         

         if(Double.isNaN(results.z_best_focus)){
            IJ.log("Unable to estimate best focus position. Please check the input parameters and try again.");
            core_.setPosition(core_.getFocusDevice(), original_z);

         }else{
            bestZ = results.z_best_focus + original_z;
            IJ.log("Best focus position found at Z = " + bestZ + " (relative Z = " + results.z_best_focus + ")");
            
            if(dryrun_) {
               IJ.log("Dry run enabled, not moving to best focus position, staying at original position  " + original_z);
               core_.setPosition(core_.getFocusDevice(), original_z);
            } else {
               IJ.log("Moving to best focus position...");
               core_.setPosition(core_.getFocusDevice(), bestZ);
            }
            // indx =1;
            snapSingleImage();
         }
         // indx =0;  
         if (oldState != null) {
            // restoring whole config
            // it will not show in debug log as "switching channel to ___"
            // because we are restoring whole config, not just channel
            core_.setSystemState(oldState);
         }
         core_.setShutterOpen(shutterOpen);
         core_.setAutoShutter(autoShutter);
         IJ.log("Focus scores computed, plotting results...");

         // Plot data:
         SortedMap<Double, Double> focusScoreMap = new TreeMap<>();
         int m = 0; // metric ID
         IJ.log("Will only plot curve for metric " + results.metricNames[m]);
         try {
            for (int i = 0; i < zSampled.length; i++) {
               double score = results.metricValuesNorm[i][m];
               focusScoreMap.put(zSampled[i]+original_z, score);
            }
         } catch (Exception e) {
            IJ.log("Error while populating focus score map: " + e.getMessage());
         }   
         
         //IJ.log("focusScoreMap: " + focusScoreMap);

          
         XYSeries xySeries = new XYSeries("Focus Score from images");
         focusScoreMap.forEach(xySeries::add);
         // save focusScoreMap to CSV for debugging
         
         try {
            String curr_datetime = new Date().toString().replace(" ", "_").replace(":", "-");
            saveXYSeriesToCsv(xySeries, curr_datetime+"focus_scores.csv");
         } catch (IOException e) {
            IJ.log("Error while saving focus scores to CSV: " + e.getMessage());
         }


         XYSeries xySeriesFitted = new XYSeries("Fitted Focus Score");
         // populate xySeriesFitted with data from results.smoothCurvesNorm[m]
         for (int i = 0; i < results.zFine.length; i++) {
            xySeriesFitted.add(results.zFine[i]+original_z, results.smoothCurvesNorm[i][m]);
         }

         
         
         try {
            String curr_datetime = new Date().toString().replace(" ", "_").replace(":", "-");
            saveXYSeriesToCsv(xySeries, curr_datetime+"focus_scores.csv");
            saveXYSeriesToCsv(xySeriesFitted, curr_datetime+"fitted_focus_scores.csv");
         } catch (IOException e) {
            IJ.log("Error while saving focus scores to CSV: " + e.getMessage());
         }
         

         XYSeries[] data = {xySeries, xySeriesFitted};

         boolean[] shapes = {true, false};
         PlotUtils pu = new PlotUtils(app_);
         pu.plotDataN("Focus Score", data, "z position", "Focus Score", shapes, "", bestZ);

         IJ.log("Total Time: " + (System.currentTimeMillis() - t0));
      } catch (Exception e) {
         IJ.error(e.getMessage());
      }
      return bestZ; 
   }
   private static void saveXYSeriesToCsv(XYSeries series, String filePath) throws IOException {
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {

         // Optional header
         writer.write("X,Y");
         writer.newLine();

         for (int i = 0; i < series.getItemCount(); i++) {
               double x = series.getX(i).doubleValue();
               double y = series.getY(i).doubleValue();

               writer.write(x + "," + y);
               writer.newLine();
         }
      }
   }

   //take a snapshot and save pixel values in ipCurrent_
   private boolean snapSingleImage() {

      try {
         core_.snapImage();
         Object img = core_.getImage();
         ImagePlus implus = newWindow(); // this step will create a new window iff indx = 1
         implus.getProcessor().setPixels(img);
         ipCurrent_ = implus.getProcessor();
      } catch (Exception e) {
         IJ.log(e.getMessage());
         IJ.error(e.getMessage());
         return false;
      }

      return true;
   }

   //waiting    
   private void delayTime(double delay) {
      Date date = new Date();
      long sec = date.getTime();
      while (date.getTime() < (sec + delay)) {
         date = new Date();
      }
   }

   /**
    * calculate the sharpness of a given image (in "impro").
    *
    * @param impro ImageJ Processor
    * @return sharpness score
    */
   @Override
   public double computeScore(final ImageProcessor impro) {

      int width =  (int) (cropSize_ * core_.getImageWidth());
      int height = (int) (cropSize_ * core_.getImageHeight());
      int ow = (int) (((1 - cropSize_) / 2) * core_.getImageWidth());
      int oh = (int) (((1 - cropSize_) / 2) * core_.getImageHeight());

      double sharpNess = 0;

      impro.medianFilter();
      int[] ken = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      impro.convolve3x3(ken);
      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            sharpNess = sharpNess + Math.pow(impro.getPixel(ow + i, oh + j), 2);
         } 
      }

      // Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2].
      // Then sum all pixel values. Ideally, the sum is large if most edges are sharp

      return sharpNess;
   }


   //making a new window for a new snapshot.
   private ImagePlus newWindow() {
      ImageProcessor ip;
      long byteDepth = core_.getBytesPerPixel();

      if (byteDepth == 1) {
         ip = new ByteProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
      } else  {
         ip = new ShortProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
      }
      ip.setColor(Color.black);
      ip.fill();

      ImagePlus implus = new ImagePlus(String.valueOf(curDist_), ip);
      //snapshot show new window iff indx = 1
      double indx = 0;
      if (indx == 1) {
         if (verbose_) {
            // create image window if we are in the verbose mode
            ImageWindow imageWin = new ImageWindow(implus);
         }
      }
      return implus;
   }

   private double median(double[] arr) {
      double [] newArray = Arrays.copyOf(arr, arr.length);
      Arrays.sort(newArray);
      int middle = newArray.length / 2;
      return (newArray.length % 2 == 1) ? newArray[middle] :
            (newArray[middle - 1] + newArray[middle]) / 2.0;
   }

   @Override
   public String getVerboseStatus() {
      return "OK";
   }
   
   @Override
   public double incrementalFocus() throws Exception {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public PropertyItem[] getProperties() {
      // use default dialog
      // make sure we have the right list of channels
            
      channelGroup_ = core_.getChannelGroup();
      StrVector channels = core_.getAvailableConfigs(channelGroup_);
      String[] allowedChannels = new String[(int) channels.size() + 1];
      allowedChannels[0] = NOCHANNEL;

      try {
         PropertyItem p = getProperty(KEY_CHANNEL);
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
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      return super.getProperties();
   }
      
   void setCropSize(double cs) {
      cropSize_ = cs;
   }
   
   void setThreshold(double thr) {
      thres_ = thr;
   }

   @Override
   public double getCurrentFocusScore() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public int getNumberOfImages() {
      // TODO Auto-generated method stub
      return 0;
   }

   /**
    * Supplies this plugin with the Studio object.
    *
    * @param app The always present Studio object.
    */
   public void setContext(Studio app) {
      app_ = app;
      core_ = app.getCMMCore();
      app_.events().registerForEvents(this);
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
   public String getCopyright() {
      return "Copyright UCSF, 100x Imaging 2007";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }
}   
