package org.micromanager.fastacq;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.util.GregorianCalendar;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.MMCoreJ;

import org.micromanager.image5d.ChannelControl;
import org.micromanager.image5d.Image5D;
import org.micromanager.image5d.Image5DWindow;
import org.micromanager.metadata.AcquisitionData;
import org.micromanager.metadata.ImagePropertyKeys;
import org.micromanager.metadata.MMAcqDataException;
import org.micromanager.utils.Annotator;
import org.micromanager.utils.MMLogger;

public class DiskStreamingThread extends Thread {

   CMMCore core_;
   String outDirName_;
   GUIStatus gui_;
   boolean stop_ = false;
   boolean stack_;
   double intervalMs_;
   private double pixelSize_um_;
   private double pixelAspect_;
   private double elapsedTimeMs_;
   private AcquisitionData acqData_;
   private String acqName_;
   private String acqRoot_;
   
   public DiskStreamingThread(CMMCore core, GUIStatus gui, String rootName, String dirName, boolean stack, double intervalMs) {
      core_ = core;
      gui_ = gui;
      stack_ = stack;
      elapsedTimeMs_ = 0;
      acqData_ = new AcquisitionData();
      
      String cameraName_ = core_.getCameraDevice();
      try {
         if (core_.hasProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms()))
            intervalMs_ = Double.parseDouble(core_.getProperty(cameraName_, MMCoreJ.getG_Keyword_ActualInterval_ms()));
         else
            intervalMs_ = intervalMs;
      } catch (NumberFormatException nfe) {
         intervalMs_ = 0;
      } catch (Exception e) {
         intervalMs_ = 0;
         MMLogger.getLogger().severe(e.getMessage());
      } 
      
      // create unique directory name for storing acquisition images
      acqName_ = dirName;
      acqRoot_ = rootName;
      if (!stack_)
      {
         try {
            // create new acquisition data file
            acqData_.createNew(acqName_, acqRoot_, true);
            acqData_.setDimensions(0, 1, 1);
            acqData_.setChannelName(0, "Sequence");
         } catch (MMAcqDataException e) {
            gui_.displayStreamingMessage("Error creating acquisition data file.");
         }
      } else {
         // create new in-memory data acquisition
         try {
            acqData_.createNew();
            acqData_.setDimensions(0, 1, 1);
            acqData_.setChannelName(0, "Sequence");
         } catch (MMAcqDataException e) {
            gui_.displayStreamingMessage("Error creating in-memory acquisition data.");
         }
      }
   }

   public void run() {
      stop_ = false;
      elapsedTimeMs_ = 0;
      
      // obtain camera name
      String camera = core_.getCameraDevice();
      int width = (int)core_.getImageWidth();
      int height = (int)core_.getImageHeight();
      long byteDepth = core_.getBytesPerPixel();
      try {
         acqData_.setImagePhysicalDimensions(width, height, (int)byteDepth);
      } catch (MMAcqDataException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }
      double expMs = 0.0;
      
      ImageProcessor ip;
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
            
      // create stack
      ip.setColor(Color.black);
      ip.fill();
      ImageStack stack = new ImageStack(width, height);
      ImagePlus imp = new ImagePlus("sequence", ip);
            
      // Start streaming to disk
      GregorianCalendar cld = new GregorianCalendar(); // begin time
      int count = 0;
      boolean error = false;
      try {
         expMs = core_.getExposure();
         
         // the speed is important here so we will not save the entire state
         // TODO: resolve
         Configuration state = core_.getSystemStateCache();
         while ((core_.getRemainingImageCount() > 0 || core_.deviceBusy(camera)) && !error && !stop_)
         {
            if (core_.getRemainingImageCount() > 0)
            {
               long start = GregorianCalendar.getInstance().getTimeInMillis();
               Object img = core_.popNextImage();
               double interval = core_.getBufferIntervalMs();
               if (stack_) {
                  ip.setPixels(img);
                  stack.addSlice(Integer.toString(stack.getSize()+1), ip);
                  acqData_.insertImageMetadata(count, 0, 0);                  
               } else {
                  acqData_.insertImage(img, count, 0, 0);                  
               }
               acqData_.setImageValue(count, 0, 0, ImagePropertyKeys.EXPOSURE_MS, expMs);
               
               // insert state only for the first frame
               // (performance reasons)
               if (count == 0)
                  Annotator.setStateMetadata(acqData_, count, 0, 0, state);
               count++;
               String msg = "Saved image: " + count + ", in " + (GregorianCalendar.getInstance().getTimeInMillis() - start) + " ms";
               gui_.displayStreamingMessage(msg);                                             
               elapsedTimeMs_ += interval;
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
      } catch (OutOfMemoryError e) {
         // TODO Auto-generated catch block
         e.printStackTrace();         
      }
      
      try {
         // insert summary data
         acqData_.setDimensions(count, 1, 1);
         acqData_.setComment("Burst acquistion");
         pixelSize_um_ = core_.getPixelSizeUm();
         acqData_.setPixelSizeUm(pixelSize_um_);      
         acqData_.setImageIntervalMs(intervalMs_);
         acqData_.saveMetadata();
         
      } catch (MMAcqDataException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
         
      if (stack_ && stack.getSize() > 0) {
         Image5D i5D = new Image5D("MM sequence stack", stack, 1, 1, stack.getSize());
         i5D.setCalibration(acqData_.ijCal());      
         i5D.show();
         i5D.setDisplayMode(ChannelControl.ONE_CHANNEL_GRAY);
         i5D.draw();
         Image5DWindow win = (Image5DWindow) i5D.getWindow();
         win.setAcquisitionData(acqData_);
      }
   }   
   public void stopSaving() {
      stop_ = true;
   }
}
