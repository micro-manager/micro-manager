/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition.engine;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.micromanager.api.Autofocus;
import org.micromanager.api.EngineTask;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.navigation.StagePosition;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ImageTask implements EngineTask {

   public final ImageRequest imageRequest_;
   private Engine eng_;
   private CMMCore core_;
   private boolean stopRequested_;
   private boolean pauseRequested_;
   boolean setZPosition_ = false;
   private final JSONObject md_;
   private double zPosition_;
   private final SimpleDateFormat iso8601modified;

   ImageTask(ImageRequest imageRequest) {
      imageRequest_ = imageRequest;
      stopRequested_ = false;
      md_ = new JSONObject();
      iso8601modified = new SimpleDateFormat("yyyy-MM-dd E HH:mm:ss Z");
   }

   private void log(String msg) {
      ReportingUtils.logMessage("ImageTask: " + msg);
   }

   public void run(Engine eng) {
      eng_ = eng;
      core_ = eng.core_;

      if (!isStopRequested() && !core_.isSequenceRunning()) {
         updateChannel();
      }
      if (!isStopRequested()) {
         updatePosition();
      }
      if (!isStopRequested()) {
         sleep();
      }
      if (!isStopRequested() && !core_.isSequenceRunning()) {
         autofocus();
      }
      if (!isStopRequested() && !core_.isSequenceRunning()) {
         updateSlice();
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
            core_.waitForConfig(chanGroup,imageRequest_.Channel.config_);
            log("channel set");
         } catch (Exception ex) {
            ReportingUtils.logError(ex, "Channel setting failed.");
         }
      }
   }

   void updateSlice() {
      try {
         if (imageRequest_.UseSlice) {
            setZPosition_ = true;
            if (imageRequest_.relativeZSlices) {
               zPosition_ += imageRequest_.SlicePosition;
               System.out.println(zPosition_);
            } else {
               zPosition_ = imageRequest_.SlicePosition;
            }
         } else {
            zPosition_ = core_.getPosition(core_.getFocusDevice());
         }

         if (imageRequest_.UseChannel && imageRequest_.Channel.zOffset_ != 0) {
            setZPosition_ = true;
            zPosition_ += imageRequest_.Channel.zOffset_;
         }

         if (setZPosition_) {
            imageRequest_.zPosition = zPosition_;
            core_.setPosition(core_.getFocusDevice(), zPosition_);
            core_.waitForDevice(core_.getFocusDevice());
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   void updatePosition() {
      try {
         zPosition_ = imageRequest_.zReference;
         if (imageRequest_.UsePosition) {
            MultiStagePosition msp = imageRequest_.Position;
            for (int i = 0; i < msp.size(); ++i) {

               StagePosition sp = msp.get(i);
               if (sp.numAxes == 1) {
                  if (sp.stageName.equals(core_.getFocusDevice())) {
                     zPosition_ = sp.x; // Surprisingly it should be sp.x!
                     setZPosition_ = true;
                  } else {
                     core_.setPosition(sp.stageName, sp.x);
                     core_.waitForDevice(sp.stageName);
                     md_.put("Acquisition-"+sp.stageName+"RequestedZPosition",
                             sp.x);
                  }

               } else if (sp.numAxes == 2) {
                  core_.setXYPosition(sp.stageName, sp.x, sp.y);
                  core_.waitForDevice(sp.stageName);
                  md_.put("Acquisition-"+sp.stageName+"RequestedXPosition",
                          sp.x);
                  md_.put("Acquisition-"+sp.stageName+"RequestedYPosition",
                          sp.y);
               }
               log("position set\n");
            }
         }
         String focusDevice = core_.getFocusDevice();
         if (!focusDevice.equals(""))
            core_.waitForDevice(focusDevice);
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Set position failed.");
      }
   }

   public synchronized void sleep() {
      if (imageRequest_.UseFrame) {
         while (!stopRequested_ && eng_.lastWakeTime_ > 0) {
            double sleepTime = (eng_.lastWakeTime_ + imageRequest_.WaitTime)
                    - (System.nanoTime() / 1000000);
            if (sleepTime > 0) {
               try {
                  wait((long) sleepTime);
               } catch (InterruptedException ex) {
                  ReportingUtils.logError(ex);
               }
            } else {
               if (imageRequest_.WaitTime > 0) {
                  try {
                     md_.put("Acquisition-TimingState", "Lagging");
                  } catch (Exception ex) {
                     ReportingUtils.logError(ex);
                  }
               }
               break;
            }
         }
         log("wait finished");

         eng_.lastWakeTime_ = (System.nanoTime() / 1000000);
      }
   }

   public void autofocus() {
      String afResult = "AutofocusResult";
      StagePosition sp;
      Autofocus afDevice;
      if (imageRequest_.AutoFocus) {
         try {
            String focusDevice = core_.getFocusDevice();
            core_.setPosition(focusDevice, zPosition_);
            core_.waitForDevice(focusDevice);
            afDevice = eng_.getAutofocusManager().getDevice();
            afDevice.fullFocus();

            md_.put(afResult, "Success");
            if (imageRequest_.UsePosition) {
               sp = imageRequest_.Position.get(core_.getFocusDevice());
               if (sp != null)
                  sp.x = core_.getPosition(core_.getFocusDevice());
            }
            zPosition_ = core_.getPosition(focusDevice);
            core_.waitForDevice(focusDevice);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            try {
               md_.put("AutofocusResult", "Failure");
            } catch (Exception ex1) {
               ReportingUtils.logError(ex1);
            }
         }
      }
   }

   void acquireImage() {
      waitDuringPause();
      try {
      md_.put("Slice", imageRequest_.SliceIndex);
      if (imageRequest_.UseChannel) {
         md_.put("Channel", imageRequest_.Channel.config_);
      }
      md_.put("PositionIndex", imageRequest_.PositionIndex);
      md_.put("ChannelIndex", imageRequest_.ChannelIndex);
      md_.put("Frame", imageRequest_.FrameIndex);

      if (imageRequest_.UsePosition) {
         md_.put("PositionName", imageRequest_.Position.getLabel());
      }
      md_.put("SlicePosition", imageRequest_.SlicePosition);

      long bits = core_.getBytesPerPixel() * 8;
      String lbl = "";
      if (core_.getNumberOfComponents() == 1) {
         lbl = "GRAY";
      } else if (core_.getNumberOfComponents() == 4) {
         lbl = "RGB";
      }
      md_.put("Exposure-ms", imageRequest_.exposure);
      md_.put("PixelSizeUm", core_.getPixelSizeUm());
      try {
         String focusDevice = core_.getFocusDevice();
         if (!focusDevice.equals(""))
            md_.put("ZPositionUm", core_.getPosition(core_.getFocusDevice()));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

      md_.put("PixelType", lbl + bits);
      try {
         MDUtils.setWidth(md_, (int) core_.getImageWidth());
         MDUtils.setHeight(md_, (int) core_.getImageHeight());
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

      long dTime = System.nanoTime() - eng_.getStartTimeNs();
      md_.put("ElapsedTime-ms", ((double) dTime) / 1e9);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      try {
         String shutterDevice = core_.getShutterDevice();
         if (!shutterDevice.equals(""))
            core_.waitForDevice(shutterDevice);
         if (core_.getAutoShutter())
            core_.setAutoShutter(false);
         if (eng_.autoShutterSelected_ && !core_.getShutterOpen()) {
            core_.setShutterOpen(true);
            log("opened shutter");
         }

         Object pixels;
         if (!imageRequest_.collectBurst) {
            core_.snapImage(); //Should be: core_.snapImage(jsonMetadata);
            log("snapped image");
            if (eng_.autoShutterSelected_ && imageRequest_.CloseShutter) {
               if (!shutterDevice.equals(""))
                  core_.waitForDevice(shutterDevice);
               core_.setShutterOpen(false);
               log("closed shutter");
            }
            pixels = core_.getImage();
         } else {
            while (core_.getRemainingImageCount() == 0)
               JavaUtils.sleep(5);
            pixels = core_.popNextImage();
            log("collected burst image");
         }
        

         md_.put("Source",core_.getCameraDevice());
         Configuration config = core_.getSystemStateCache();
         MDUtils.addConfiguration(md_, config);
         if (imageRequest_.NextWaitTime > 0) {
            long nextFrameTimeMs = (long)
                    (imageRequest_.NextWaitTime + eng_.lastWakeTime_);
            md_.put("NextFrameTimeMs", nextFrameTimeMs);
         }
         MDUtils.addRandomUUID(md_);
         md_.put("Time", iso8601modified.format(new Date()));
         md_.put("Binning",
                 core_.getProperty(core_.getCameraDevice(), "Binning"));

         TaggedImage taggedImage = new TaggedImage(pixels, md_);
         eng_.imageReceivingQueue_.put(taggedImage);
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
