package org.micromanager.data.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * DefaultCoords indicate the position of a given image within a dataset.
 * They are immutable, constructed using a Builder pattern.
 */
public class DefaultCoords implements Coords, Comparable<DefaultCoords> {

   public static class Builder implements Coords.CoordsBuilder {
      // Maps axis labels to our position along those axes.
      private HashMap<String, Integer> axisToPos_;
      // Axes for which we are the last image along that axis.
      private Set<String> terminalAxes_;
      // Convenience/optimization: we maintain a sorted list of our axes.
      private ArrayList<String> sortedAxes_;

      public Builder() {
         axisToPos_ = new HashMap<String, Integer>();
         terminalAxes_ = new HashSet<String>();
         sortedAxes_ = new ArrayList<String>();
      }

      @Override
      public DefaultCoords build() {
         return new DefaultCoords(this);
      }

      @Override
      public CoordsBuilder time(int time) {
         return position(Coords.TIME, time);
      }

      @Override
      public CoordsBuilder channel(int channel) {
         return position(Coords.CHANNEL, channel);
      }

      @Override
      public CoordsBuilder stagePosition(int stagePosition) {
         return position(Coords.STAGE_POSITION, stagePosition);
      }

      @Override
      public CoordsBuilder z(int z) {
         return position(Coords.Z, z);
      }
      
      @Override
      public CoordsBuilder position(String axis, int position) {
         axisToPos_.put(axis, new Integer(position));
         if (!sortedAxes_.contains(axis)) {
            sortedAxes_.add(axis);
            try {
               Collections.sort(sortedAxes_);
            }
            catch (UnsupportedOperationException e) {
               ReportingUtils.logError(e, "Unable to sort coordinate axes");
            }
         }
         return this;
      }

      @Override
      public CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException {
         if (!axisToPos_.containsKey(axis)) {
            throw new IllegalArgumentException("Axis " + axis + " is not a part of this CoordsBuilder.");
         }
         int curVal = axisToPos_.get(axis);
         if (curVal + offset < 0) {
            throw new IllegalArgumentException("Adding offset " + offset + " to current position " + curVal + " for axis " + axis + " would result in a negative position.");
         }
         position(axis, curVal + offset);
         return this;
      }
      
      @Override
      public CoordsBuilder isAxisEndFor(String axis) {
         terminalAxes_.add(axis);
         return this;
      }

      @Override
      public int getPositionAt(String axis) {
         if (axisToPos_.containsKey(axis)) {
            return axisToPos_.get(axis);
         }
         return -1;
      }
   }

   // Maps axis labels to our position along those axes.
   private HashMap<String, Integer> axisToPos_;
   // Axes for which we are the last image along that axis.
   private Set<String> terminalAxes_;
   // Convenience/optimization: we maintain a sorted list of our axes.
   private ArrayList<String> sortedAxes_;

   public DefaultCoords(Builder builder) {
      axisToPos_ = new HashMap<String, Integer>(builder.axisToPos_);
      terminalAxes_ = new HashSet<String>(builder.terminalAxes_);
      sortedAxes_ = new ArrayList<String>(builder.sortedAxes_);
   }
   
   @Override
   public int getPositionAt(String axis) {
      if (axisToPos_.containsKey(axis)) {
         return axisToPos_.get(axis);
      }
      return -1;
   }

   @Override
   public int getChannel() {
      return getPositionAt(Coords.CHANNEL);
   }
   
   @Override
   public int getTime() {
      return getPositionAt(Coords.TIME);
   }

   @Override
   public int getZ() {
      return getPositionAt(Coords.Z);
   }

   @Override
   public int getStagePosition() {
      return getPositionAt(Coords.STAGE_POSITION);
   }
   
   @Override
   public boolean getIsAxisEndFor(String axis) {
      return terminalAxes_.contains(axis);
   }

   @Override
   public Set<String> getTerminalAxes() {
      return new HashSet<String>(terminalAxes_);
   }

   @Override
   public List<String> getAxes() {
      return new ArrayList<String>(sortedAxes_);
   }

   @Override
   public boolean matches(Coords alt) {
      for (String axis : alt.getAxes()) {
         if (getPositionAt(axis) != alt.getPositionAt(axis)) {
            return false;
         }
      }
      return true;
   }

   @Override
   public CoordsBuilder copy() {
      Builder result = new Builder();
      for (String axis : axisToPos_.keySet()) {
         result.position(axis, axisToPos_.get(axis));
      }
      for (String axis : terminalAxes_) {
         result.isAxisEndFor(axis);
      }
      return result;
   }

   /**
    * Compare us to the other DefaultCoords; useful for sorting. We go through
    * our axes in alphabetical order and compare positions. Axes that we have
    * and it does not are presumed "less than", so a DefaultCoords with no
    * axes should be "greater than" all others.
    */
   @Override
   public int compareTo(DefaultCoords alt) {
      for (String axis : sortedAxes_) {
         int ourPosition = getPositionAt(axis);
         int altPosition = alt.getPositionAt(axis);
         if (altPosition == -1) {
            // They have no position along this axis, so we come first.
            return -1;
         }
         else if (altPosition != ourPosition) {
            // They have a position on this axis and it's different from ours.
            return (ourPosition < altPosition) ? -1 : 1;
         }
      }
      // Equal for all axes we care about.
      return 0;
   }

   /**
    * Generate a hash of this DefaultCoords. We want to be able to refer to
    * images by their coordinates (e.g. in HashMaps), which requires a 
    * consistent mechanism for identifying a specific coordinate value.
    */
   @Override
   public int hashCode() {
      int result = 0;
      int multiplier = 23; // Semi-randomly-chosen prime number
      for (String axis : sortedAxes_) {
         result = result * multiplier + axis.hashCode();
         result = result * multiplier + getPositionAt(axis);
      }
      return result;
   }

   /**
    * Since we override hashCode, we should override equals as well.
    */
   @Override
   public boolean equals(Object alt) {
      if (!(alt instanceof DefaultCoords) || 
            compareTo((DefaultCoords) alt) != 0) {
         return false;
      }
      // Manually verify that we have the same set of axes.
      HashSet<String> ourAxes = new HashSet<String>(sortedAxes_);
      HashSet<String> altAxes = new HashSet<String>(((DefaultCoords) alt).getAxes());
      return ourAxes.equals(altAxes);
   }

   /**
    * Convert to string. Should only be used for debugging (?).
    */
   public String toString() {
      String result = "<";
      boolean isFirst = true;
      for (String axis : sortedAxes_) {
         if (!isFirst) {
            result += ", ";
         }
         isFirst = false;
         result += String.format("%s: %d", axis, getPositionAt(axis));
      }
      result += ">";
      return result;
   }

   /**
    * Legacy method: convert from the position information in the JSONObject
    * of a TaggedImage. This is pretty messy with all the try/catches, but we
    * don't want to lose all the positions just because a single one is
    * unavailable.
    */
   public static DefaultCoords legacyFromJSON(JSONObject tags) {
      Builder builder = new Builder();
      try {
         if (MDUtils.hasChannelIndex(tags)) {
            builder.position("channel", MDUtils.getChannelIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract channel coordinate from tags");
      }
      try {
         if (MDUtils.hasSliceIndex(tags)) {
            builder.position("z", MDUtils.getSliceIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract z coordinate from tags");
      }
      try {
         if (MDUtils.hasFrameIndex(tags)) {
            builder.position("time", MDUtils.getFrameIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract time coordinate from tags");
      }
      try {
         if (MDUtils.hasPositionIndex(tags)) {
            builder.position("position", MDUtils.getPositionIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract position coordinate from tags");
      }
      return builder.build();
   }
}
