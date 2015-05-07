import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.util.prefs.Preferences;
import java.util.Date;
import java.awt.Rectangle;

import mmcorej.StrVector;

import org.micromanager.api.Autofocus;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.AutofocusBase;
import org.micromanager.utils.MMException;
import org.micromanager.utils.PropertyItem;

import mmcorej.CMMCore;

/*
*  Created on June 2nd 2007
*  author: Pakpoom Subsoontorn & Hernan Garcia
*/

/*
*  This plugin take a stack of snapshots and computes their sharpness
*/
public class AutofocusTB extends AutofocusBase implements Autofocus {

   private final static String KEY_SIZE_FIRST = "1st step size";
   private final static String KEY_NUM_FIRST = "1st setp number";
   private final static String KEY_SIZE_SECOND = "2nd step size";
   private final static String KEY_NUM_SECOND = "2nd step number";
   private final static String KEY_THRES = "Threshold";
   private final static String KEY_CROP_SIZE = "Crop ratio";
   private final static String KEY_CHANNEL1 = "Channel-1";
   private final static String KEY_CHANNEL2 = "Channel-2";
   private static final String NOCHANNEL = "";
   private final static String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   private static final String AF_DEVICE_NAME = "JAF(TB)";

   private ScriptInterface app_;
   private CMMCore core_;
   private ImageProcessor ipCurrent_ = null;

   /**
    *  Description of the Field
    */
   public double SIZE_FIRST = 2;
   //
   /**
    *  Description of the Field
    */
   public int NUM_FIRST = 1;
   // +/- #of snapshot
   /**
    *  Description of the Field
    */
   public double SIZE_SECOND = 0.2;
   /**
    *  Description of the Field
    */
   public int NUM_SECOND = 5;
   /**
    *  Description of the Field
    */
   public double THRES = 0.02;
   /**
    *  Description of the Field
    */
   public double CROP_SIZE = 0.2;
   /**
    *  Description of the Field
    */
   public String CHANNEL1 = "Bright-Field";
   /**
    *  Description of the Field
    */
   public String CHANNEL2 = "DAPI";

   private double indx = 0;
   //snapshot show new window iff indx = 1

   private boolean verbose_ = true;
   // displaying debug info or not

   private Preferences prefs_;
   //********

   private String channelGroup_;

   private double curDist;
   private double baseDist;
   private double bestDist;
   private double curSh;
   private double bestSh;
   private long t0;
   private long tPrev;
   private long tcur;


   /**
    *  Constructor for the Autofocus_ object
    */
   public AutofocusTB() {
      super();

      // set-up properties
      createProperty(KEY_SIZE_FIRST, Double.toString(SIZE_FIRST));                                    
      createProperty(KEY_NUM_FIRST, Integer.toString(NUM_FIRST));
      createProperty(KEY_SIZE_SECOND, Double.toString(SIZE_SECOND));
      createProperty(KEY_NUM_SECOND, Integer.toString(NUM_SECOND));
      createProperty(KEY_THRES, Double.toString(THRES));
      createProperty(KEY_CROP_SIZE, Double.toString(CROP_SIZE));
      createProperty(KEY_CHANNEL1, CHANNEL1);
      createProperty(KEY_CHANNEL2, CHANNEL2);

      loadSettings();
   }

   public void applySettings() {
      try {
         SIZE_FIRST = Double.parseDouble(getPropertyValue(KEY_SIZE_FIRST));
         NUM_FIRST = Integer.parseInt(getPropertyValue(KEY_NUM_FIRST));
         SIZE_SECOND = Double.parseDouble(getPropertyValue(KEY_SIZE_SECOND));
         NUM_SECOND = Integer.parseInt(getPropertyValue(KEY_NUM_SECOND));
         THRES = Double.parseDouble(getPropertyValue(KEY_THRES));
         CROP_SIZE = Double.parseDouble(getPropertyValue(KEY_CROP_SIZE));
         CHANNEL1 = getPropertyValue(KEY_CHANNEL1);
         CHANNEL2 = getPropertyValue(KEY_CHANNEL2);

      } catch (NumberFormatException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (MMException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();                                                                         
      }                                                                                               
                                                                                                      
   }

   /**
    *  Main processing method for the Autofocus_ object
    *
    *@param  arg  Description of the Parameter
    */
   public void run(String arg) {
      t0 = System.currentTimeMillis();
      bestDist = 5000;
      bestSh = 0;
      //############# CHECK INPUT ARG AND CORE ########
      if (arg.compareTo("silent") == 0) {
         verbose_ = false;
      } else {
         verbose_ = true;
      }

      if (arg.compareTo("options") == 0) {
         app_.getAutofocusManager().showOptionsDialog();
      }

      if (core_ == null) {
         // if core object is not set attempt to get its global handle
         core_ = app_.getMMCore();
      }

      if (core_ == null) {
         IJ.error("Unable to get Micro-Manager Core API handle.\n" +
               "If this module is used as ImageJ plugin, Micro-Manager Studio must be running first!");
         return;
      }

      applySettings();

      //######################## START THE ROUTINE ###########

      try {
         IJ.log("Autofocus TB started.");
         //########System setup##########
         if (!CHANNEL1.equals(NOCHANNEL) && channelGroup_ != null) 
            core_.setConfig(channelGroup_,CHANNEL1); 
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         //delay_time(3000);


         //Snapshot, zdistance and sharpNess before AF
         /*
          *  curDist = core_.getPosition(core_.getFocusDevice());
          *  indx =1;
          *  snapSingleImage();
          *  indx =0;
          *  tPrev = System.currentTimeMillis();
          *  curSh = sharpNess(ipCurrent_);
          *  tcur = System.currentTimeMillis()-tPrev;
          */
         //set z-distance to the lowest z-distance of the stack
         curDist = core_.getPosition(core_.getFocusDevice());
         baseDist = curDist - SIZE_FIRST * NUM_FIRST;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         core_.waitForDevice(core_.getFocusDevice());
         delay_time(100);

         //core_.setShutterOpen(true);
         //core_.setAutoShutter(false);

         IJ.log("Before rough search: " + String.valueOf(curDist));

         //Rough search
         for (int i = 0; i < 2 * NUM_FIRST + 1; i++) {
            tPrev = System.currentTimeMillis();

            core_.setPosition(core_.getFocusDevice(), baseDist + i * SIZE_FIRST);
            core_.waitForDevice(core_.getFocusDevice());

            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;


            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist;
            } else if (bestSh - curSh > THRES * bestSh) {
               break;
            }
            tcur = System.currentTimeMillis() - tPrev;

            //===IJ.write(String.valueOf(curDist)+" "+String.valueOf(curSh)+" " +String.valueOf(tcur));
         }

         //===IJ.write("BEST_DIST_FIRST"+String.valueOf(bestDist)+" BEST_SH_FIRST"+String.valueOf(bestSh));

         baseDist = bestDist - SIZE_SECOND * NUM_SECOND;
         core_.setPosition(core_.getFocusDevice(), baseDist);
         delay_time(100);

         bestSh = 0;

         if (!CHANNEL2.equals(NOCHANNEL) && channelGroup_ != null)
            core_.setConfig(channelGroup_,CHANNEL2); 
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0) {
            core_.waitForDevice(core_.getShutterDevice());
         }
         //Fine search
         for (int i = 0; i < 2 * NUM_SECOND + 1; i++) {
            tPrev = System.currentTimeMillis();
            core_.setPosition(core_.getFocusDevice(), baseDist + i * SIZE_SECOND);
            core_.waitForDevice(core_.getFocusDevice());

            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;

            curSh = computeScore(ipCurrent_);

            if (curSh > bestSh) {
               bestSh = curSh;
               bestDist = curDist;
            } else if (bestSh - curSh > THRES * bestSh) {
               break;
            }
            tcur = System.currentTimeMillis() - tPrev;

            //===IJ.write(String.valueOf(curDist)+" "+String.valueOf(curSh)+" "+String.valueOf(tcur));
         }

         IJ.log("BEST_DIST_SECOND= " + String.valueOf(bestDist) + " BEST_SH_SECOND= " + String.valueOf(bestSh));

         core_.setPosition(core_.getFocusDevice(), bestDist);
         // indx =1;
         snapSingleImage();
         // indx =0;
         //core_.setShutterOpen(false);
         //core_.setAutoShutter(true);

         IJ.log("Total Time: " + String.valueOf(System.currentTimeMillis() - t0));
      } catch (Exception e) {
         IJ.error(e.getMessage());
      }
   }



   //take a snapshot and save pixel values in ipCurrent_
   /**
    *  Description of the Method
    *
    *@return    Description of the Return Value
    */
   private boolean snapSingleImage() {

      try {
         core_.snapImage();
         Object img = core_.getImage();
         ImagePlus implus = newWindow();
         // this step will create a new window iff indx = 1
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
   /**
    *  Description of the Method
    *
    *@param  delay  Description of the Parameter
    */
   private void delay_time(double delay) {
      Date date = new Date();
      long sec = date.getTime();
      while (date.getTime() < sec + delay) {
         date = new Date();
      }
   }



   /*
    *  calculate the sharpness of a given image (in "impro").
    */
   /**
    *  Description of the Method
    *
    *@param  impro  Description of the Parameter
    *@return        Description of the Return Value
    */
   private double sharpNessp(ImageProcessor impro) {

      int width = (int) (CROP_SIZE * core_.getImageWidth());
      int height = (int) (CROP_SIZE * core_.getImageHeight());
      int ow = (int) (((1 - CROP_SIZE) / 2) * core_.getImageWidth());
      int oh = (int) (((1 - CROP_SIZE) / 2) * core_.getImageHeight());

      double[][] medPix = new double[width][height];
      double sharpNess = 0;
      double[] windo = new double[9];

      /*
       *  Apply 3x3 median filter to reduce noise
       */
      for (int i = 1; i < width - 1; i++) {
         for (int j = 1; j < height - 1; j++) {

            windo[0] = (double) impro.getPixel(ow + i - 1, oh + j - 1);
            windo[1] = (double) impro.getPixel(ow + i, oh + j - 1);
            windo[2] = (double) impro.getPixel(ow + i + 1, oh + j - 1);
            windo[3] = (double) impro.getPixel(ow + i - 1, oh + j);
            windo[4] = (double) impro.getPixel(ow + i, oh + j);
            windo[5] = (double) impro.getPixel(ow + i + 1, oh + j);
            windo[6] = (double) impro.getPixel(ow + i - 1, oh + j + 1);
            windo[7] = (double) impro.getPixel(ow + i, oh + j + 1);
            windo[8] = (double) impro.getPixel(ow + i + 1, oh + j + 1);

            medPix[i][j] = findMed(windo);
         }
      }

      /*
       *  Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp
       */
      double edgehoriz;
      double edvevert;
      for (int k = 1; k < width - 1; k++) {
         for (int l = 1; l < height - 1; l++) {
            edgehoriz = Math.pow((-2 * medPix[k - 1][l] - medPix[k - 1][l - 1] - medPix[k - 1][l + 1] + medPix[k + 1][l - 1] + medPix[k + 1][l + 1] + 2 * medPix[k + 1][l]), 2);
            edvevert = Math.pow((-2 * medPix[k][l - 1] - medPix[k - 1][l - 1] - medPix[k + 1][l - 1] + medPix[k - 1][l + 1] + medPix[k + 1][l + 1] + 2 * medPix[k][l + 1]), 2);
            sharpNess += (edgehoriz + edvevert);
            //sharpNess = sharpNess + Math.pow((-2 * medPix[k - 1][l - 1] - medPix[k][l - 1] - medPix[k - 1][l] + medPix[k + 1][l] + medPix[k][l + 1] + 2 * medPix[k + 1][l + 1]), 2);

         }
      }
      return sharpNess;
   }


   /*
    *  calculate the sharpness of a given image (in "impro").
    */
   /**
    *  Description of the Method
    *
    *@param  impro  Description of the Parameter
    *@return        Description of the Return Value
    */
   public double computeScore(final ImageProcessor impro) {

      int width = (int) (CROP_SIZE * core_.getImageWidth());
      int height = (int) (CROP_SIZE * core_.getImageHeight());
      int sx = (int) (core_.getImageWidth() - width) / 2;
      int sy = (int) (core_.getImageHeight() - height) / 2;
      //int ow = (int) (((1 - CROP_SIZE) / 2) * core_.getImageWidth());
      //int oh = (int) (((1 - CROP_SIZE) / 2) * core_.getImageHeight());

      // double[][] medPix = new double[width][height];
      double sharpNess = 0;
      //double[] windo = new double[9];

      /*
       *  Apply 3x3 median filter to reduce noise
       */
      /*
       *  for (int i=0; i<width; i++){
       *  for (int j=0; j<height; j++){
       *  windo[0] = (double)impro.getPixel(ow+i-1,oh+j-1);
       *  windo[1] = (double)impro.getPixel(ow+i,oh+j-1);
       *  windo[2] = (double)impro.getPixel(ow+i+1,oh+j-1);
       *  windo[3] = (double)impro.getPixel(ow+i-1,oh+j);
       *  windo[4] = (double)impro.getPixel(ow+i,oh+j);
       *  windo[5] = (double)impro.getPixel(ow+i+1,oh+j);
       *  windo[6] = (double)impro.getPixel(ow+i-1,oh+j+1);
       *  windo[7] = (double)impro.getPixel(ow+i,oh+j+1);
       *  windo[8] = (double)impro.getPixel(ow+i+1,oh+j+1);
       *  medPix[i][j] = findMed(windo);
       *  }
       *  }
       */
      //tPrev = System.currentTimeMillis();

      impro.setRoi(new Rectangle(sx, sy, width, height));
      impro.crop();
      impro.medianFilter();
      impro.findEdges();

      //int[] ken = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      //impro.convolve3x3(ken);

      for (int i = 0; i < width; i++) {
         for (int j = 0; j < height; j++) {
            sharpNess += impro.getPixelValue(i, j);
         }
      }

      // tcur = System.currentTimeMillis()-tPrev;

      /*
       *  Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp
       */
      /*
       *  for (int k=1; k<width-1; k++){
       *  for (int l=1; l<height-1; l++){
       *  sharpNess = sharpNess + Math.pow((-2*medPix[k-1][l-1]- medPix[k][l-1]-medPix[k-1][l]+medPix[k+1][l]+medPix[k][l+1]+2*medPix[k+1][l+1]),2);
       *  }
       *  }
       */
      return sharpNess;
   }


   //making a new window for a new snapshot.
   /**
    *  Description of the Method
    *
    *@return    Description of the Return Value
    */
   private ImagePlus newWindow() {
      ImagePlus implus;
      ImageProcessor ip;
      long byteDepth = core_.getBytesPerPixel();

      if (byteDepth == 1) {
         ip = new ByteProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
      } else {
         ip = new ShortProcessor((int) core_.getImageWidth(), (int) core_.getImageHeight());
      }
      ip.setColor(Color.black);
      ip.fill();

      implus = new ImagePlus(String.valueOf(curDist), ip);
      if (indx == 1) {
         if (verbose_) {
            // create image window if we are in the verbose mode
            ImageWindow imageWin = new ImageWindow(implus);
         }
      }
      return implus;
   }


   /**
    *  Description of the Method
    *
    *@param  arr  Description of the Parameter
    *@return      Description of the Return Value
    */
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


   /**
    *  Description of the Method
    *
    *@return    Description of the Return Value
    */
   public double fullFocus() {
      run("silent");
      return 0;
   }


   /**
    *  Gets the verboseStatus attribute of the Autofocus_ object
    *
    *@return    The verboseStatus value
    */
   public String getVerboseStatus() {
      return "OK";
   }


   /**
    *  Description of the Method
    *
    *@return    Description of the Return Value
    */
   public double incrementalFocus() {
      run("silent");
      return 0;
   }

   public void focus(double coarseStep, int numCoarse, double fineStep, int numFine) {
      SIZE_FIRST = coarseStep;
      NUM_FIRST = numCoarse;
      SIZE_SECOND = fineStep;
      NUM_SECOND = numFine;

      run("silent");
   }

   /**
    *  Description of the Method
    */
   @Override
   public PropertyItem[] getProperties() {

      channelGroup_ = core_.getChannelGroup();
      StrVector channels = core_.getAvailableConfigs(channelGroup_);
      String allowedChannels[] = new String[(int)channels.size() + 1];
      allowedChannels[0] = NOCHANNEL;

      try {
         PropertyItem p1 = getProperty(KEY_CHANNEL1);
         PropertyItem p2 = getProperty(KEY_CHANNEL2);
         boolean found1 = false;
         boolean found2 = false;
         for (int i=0; i<channels.size(); i++) {
            allowedChannels[i+1] = channels.get(i);
            if (p1.value.equals(channels.get(i)))
               found1 = true;
            if (p2.value.equals(channels.get(i)))
               found2 = true;
         }
         p1.allowed = allowedChannels;                                                                 
         if (!found1)                                                                                  
            p1.value = allowedChannels[0];                                                             
         setProperty(p1);                                                                              
         p2.allowed = allowedChannels;                                                                 
         if (!found2)                                                                                  
            p2.value = allowedChannels[0];                                                             
         setProperty(p2);                                                                              
      } catch (MMException e1) {                                                                      
         // TODO Auto-generated catch block                                                           
         e1.printStackTrace();                                                                        
      }                                                                                               

      return super.getProperties();
   }
                                                                                                      

   /**
    *  Description of the Method
    *
   public void loadSettings() {
      SIZE_FIRST = prefs_.getDouble(KEY_SIZE_FIRST, SIZE_FIRST);
      NUM_FIRST = prefs_.getDouble(KEY_NUM_FIRST, NUM_FIRST);
      SIZE_SECOND = prefs_.getDouble(KEY_SIZE_SECOND, SIZE_SECOND);
      NUM_SECOND = prefs_.getDouble(KEY_NUM_SECOND, NUM_SECOND);
      THRES = prefs_.getDouble(KEY_THRES, THRES);
      CROP_SIZE = prefs_.getDouble(KEY_CROP_SIZE, CROP_SIZE);
      CHANNEL1 = prefs_.get(KEY_CHANNEL1, CHANNEL1);
      CHANNEL2 = prefs_.get(KEY_CHANNEL2, CHANNEL2);
   }
   */


   /**
    *  Description of the Method
    */
   /*
   public void saveSettings() {
      prefs_.putDouble(KEY_SIZE_FIRST, SIZE_FIRST);
      prefs_.putDouble(KEY_NUM_FIRST, NUM_FIRST);
      prefs_.putDouble(KEY_SIZE_SECOND, SIZE_SECOND);
      prefs_.putDouble(KEY_NUM_SECOND, NUM_SECOND);
      prefs_.putDouble(KEY_THRES, THRES);
      prefs_.putDouble(KEY_CROP_SIZE, CROP_SIZE);
      prefs_.put(KEY_CHANNEL1, CHANNEL1);
      prefs_.put(KEY_CHANNEL2, CHANNEL2);
   }
   */

   public double getCurrentFocusScore() {
      return 0;
   }

   public int getNumberOfImages() {
      // TODO Auto-generated method stub
      return 0;
   }

   public String getDeviceName() {
      return AF_DEVICE_NAME;
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      core_ = app.getMMCore();
   }

}
