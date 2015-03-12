///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package org.micromanager.data.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
      // Maps axis labels to our index along those axes.
      private HashMap<String, Integer> axisToPos_;
      // Convenience/optimization: we maintain a sorted list of our axes.
      private ArrayList<String> sortedAxes_;

      public Builder() {
         axisToPos_ = new HashMap<String, Integer>();
         sortedAxes_ = new ArrayList<String>();
      }

      @Override
      public DefaultCoords build() {
         return new DefaultCoords(this);
      }

      @Override
      public CoordsBuilder time(int time) {
         return index(Coords.TIME, time);
      }

      @Override
      public CoordsBuilder channel(int channel) {
         return index(Coords.CHANNEL, channel);
      }

      @Override
      public CoordsBuilder stagePosition(int stagePosition) {
         return index(Coords.STAGE_POSITION, stagePosition);
      }

      @Override
      public CoordsBuilder z(int z) {
         return index(Coords.Z, z);
      }
      
      @Override
      public CoordsBuilder index(String axis, int index) {
         if (index < 0 && axisToPos_.containsKey(axis)) {
            // Delete the axis instead.
            axisToPos_.remove(axis);
            sortedAxes_.remove(axis);
            return this;
         }

         axisToPos_.put(axis, new Integer(index));
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
      public CoordsBuilder removeAxis(String axis) {
         return index(axis, -1);
      }

      @Override
      public CoordsBuilder offset(String axis, int offset) throws IllegalArgumentException {
         if (!axisToPos_.containsKey(axis)) {
            throw new IllegalArgumentException("Axis " + axis + " is not a part of this CoordsBuilder.");
         }
         int curVal = axisToPos_.get(axis);
         if (curVal + offset < 0) {
            throw new IllegalArgumentException("Adding offset " + offset + " to current index " + curVal + " for axis " + axis + " would result in a negative index.");
         }
         index(axis, curVal + offset);
         return this;
      }
   }

   // Maps axis labels to our index along those axes.
   private HashMap<String, Integer> axisToPos_;
   // Convenience/optimization: we maintain a sorted list of our axes.
   private ArrayList<String> sortedAxes_;

   public DefaultCoords(Builder builder) {
      axisToPos_ = new HashMap<String, Integer>(builder.axisToPos_);
      sortedAxes_ = new ArrayList<String>(builder.sortedAxes_);
   }
   
   @Override
   public int getIndex(String axis) {
      if (axisToPos_.containsKey(axis)) {
         return axisToPos_.get(axis);
      }
      return -1;
   }

   @Override
   public int getChannel() {
      return getIndex(Coords.CHANNEL);
   }
   
   @Override
   public int getTime() {
      return getIndex(Coords.TIME);
   }

   @Override
   public int getZ() {
      return getIndex(Coords.Z);
   }

   @Override
   public int getStagePosition() {
      return getIndex(Coords.STAGE_POSITION);
   }
   
   @Override
   public List<String> getAxes() {
      return new ArrayList<String>(sortedAxes_);
   }

   @Override
   public boolean matches(Coords alt) {
      for (String axis : alt.getAxes()) {
         if (getIndex(axis) != alt.getIndex(axis)) {
            return false;
         }
      }
      return true;
   }

   @Override
   public CoordsBuilder copy() {
      Builder result = new Builder();
      for (String axis : axisToPos_.keySet()) {
         result.index(axis, axisToPos_.get(axis));
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
         int ourPosition = getIndex(axis);
         int altPosition = alt.getIndex(axis);
         if (altPosition == -1) {
            // They have no index along this axis, so we come first.
            return -1;
         }
         else if (altPosition != ourPosition) {
            // They have a index on this axis and it's different from ours.
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
         result = result * multiplier + getIndex(axis);
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
         result += String.format("%s: %d", axis, getIndex(axis));
      }
      result += ">";
      return result;
   }

   /**
    * Legacy method: convert from the index information in the JSONObject
    * of a TaggedImage. This is pretty messy with all the try/catches, but we
    * don't want to lose all the positions just because a single one is
    * unavailable.
    */
   public static DefaultCoords legacyFromJSON(JSONObject tags) {
      Builder builder = new Builder();
      try {
         if (MDUtils.hasChannelIndex(tags)) {
            builder.channel(MDUtils.getChannelIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract channel coordinate from tags");
      }
      try {
         if (MDUtils.hasSliceIndex(tags)) {
            builder.z(MDUtils.getSliceIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract z coordinate from tags");
      }
      try {
         if (MDUtils.hasFrameIndex(tags)) {
            builder.time(MDUtils.getFrameIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract time coordinate from tags");
      }
      try {
         if (MDUtils.hasPositionIndex(tags)) {
            builder.stagePosition(MDUtils.getPositionIndex(tags));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError("Couldn't extract position coordinate from tags");
      }
      return builder.build();
   }
}
