package org.micromanager.fastacq;


import java.io.File;
import java.io.IOException;
import java.util.GregorianCalendar;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import mmcorej.CMMCore;

public class DiskStreamingThread extends Thread {

   CMMCore core_;
   String outDirName_;
   GUIStatus gui_;
   
   public DiskStreamingThread(CMMCore core, GUIStatus gui, String rootName, String dirName) {
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

   public void run() {
      // obtain camera name
      String camera = core_.getCameraDevice();
      int width = (int)core_.getImageWidth();
      int height = (int)core_.getImageHeight();
      
      int count = 0;
      boolean error = false;
      try {         
         while ((core_.getRemainingImageCount() > 0 || core_.deviceBusy(camera)) && !error)
         {
            if (core_.getRemainingImageCount() > 0)
            {
               long start = GregorianCalendar.getInstance().getTimeInMillis();
               Object img = core_.popNextImage();
               error = !saveImageFile(outDirName_ + "/img_" + count + ".tif", img, width, height);
               Thread.sleep(150);
               count++;
               String msg = "Saved image: " + count + ", in " + (GregorianCalendar.getInstance().getTimeInMillis() - start) + " ms";
               gui_.displayStreamingMessage(msg);
            } else {
               // wait for next image
               //gui_.displayStreamingMessage("Waiting...");
               Thread.sleep(100);
            }
         }
         // done
         if (error)
            gui_.displayStreamingMessage("Error saving files.");
         else
            gui_.displayStreamingMessage("Finished. " + count + " images saved.");
         
      } catch (InterruptedException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
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
}


