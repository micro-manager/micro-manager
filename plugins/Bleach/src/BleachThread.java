import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.GregorianCalendar;

import mmcorej.CMMCore;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.metadata.ImageKey;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.SummaryKeys;


public class BleachThread extends Thread {

   CMMCore core_;
   String outDirName_;
   BleachControlDlg gui_;
   boolean stop_ = false;

   // thread data
   public double intervalMs_ = 5000.0;
   public double intervalPreMs_ = 5000.0;
   public double exposureMs_ = 100.0;
   public int numFrames_ = 10;
   public int numPreFrames_ = 1;
   public double bleachDurationMs_ = 100.0;
   
   // TTL control hard-coded presets
   public static final String ttlGroup_ = "TTL";
   public static final String signalON = "ON";
   public static final String signalOFF = "OFF";


   public BleachThread(CMMCore core, BleachControlDlg gui, String rootName, String dirName) {
      core_ = core;
      gui_ = gui;

      // create unique directory name for storing acquistion images
      int suffixCounter = 0;
      String testName;
      File testDir;
      do {
         testName = new String(rootName + "/" + dirName + "_" + suffixCounter);
         suffixCounter++;
         testDir = new File(testName);

      } while (testDir.exists());

      outDirName_ = testName;
      File newDir = new File(outDirName_);
      newDir.mkdirs();
   }

   /**
    * Run the entire blach-image protocol.
    */
   public void run() {
      stop_ = false;
      int imageCount = 0;

      int width = (int)core_.getImageWidth();
      int height = (int)core_.getImageHeight();

      ImageProcessor ip;
      long byteDepth = core_.getBytesPerPixel();
      if (byteDepth == 1){
         ip = new ByteProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
      } else if (byteDepth == 2) {
         ip = new ShortProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
      }
      else if (byteDepth == 0) {
         return;
      }
      else {
         return;
      }

      // set-up metadata
      JSONObject metadata = new JSONObject();
      JSONObject i5dData = new JSONObject();

      // create ImagePlus
      ip.setColor(Color.black);
      ip.fill();
      ImagePlus imp = new ImagePlus("bleach", ip);

      // Start streaming to disk
      GregorianCalendar cldBegin = new GregorianCalendar(); // begin time
      boolean error = false;
      boolean bleachSection = false;
      
      try {
         core_.setExposure(exposureMs_);

         while (imageCount < numFrames_ + numPreFrames_ && !stop_)
         {
            if (imageCount == numPreFrames_ && bleachSection == false) {
               bleachSection = true;               
            } else {
               bleachSection = false;
            }

            if (bleachSection) {
               gui_.displayMessage("Bleaching for " + (long)bleachDurationMs_ + " ms");
               core_.setConfig(ttlGroup_, signalON); // set the TTL to ON
               // bleaching starts
               Thread.sleep((long)bleachDurationMs_);
               // bleaching ends
               core_.setConfig(ttlGroup_, signalOFF); // set the TTL to OFF
               continue;
            }
            
            gui_.displayMessage("Acquiring image: " + (imageCount+1));

            // start frame
            long start = GregorianCalendar.getInstance().getTimeInMillis();

            core_.snapImage();
            Object img = core_.getImage();
            gui_.displayImage(img);

            String fileName = "img_" + imageCount + ".tif";
            error = !saveImageFile(outDirName_ + "/" + fileName, img, width, height);                  
            imageCount++;

            JSONObject jsonData = new JSONObject();
            jsonData.put(ImagePropertyKeys.FILE, fileName);
            jsonData.put(ImagePropertyKeys.FRAME, imageCount-1);
            jsonData.put(ImagePropertyKeys.CHANNEL, "FRAP");
            jsonData.put(ImagePropertyKeys.SLICE, 0);
            jsonData.put(ImagePropertyKeys.ELAPSED_TIME_MS, start - cldBegin.getTimeInMillis());
            jsonData.put(ImagePropertyKeys.EXPOSURE_MS, bleachSection ? bleachDurationMs_ : exposureMs_);
            // insert the metadata for the current image
            metadata.put(ImageKey.generateFrameKey(imageCount-1, 0, 0), jsonData);

            // end frame

            // wait until the frame interval expires
            long deltaT = (long)(intervalMs_ + 0.5) - (GregorianCalendar.getInstance().getTimeInMillis() - start);
            if (deltaT > 0 && !bleachSection)
               Thread.sleep(deltaT);
         }

         // done
         if (error)
            gui_.displayMessage("Error acquiring images. " + imageCount + " images saved.");
         else
            gui_.displayMessage("Finished. " + imageCount + " images saved.");

      } catch (InterruptedException e) {
         // thread was terminated by user action
         gui_.displayMessage("Acquisition interrupted. " + imageCount + " images saved.");
      } catch (Exception e) {
         gui_.displayMessage(e.getMessage());
      }

      try {
         // insert summary data
         // i5dData.put(SummaryKeys.GUID, guidgen_.genNewGuid());
         i5dData.put(SummaryKeys.METADATA_VERSION, SummaryKeys.VERSION);
         i5dData.put(SummaryKeys.METADATA_SOURCE, SummaryKeys.SOURCE);
         i5dData.put(SummaryKeys.NUM_FRAMES, imageCount);
         i5dData.put(SummaryKeys.NUM_CHANNELS, 1);
         i5dData.put(SummaryKeys.NUM_SLICES, 1);
         i5dData.put(SummaryKeys.TIME, cldBegin.getTime());
         i5dData.put(SummaryKeys.COMMENT, "FRAP plugin");
         i5dData.put(SummaryKeys.IMAGE_WIDTH, ip.getWidth());
         i5dData.put(SummaryKeys.IMAGE_HEIGHT, ip.getHeight());
         if (ip instanceof ByteProcessor)
            i5dData.put(SummaryKeys.IMAGE_DEPTH, 1);
         else if (ip instanceof ShortProcessor)
            i5dData.put(SummaryKeys.IMAGE_DEPTH, 2);
         i5dData.put(SummaryKeys.IJ_IMAGE_TYPE, imp.getType());
         metadata.put(SummaryKeys.SUMMARY_OBJ, i5dData);

      } catch (JSONException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      // write metadata
      try {
         FileWriter metaWriter = new FileWriter(new File(outDirName_ + "/" + ImageKey.METADATA_FILE_NAME));
         metaWriter.write(metadata.toString());
         metaWriter.close();
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      };
   }

   private boolean saveImageFile(String fname, Object img, int width, int height) {
      ImageProcessor ip;
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[])img);
      }
      else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[])img);
      }
      else
         return false;

      ImagePlus imp = new ImagePlus(fname, ip);
      FileSaver fs = new FileSaver(imp);
      return fs.saveAsTiff(fname);
   }

   public void stopAcquisition() {
      stop_ = true;
   }
}

