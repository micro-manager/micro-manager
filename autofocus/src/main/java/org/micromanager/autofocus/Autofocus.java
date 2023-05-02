///////////////////////////////////////////////////////////////////////////////
//FILE:           Autofocus_.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for mciro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Pakpoom Subsoontorn & Hernan Garcia, June, 2007
//                Nenad Amodaj, nenad@amodaj.com
//
//COPYRIGHT:      California Institute of Technology
//                University of California San Francisco
//                100X Imaging Inc, www.100ximaging.com
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

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.util.Arrays;
import java.util.Date;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.PropertyItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * This plugin take a stack of snapshots and computes their sharpness.
 * Created on June 2nd 2007
 * author: Pakpoom Subsoontorn & Hernan Garcia
 */
@Plugin(type = AutofocusPlugin.class)
public class Autofocus extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin  {

   private static final String KEY_SIZE_FIRST = "1st step size";
   private static final String KEY_NUM_FIRST = "1st step number";
   private static final String KEY_SIZE_SECOND = "2nd step size";
   private static final String KEY_NUM_SECOND = "2nd step number";
   private static final String KEY_THRES    = "Threshold";
   private static final String KEY_CROP_SIZE = "Crop ratio";
   private static final String KEY_CHANNEL = "Channel";
   private static final String NOCHANNEL = "";
   //private static final String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   
   private static final String AF_DEVICE_NAME = "JAF(H&P)";

   private Studio app_;
   private CMMCore core_;
   private ImageProcessor ipCurrent_ = null;

   public double sizeFirst_ = 2;
   public int numFirst_ = 1; // +/- #of snapshot
   public  double sizeSecond_ = 0.2;
   public  int numSecond_ = 5;
   public double thres_ = 0.02;
   public double cropSize_ = 0.2;
   public String channel_ = "";

   private boolean verbose_ = true; // displaying debug info or not
   private String channelGroup_;
   private double curDist_;

   /**
    * Constructors creates needed properties.
    *
    */
   public Autofocus() {
      super.createProperty(KEY_SIZE_FIRST, Double.toString(sizeFirst_));
      super.createProperty(KEY_NUM_FIRST, Integer.toString(numFirst_));
      super.createProperty(KEY_SIZE_SECOND, Double.toString(sizeSecond_));
      super.createProperty(KEY_NUM_SECOND, Integer.toString(numSecond_));
      super.createProperty(KEY_THRES, Double.toString(thres_));
      super.createProperty(KEY_CROP_SIZE, Double.toString(cropSize_));
      super.createProperty(KEY_CHANNEL, channel_);
   }


   @Override
   public void applySettings() {
      try {
         sizeFirst_ = Double.parseDouble(getPropertyValue(KEY_SIZE_FIRST));
         numFirst_ = Integer.parseInt(getPropertyValue(KEY_NUM_FIRST));
         sizeSecond_ = Double.parseDouble(getPropertyValue(KEY_SIZE_SECOND));
         numSecond_ = Integer.parseInt(getPropertyValue(KEY_NUM_SECOND));
         thres_ = Double.parseDouble(getPropertyValue(KEY_THRES));
         cropSize_ = Double.parseDouble(getPropertyValue(KEY_CROP_SIZE));
         channel_ = getPropertyValue(KEY_CHANNEL);
      
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }

   }

   /**
    * Old fashioned run method.
    *
    * @param arg Unsure what should go here, please fill in.
    */
   public void run(String arg) {
      long t0 = System.currentTimeMillis();
      double bestDist = 5000;
      double bestSh = 0;
      //############# CHECK INPUT ARG AND CORE ########
      verbose_ = arg.compareTo("silent") != 0;

      if (arg.compareTo("options") == 0) {
         app_.app().showAutofocusDialog();
      }

      if (core_ == null) {
         // if core object is not set attempt to get its global handle
         core_ = app_.getCMMCore();
      }

      if (core_ == null) {
         IJ.error("Unable to get Micro-Manager Core API handle.\n"
               + "If this module is used as ImageJ plugin, Micro-Manager Studio "
               + "must be running first!");
         return;
      }
      
      applySettings();

      //######################## START THE ROUTINE ###########

      try {
         IJ.log("Autofocus started.");
         final boolean shutterOpen = core_.getShutterOpen();
         core_.setShutterOpen(true);
         final boolean autoShutter = core_.getAutoShutter();
         core_.setAutoShutter(false);

         //########System setup##########
         if (!channel_.equals(NOCHANNEL)) {
            core_.setConfig(channelGroup_, channel_);
         }
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         //delay_time(3000);


         //Snapshot, zdistance and sharpNess before AF 
         /* curDist = core_.getPosition(core_.getFocusDevice());
         indx =1;
         snapSingleImage();
         indx =0;

         tPrev = System.currentTimeMillis();
         curSh = sharpNess(ipCurrent_);
         tcur = System.currentTimeMillis()-tPrev;*/



         //set z-distance to the lowest z-distance of the stack
         curDist_ = core_.getPosition(core_.getFocusDevice());
         double baseDist = curDist_ - sizeFirst_ * numFirst_;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         core_.waitForDevice(core_.getFocusDevice());
         delayTime(300);
         IJ.log(" Before rough search: " + curDist_);

         //Rough search
         double curSh;
         for (int i = 0; i < 2 * numFirst_ + 1; i++) {
            core_.setPosition(core_.getFocusDevice(), baseDist + i * sizeFirst_);
            core_.waitForDevice(core_.getFocusDevice());
            curDist_ = core_.getPosition(core_.getFocusDevice());
            snapSingleImage();

            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist_;
            } else if (bestSh - curSh > thres_ * bestSh && bestDist < 5000) {
               break;
            }

         }

         baseDist = bestDist - sizeSecond_ * numSecond_;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         delayTime(100);

         bestSh = 0;

         //Fine search
         for (int i = 0; i < 2 * numSecond_ + 1; i++) {
            core_.setPosition(core_.getFocusDevice(), baseDist + i * sizeSecond_);
            core_.waitForDevice(core_.getFocusDevice());

            curDist_ = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;    

            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist_;
            } else if (bestSh - curSh > thres_ * bestSh && bestDist < 5000) {
               break;
            }
            //===IJ.log(String.valueOf(curDist)+" "+String.valueOf(curSh)+" "+String.valueOf(tcur));
         }

         IJ.log("BEST_DIST_SECOND" + bestDist + " BEST_SH_SECOND" + bestSh);

         core_.setPosition(core_.getFocusDevice(), bestDist);
         // indx =1;
         snapSingleImage();
         // indx =0;  
         core_.setShutterOpen(shutterOpen);
         core_.setAutoShutter(autoShutter);


         IJ.log("Total Time: " + (System.currentTimeMillis() - t0));
      } catch (Exception e) {
         IJ.error(e.getMessage());
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

   /*calculate the sharpness of a given image (in "impro").*/
   private double sharpNessp(ImageProcessor impro) {
      int width =  (int) (cropSize_ * core_.getImageWidth());
      int height = (int) (cropSize_ * core_.getImageHeight());
      int ow = (int) (((1 - cropSize_) / 2) * core_.getImageWidth());
      int oh = (int) (((1 - cropSize_) / 2) * core_.getImageHeight());

      double[][] medPix = new double[width][height];
      double sharpNess = 0;
      double[] windo = new double[9];

      /*Apply 3x3 median filter to reduce noise*/
      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            windo[0] = impro.getPixel(ow + i - 1, oh + j - 1);
            windo[1] = impro.getPixel(ow + i, oh + j - 1);
            windo[2] = impro.getPixel(ow + i + 1, oh + j - 1);
            windo[3] = impro.getPixel(ow + i - 1, oh + j);
            windo[4] = impro.getPixel(ow + i, oh + j);
            windo[5] = impro.getPixel(ow + i + 1, oh + j);
            windo[6] = impro.getPixel(ow + i - 1, oh + j + 1);
            windo[7] = impro.getPixel(ow + i, oh + j + 1);
            windo[8] = impro.getPixel(ow + i + 1, oh + j + 1);

            medPix[i][j] = median(windo);
         } 
      }

      // Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2].
      // Then sum all pixel values. Ideally, the sum is large if most edges are sharp.

      for (int k = 1; k < width - 1; k++) {
         for (int l = 1; l < height - 1; l++) {
            sharpNess = sharpNess + Math.pow((-2 * medPix[k - 1][l - 1]
                  - medPix[k][l - 1] - medPix[k - 1][l] + medPix[k + 1][l]
                  + medPix[k][l + 1] + 2 * medPix[k + 1][l + 1]), 2);
         }
      }
      return sharpNess;
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
   public double fullFocus() {
      run("silent");
      return 0;
   }

   @Override
   public String getVerboseStatus() {
      return "OK";
   }

   @Override
   public double incrementalFocus() {
      run("silent");
      return 0;
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
