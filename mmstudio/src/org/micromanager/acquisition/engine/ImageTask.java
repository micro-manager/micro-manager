/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import com.google.gson.Gson;
import mmcorej.CMMCore;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ImageTask implements Runnable {

   private final ImageRequest imageRequest_;
   private final Engine eng_;
   private final CMMCore core_;

   ImageTask(Engine eng, ImageRequest imageRequest) {
      eng_ = eng;
      core_ = eng.core_;
      imageRequest_ = imageRequest;
   }

   public void run() {
      if (!eng_.stopHasBeenRequested()) {
         updatePositionAndSlice();
      }
      if (!eng_.stopHasBeenRequested()) {
         updateChannel();
      }
      if (!eng_.stopHasBeenRequested()) {
         sleep();
      }
      if (!eng_.stopHasBeenRequested()) {
         autofocus();
      }
      if (!eng_.stopHasBeenRequested()) {
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
            core_.logMessage("channel set");
         } catch (Exception ex) {
            ReportingUtils.logError(ex, "Channel setting failed.");
         }
      }
   }

   void updateSlice(double zPosition) throws Exception {
      if (imageRequest_.UseSlice) {
         if (imageRequest_.relativeZSlices) {
            zPosition += imageRequest_.SlicePosition;
         } else {
            zPosition = imageRequest_.SlicePosition;
         }
      }

      if (imageRequest_.UseChannel) {
         zPosition += imageRequest_.Channel.zOffset_;
      }

      core_.setPosition(core_.getFocusDevice(), zPosition);
   }

   void updatePositionAndSlice() {
      try {
         double zPosition = core_.getPosition(core_.getFocusDevice());
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
               core_.logMessage("position set\n");
            }
         }
         updateSlice(zPosition);
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Set position failed.");
      }
   }

   public void sleep() {
      if (imageRequest_.UseFrame) {
         while (!eng_.stopHasBeenRequested() && eng_.lastWakeTime_ > 0) {
            double sleepTime = (eng_.lastWakeTime_ + imageRequest_.WaitTime) - (System.nanoTime() / 1000000);
            if (sleepTime > 0) {
               JavaUtils.sleep((int) sleepTime);
            } else {
               break;
            }
         }
         core_.logMessage("wait finished");

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
      Gson gson = new Gson();
      String jsonMetadata = gson.toJson(imageRequest_);
    /*  Map<String, String> md = new HashMap<String, String>();
      MDUtils.put(md, "SliceIndex", imageRequest_.SliceIndex);
      MDUtils.put(md, "ChannelName", imageRequest_.Channel.name_);
      MDUtils.put(md, "PositionIndex", imageRequest_.PositionIndex);
      MDUtils.put(md, "ChannelIndex", imageRequest_.ChannelIndex);
      MDUtils.put(md, "Frame", imageRequest_.FrameIndex);
      MDUtils.put(md, "ExposureMs", imageRequest_.exposure);
      if (imageRequest_.UsePosition) {
         MDUtils.put(md, "PositionName", imageRequest_.Position.getLabel());
      }*/

      try {
         if (core_.getAutoShutter() && !core_.getShutterOpen()) {
            core_.setShutterOpen(true);
            core_.logMessage("opened shutter");
         }
         core_.snapImage(); //Should be: core_.snapImage(jsonMetadata);
         core_.logMessage("snapped image");

         if (core_.getAutoShutter() && imageRequest_.CloseShutter) {
            core_.setShutterOpen(false);
            core_.logMessage("closed shutter");
         }

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
}
