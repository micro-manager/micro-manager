package org.micromanager.autofocus;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.Date;
import mmcorej.CMMCore;
import mmcorej.StrVector;
import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.PropertyItem;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/*
*  Created on June 2nd 2007
*  author: Pakpoom Subsoontorn & Hernan Garcia
*/

/*
*  This plugin take a stack of snapshots and computes their sharpness
*/
@Plugin(type = AutofocusPlugin.class)
public class AutofocusTB extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin {

   private static final String KEY_SIZE_FIRST = "1st step size";
   private static final String KEY_NUM_FIRST = "1st setp number";
   private static final String KEY_SIZE_SECOND = "2nd step size";
   private static final String KEY_NUM_SECOND = "2nd step number";
   private static final String KEY_THRES = "Threshold";
   private static final String KEY_CROP_SIZE = "Crop ratio";
   private static final String KEY_CHANNEL1 = "Channel-1";
   private static final String KEY_CHANNEL2 = "Channel-2";
   private static final String NOCHANNEL = "";
   private static final String AF_DEVICE_NAME = "JAF(TB)";

   private Studio app_;
   private CMMCore core_;
   private ImageProcessor ipCurrent_ = null;

   /**
    * Description of the Field.
    */
   public double sizeFirst_ = 2;
   //
   public int numFirst_ = 1;
   // +/- #of snapshot
   public double sizeSecond_ = 0.2;
   public int numSecond_ = 5;
   public double threshold_ = 0.02;
   public double cropSize_ = 0.2;
   public String channel1_ = "Bright-Field";
   public String channel2_ = "DAPI";

   private final double indx = 0;
   // snapshot show new window iff indx = 1

   private boolean verbose_ = true;
   // displaying debug info or not

   private String channelGroup_;

   private double curDist;
   private double baseDist;
   private double bestDist;
   private double curSh;
   private double bestSh;
   private long t0;
   private long tPrev;
   private long tcur;

   // channel group for autofocus
   private static final String KEY_COLOR_CHANNEL = "Color Channel";
   private String selectedColorChannel_ = "Red (BGRA)"; // Default

   /**
    * Constructor for the Autofocus_ object.
    */
   public AutofocusTB() {
      super.createProperty(KEY_SIZE_FIRST, Double.toString(sizeFirst_));
      super.createProperty(KEY_NUM_FIRST, Integer.toString(numFirst_));
      super.createProperty(KEY_SIZE_SECOND, Double.toString(sizeSecond_));
      super.createProperty(KEY_NUM_SECOND, Integer.toString(numSecond_));
      super.createProperty(KEY_THRES, Double.toString(threshold_));
      super.createProperty(KEY_CROP_SIZE, Double.toString(cropSize_));
      super.createProperty(KEY_CHANNEL1, channel1_);
      super.createProperty(KEY_CHANNEL2, channel2_);
      super.createProperty(KEY_COLOR_CHANNEL, selectedColorChannel_);
   }

   @Override
   public void applySettings() {
      try {
         sizeFirst_ = Double.parseDouble(getPropertyValue(KEY_SIZE_FIRST));
         numFirst_ = Integer.parseInt(getPropertyValue(KEY_NUM_FIRST));
         sizeSecond_ = Double.parseDouble(getPropertyValue(KEY_SIZE_SECOND));
         numSecond_ = Integer.parseInt(getPropertyValue(KEY_NUM_SECOND));
         threshold_ = Double.parseDouble(getPropertyValue(KEY_THRES));
         cropSize_ = Double.parseDouble(getPropertyValue(KEY_CROP_SIZE));
         channel1_ = getPropertyValue(KEY_CHANNEL1);
         channel2_ = getPropertyValue(KEY_CHANNEL2);

         selectedColorChannel_ = getPropertyValue(KEY_COLOR_CHANNEL);

      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   /**
    * Main processing method for the Autofocus_ object.
    *
    * @param arg Description of the Parameter
    */
   public void run(String arg) {
      t0 = System.currentTimeMillis();
      bestDist = 5000;
      bestSh = 0;
      // ############# CHECK INPUT ARG AND CORE ########
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

      // ######################## START THE ROUTINE ###########

      try {
         IJ.log("Autofocus TB started.");
         // ########System setup##########
         if (!channel1_.equals(NOCHANNEL) && channelGroup_ != null) {
            core_.setConfig(channelGroup_, channel1_);
         }
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         // delay_time(3000);

         // Snapshot, zdistance and sharpNess before AF
         /*
          * curDist = core_.getPosition(core_.getFocusDevice());
          * indx =1;
          * snapSingleImage();
          * indx =0;
          * tPrev = System.currentTimeMillis();
          * curSh = sharpNess(ipCurrent_);
          * tcur = System.currentTimeMillis()-tPrev;
          */
         // set z-distance to the lowest z-distance of the stack
         curDist = core_.getPosition(core_.getFocusDevice());
         baseDist = curDist - sizeFirst_ * numFirst_;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         core_.waitForDevice(core_.getFocusDevice());
         delayTime(100);

         // core_.setShutterOpen(true);
         // core_.setAutoShutter(false);

         IJ.log("Before rough search: " + curDist);

         // Rough search
         for (int i = 0; i < 2 * numFirst_ + 1; i++) {
            tPrev = System.currentTimeMillis();

            core_.setPosition(core_.getFocusDevice(), baseDist + i * sizeFirst_);
            core_.waitForDevice(core_.getFocusDevice());

            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;

            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist;
            } else if (bestSh - curSh > threshold_ * bestSh) {
               break;
            }
            tcur = System.currentTimeMillis() - tPrev;

         }

         baseDist = bestDist - sizeSecond_ * numSecond_;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         delayTime(100);

         bestSh = 0;

         if (!channel2_.equals(NOCHANNEL) && channelGroup_ != null) {
            core_.setConfig(channelGroup_, channel2_);
         }
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         // Fine search
         for (int i = 0; i < 2 * numSecond_ + 1; i++) {
            tPrev = System.currentTimeMillis();
            core_.setPosition(core_.getFocusDevice(), baseDist + i * sizeSecond_);
            core_.waitForDevice(core_.getFocusDevice());

            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;

            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist;
            } else if (bestSh - curSh > threshold_ * bestSh) {
               break;
            }
            tcur = System.currentTimeMillis() - tPrev;

            // ===IJ.write(String.valueOf(curDist)+" "+String.valueOf(curSh)+" "
            // +String.valueOf(tcur));
         }

         IJ.log("BEST_DIST_SECOND= " + bestDist + " BEST_SH_SECOND= " + bestSh);

         core_.setPosition(core_.getFocusDevice(), bestDist);
         // indx =1;
         snapSingleImage();
         // indx =0;
         // core_.setShutterOpen(false);
         // core_.setAutoShutter(true);

         IJ.log("Total Time: " + (System.currentTimeMillis() - t0));
      } catch (Exception e) {
         IJ.error(e.getMessage());
      }
   }

   // take a snapshot and save pixel values in ipCurrent_
   private boolean snapSingleImage() {
      try {
         core_.snapImage();
         Thread.sleep(50);
         Object img = core_.getImage();

         int width = (int) core_.getImageWidth();
         int height = (int) core_.getImageHeight();

         ImagePlus implus;

         if (selectedColorChannel_.equals("Grayscale")) {
            if (img instanceof byte[]) {
               IJ.log("snapSingleImage: Detected 8-bit grayscale image.");
               ByteProcessor bp = new ByteProcessor(width, height);
               bp.setPixels(img);
               implus = new ImagePlus("Grayscale 8-bit", bp);
               this.ipCurrent_ = bp;
            } else if (img instanceof short[]) {
               IJ.log("snapSingleImage: Detected 16-bit grayscale image.");
               ShortProcessor sp = new ShortProcessor(width, height);
               sp.setPixels(img);
               implus = new ImagePlus("Grayscale 16-bit", sp);
               this.ipCurrent_ = sp;
            } else {
               String err = "Unsupported grayscale format: " + img.getClass().getName();
               IJ.log("snapSingleImage: " + err);
               IJ.error(err);
               return false;
            }

         } else if (this.core_.getNumberOfComponents() >= 3 && img instanceof byte[]) {
            IJ.log("snapSingleImage: Detected color image, extracting: " + selectedColorChannel_);
            byte[] rgbBytes = (byte[]) img;
            byte[] channel = new byte[width * height];

            int channelOffset;
            switch (selectedColorChannel_) {
               case "Red (BGRA)":
                  channelOffset = 2;
                  break;
               case "Green (BGRA)":
                  channelOffset = 1;
                  break;
               case "Blue (BGRA)":
                  channelOffset = 0;
                  break;
               default:
                  IJ.log("snapSingleImage: Invalid color channel selection.");
                  return false;
            }

            for (int i = 0; i < width * height; i++) {
               channel[i] = rgbBytes[4 * i + channelOffset];
            }

            ByteProcessor channelProcessor = new ByteProcessor(width, height, channel, null);
            implus = new ImagePlus(selectedColorChannel_, channelProcessor);
            // implus.show();
            this.ipCurrent_ = channelProcessor;
         } else {
            String err = "Unsupported image format or configuration.";
            IJ.log("snapSingleImage: " + err);
            // IJ.error(err);
            return false;
         }

      } catch (Exception e) {
         IJ.log("Exception in snapSingleImage: " + e.getMessage());
         e.printStackTrace();
         IJ.error(e.getMessage());
         return false;

      }

      return true;
   }

   // waiting
   private void delayTime(double delay) {
      Date date = new Date();
      long sec = date.getTime();
      while (date.getTime() < sec + delay) {
         date = new Date();
      }
   }

   /*
    * calculate the sharpness of a given image (in "impro").
    */
   private double sharpNessp(ImageProcessor impro) {

      int width = (int) (cropSize_ * core_.getImageWidth());
      int height = (int) (cropSize_ * core_.getImageHeight());
      int ow = (int) (((1 - cropSize_) / 2) * core_.getImageWidth());
      int oh = (int) (((1 - cropSize_) / 2) * core_.getImageHeight());

      double[][] medPix = new double[width][height];
      double sharpNess = 0;
      double[] windo = new double[9];

      /*
       * Apply 3x3 median filter to reduce noise
       */
      for (int i = 1; i < width - 1; i++) {
         for (int j = 1; j < height - 1; j++) {

            windo[0] = impro.getPixel(ow + i - 1, oh + j - 1);
            windo[1] = impro.getPixel(ow + i, oh + j - 1);
            windo[2] = impro.getPixel(ow + i + 1, oh + j - 1);
            windo[3] = impro.getPixel(ow + i - 1, oh + j);
            windo[4] = impro.getPixel(ow + i, oh + j);
            windo[5] = impro.getPixel(ow + i + 1, oh + j);
            windo[6] = impro.getPixel(ow + i - 1, oh + j + 1);
            windo[7] = impro.getPixel(ow + i, oh + j + 1);
            windo[8] = impro.getPixel(ow + i + 1, oh + j + 1);

            medPix[i][j] = findMed(windo);
         }
      }

      /*
       * Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2].
       * Then sum all pixel values. Ideally, the sum is large if most edges are sharp
       */
      double edgehoriz;
      double edvevert;
      for (int k = 1; k < width - 1; k++) {
         for (int l = 1; l < height - 1; l++) {
            edgehoriz = Math.pow((-2 * medPix[k - 1][l] - medPix[k - 1][l - 1]
                  - medPix[k - 1][l + 1] + medPix[k + 1][l - 1] + medPix[k + 1][l + 1]
                  + 2 * medPix[k + 1][l]), 2);
            edvevert = Math.pow((-2 * medPix[k][l - 1] - medPix[k - 1][l - 1]
                  - medPix[k + 1][l - 1] + medPix[k - 1][l + 1] + medPix[k + 1][l + 1]
                  + 2 * medPix[k][l + 1]), 2);
            sharpNess += (edgehoriz + edvevert);
            // sharpNess = sharpNess + Math.pow((-2 * medPix[k - 1][l - 1]
            // - medPix[k][l - 1] - medPix[k - 1][l] + medPix[k + 1][l] + medPix[k][l + 1]
            // + 2 * medPix[k + 1][l + 1]), 2);

         }
      }
      return sharpNess;
   }

   /*
    * calculate the sharpness of a given image (in "impro").
    */
   public double computeScore(final ImageProcessor impro) {
      int width = (int) (cropSize_ * core_.getImageWidth());
      int height = (int) (cropSize_ * core_.getImageHeight());
      int sx = (int) (core_.getImageWidth() - width) / 2;
      int sy = (int) (core_.getImageHeight() - height) / 2;
      // int ow = (int) (((1 - CROP_SIZE) / 2) * core_.getImageWidth());
      // int oh = (int) (((1 - CROP_SIZE) / 2) * core_.getImageHeight());

      // double[][] medPix = new double[width][height];
      double sharpNess = 0;
      // double[] windo = new double[9];

      /*
       * Apply 3x3 median filter to reduce noise
       */
      /*
       * for (int i=0; i<width; i++){
       * for (int j=0; j<height; j++){
       * windo[0] = (double)impro.getPixel(ow+i-1,oh+j-1);
       * windo[1] = (double)impro.getPixel(ow+i,oh+j-1);
       * windo[2] = (double)impro.getPixel(ow+i+1,oh+j-1);
       * windo[3] = (double)impro.getPixel(ow+i-1,oh+j);
       * windo[4] = (double)impro.getPixel(ow+i,oh+j);
       * windo[5] = (double)impro.getPixel(ow+i+1,oh+j);
       * windo[6] = (double)impro.getPixel(ow+i-1,oh+j+1);
       * windo[7] = (double)impro.getPixel(ow+i,oh+j+1);
       * windo[8] = (double)impro.getPixel(ow+i+1,oh+j+1);
       * medPix[i][j] = findMed(windo);
       * }
       * }
       */
      // tPrev = System.currentTimeMillis();

      impro.setRoi(new Rectangle(sx, sy, width, height));
      impro.crop();
      impro.medianFilter();
      impro.findEdges();

      // int[] ken = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      // impro.convolve3x3(ken);

      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            sharpNess += impro.getPixelValue(i, j);
         }
      }

      // tcur = System.currentTimeMillis()-tPrev;

      /*
       * Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2].
       * Then sum all pixel values. Ideally, the sum is large if most edges are sharp
       */
      /*
       * for (int k=1; k<width-1; k++){
       * for (int l=1; l<height-1; l++){
       * sharpNess = sharpNess + Math.pow((-2*medPix[k-1][l-1]- medPix[k][l-1]
       * - medPix[k-1][l]+medPix[k+1][l]+medPix[k][l+1]+2*medPix[k+1][l+1]),2);
       * }
       * }
       */
      return sharpNess;
   }

   // making a new window for a new snapshot.
   private ImagePlus newWindow() {
      ImageProcessor ip;
      long byteDepth = core_.getBytesPerPixel();
      long numComponents = core_.getNumberOfComponents();

      if (numComponents >= 3) {
         ip = new ColorProcessor(
               (int) this.core_.getImageWidth(),
               (int) this.core_.getImageHeight());
      } else if (byteDepth == 1L) {
         ip = new ByteProcessor(
               (int) this.core_.getImageWidth(),
               (int) this.core_.getImageHeight());
      } else {
         ip = new ShortProcessor(
               (int) this.core_.getImageWidth(),
               (int) this.core_.getImageHeight());
      }
      ip.setColor(Color.black);
      ip.fill();

      ImagePlus implus = new ImagePlus(String.valueOf(curDist), ip);
      // if (indx == 1) {
      // if (verbose_) {
      // // create image window if we are in the verbose mode
      // ImageWindow imageWin = new ImageWindow(implus);
      // }
      // }
      return implus;
   }

   private double findMed(double[] arr) {
      double tmp;
      boolean sorted = false;
      int i = 0;
      while (!sorted) {
         sorted = true;
         for (int j = 0; j < 8 - i; j++) {
            if (arr[j + 1] < arr[j]) {
               sorted = false;
               tmp = arr[j];
               arr[j] = arr[j + 1];
               arr[j + 1] = tmp;
            }
         }
         i++;
      }
      return arr[5];
   }

   public double fullFocus() {
      run("silent");
      return 0;
   }

   /**
    * Gets the verboseStatus attribute of the Autofocus_ object.
    *
    * @return The verboseStatus value
    */
   public String getVerboseStatus() {
      return "OK";
   }

   /**
    * Description of the Method.
    *
    * @return Description of the Return Value
    */
   public double incrementalFocus() {
      run("silent");
      return 0;
   }

   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) {
      sizeFirst_ = coarseStep;
      numFirst_ = numCoarse;
      sizeSecond_ = fineStep;
      numSecond_ = numFine;

      run("silent");
   }

   /**
    * Description of the Method.
    */
   @Override
   public PropertyItem[] getProperties() {
      channelGroup_ = core_.getChannelGroup();
      StrVector channels = core_.getAvailableConfigs(channelGroup_);
      String[] allowedChannels = new String[(int) channels.size() + 1];
      allowedChannels[0] = NOCHANNEL;

      try {
         PropertyItem colorChannel = getProperty(KEY_COLOR_CHANNEL);
         colorChannel.allowed = new String[] {
               "Grayscale",
               "Red (BGRA)",
               "Green (BGRA)",
               "Blue (BGRA)"
         };
         setProperty(colorChannel);

         PropertyItem p1 = getProperty(KEY_CHANNEL1);
         PropertyItem p2 = getProperty(KEY_CHANNEL2);
         boolean found1 = false;
         boolean found2 = false;
         for (int i = 0; i < channels.size(); i++) {
            allowedChannels[i + 1] = channels.get(i);
            if (p1.value.equals(channels.get(i))) {
               found1 = true;
            }
            if (p2.value.equals(channels.get(i))) {
               found2 = true;
            }
         }
         p1.allowed = allowedChannels;
         if (!found1) {
            p1.value = allowedChannels[0];
         }
         setProperty(p1);

         p2.allowed = allowedChannels;
         if (!found2) {
            p2.value = allowedChannels[0];
         }
         setProperty(p2);
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      return super.getProperties();
   }

   @Override
   public double getCurrentFocusScore() {
      return 0;
   }

   @Override
   public int getNumberOfImages() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
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
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Pakpoom Subsoontorn & Hernan Garcia, 2007";
   }
}
