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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ArrayUtils;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Coords;
import static org.micromanager.data.Coords.CHANNEL;
import static org.micromanager.data.Coords.STAGE_POSITION;
import static org.micromanager.data.Coords.TIME_POINT;
import static org.micromanager.data.Coords.Z_SLICE;


public final class DefaultCoords implements Coords {

   public static class Builder implements Coords.Builder {
      // Since we only hold several axes, array lists are likely more efficient
      // than a LinkedHashMap
      private final List<String> axes_;
      private final List<Integer> indices_;

      public Builder() {
         axes_ = new ArrayList<String>(5);
         indices_ = new ArrayList<Integer>(5);
      }

      private Builder(List<String> axes, List<Integer> indices) {
         axes_ = new ArrayList<String>(axes);
         indices_ = new ArrayList<Integer>(indices);
      }

      @Override
      public DefaultCoords build() {
         return new DefaultCoords(this);
      }

      @Override
      public Builder index(String axis, int index) {
         Preconditions.checkArgument(isValidAxis(axis), "Invalid axis name");
         if (index < 0) {
            return removeAxis(axis);
         }

         removeAxis(axis);
         axes_.add(axis);
         indices_.add(index);
         return this;
      }

      @Override
      public Builder removeAxis(String axis) {
         int i = axes_.indexOf(axis);
         if (i >= 0) {
            axes_.remove(i);
            indices_.remove(i);
         }
         return this;
      }

      @Override
      public Builder offset(String axis, int offset)
            throws IllegalArgumentException, IndexOutOfBoundsException
      {
         int i = axes_.indexOf(axis);
         if (i < 0) {
            throw new IllegalArgumentException(
                  "Coords does not have index for axis \"" + axis + "\"");
         }
         int newIndex = indices_.get(i) + offset;
         if (newIndex < 0) {
            throw new IndexOutOfBoundsException(
                  "Offset would make Coords have negative index for axis \"" +
                        axis + "\"");
         }
         indices_.set(i, newIndex);
         return this;
      }

      @Override public Builder timePoint(int frame) { return index(TIME_POINT, frame); }
      @Override @Deprecated public Builder time(int frame) { return timePoint(frame); }
      @Override public Builder t(int frame) { return timePoint(frame); }

      @Override public Builder stagePosition(int index) { return index(STAGE_POSITION, index); }
      @Override public Builder p(int index) { return stagePosition(index); }

      @Override public Builder zSlice(int slice) { return index(Z_SLICE, slice); }
      @Override public Builder z(int slice) { return zSlice(slice); }

      @Override public Builder channel(int channel) { return index(CHANNEL, channel); }
      @Override public Builder c(int channel) { return channel(channel); }
   }

   // Since we only hold several axes, array lists are likely more efficient
   // than a LinkedHashMap
   private final List<String> axes_;
   private final List<Integer> indices_;

   public DefaultCoords(Builder builder) {
      axes_ = ImmutableList.copyOf(builder.axes_);
      indices_ = ImmutableList.copyOf(builder.indices_);
   }

   @Override
   public int getIndex(String axis) {
      int i = axes_.indexOf(axis);
      if (i < 0) {
         return -1;
      }
      return indices_.get(i);
   }

   @Override public int getTimePoint() { return getIndex(TIME_POINT); }
   @Override public int getT() { return getTimePoint(); }
   @Override public int getStagePosition() { return getIndex(STAGE_POSITION); }
   @Override public int getP() { return getStagePosition(); }
   @Override public int getZSlice() { return getIndex(Z_SLICE); }
   @Override public int getZ() { return getZSlice(); }
   @Override public int getChannel() { return getIndex(Coords.CHANNEL); }
   @Override public int getC() { return getChannel(); }
   @Override @Deprecated public int getTime() { return getTimePoint(); }

   @Override
   public List<String> getAxes() {
      return new ArrayList<String>(axes_);
   }

   @Override
   public boolean hasAxis(String axis) {
      return axes_.contains(axis);
   }

   @Override public boolean hasTimePointAxis() { return hasAxis(TIME_POINT); }
   @Override public boolean hasT() { return hasTimePointAxis(); }
   @Override public boolean hasStagePositionAxis() { return hasAxis(STAGE_POSITION); }
   @Override public boolean hasP() { return hasStagePositionAxis(); }
   @Override public boolean hasZSliceAxis() { return hasAxis(Z_SLICE); }
   @Override public boolean hasZ() { return hasZSliceAxis(); }
   @Override public boolean hasChannelAxis() { return hasAxis(Coords.CHANNEL); }
   @Override public boolean hasC() { return hasChannelAxis(); }

   @Override
   public boolean isSuperspaceCoordsOf(Coords other) {
      for (String axis : axes_) {
         // If other doesn't have axis, -1 != this.getIndex(axis)
         if (other.getIndex(axis) != getIndex(axis)) {
            return false;
         }
      }
      return true;
   }

   @Override
   public boolean isSubspaceCoordsOf(Coords other) {
      return other.isSuperspaceCoordsOf(this);
   }

   @Override
   @Deprecated
   public boolean matches(Coords other) {
      return isSubspaceCoordsOf(other);
   }

   @Override
   public Builder copyBuilder() {
      return new Builder(axes_, indices_);
   }

   @Override
   @Deprecated
   public Builder copy() {
      return copyBuilder();
   }

   @Override
   public Coords copyRemovingAxes(String... axes) {
      Builder b = copyBuilder();
      for (String axis : axes) {
         b.removeAxis(axis);
      }
      return b.build();
   }

   @Override
   public Coords copyRetainingAxes(String... axes) {
      Builder b = new Builder();
      for (String axis : axes_) {
         if (ArrayUtils.contains(axes, axis)) {
            b.index(axis, getIndex(axis));
         }
      }
      return b.build();
   }

   @Override
   public boolean equals(Object other) {
      if (!(other instanceof Coords)) {
         return false;
      }
      // Axis order is not considered for equality
      List<String> thisAxes = getAxes();
      List<String> otherAxes = ((Coords) other).getAxes();
      if (thisAxes.size() != otherAxes.size()) {
         return false;
      }
      for (String axis : thisAxes) {
         if (!otherAxes.contains(axis)) {
            return false;
         }
         if (((Coords) other).getIndex(axis) != getIndex(axis)) {
            return false;
         }
      }
      return true;
   }
   
   @Override
   public int hashCode() {
      // Axis order is not considered for equality
      List<String> axes = getAxes();
      Collections.sort(axes);
      List<Integer> indices = new ArrayList<Integer>(axes.size());
      for (String axis : axes) {
         indices.add(getIndex(axis));
      }
      int hash = 3;
      hash = 23 * hash + axes.hashCode();
      hash = 23 * hash + indices.hashCode();
      return hash;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder().append("<");
      boolean isFirst = true;
      for (String axis : axes_) {
         if (!isFirst) {
            sb.append(" ");
         }
         isFirst = false;
         sb.append(String.format("%s=%d", axis, getIndex(axis)));
      }
      return sb.append(">").toString();
   }

   public PropertyMap toPropertyMap() {
      PropertyMap.Builder b = PropertyMaps.builder();
      for (String axis : axes_) {
         b.putInteger(axis, getIndex(axis));
      }
      return b.build();
   }

   public static Coords fromPropertyMap(PropertyMap pmap) {
      Builder b = new Builder();
      for (String axis : pmap.keySet()) {
         b.index(axis, pmap.getInteger(axis, -1));
      }
      return b.build();
   }


   /**
    * Generate a normalized string representation of this Coords, that we can
    * later parse out using {@link #fromNormalizedString}.
    */
   @Deprecated
   public String toNormalizedString() {
      StringBuilder sb = new StringBuilder();
      for (String axis : axes_) {
         // Trailing commas are allowed
         sb.append(String.format("%s=%d,", axis, getIndex(axis)));
      }
      return sb.toString();
   }

   /**
    * Generate a Coords from a string using the normalized string format.
    */
   public static DefaultCoords fromNormalizedString(String def) throws IllegalArgumentException {
      Builder builder = new Builder();

      def = def.replaceAll("\\s+", ""); // Ignore all whitespace
      for (String token : def.split(",")) {
         if (token.equals("")) {
            // Either a trailing comma or an "empty" entry; either way we'll
            // just ignore it.
            continue;
         }
         String[] components = token.split("=");
         if (components.length != 2) {
            throw new IllegalArgumentException("Malformatted coords string");
         }
         String axis = components[0];
         if (!isValidAxis(axis)) {
            throw new IllegalArgumentException("Malformatted coords string: axis " + axis + " is not a valid name");
         }

         // Shorthands allowed for axis names
         if (axis.equals("t")) axis = TIME_POINT;
         if (axis.equals("p")) axis = STAGE_POSITION;
         if (axis.equals("z")) axis = Z_SLICE;
         if (axis.equals("c")) axis = CHANNEL;

         int index;
         try {
            index = Integer.parseInt(components[1]);
         }
         catch (NumberFormatException e) {
            throw new IllegalArgumentException("Malformatted coords string: position of axis " + axis + " is not an integer");
         }

         builder.index(axis, index);
      }

      return builder.build();
   }

   private static Pattern AXIS_NAME_PATTERN =
         Pattern.compile("[A-Za-z]+[A-Za-z0-9_]*");
   public static boolean isValidAxis(String axis) {
      return AXIS_NAME_PATTERN.matcher(axis).matches();
   }
}