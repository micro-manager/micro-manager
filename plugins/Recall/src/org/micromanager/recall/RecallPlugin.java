/**
 * Micro-Manager "Live Replay"
 * 
 * This plugin will copy all images in the Micro-Manager circular buffer
 * into a viewer window.  The counter of the circular buffer is set to 0 (i.e.
 * images in the circular buffer will be destroyed.
 * 
 * This is useful when you see something interesting in live mode and want to 
 * save this data.
 * 
 * 
 * Nico Stuurman, 2009(?)
 * 
 * Updated June 2013 to use ImageProcssor Queue, so that images from multiple 
 * cameras are displayed correctly and so that images are all processed by
 * the default image processing queue.
 * 
 * 
 * Copyright University of California
 * 
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */

package org.micromanager.recall;

import java.util.concurrent.LinkedBlockingQueue;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;




public class RecallPlugin implements MMPlugin {
   public static String menuName = "Live Replay";
   public static String tooltipDescription = "Recalls live images remaining in internal"
		   +" buffer.  The size of Micromanager's internal buffer can be changed" +
		   "in options (under Tools menu)";
   private CMMCore core_;
   private MMStudioMainFrame gui_;
   private MMStudioMainFrame.DisplayImageRoutine displayImageRoutine_;
   private final String ACQ_NAME = "Live Replay";
   private int multiChannelCameraNrCh_;
   
  

   public void setApp(ScriptInterface app) {
      gui_ = (MMStudioMainFrame) app;                                        
      core_ = app.getMMCore(); 
      
      displayImageRoutine_ = new MMStudioMainFrame.DisplayImageRoutine() {
         public void show(final TaggedImage ti) {
            try {
               gui_.addImage(ACQ_NAME, ti, true, true);
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
      };
   }

   public void dispose() {
      // nothing todo:
   }

   public void show() {
      try {
         if (gui_.acquisitionExists(ACQ_NAME))
            gui_.closeAcquisition(ACQ_NAME);

         int remaining = core_.getRemainingImageCount();

         if (remaining < 1) {
            ReportingUtils.showMessage("There are no Images in the Micro-Manage buffer");
            return;
         }       
         
         LinkedBlockingQueue imageQueue_ = new LinkedBlockingQueue();
         
         multiChannelCameraNrCh_ = (int) core_.getNumberOfCameraChannels();
         
         gui_.openAcquisition(ACQ_NAME, "tmp", core_.getRemainingImageCount(), 
                 multiChannelCameraNrCh_, 1, true);

         String camera = core_.getCameraDevice();
         long width = core_.getImageWidth();
         long height = core_.getImageHeight();
         long depth = core_.getBytesPerPixel();

         gui_.initializeAcquisition(ACQ_NAME, (int) width,(int) height, (int) depth);

         gui_.runDisplayThread(imageQueue_, displayImageRoutine_);
         
         if (multiChannelCameraNrCh_ == 0) {                    
            int frameCounter = 0;
            try {
               for (int i = 0; i < remaining; i++) {
                  TaggedImage tImg = core_.popNextTaggedImage();
                  normalizeTags(tImg, frameCounter);
                  frameCounter++;
                  imageQueue_.put(tImg);
               }
               imageQueue_.put(TaggedImageQueue.POISON);
            } catch (Exception ex) {
               ReportingUtils.logError(ex, "Error in Live Replay");
            }
         } else if (multiChannelCameraNrCh_ > 0) {
            int[] frameCounters = new int[multiChannelCameraNrCh_];
            try {
               for (int i = 0; i < remaining; i++) {
                  TaggedImage tImg = core_.popNextTaggedImage();
                  
                  if (tImg.tags.has(camera + "-CameraChannelName")) {
                     String channelName = tImg.tags.getString(camera + "-CameraChannelName");
                     tImg.tags.put("Channel", channelName);
                     int channelIndex = tImg.tags.getInt(camera + "-CameraChannelIndex");
                     tImg.tags.put("ChannelIndex", channelIndex);
                     normalizeTags(tImg, frameCounters[channelIndex]);
                     frameCounters[channelIndex]++;
                     imageQueue_.put(tImg);
                  }
               }  
               imageQueue_.put(TaggedImageQueue.POISON);
            } catch (Exception ex) {
               ReportingUtils.logError(ex, "Error in Live Replay Plugin");
            }
         }
        
      } catch (MMScriptException e) {
         ReportingUtils.showError(e);
      }        
   }

   
   private void normalizeTags(TaggedImage ti, int frameIndex) {
      if (ti != TaggedImageQueue.POISON) {
         int channel = 0;
         try {
            if (ti.tags.has("Multi Camera-CameraChannelIndex")) {
               channel = ti.tags.getInt("Multi Camera-CameraChannelIndex");
            } else if (ti.tags.has("CameraChannelIndex")) {
               channel = ti.tags.getInt("CameraChannelIndex");
            } else if (ti.tags.has("ChannelIndex")) {
               channel = MDUtils.getChannelIndex(ti.tags);
            }
            ti.tags.put("ChannelIndex", channel);
            ti.tags.put("PositionIndex", 0);
            ti.tags.put("SliceIndex", 0);
            ti.tags.put("FrameIndex", frameIndex);

         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   public void configurationChanged() {
   }

   public String getInfo () {
      return "Recalls live images remaining in internal buffer.  Set size of the buffer in options (under Tools menu)";
   }

   public String getDescription() {
      return tooltipDescription;
   }
   
   public String getVersion() {
      return "First version";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
