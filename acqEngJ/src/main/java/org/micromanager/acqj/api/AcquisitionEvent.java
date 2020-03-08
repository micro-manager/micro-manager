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
package org.micromanager.acqj.api;

import java.awt.geom.Point2D;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Information about the acquisition of a single image or a sequence of image
 *
 */
public class AcquisitionEvent {

   enum SpecialFlag {
      AcqusitionFinished,
      AcqusitionSequenceEnd
   };

   public AcquisitionBase acquisition_;

   //For encoded time, z indices (or other generic axes)
   //XY position and channel indices should not be encoded because acq engine
   //will dynamically infer them at runtime
   private HashMap<String, Integer> axisPositions_ = new HashMap<String, Integer>();

   private String channelName_ = null;
   private long miniumumStartTime_; //For pausing between time points

   //positions for devices that are generically hardcoded into MMCore
   private Double zPosition_ = null;
   private XYStagePosition xyPosition_ = null;
   //TODO: SLM, Galvo, etc

   //Arbitary additional properties
   private TreeSet<Triplet> properties_ = new TreeSet<Triplet>();

   //for hardware sequencing
   private List<AcquisitionEvent> sequence_ = null;
   private boolean xySequenced_ = false, zSequenced_ = false, exposureSequenced_ = false, channelSequenced_ = false;

   //To specify end of acquisition or end of sequence
   private SpecialFlag specialFlag_;

   public AcquisitionEvent(AcquisitionBase acq) {
      acquisition_ = acq;
      miniumumStartTime_ = 0;
   }

   /**
    * Constructor used for running a list of events in a sequence It should have
    * already been verified that these events are sequencable. This constructor
    * figures out which device types need a sequence and which ones can be left
    * with a single value
    *
    * @param sequence
    */
   public AcquisitionEvent(List<AcquisitionEvent> sequence) {
      sequence_ = new ArrayList<>();
      sequence_.addAll(sequence);
      TreeSet<Double> zPosSet = new TreeSet<Double>();
      HashSet<XYStagePosition> xyPosSet = new HashSet<XYStagePosition>();
      TreeSet<Double> exposureSet = new TreeSet<Double>();
      TreeSet<String> configSet = new TreeSet<String>();
      for (int i = 0; i < sequence_.size(); i++) {
         zPosSet.add(sequence_.get(i).zPosition_);
         xyPosSet.add(sequence_.get(i).xyPosition_);
         exposureSet.add(sequence_.get(i).acquisition_.getChannels().getChannelSetting(sequence.get(0).channelName_).exposure_);
         configSet.add(sequence_.get(i).acquisition_.getChannels().getChannelSetting(sequence.get(0).channelName_).config_);
      }
      exposureSequenced_ = exposureSet.size() > 1;
      channelSequenced_ = configSet.size() > 1;
      xySequenced_ = xyPosSet.size() > 1;
      zSequenced_ = zPosSet.size() > 1;
   }

   public AcquisitionEvent copy() {
      AcquisitionEvent e = new AcquisitionEvent(this.acquisition_);
      e.axisPositions_ = (HashMap<String, Integer>) axisPositions_.clone();
      e.channelName_ = channelName_;
      e.zPosition_ = zPosition_;
      e.xyPosition_ = xyPosition_;
      e.miniumumStartTime_ = miniumumStartTime_;
      return e;
   }

   public JSONObject toJSON() {
      try {
         JSONObject json = new JSONObject();
         if (channelName_ != null) {
            json.put("channel", channelName_);
         }

         //Coordinate indices
         JSONObject axes = new JSONObject();
         for (String axis : axisPositions_.keySet()) {
            axes.put(axis, axisPositions_.get(axis));
         }
         json.put("axes", axes);

         //Things for which a generic device tyoe and functions to operate on
         //it exists in MMCore
         if (zPosition_ != null) {
            json.put("z", zPosition_);
         }
         if (xyPosition_ != null) {
            json.put("x", xyPosition_.getCenter().x);
            json.put("y", xyPosition_.getCenter().y);
         }
         //TODO: SLM, galvo, etc

         //Arbitrary extra properties
         JSONArray props = new JSONArray();
         for (Triplet t : properties_) {
            props.put(t.dev + "-" + t.prop + "-" + t.val);
         }
         json.put("properties", props);

         return json;
      } catch (JSONException ex) {
         throw new RuntimeException(ex);
      }
   }

   public static AcquisitionEvent fromJSON(JSONObject json, AcquisitionBase acq) throws JSONException {
      AcquisitionEvent event = new AcquisitionEvent(acq);
      try {
         event.zPosition_ = json.getDouble("z_position");
      } catch (JSONException ex) {
         throw new RuntimeException("Z position undefined");
      }

      //convert JSON axes to internal hashmap
      if (json.has("axes")) {
         JSONObject axes = json.getJSONObject("axes");
         axes.keys().forEachRemaining((String axisLabel) -> {
            try {
               event.axisPositions_.put(axisLabel, axes.getInt(axisLabel));
            } catch (JSONException ex) {
               throw new RuntimeException(ex);
            }
         });
      }

      //channel name
      if (json.has("channel")) {
         event.channelName_ = json.getString("channel");
      }

      //Things for which a generic device type exists in MMCore
      if (json.has("z")) {
         event.zPosition_ = json.getDouble("z");
      }
      if (json.has("x") && json.has("y")) {
         double x = json.getDouble("x");
         double y = json.getDouble("y");
         event.xyPosition_ = new XYStagePosition(new Point2D.Double(x, y));
      }
      //TODO: SLM, galvo, etc

      //Arbitrary additional properties
      if (json.has("properties")) {
         JSONArray props = json.getJSONArray("properties");
         for (int i = 0; i < props.length(); i++) {
            Triplet t = new Triplet(props.getString(i).split("-"));
            event.properties_.add(t);
         }
      }

      return event;
   }

   public void setChannelName(String c) {
      channelName_ = c;
   }

   public void setXY(XYStagePosition xy) {
      xyPosition_ = xy;
   }

   public void setMinimumStartTime(long l) {
      miniumumStartTime_ = l;
   }
   
   public Set<String> getDefinedAxes() {
      return axisPositions_.keySet();
   }

   public void setAxisPosition(String label, int index) {
      axisPositions_.put(label, index);
   }

   public int getAxisPosition(String label) {
      return axisPositions_.get(label);
   }

   public void setTimeIndex(int index) {
      setAxisPosition(AcqEngMetadata.TIME_AXIS, index);
   }

   public void setZ(int index, double position) {
      setAxisPosition(AcqEngMetadata.Z_AXIS, index);
      zPosition_ = position;
   }

   public int getTIndex() {
      return getAxisPosition(AcqEngMetadata.TIME_AXIS);
   }

   public int getZIndex() {
      return getAxisPosition(AcqEngMetadata.Z_AXIS);
   }

   public static AcquisitionEvent createAcquisitionFinishedEvent(AcquisitionBase acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq);
      evt.specialFlag_ = SpecialFlag.AcqusitionFinished;
      return evt;
   }

   public boolean isAcquisitionFinishedEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionFinished;
   }

   public static AcquisitionEvent createAcquisitionSequenceEndEvent(AcquisitionBase acq) {
      AcquisitionEvent evt = new AcquisitionEvent(acq);
      evt.specialFlag_ = SpecialFlag.AcqusitionSequenceEnd;
      return evt;
   }

   public boolean isAcquisitionSequenceEndEvent() {
      return specialFlag_ == SpecialFlag.AcqusitionSequenceEnd;
   }

   public Double getZPosition() {
      return zPosition_;
   }

   public String getChannelName() {
      if (channelName_ == null) {
         return "";
      }
      return channelName_;
   }

   public XYStagePosition getXY() {
      return xyPosition_;
   }

   public long getMinimumStartTime() {
      return miniumumStartTime_;
   }

   public List<AcquisitionEvent> getSequence() {
      return sequence_;
   }

   public boolean isExposureSequenced() {
      return exposureSequenced_;
   }

   public boolean isChannelSequenced() {
      return channelSequenced_;
   }

   public boolean isXYSequenced() {
      return xySequenced_;
   }

   public boolean isZSequenced() {
      return zSequenced_;
   }

   //For debugging
   @Override
   public String toString() {
      if (specialFlag_ == SpecialFlag.AcqusitionFinished) {
         return "Acq finished event";
      } else if (specialFlag_ == SpecialFlag.AcqusitionSequenceEnd) {
         return "Acq sequence end event";
      }

      StringBuilder builder = new StringBuilder();
      builder.append("Channel: " + channelName_);
      if (xyPosition_ != null) {
         builder.append("\tXY: " + xyPosition_.getCenter());
      }
      for (String axis : axisPositions_.keySet()) {
         builder.append("\t" + axis + ": " + axisPositions_.get(axis));
      }

      return builder.toString();
   }

}

class Triplet implements Comparable<Triplet> {

   final String dev, prop, val;

   public Triplet(String[] trip) {
      dev = trip[0];
      prop = trip[1];
      val = trip[2];
   }

   @Override
   public int compareTo(Triplet t) {
      if (!dev.equals(t.dev)) {
         return dev.compareTo(dev);
      } else {
         return prop.compareTo(prop);
      }
   }

}
