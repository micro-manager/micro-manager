///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.magellan.internal.acq;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;

/**
 * Information about the acquisition of a single image
 */
public class AcquisitionEvent {


   enum SpecialFlag {
      AcqusitionFinished,
      AcqusitionSequenceEnd
   };

   public Acquisition acquisition_;
   public int timeIndex_, zIndex_, positionIndex_;
   public String channelName_ = "";
   public long miniumumStartTime_; //For pausing between time points
   public double zPosition_;
   public XYStagePosition xyPosition_;
   public List<AcquisitionEvent> sequence_ = null;
   public boolean xySequenced_ = false, zSequenced_ = false, exposureSequenced_ = false, channelSequenced_ = false;
   public AcquisitionRunnable beforeHardwareHook_ = null, afterHardwareHook_ = null, afterImageSavedHook_ = null;
   
   private SpecialFlag specialFlag_;

   public JSONObject toJSON() {
      try {
         JSONObject json = new JSONObject();
//         json.put("acquisition-name", acquisition_.getUUID());
         json.put("time-index", timeIndex_);
         json.put("channel-index", channelName_);
         json.put("z-index", zIndex_);
         json.put("position-index", positionIndex_);
         
         json.put("z-position", zPosition_);
         return json;
      } catch (JSONException ex) {
        throw new RuntimeException(ex);
      }
   }
   
   public static AcquisitionEvent fromJSON(JSONObject json, Acquisition acq) {
      AcquisitionEvent event = new AcquisitionEvent(acq);
      try {
         event.zPosition_ = json.getDouble("z-position");
      } catch (JSONException ex) {
         throw new RuntimeException("Z position undefined");
      }
      try {
         event.zIndex_ = json.getInt("z-index");
      } catch (JSONException ex) {
         event.zIndex_ = 0;
      }
      try {
         event.positionIndex_ = json.getInt("position-index");
      } catch (JSONException ex) {
         event.positionIndex_ = 0;
      }
      try {
         event.timeIndex_ = json.getInt("time-index");
      } catch (JSONException ex) {
         event.timeIndex_ = 0;
      }
      try {
         event.channelName_.equals(json.getString("channel-name"));
      } catch (JSONException ex) {
         event.channelName_ = null;
      }
      return event;
   }
   
   public AcquisitionEvent(List<AcquisitionEvent> sequence) {
      sequence_ = new ArrayList<>();
      sequence_.addAll(sequence);
      //figure out which if any of xy, z, exposure, and channels are sequenced
      double z = sequence_.get(0).zPosition_;
      XYStagePosition xy = sequence_.get(0).xyPosition_;
      double exposure = sequence_.get(0).acquisition_.channels_.getChannelSetting(sequence.get(0).channelName_).exposure_;
      String config = sequence_.get(0).acquisition_.channels_.getChannelSetting(sequence.get(0).channelName_).config_;
      for (int i = 1; i < sequence_.size(); i++) {
         if (sequence_.get(i).zPosition_ != z) {
            zSequenced_ = true;
         }
         if (!sequence_.get(i).xyPosition_.getCenter().equals(xy.getCenter())) {
            xySequenced_ = true;
         }
         if (exposure != sequence_.get(i).acquisition_.channels_.getChannelSetting(sequence.get(0).channelName_).exposure_) {
            exposureSequenced_ = true;
         }
         if (!config.equals(sequence_.get(i).acquisition_.channels_.getChannelSetting(sequence.get(0).channelName_).config_)) {
            channelSequenced_ = true;
         }
      }
   }


   public AcquisitionEvent(Acquisition acq) {
      acquisition_ = acq;
      miniumumStartTime_ = 0;
   }

   public AcquisitionEvent copy() {
      AcquisitionEvent e = new AcquisitionEvent(this.acquisition_);
      e.timeIndex_ = timeIndex_;
      e.channelName_ = channelName_;
      e.zIndex_ = zIndex_;
      e.positionIndex_ = positionIndex_;
      e.zPosition_ = zPosition_;
      e.xyPosition_ = xyPosition_;
      e.miniumumStartTime_ = miniumumStartTime_;
      return e;
   }

   public static AcquisitionEvent createAcquisitionFinishedEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq);
      evt.specialFlag_ = SpecialFlag.AcqusitionFinished;
      return evt;
   }

   public boolean isAcquisitionFinishedEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionFinished;
   }

   public static AcquisitionEvent createAcquisitionSequenceEndEvent(Acquisition acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq);
      evt.specialFlag_ = SpecialFlag.AcqusitionSequenceEnd;
      return evt;
   }

   public boolean isAcquisitionSequenceEndEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionSequenceEnd;
   }

   @Override
   public String toString() {
      if (specialFlag_ == SpecialFlag.AcqusitionFinished) {
         return "Acq finished event";
      }

      return "P: " + positionIndex_ + "\tT: " + timeIndex_ + "\tZ: "
              + zIndex_ + "\tC: " + channelName_ + "\tPos: " + xyPosition_;
   }
}
