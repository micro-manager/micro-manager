/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import org.micromanager.api.EngineTask;
import java.util.HashMap;
import java.util.Map;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ImageTask implements EngineTask {

   private final ImageRequest imageRequest_;
   private final Engine eng_;
   private final CMMCore core_;
   private boolean stopRequested_;
   private boolean pauseRequested_;

   ImageTask(Engine eng, ImageRequest imageRequest) {
      eng_ = eng;
      core_ = eng.core_;
      imageRequest_ = imageRequest;
      stopRequested_ = false;
   }

   private void log(String msg) {
      ReportingUtils.logMessage("ImageTask: " + msg);
   }

   public void run() {
      if (!isStopRequested()) {
         updatePositionAndSlice();
      }
      if (!isStopRequested()) {
         updateChannel();
      }
      if (!isStopRequested()) {
         sleep();
      }
      if (!isStopRequested()) {
         autofocus();
      }
      if (!isStopRequested()) {
         acquireImage();
      }
   }

   void updateChannel() {
      if (imageRequest_.UseChannel) {
         try {
            core_.setExposure(imageRequest_.Channel.exposure_);
            imageRequest_.exposure = imageRequest_.Channel.exposure_;
            String chanGroup = imageRequest_.Channel.name_;
            if (chanGroup.length() == 0) {
               chanGroup = core_.getChannelGroup();
            }
            core_.setConfig(chanGroup, imageRequest_.Channel.config_);
            log("channel set");
         } catch (Exception ex) {
            ReportingUtils.logError(ex, "Channel setting failed.");
         }
      }
   }

   void updateSlice(double zPosition) throws Exception {
      if (imageRequest_.UseSlice) {
         if (imageRequest_.relativeZSlices) {
            zPosition += imageRequest_.SlicePosition;
            System.out.println(zPosition);
         } else {
            zPosition = imageRequest_.SlicePosition;
         }
      }

      if (imageRequest_.UseChannel) {
         zPosition += imageRequest_.Channel.zOffset_;
      }

      imageRequest_.zPosition = zPosition;
      core_.setPosition(core_.getFocusDevice(), zPosition);
   }

   void updatePositionAndSlice() {
      try {
         double zPosition = imageRequest_.zReference;
         if (imageRequest_.UsePosition) {
            MultiStagePosition msp = imageRequest_.Position;
            for (int i = 0; i < msp.size(); ++i) {

               StagePosition sp = msp.get(i);
               if (sp.numAxes == 1) {
                  if (sp.stageName.equals(core_.getFocusDevice())) {
                     zPosition = sp.z;
                  } else {
                     core_.setPosition(sp.stageName, sp.z);
                  }

               } else if (sp.numAxes == 2) {
                  core_.setXYPosition(sp.stageName, sp.x, sp.y);
               }
               log("position set\n");
            }
         }
         updateSlice(zPosition);
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Set position failed.");
      }
   }

   public synchronized void sleep() {
      if (imageRequest_.UseFrame) {
         while (!stopRequested_ && eng_.lastWakeTime_ > 0) {
            double sleepTime = (eng_.lastWakeTime_ + imageRequest_.WaitTime) - (System.nanoTime() / 1000000);
            if (sleepTime > 0) {
               try {
                  wait((long) sleepTime);
               } catch (InterruptedException ex) {
                  ReportingUtils.logError(ex);
               }
            } else {
               break;
            }
         }
         log("wait finished");

         eng_.lastWakeTime_ = (System.nanoTime() / 1000000);
      }
   }

   public void autofocus() {
      if (imageRequest_.AutoFocus && imageRequest_.ChannelIndex == 0 && imageRequest_.PositionIndex == 0) {
         try {
            core_.fullFocus();
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   void acquireImage() {
      //Gson gson = new Gson();
      //String jsonMetadata = gson.toJson(imageRequest_);
      waitDuringPause();
      Map<String, String> md = new HashMap<String, String>();
      MDUtils.put(md, "Acquisition-SliceIndex", imageRequest_.SliceIndex);
      if (imageRequest_.UseChannel) {
         MDUtils.put(md, "Acquisition-ChannelName", imageRequest_.Channel.config_);
      }
      MDUtils.put(md, "Acquisition-PositionIndex", imageRequest_.PositionIndex);
      MDUtils.put(md, "Acquisition-ChannelIndex", imageRequest_.ChannelIndex);
      MDUtils.put(md, "Acquisition-FrameIndex", imageRequest_.FrameIndex);

      if (imageRequest_.UsePosition) {
         MDUtils.put(md, "Acquisition-PositionName", imageRequest_.Position.getLabel());
      }
      MDUtils.put(md, "Acquisition-SlicePosition", imageRequest_.SlicePosition);

      long bits = core_.getBytesPerPixel() * 8;
      String lbl = "";
      if (core_.getNumberOfComponents() == 1) {
         lbl = "GRAY";
      } else if (core_.getNumberOfComponents() == 4) {
         lbl = "RGB";
      }
      MDUtils.put(md, "Acquisition-ExposureMs", imageRequest_.exposure);
      MDUtils.put(md, "Acquisition-PixelSizeUm", core_.getPixelSizeUm());
      try {
         MDUtils.put(md, "Acquisition-ZPositionUm", core_.getPosition(core_.getFocusDevice()));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         MDUtils.put(md, "Acquisition-ZPositionUm", "");
      }

      MDUtils.put(md, "Image-PixelType", lbl + bits);
      try {
         MDUtils.setWidth(md, (int) core_.getImageWidth());
         MDUtils.setHeight(md, (int) core_.getImageHeight());
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      long dTime = System.nanoTime() - eng_.getStartTimeNs();
      MDUtils.put(md, "Acquisition-TimeMs", ((double) dTime) / 1e9);

      try {
         if (eng_.autoShutterSelected_ && !core_.getShutterOpen()) {
            core_.setShutterOpen(true);
            log("opened shutter");
         }
         core_.snapImage(); //Should be: core_.snapImage(jsonMetadata);
         log("snapped image");

         if (eng_.autoShutterSelected_ && imageRequest_.CloseShutter) {
            core_.setShutterOpen(false);
            log("closed shutter");
         }

         Object pixels = core_.getImage();
         Configuration config = core_.getSystemStateCache();
         MDUtils.addConfiguration(md, config);
         if (imageRequest_.NextWaitTime > 0) {
            long nextImageTimeMs = (long) (imageRequest_.NextWaitTime + eng_.lastWakeTime_);
            MDUtils.put(md, "Acquisition-NextImageTimeMs", nextImageTimeMs);
         }
         MDUtils.addRandomUUID(md);
         MDUtils.generateLabel(md);
         TaggedImage taggedImage = new TaggedImage(pixels, md);

         eng_.imageReceivingQueue_.add(taggedImage);

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public synchronized void requestStop() {
      stopRequested_ = true;
      notify();
   }

   public synchronized void requestPause() {
      pauseRequested_ = true;
   }

   public synchronized void requestResume() {
      pauseRequested_ = false;
      this.notify();
   }

   private synchronized boolean isPauseRequested() {
      return pauseRequested_;
   }

   private synchronized void waitDuringPause() {
      try {
         if (isPauseRequested()) {
            wait();
         }
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      }

   }

   private synchronized boolean isStopRequested() {
      return stopRequested_;
   }
}
