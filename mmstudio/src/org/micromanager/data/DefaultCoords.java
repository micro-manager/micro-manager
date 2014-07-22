package org.micromanager.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.micromanager.api.data.Coords;
import org.micromanager.utils.ReportingUtils;

/**
 * DefaultCoords indicate the position of a given image within a dataset.
 * They are immutable, constructed using a Builder pattern.
 */
public class DefaultCoords implements Coords {

   public static class DefaultCoordsBuilder implements Coords.CoordsBuilder {
      // Maps axis labels to our position along those axes.
      private HashMap<String, Integer> axisToPos_;
      // Axes for which we are the last image along that axis.
      private Set<String> terminalAxes_;
      // Convenience/optimization: we maintain a sorted list of our axes.
      private ArrayList<String> sortedAxes_;

      public DefaultCoordsBuilder() {
         axisToPos_ = new HashMap<String, Integer>();
         terminalAxes_ = new HashSet<String>();
         sortedAxes_ = new ArrayList<String>();
      }

      public DefaultCoords build() {
         return new DefaultCoords(this);
      }
      
      public CoordsBuilder position(String axis, int position) {
         axisToPos_.put(axis, new Integer(position));
         sortedAxes_.add(axis);
         try {
            Collections.sort(sortedAxes_);
         }
         catch (UnsupportedOperationException e) {
            ReportingUtils.logError(e, "Unable to sort coordinate axes");
         }
         return this;
      }

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
      
      public CoordsBuilder isAxisEndFor(String axis) {
         terminalAxes_.add(axis);
         return this;
      }
   }

   // Maps axis labels to our position along those axes.
   private HashMap<String, Integer> axisToPos_;
   // Axes for which we are the last image along that axis.
   private Set<String> terminalAxes_;
   // Convenience/optimization: we maintain a sorted list of our axes.
   private ArrayList<String> sortedAxes_;

   public DefaultCoords(DefaultCoordsBuilder builder) {
      axisToPos_ = builder.axisToPos_;
      terminalAxes_ = builder.terminalAxes_;
      sortedAxes_ = builder.sortedAxes_;
   }
   
   public int getPositionAt(String axis) {
      if (axisToPos_.containsKey(axis)) {
         return axisToPos_.get(axis);
      }
      return -1;
   }
   
   public boolean getIsAxisEndFor(String axis) {
      return terminalAxes_.contains(axis);
   }

   public Set<String> getTerminalAxes() {
      return new HashSet<String>(terminalAxes_);
   }

   public List<String> getAxes() {
      return new ArrayList<String>(sortedAxes_);
   }

   public CoordsBuilder copy() {
      DefaultCoordsBuilder result = new DefaultCoordsBuilder();
      for (String axis : axisToPos_.keySet()) {
         result.position(axis, axisToPos_.get(axis));
      }
      for (String axis : terminalAxes_) {
         result.isAxisEndFor(axis);
      }
      return result;
   }
}
